package com.arkcronist.content.bukkit.furniture;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.furniture.PieceAxes;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Furniture with {@code interactable: effect}: particles on a loop - the steam over a stew, a
 * brazier's flames - and a right click that hands out what is in it: a drink's potion effects, a
 * sound, a line of text.
 *
 * <h2>The loop</h2>
 * <p>Every tick the main thread does only what Paper allows nowhere else, and only briefly: it picks
 * the pieces whose interval comes round this tick and notes where the players are - one position
 * per player, no chunk or entity lookups. Everything else happens on the {@code ArkContent-Effects}
 * thread: where each particle goes, turned with its piece, which players are close enough to see
 * it, and the packets themselves, sent with Paper's {@code ParticleBuilder}, which may be used off
 * the main thread. However many pieces smoke and burn, the tick only pays for the list.</p>
 *
 * <p>The pieces are known from their root displays - as they are spawned, and as they load with
 * their chunk - and forgotten once their display is gone, checked every few seconds. Which of them
 * have particles, and which particles, is read again after every rebuild.</p>
 *
 * <p>Main thread, except where said.</p>
 */
public final class AmbienceService {

    /** Ticks between checks that the pieces still stand. */
    private static final int SWEEP_TICKS = 100;

    /** A piece that may give off particles: its root display, and where it stands. */
    private record Emitter(UUID root, World world, double x, double y, double z, float yaw, String id, int phase) {
    }

    /** A player's position, read on the main thread for the effects thread. */
    private record Viewer(Player player, double x, double y, double z) {
    }

    /** One piece's particles this tick. */
    private record Burst(Emitter emitter, int range, List<Resolved> particles) {
    }

    /** A particle as written, with the server's own particle type for it. */
    private record Resolved(Particle type, Placement.Particles spec) {
    }

    private final Plugin plugin;
    private java.util.function.BiFunction<Player, Map<String, Double>, List<String>> hud;
    private final ItemRegistry registry;
    private final FurnitureService furniture;
    private final Map<UUID, Emitter> emitters = new HashMap<>();
    /** The registry's id list the definitions below were read from: a new list means a rebuild. */
    private @Nullable List<String> readFrom;
    private Map<String, Placement.Ambience> ambience = Map.of();
    private Map<String, List<Resolved>> particles = Map.of();
    /** Until which tick each player may not take from each piece again. */
    private final Map<UUID, Map<UUID, Integer>> cooldowns = new HashMap<>();
    /** Tells this plugin's own items apart, which no forge or fire cooks; null until it is set. */
    private @Nullable java.util.function.Predicate<org.bukkit.inventory.ItemStack> custom;
    private final Set<String> warned = new HashSet<>();
    private final ExecutorService thread;
    private @Nullable BukkitTask loop;

    public AmbienceService(Plugin plugin, ItemRegistry registry, FurnitureService furniture) {
        this.plugin = plugin;
        this.registry = registry;
        this.furniture = furniture;
        this.thread = Executors.newSingleThreadExecutor(runnable -> {
            Thread worker = new Thread(runnable, "ArkContent-Effects");
            worker.setDaemon(true);
            return worker;
        });
    }

    /** Starts the loop, and takes in the pieces already loaded - those whose chunks loaded before this plugin. */
    public void start() {
        for (World world : plugin.getServer().getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (furniture.isPieceRoot(display)) {
                    track(display);
                }
            }
        }
        loop = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (loop != null) {
            loop.cancel();
            loop = null;
        }
        thread.shutdown();
        try {
            thread.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        emitters.clear();
        cooldowns.clear();
    }

    /** A piece's root display was spawned, or loaded with its chunk. */
    public void track(ItemDisplay root) {
        String id = furniture.furnitureId(root);
        if (id == null || !root.isValid()) {
            return;
        }
        Location at = root.getLocation();
        Block anchor = furniture.anchorOf(root);
        emitters.put(root.getUniqueId(), new Emitter(root.getUniqueId(), root.getWorld(), at.getX(), anchor.getY(),
                at.getZ(), at.getYaw(), id, Math.floorMod(root.getUniqueId().hashCode(), 1 << 16)));
    }

    /** How many pieces are known, and how many of them give off particles. For /arkcontent info. */
    public int[] counts() {
        refresh();
        int lit = 0;
        for (Emitter emitter : emitters.values()) {
            if (particles.containsKey(emitter.id())) {
                lit++;
            }
        }
        return new int[] {emitters.size(), lit};
    }

    // ---------------------------------------------------------------- the loop

    private void tick() {
        Server server = plugin.getServer();
        int now = server.getCurrentTick();
        if (now % SWEEP_TICKS == 0) {
            sweep(server);
        }
        refresh();
        if (particles.isEmpty() || emitters.isEmpty()) {
            return;
        }
        List<Burst> due = null;
        for (Emitter emitter : emitters.values()) {
            List<Resolved> drawn = particles.get(emitter.id());
            if (drawn == null) {
                continue;
            }
            Placement.Ambience spec = ambience.get(emitter.id());
            if ((now + emitter.phase()) % spec.intervalTicks() != 0) {
                continue;
            }
            // 3.1: candles put out by a click do not burn on.
            if (spec.toggleLight() && server.getEntity(emitter.root()) instanceof ItemDisplay root && !furniture.lit(root)) {
                continue;
            }
            if (due == null) {
                due = new ArrayList<>();
            }
            due.add(new Burst(emitter, spec.range(), drawn));
        }
        if (due == null) {
            return;
        }
        Map<UUID, List<Viewer>> viewers = new HashMap<>();
        for (Player player : server.getOnlinePlayers()) {
            Location at = player.getLocation();
            viewers.computeIfAbsent(at.getWorld().getUID(), ignored -> new ArrayList<>())
                    .add(new Viewer(player, at.getX(), at.getY(), at.getZ()));
        }
        if (viewers.isEmpty()) {
            return;
        }
        List<Burst> bursts = due;
        try {
            thread.execute(() -> send(bursts, viewers));
        } catch (RejectedExecutionException stopping) {
            // Shutting down: no more particles.
        }
    }

    /** On the effects thread: positions, who sees them, and the packets. */
    private void send(List<Burst> bursts, Map<UUID, List<Viewer>> viewers) {
        for (Burst burst : bursts) {
            Emitter emitter = burst.emitter();
            List<Viewer> inWorld = viewers.get(emitter.world().getUID());
            if (inWorld == null) {
                continue;
            }
            double reach = (double) burst.range() * burst.range();
            List<Player> receivers = new ArrayList<>();
            for (Viewer viewer : inWorld) {
                double dx = viewer.x() - emitter.x();
                double dy = viewer.y() - emitter.y();
                double dz = viewer.z() - emitter.z();
                if (dx * dx + dy * dy + dz * dz <= reach) {
                    receivers.add(viewer.player());
                }
            }
            if (receivers.isEmpty()) {
                continue;
            }
            for (Resolved particle : burst.particles()) {
                Placement.Particles spec = particle.spec();
                double[] offset = PieceAxes.offset(emitter.yaw(), spec.x(), spec.y(), spec.z());
                try {
                    particle.type().builder()
                            .location(emitter.world(), emitter.x() + offset[0], emitter.y() + offset[1],
                                    emitter.z() + offset[2])
                            .count(spec.count())
                            .offset(spec.spread(), spec.spread(), spec.spread())
                            .extra(spec.speed())
                            .receivers(receivers)
                            .spawn();
                } catch (RuntimeException failed) {
                    // A player who left between the snapshot and now, or a world going away.
                    plugin.getLogger().log(Level.FINE, "A furniture particle could not be sent", failed);
                }
            }
        }
    }

    /** Forgets the pieces whose root display is gone: broken, or unloaded with its chunk. */
    private void sweep(Server server) {
        emitters.values().removeIf(emitter -> {
            var entity = server.getEntity(emitter.root());
            return entity == null || !entity.isValid();
        });
        int now = server.getCurrentTick();
        cooldowns.values().forEach(pieces -> pieces.values().removeIf(until -> until <= now));
        cooldowns.values().removeIf(Map::isEmpty);
    }

    /** After a rebuild: which furniture gives off which particles, as this server knows them. */
    private void refresh() {
        List<String> ids = registry.ids();
        if (ids == readFrom) {
            return;
        }
        Map<String, Placement.Ambience> specs = new HashMap<>();
        Map<String, List<Resolved>> drawn = new HashMap<>();
        for (String id : ids) {
            registry.get(id).map(CustomItem::placement)
                    .filter(Placement.Furniture.class::isInstance).map(Placement.Furniture.class::cast)
                    .map(Placement.Furniture::ambience)
                    .ifPresent(spec -> {
                        specs.put(id, spec);
                        List<Resolved> resolved = new ArrayList<>();
                        for (Placement.Particles particle : spec.particles()) {
                            Particle type = particle(id, particle.particle());
                            if (type != null) {
                                resolved.add(new Resolved(type, particle));
                            }
                        }
                        if (!resolved.isEmpty()) {
                            drawn.put(id, List.copyOf(resolved));
                        }
                    });
        }
        ambience = specs;
        particles = drawn;
        readFrom = ids;
    }

    /**
     * The server's particle by that id, if it needs nothing more than a position - a colour or a
     * block would have to be written too, which {@code effect:} has no place for.
     */
    @Nullable Particle particle(String furniture, String name) {
        NamespacedKey key = NamespacedKey.fromString(name);
        Particle type = key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.PARTICLE_TYPE).get(key);
        if (type == null) {
            warn(furniture + ": '" + name + "' is not a particle on this server; it is left out");
            return null;
        }
        if (type.getDataType() != Void.class) {
            warn(furniture + ": the particle '" + name + "' needs data (" + type.getDataType().getSimpleName()
                    + ") that effect: cannot give it; it is left out");
            return null;
        }
        return type;
    }

    private void warn(String message) {
        if (warned.add(message)) {
            plugin.getLogger().warning("Furniture effects: " + message);
        }
    }

    // ---------------------------------------------------------------- a click

    /**
     * A right click on the piece {@code piece}: its potion effects, its sound and its message - once
     * per cooldown for each player and piece.
     */
    public void click(Player player, FurnitureService.Piece piece, CustomItem item, Placement.Ambience spec) {
        if (!spec.clickable()) {
            return;
        }
        // 3.1: what the piece does with the hand comes first - a forge melts the ore held out to it,
        // a fire roasts the meat - and is never held back by the wait between two drinks.
        org.bukkit.inventory.ItemStack held = player.getInventory().getItemInMainHand();
        if (spec.cook() != null && !(custom != null && custom.test(held))) {
            org.bukkit.inventory.ItemStack cooked = Cooking.cook(player, spec.cook());
            if (cooked != null) {
                Location fire = piece.anchor().getLocation().add(0.5, 1.0, 0.5);
                fire.getWorld().playSound(fire, org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.4f);
                player.sendActionBar(Component.text("+1 " + cooked.getType().getKey().getKey().replace('_', ' '),
                        NamedTextColor.GOLD));
                burst(piece, spec.clickParticles());
                return;
            }
            if (spec.station() == null && spec.potions().isEmpty() && spec.message() == null) {
                player.sendActionBar(Component.text(held.getType().isAir() ? "Hold out something to cook"
                        : "This does not cook " + held.getType().getKey().getKey().replace('_', ' '), NamedTextColor.GRAY));
                return;
            }
        }
        // 3.1: a workstation opens on every click.
        if (spec.station() != null) {
            openStation(player, piece, spec.station());
            if (spec.sound() != null) {
                Location centre = piece.anchor().getLocation().add(0.5, 0.5, 0.5);
                centre.getWorld().playSound(centre, spec.sound(), 0.8f, 1f);
            }
            burst(piece, spec.clickParticles());
            return;
        }
        int now = plugin.getServer().getCurrentTick();
        Map<UUID, Integer> mine = cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        Integer until = mine.get(piece.root().getUniqueId());
        if (until != null && until > now && player.getGameMode() != GameMode.CREATIVE) {
            int seconds = (int) Math.ceil((until - now) / 20.0);
            player.sendActionBar(Component.text("Not yet: " + seconds + "s", NamedTextColor.GRAY));
            return;
        }
        if (spec.cooldownTicks() > 0) {
            mine.put(piece.root().getUniqueId(), now + spec.cooldownTicks());
        }
        if (spec.toggleLight()) {
            furniture.toggleLight(piece);
        }
        burst(piece, spec.clickParticles());
        if (!spec.hud().isEmpty() && hud != null) {
            for (String unknown : hud.apply(player, spec.hud())) {
                warn(item.id() + ": there is no HUD '" + unknown + "' kept by this plugin; nothing is added to it");
            }
        }
        for (Placement.Effect effect : spec.potions()) {
            PotionEffectType type = effectType(item.id(), effect.type());
            if (type != null) {
                player.addPotionEffect(new PotionEffect(type, effect.durationTicks(), effect.amplifier()));
            }
        }
        Location centre = piece.anchor().getLocation().add(0.5, 0.5, 0.5);
        if (spec.sound() != null) {
            centre.getWorld().playSound(centre, spec.sound(), 0.8f, 1f);
        }
        if (spec.message() != null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(spec.message().replace("%player%", player.getName())));
        }
    }

    /** 3.4: adds a click's HUD values - a trough's water to thirst; returns the HUD ids it does not know. */
    public void hudGains(java.util.function.BiFunction<Player, Map<String, Double>, List<String>> hud) {
        this.hud = hud;
    }

    /** 3.1: what is one of this plugin's own items - a forge or a fire leaves those alone. */
    public void useCustomItems(java.util.function.Predicate<org.bukkit.inventory.ItemStack> custom) {
        this.custom = custom;
    }

    /** 3.1: the vanilla workstation a piece stands for, opened for {@code player} - no block needed. */
    @SuppressWarnings("deprecation")
    private static void openStation(Player player, FurnitureService.Piece piece, String station) {
        switch (station) {
            case "crafting" -> player.openWorkbench(null, true);
            case "anvil" -> player.openAnvil(null, true);
            case "grindstone" -> player.openGrindstone(null, true);
            case "smithing" -> player.openSmithingTable(null, true);
            case "stonecutter" -> player.openStonecutter(null, true);
            case "loom" -> player.openLoom(null, true);
            case "cartography" -> player.openCartographyTable(null, true);
            // At the piece: the bookshelves around it count, as for an enchanting table.
            case "enchanting" -> player.openEnchanting(piece.anchor().getLocation(), true);
            default -> {
            }
        }
    }

    /** 3.1: particles drawn once, around the piece, for everyone near enough. */
    private void burst(FurnitureService.Piece piece, List<Placement.Particles> specs) {
        Location base = piece.root().getLocation();
        for (Placement.Particles spec : specs) {
            Particle type = particle(furniture.furnitureId(piece.root()), spec.particle());
            if (type == null) {
                continue;
            }
            double[] offset = PieceAxes.offset(base.getYaw(), spec.x(), spec.y(), spec.z());
            type.builder().location(base.getWorld(), base.getX() + offset[0], piece.anchor().getY() + offset[1],
                            base.getZ() + offset[2])
                    .count(spec.count()).offset(spec.spread(), spec.spread(), spec.spread()).extra(spec.speed())
                    .receivers(32, true).spawn();
        }
    }

    /** A player left: their cooldowns go with them. */
    public void forget(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    private @Nullable PotionEffectType effectType(String furniture, String key) {
        NamespacedKey parsed = NamespacedKey.fromString(key);
        PotionEffectType type = parsed == null ? null
                : RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(parsed);
        if (type == null) {
            warn(furniture + ": '" + key + "' is not a potion effect on this server; it is left out");
        }
        return type;
    }
}
