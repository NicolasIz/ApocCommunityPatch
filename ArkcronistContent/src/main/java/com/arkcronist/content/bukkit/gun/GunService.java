package com.arkcronist.content.bukkit.gun;

import com.arkcronist.content.bukkit.EngineSettings;
import com.arkcronist.content.bukkit.hooks.HookManager;
import com.arkcronist.content.bukkit.hud.ActionBars;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.ballistics.Aabb;
import com.arkcronist.content.core.ballistics.Ray;
import com.arkcronist.content.core.ballistics.RayCaster;
import com.arkcronist.content.core.ballistics.Shot;
import com.arkcronist.content.core.ballistics.Spread;
import com.arkcronist.content.core.ballistics.Vec3;
import com.arkcronist.content.core.ballistics.VoxelTraversal;
import com.arkcronist.content.core.definition.GunDefinition;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Guns: items with a {@code gun:} section, fired with a right click.
 *
 * <p>A shot goes through three steps, each on the thread it belongs on:</p>
 * <ol>
 *   <li><b>Main thread - the copy.</b> Only the server thread may read the world, so it copies
 *       what the shot needs and nothing else: the pellets' directions, the collision boxes of the
 *       blocks each pellet's line passes through (walked block by block, stopping at the first full
 *       block), and the hitboxes of the living entities near the line. Values only - no entity or
 *       block is handed on.</li>
 *   <li><b>{@code ArkContent-Guns} - the trace.</b> A {@link CompletableFuture} on the guns' own
 *       threads traces every pellet through that copy ({@link Shot#resolve()}): walls, slabs,
 *       hitboxes, heads, piercing, falloff, and every pellet's damage added up per target.</li>
 *   <li><b>Main thread - the hit.</b> The damage is dealt as a {@link DamageSource} whose causing
 *       and direct entity is the shooter, so it is a player's attack to everyone listening:
 *       WorldGuard's {@code pvp} flag, GriefPrevention's claim PvP rules, MythicMobs' damage
 *       triggers and threat tables, armour and enchantments all apply. A target that has died or
 *       left meanwhile is skipped.</li>
 * </ol>
 *
 * <p>Rounds in the magazine live on the gun's own stack, so they travel with it - into a chest,
 * to another player. While a gun is being fired the count is kept in memory instead, and written
 * to the stack when the gun is put away, moved, dropped, reloaded, or the world saves: every write
 * changes the stack, and a changed stack makes the client lower and raise the held item, which on
 * every shot would leave the gun bobbing. A reload takes {@code reload-seconds}, and is called off
 * if the gun leaves the hand.</p>
 */
public final class GunService {

    /** One shot's world copy: kept small so a stray huge range does not read the whole map. */
    private static final int MAX_VOXELS_PER_PELLET = 512;
    private static final int AMMO_BAR_TICKS = 40;

    private record Reload(int slot, String gun, BukkitTask task) {
    }

    /**
     * The gun a player is firing: which slot, which gun (by the id written on its stack the first
     * time it is fired), and the rounds left, not yet written to the stack.
     */
    private static final class Session {

        final int slot;
        final String gunId;
        int loaded;

        Session(int slot, String gunId, int loaded) {
            this.slot = slot;
            this.gunId = gunId;
            this.loaded = loaded;
        }
    }

    /** What the ammunition placeholders show: read from any thread. */
    public record AmmoView(int loaded, int magazine, int reserve, boolean reloading) {
    }

    private final Plugin plugin;
    private final ItemRegistry items;
    private final ItemFactory factory;
    private final HookManager hooks;
    private final ActionBars actionBars;
    private final Logger logger;
    private final NamespacedKey ammoKey;
    private final NamespacedKey gunIdKey;
    private final EngineSettings.Guns settings;
    private final ExecutorService tracer;
    private final Executor mainThread;
    private final Map<UUID, Long> lastShot = new HashMap<>();
    private final Map<UUID, Reload> reloads = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, AmmoView> views = new ConcurrentHashMap<>();
    private final Set<String> warned = new HashSet<>();

    public GunService(Plugin plugin, ItemRegistry items, ItemFactory factory, HookManager hooks, ActionBars actionBars,
                      EngineSettings.Guns settings) {
        this.plugin = plugin;
        this.items = items;
        this.factory = factory;
        this.hooks = hooks;
        this.actionBars = actionBars;
        this.logger = plugin.getLogger();
        this.ammoKey = new NamespacedKey(plugin, "gun_ammo");
        this.gunIdKey = new NamespacedKey(plugin, "gun_id");
        this.settings = settings;
        int threads = settings.threads();
        AtomicInteger count = new AtomicInteger();
        this.tracer = Executors.newFixedThreadPool(Math.max(1, threads), runnable -> {
            Thread thread = new Thread(runnable, "ArkContent-Guns-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.mainThread = task -> {
            if (plugin.isEnabled()) {
                plugin.getServer().getScheduler().runTask(plugin, task);
            }
        };
    }

    /** The gun this stack is, if it is one. */
    public Optional<GunDefinition> gun(@Nullable ItemStack stack) {
        return factory.identify(stack).map(item -> item.definition().gun());
    }

    // ------------------------------------------------------------------ shooting

    /**
     * A right click with a gun in the main hand. Main thread.
     *
     * @return the shot's outcome once dealt; empty when no shot was fired (cooling down, reloading,
     *         empty)
     */
    public Optional<CompletableFuture<Shot.Outcome<UUID>>> trigger(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack stack = inventory.getItemInMainHand();
        Optional<CustomItem> custom = factory.identify(stack);
        GunDefinition gun = custom.map(item -> item.definition().gun()).orElse(null);
        if (gun == null || reloads.containsKey(player.getUniqueId())) {
            return Optional.empty();
        }
        long now = plugin.getServer().getCurrentTick();
        Long last = lastShot.get(player.getUniqueId());
        if (last != null && now - last < gun.fireDelayTicks()) {
            return Optional.empty();
        }
        Session session = session(player, stack, gun);
        if (session.loaded <= 0) {
            lastShot.put(player.getUniqueId(), now);
            if (!reload(player)) {
                play(player.getEyeLocation(), gun.sounds().empty());
            }
            return Optional.empty();
        }
        lastShot.put(player.getUniqueId(), now);
        if (player.getGameMode() != GameMode.CREATIVE) {
            session.loaded--;
        }

        Shot<UUID> shot = copy(player, gun);
        play(player.getEyeLocation(), gun.sounds().shoot());
        kick(player, gun.recoil());
        showAmmo(player, custom.get(), stack, gun);

        UUID shooter = player.getUniqueId();
        World world = player.getWorld();
        CompletableFuture<Shot.Outcome<UUID>> outcome = CompletableFuture.supplyAsync(shot::resolve, tracer);
        outcome.thenAcceptAsync(result -> deal(shooter, world, gun, result), mainThread)
                .exceptionally(error -> {
                    logger.log(Level.WARNING, "A shot could not be traced", error);
                    return null;
                });
        return Optional.of(outcome);
    }

    /** Step one: the world as the shot needs it, copied on the main thread. */
    private Shot<UUID> copy(Player player, GunDefinition gun) {
        Location eyeLocation = player.getEyeLocation();
        World world = eyeLocation.getWorld();
        Vec3 eye = new Vec3(eyeLocation.getX(), eyeLocation.getY(), eyeLocation.getZ());
        Vec3 forward = Vec3.fromRotation(eyeLocation.getYaw(), eyeLocation.getPitch());
        double spread = player.isSneaking() ? gun.sneakSpread() : gun.spread();
        List<Vec3> directions = Spread.directions(forward, spread, gun.pellets(), ThreadLocalRandom.current());
        double range = gun.range();

        Map<Long, List<Aabb>> blocks = new HashMap<>();
        Aabb reach = new Aabb(eye.x(), eye.y(), eye.z(), eye.x(), eye.y(), eye.z());
        for (Vec3 direction : directions) {
            Ray ray = new Ray(eye, direction);
            AtomicInteger visited = new AtomicInteger();
            Optional<VoxelTraversal.Voxel> stop = VoxelTraversal.walk(ray, range, voxel ->
                    visited.incrementAndGet() > MAX_VOXELS_PER_PELLET || copyBlock(world, voxel, blocks));
            Vec3 end = ray.at(stop.map(VoxelTraversal.Voxel::distance).orElse(range) + 1);
            reach = reach.union(Aabb.between(eye, end));
        }
        List<Aabb> obstacles = new ArrayList<>();
        blocks.values().forEach(obstacles::addAll);

        List<RayCaster.Target<UUID>> targets = new ArrayList<>();
        BoundingBox area = new BoundingBox(reach.minX(), reach.minY(), reach.minZ(), reach.maxX(), reach.maxY(),
                reach.maxZ()).expand(1);
        for (Entity entity : world.getNearbyEntities(area, entity -> targetable(player, entity, gun))) {
            LivingEntity living = (LivingEntity) entity;
            BoundingBox box = living.getBoundingBox();
            Aabb body = new Aabb(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
            // The head: from just under the eyes up, and never more than the top half of the body.
            double headFrom = Math.max(living.getEyeLocation().getY() - 0.25, box.getMinY() + box.getHeight() / 2);
            targets.add(new RayCaster.Target<>(living.getUniqueId(), body, body.above(headFrom)));
        }
        return new Shot<>(eye, directions, range, obstacles, targets, gun.pierce(), gun.damage(),
                gun.headshotMultiplier(), gun.falloff());
    }

    /**
     * Copies one block's collision boxes. Blocks a shot flies through - air, grass, water, this
     * plugin's liquids - have none.
     *
     * @return true when nothing gets past this block, so the pellet's walk can stop
     */
    private static boolean copyBlock(World world, VoxelTraversal.Voxel voxel, Map<Long, List<Aabb>> blocks) {
        if (voxel.y() < world.getMinHeight() || voxel.y() >= world.getMaxHeight()) {
            return false;
        }
        if (!world.isChunkLoaded(voxel.x() >> 4, voxel.z() >> 4)) {
            // An unloaded chunk is treated as a wall rather than loaded for a bullet.
            blocks.put(key(voxel), List.of(Aabb.block(voxel.x(), voxel.y(), voxel.z())));
            return true;
        }
        long key = key(voxel);
        List<Aabb> known = blocks.get(key);
        if (known == null) {
            Block block = world.getBlockAt(voxel.x(), voxel.y(), voxel.z());
            if (block.isPassable()) {
                known = List.of();
            } else {
                known = new ArrayList<>();
                for (BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
                    known.add(new Aabb(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(),
                            box.getMaxZ()).offset(voxel.x(), voxel.y(), voxel.z()));
                }
            }
            blocks.put(key, known);
        }
        return known.size() == 1 && known.get(0).isFullBlock();
    }

    private static long key(VoxelTraversal.Voxel voxel) {
        return ((long) voxel.x() & 0x3FFFFFF) << 38 | ((long) voxel.z() & 0x3FFFFFF) << 12 | (voxel.y() & 0xFFF);
    }

    private boolean targetable(Player shooter, Entity entity, GunDefinition gun) {
        if (!(entity instanceof LivingEntity living) || entity.equals(shooter) || living.isDead() || !living.isValid()
                || living.isInvulnerable() || entity instanceof ArmorStand stand && stand.isMarker()) {
            return false;
        }
        if (entity instanceof Player player) {
            return gun.targets().contains(GunDefinition.TargetKind.PLAYERS)
                    && player.getGameMode() != GameMode.SPECTATOR && player.getGameMode() != GameMode.CREATIVE;
        }
        return gun.targets().contains(hooks.isMythicMob(entity) ? GunDefinition.TargetKind.MYTHIC_MOBS
                : GunDefinition.TargetKind.MOBS);
    }

    /** Step three: back on the main thread, the damage and what shows it. */
    private void deal(UUID shooterId, World world, GunDefinition gun, Shot.Outcome<UUID> outcome) {
        Player shooter = plugin.getServer().getPlayer(shooterId);
        draw(world, gun, outcome);
        if (shooter == null || !shooter.getWorld().equals(world)) {
            return;
        }
        DamageType type = damageType(gun);
        for (Map.Entry<UUID, Shot.Damage> entry : outcome.damage().entrySet()) {
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity target) || target.isDead() || !target.getWorld().equals(world)) {
                continue;
            }
            Shot.Damage damage = entry.getValue();
            Location at = new Location(world, damage.nearest().x(), damage.nearest().y(), damage.nearest().z());
            DamageSource source = DamageSource.builder(type)
                    .withCausingEntity(shooter)
                    .withDirectEntity(shooter)
                    .withDamageLocation(shooter.getEyeLocation())
                    .build();
            // A gun fires faster than the half second a hit leaves a target untouchable for.
            target.setNoDamageTicks(0);
            double before = target.getHealth();
            target.damage(damage.amount(), source);
            boolean hurt = target.isDead() || target.getHealth() < before;
            if (hurt) {
                play(at, gun.sounds().hit());
                if (damage.headshot() && gun.sounds().headshot() != null) {
                    GunDefinition.Sound sound = gun.sounds().headshot();
                    shooter.playSound(shooter.getLocation(), sound.key(), SoundCategory.PLAYERS, sound.volume(),
                            sound.pitch());
                }
            }
        }
    }

    private DamageType damageType(GunDefinition gun) {
        NamespacedKey key = NamespacedKey.fromString(gun.damageType());
        DamageType type = key == null ? null
                : RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).get(key);
        if (type == null) {
            if (warned.add("damage-type " + gun.damageType())) {
                logger.warning("Guns: '" + gun.damageType() + "' is not a damage type on this server; using minecraft:arrow");
            }
            return DamageType.ARROW;
        }
        return type;
    }

    /** Each pellet's path, as particles every half block. */
    private void draw(World world, GunDefinition gun, Shot.Outcome<UUID> outcome) {
        Particle particle = particle(gun);
        if (particle == null) {
            return;
        }
        for (Shot.Path path : outcome.paths()) {
            Vec3 along = path.to().subtract(path.from());
            double length = along.length();
            if (length < 1) {
                continue;
            }
            Vec3 step = along.multiply(0.5 / length);
            int points = (int) Math.min(160, length / 0.5);
            // Starting a little ahead, so the trail does not cover the shooter's own view.
            for (int i = 2; i < points; i++) {
                Vec3 point = path.from().add(step.multiply(i));
                world.spawnParticle(particle, point.x(), point.y(), point.z(), 1, 0, 0, 0, 0);
            }
        }
    }

    private @Nullable Particle particle(GunDefinition gun) {
        if (gun.particle() == null) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(gun.particle());
        Particle particle = key == null ? null : Registry.PARTICLE_TYPE.get(key);
        if (particle == null || particle.getDataType() != Void.class) {
            if (warned.add("particle " + gun.particle())) {
                logger.warning("Guns: '" + gun.particle() + "' is not a particle that needs no extra data"
                        + " (like minecraft:crit or minecraft:smoke); no trail is drawn");
            }
            return null;
        }
        return particle;
    }

    /**
     * Recoil: the view jumps up, and a little to the side. The server turns the player; their
     * position, speed and any vehicle are left alone.
     */
    private static void kick(Player player, GunDefinition.Recoil recoil) {
        if (recoil.pitch() == 0 && recoil.yaw() == 0) {
            return;
        }
        double[] kick = recoil.kick(ThreadLocalRandom.current());
        Location now = player.getLocation();
        player.setRotation((float) (now.getYaw() + kick[0]),
                (float) Math.max(-90, Math.min(90, now.getPitch() + kick[1])));
    }

    private static void play(Location at, @Nullable GunDefinition.Sound sound) {
        if (sound != null) {
            at.getWorld().playSound(at, sound.key(), SoundCategory.PLAYERS, sound.volume(), sound.pitch());
        }
    }

    // ------------------------------------------------------------------ magazine and reloading

    /** Rounds in this gun's magazine, as written on the stack. A gun never fired yet comes full. */
    private int stored(ItemStack stack, GunDefinition gun) {
        Integer loaded = stack.getPersistentDataContainer().get(ammoKey, PersistentDataType.INTEGER);
        return loaded == null ? gun.magazine() : Math.max(0, Math.min(gun.magazine(), loaded));
    }

    /** Rounds in the magazine of the gun in {@code player}'s hand: in memory while it is being fired. */
    public int loaded(Player player, ItemStack stack, GunDefinition gun) {
        Session session = sessions.get(player.getUniqueId());
        String id = stack.getPersistentDataContainer().get(gunIdKey, PersistentDataType.STRING);
        if (session != null && session.slot == player.getInventory().getHeldItemSlot() && session.gunId.equals(id)) {
            return session.loaded;
        }
        return stored(stack, gun);
    }

    /** The session for the gun in the main hand, opened - and the gun given its id - if need be. */
    private Session session(Player player, ItemStack stack, GunDefinition gun) {
        int slot = player.getInventory().getHeldItemSlot();
        String id = stack.getPersistentDataContainer().get(gunIdKey, PersistentDataType.STRING);
        Session session = sessions.get(player.getUniqueId());
        if (session != null && session.slot == slot && session.gunId.equals(id)) {
            return session;
        }
        if (session != null) {
            flush(player);
        }
        if (id == null) {
            String fresh = UUID.randomUUID().toString();
            stack.editPersistentDataContainer(data -> data.set(gunIdKey, PersistentDataType.STRING, fresh));
            player.getInventory().setItem(slot, stack);
            id = fresh;
        }
        session = new Session(slot, id, stored(stack, gun));
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    /**
     * Writes the rounds of the gun being fired onto its stack and closes the session: the gun is
     * being put away, moved or dropped. Main thread.
     */
    public void flush(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack inSlot = inventory.getItem(session.slot);
        if (write(inSlot, session)) {
            inventory.setItem(session.slot, inSlot);
            return;
        }
        // Moved without this plugin hearing of it: wherever it went in the inventory.
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (write(contents[slot], session)) {
                inventory.setItem(slot, contents[slot]);
                return;
            }
        }
    }

    /** Writes the session onto {@code stack} if it is that gun; the stack has left the inventory. */
    public boolean flushInto(Player player, @Nullable ItemStack stack) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null && write(stack, session)) {
            sessions.remove(player.getUniqueId());
            return true;
        }
        return false;
    }

    private boolean write(@Nullable ItemStack stack, Session session) {
        if (stack == null || stack.isEmpty()
                || !session.gunId.equals(stack.getPersistentDataContainer().get(gunIdKey, PersistentDataType.STRING))) {
            return false;
        }
        stack.editPersistentDataContainer(data -> data.set(ammoKey, PersistentDataType.INTEGER, session.loaded));
        return true;
    }

    /** Writes every gun being fired onto its stack: the world is saving, or the plugin stopping. */
    public void flushAll() {
        for (UUID id : List.copyOf(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                flush(player);
            } else {
                sessions.remove(id);
            }
        }
    }

    /**
     * Starts reloading the gun in the main hand. Main thread.
     *
     * @return false when there is nothing to reload: the magazine is full, or the ammunition is gone
     */
    public boolean reload(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack stack = inventory.getItemInMainHand();
        Optional<CustomItem> custom = factory.identify(stack);
        GunDefinition gun = custom.map(item -> item.definition().gun()).orElse(null);
        if (gun == null || reloads.containsKey(player.getUniqueId())) {
            return false;
        }
        int loaded = loaded(player, stack, gun);
        if (loaded >= gun.magazine()) {
            return false;
        }
        Ammo ammo = ammo(custom.get(), gun);
        if (ammo == Ammo.MISSING) {
            return false;
        }
        int available = player.getGameMode() == GameMode.CREATIVE ? Integer.MAX_VALUE : ammo.count(inventory);
        if (gun.reloadAmount(loaded, available) <= 0) {
            actionBars.flash(player, settings.noAmmo(), AMMO_BAR_TICKS);
            return false;
        }
        int slot = inventory.getHeldItemSlot();
        String id = custom.get().id();
        play(player.getLocation(), gun.sounds().reload());
        actionBars.flash(player, settings.reloading(), gun.reloadTicks() + 5);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> finishReload(player, slot, id),
                Math.max(1, gun.reloadTicks()));
        reloads.put(player.getUniqueId(), new Reload(slot, id, task));
        updateView(player);
        return true;
    }

    private void finishReload(Player player, int slot, String id) {
        reloads.remove(player.getUniqueId());
        if (!player.isOnline()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack stack = inventory.getItem(slot);
        Optional<CustomItem> custom = factory.identify(stack);
        if (custom.isEmpty() || !custom.get().id().equals(id) || custom.get().definition().gun() == null) {
            return;
        }
        GunDefinition gun = custom.get().definition().gun();
        Ammo ammo = ammo(custom.get(), gun);
        // The rounds still in memory go onto the stack first; the reload is written with them.
        flush(player);
        stack = inventory.getItem(slot);
        int loaded = stored(stack, gun);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        int amount = gun.reloadAmount(loaded, creative ? Integer.MAX_VALUE : ammo.count(inventory));
        if (amount <= 0) {
            updateView(player);
            return;
        }
        if (!creative) {
            ammo.take(inventory, amount);
        }
        stack.editPersistentDataContainer(data -> data.set(ammoKey, PersistentDataType.INTEGER, loaded + amount));
        inventory.setItem(slot, stack);
        play(player.getLocation(), gun.sounds().reloaded());
        if (slot == inventory.getHeldItemSlot()) {
            showAmmo(player, custom.get(), stack, gun);
        }
    }

    /** Calls off a reload under way, if any: the gun left the hand. */
    public void cancelReload(Player player) {
        Reload reload = reloads.remove(player.getUniqueId());
        if (reload != null) {
            reload.task().cancel();
            actionBars.flash(player, Component.empty(), 1);
            updateView(player);
        }
    }

    public boolean reloading(Player player) {
        return reloads.containsKey(player.getUniqueId());
    }

    /** The player left: their gun's rounds go onto it, and everything else of theirs is dropped. */
    public void forget(Player player) {
        cancelReload(player);
        flush(player);
        lastShot.remove(player.getUniqueId());
        views.remove(player.getUniqueId());
    }

    private void showAmmo(Player player, CustomItem item, ItemStack stack, GunDefinition gun) {
        AmmoView view = updateView(player);
        int loaded = view != null ? view.loaded() : loaded(player, stack, gun);
        Component name = item.displayName() != null ? item.displayName() : Component.text(item.definition().id());
        Component line = name.append(Component.text("  " + loaded + " / " + gun.magazine(), NamedTextColor.WHITE));
        if (view != null && view.reserve() >= 0) {
            line = line.append(Component.text("  (" + view.reserve() + ")", NamedTextColor.GRAY));
        }
        actionBars.flash(player, line, AMMO_BAR_TICKS);
    }

    /**
     * Works out what the ammunition placeholders show for the gun in the player's hand, and keeps
     * it for them to read from any thread. Main thread.
     *
     * @return the view, or null when the player holds no gun
     */
    public @Nullable AmmoView updateView(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        Optional<CustomItem> custom = factory.identify(stack);
        GunDefinition gun = custom.map(item -> item.definition().gun()).orElse(null);
        if (gun == null) {
            views.remove(player.getUniqueId());
            return null;
        }
        Ammo ammo = ammo(custom.get(), gun);
        int reserve = ammo == Ammo.NONE || ammo == Ammo.MISSING || player.getGameMode() == GameMode.CREATIVE ? -1
                : ammo.count(player.getInventory());
        AmmoView view = new AmmoView(loaded(player, stack, gun), gun.magazine(), reserve,
                reloads.containsKey(player.getUniqueId()));
        views.put(player.getUniqueId(), view);
        return view;
    }

    /** What the ammunition placeholders show for this player; null when they hold no gun. Any thread. */
    public @Nullable AmmoView view(UUID player) {
        return views.get(player);
    }

    /**
     * {@code gun_ammo}, {@code gun_magazine}, {@code gun_reserve} and {@code gun_reloading}: blank
     * when the player holds no gun, and the reserve blank too for a gun that needs no ammunition.
     * Any thread.
     */
    public @Nullable String placeholder(@Nullable OfflinePlayer player, String params) {
        if (!params.startsWith("gun_")) {
            return null;
        }
        AmmoView view = player == null ? null : views.get(player.getUniqueId());
        return switch (params) {
            case "gun_ammo" -> view == null ? "" : String.valueOf(view.loaded());
            case "gun_magazine" -> view == null ? "" : String.valueOf(view.magazine());
            case "gun_reserve" -> view == null || view.reserve() < 0 ? "" : String.valueOf(view.reserve());
            case "gun_reloading" -> String.valueOf(view != null && view.reloading());
            default -> null;
        };
    }

    /** What a gun's reload takes from the inventory. */
    private abstract static class Ammo {

        /** No ammunition needed: reloading is free. */
        static final Ammo NONE = new Ammo() {
            @Override
            int count(PlayerInventory inventory) {
                return Integer.MAX_VALUE;
            }

            @Override
            void take(PlayerInventory inventory, int amount) {
            }
        };
        /** Ammunition that names nothing on this server: the gun cannot be reloaded. */
        static final Ammo MISSING = new Ammo() {
            @Override
            int count(PlayerInventory inventory) {
                return 0;
            }

            @Override
            void take(PlayerInventory inventory, int amount) {
            }
        };

        abstract int count(PlayerInventory inventory);

        abstract void take(PlayerInventory inventory, int amount);
    }

    /** Ammunition matched by a test on each stack - one of this plugin's items, or a plain material. */
    private final class Matching extends Ammo {

        private final Predicate<ItemStack> matches;

        Matching(Predicate<ItemStack> matches) {
            this.matches = matches;
        }

        @Override
        int count(PlayerInventory inventory) {
            int count = 0;
            for (ItemStack stack : inventory.getStorageContents()) {
                if (stack != null && matches.test(stack)) {
                    count += stack.getAmount();
                }
            }
            ItemStack offHand = inventory.getItemInOffHand();
            if (matches.test(offHand)) {
                count += offHand.getAmount();
            }
            return count;
        }

        @Override
        void take(PlayerInventory inventory, int amount) {
            int left = amount;
            ItemStack[] contents = inventory.getStorageContents();
            for (int slot = 0; slot < contents.length && left > 0; slot++) {
                ItemStack stack = contents[slot];
                if (stack != null && matches.test(stack)) {
                    int used = Math.min(left, stack.getAmount());
                    stack.setAmount(stack.getAmount() - used);
                    left -= used;
                }
            }
            inventory.setStorageContents(contents);
            ItemStack offHand = inventory.getItemInOffHand();
            if (left > 0 && matches.test(offHand)) {
                offHand.setAmount(offHand.getAmount() - Math.min(left, offHand.getAmount()));
            }
        }
    }

    /**
     * The ammunition a gun names, looked up now: a custom item - in the gun's own namespace first -
     * or else a material.
     */
    private Ammo ammo(CustomItem gunItem, GunDefinition gun) {
        if (gun.ammo() == null) {
            return Ammo.NONE;
        }
        String raw = gun.ammo();
        Optional<CustomItem> custom = raw.indexOf(':') < 0
                ? items.find(gunItem.definition().namespace() + ":" + raw).or(() -> items.find(raw))
                : items.find(raw);
        if (custom.isPresent()) {
            String id = custom.get().id();
            return new Matching(stack -> factory.identify(stack).filter(item -> item.id().equals(id)).isPresent());
        }
        Material material = Material.matchMaterial(raw);
        if (material != null && material.isItem() && !material.isAir()) {
            return new Matching(stack -> stack != null && stack.getType() == material && factory.identify(stack).isEmpty());
        }
        if (warned.add("ammo " + gunItem.id())) {
            logger.warning("Guns: " + gunItem.id() + " uses '" + raw + "' as ammunition, which is neither one of"
                    + " this plugin's items nor a material; it cannot be reloaded");
        }
        return Ammo.MISSING;
    }

    /** Stops the tracing threads; shots in flight are dropped. */
    public void shutdown() {
        flushAll();
        reloads.values().forEach(reload -> reload.task().cancel());
        reloads.clear();
        tracer.shutdown();
        try {
            tracer.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
