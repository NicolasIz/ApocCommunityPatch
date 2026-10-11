package com.arkcronist.content.bukkit.cosmetic;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.cosmetic.BackPhysics;
import com.arkcronist.content.core.cosmetic.BackPose;
import com.arkcronist.content.core.cosmetic.CosmeticDefinition;
import com.arkcronist.content.core.storage.DatabaseManager;
import com.arkcronist.content.core.storage.StoredCosmetic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cosmetics worn on the back (3.0): put on with a right click, kept in {@code player_cosmetics},
 * drawn by a {@link CosmeticRenderer} and swung by {@link BackPhysics}.
 *
 * <p>Threads. The main thread takes what it is told - each {@code PlayerMoveEvent} leaves the
 * wearer's position, body yaw and stance on their {@link Worn}, which costs a few field writes - and
 * handles who sees whom. {@code ArkContent-Physics} runs on its own every 50 ms: steps every wearer's
 * springs, turns the pose into a display transformation, and hands it to the renderer, which with
 * PacketEvents sends it to the clients from there. With PacketEvents, the main thread never touches
 * the pose at all.</p>
 *
 * <p>The item worn is the very stack taken from the hand, kept in the database while it is on, and
 * given back when it comes off; the player's data is saved straight after either, so a crash cannot
 * leave it in both places.</p>
 */
public final class CosmeticService {

    private static final long STEP_MILLIS = 50;
    private static final BackPose.Transform HIDDEN = new BackPose.Transform(new Vector3f(), new Quaternionf(),
            new Vector3f());

    private final Plugin plugin;
    private final ItemRegistry items;
    private final DatabaseManager database;
    private final Executor mainThread;
    private final Logger logger;
    private final CosmeticRenderer renderer;
    private final Map<UUID, Worn> worn = new ConcurrentHashMap<>();
    private final ScheduledExecutorService physics;
    private final AtomicLong stepNanos = new AtomicLong();
    private final AtomicLong steps = new AtomicLong();
    private @Nullable BukkitTask ticker;

    public CosmeticService(Plugin plugin, ItemRegistry items, DatabaseManager database,
                           Executor mainThread, CosmeticRenderer renderer) {
        this.plugin = plugin;
        this.items = items;
        this.database = database;
        this.mainThread = mainThread;
        this.logger = plugin.getLogger();
        this.renderer = renderer;
        this.physics = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ArkContent-Physics");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        physics.scheduleAtFixedRate(this::step, STEP_MILLIS, STEP_MILLIS, TimeUnit.MILLISECONDS);
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        logger.info("Cosmetics: drawn as " + renderer.describe() + ".");
    }

    public String describe() {
        return renderer.describe();
    }

    /** Mean time of one physics step for every wearer, in microseconds, and how many steps ran. */
    public double[] timing() {
        long count = steps.get();
        return new double[]{count == 0 ? 0 : stepNanos.get() / 1000.0 / count, count};
    }

    public Optional<Worn> wornBy(UUID player) {
        return Optional.ofNullable(worn.get(player));
    }

    public int wearers() {
        return worn.size();
    }

    // ---------------------------------------------------------------- ArkContent-Physics

    private void step() {
        long started = System.nanoTime();
        try {
            for (Worn on : worn.values()) {
                BackPhysics.Sample sample = on.sample;
                if (sample == null) {
                    continue;
                }
                BackPhysics.Pose pose = on.physics.step(sample, started);
                BackPose.Transform transform = on.hidden ? HIDDEN
                        : BackPose.of(on.definition, pose, sample.bodyYaw(), sample.sneaking());
                if (same(on.latest, transform)) {
                    continue;
                }
                on.latest = transform;
                renderer.update(on, transform);
            }
        } catch (RuntimeException | LinkageError error) {
            logger.log(Level.WARNING, "A cosmetic physics step failed; the next one runs as usual", error);
        }
        if (!worn.isEmpty()) {
            stepNanos.addAndGet(System.nanoTime() - started);
            steps.incrementAndGet();
        }
    }

    /** Nothing a client would see changed: no packet. */
    private static boolean same(@Nullable BackPose.Transform before, BackPose.Transform now) {
        return before != null && before.translation().distanceSquared(now.translation()) < 1e-8
                && Math.abs(before.rotation().dot(now.rotation())) > 1 - 1e-8
                && before.scale().equals(now.scale(), 1e-6f);
    }

    // ---------------------------------------------------------------- main thread

    /** The wearer moved: where, which way their body faces, whether they sneak. */
    public void moved(Player player) {
        Worn on = worn.get(player.getUniqueId());
        if (on == null) {
            return;
        }
        on.sample = new BackPhysics.Sample(player.getX(), player.getY(), player.getZ(), player.getBodyYaw(),
                player.isSneaking(), System.nanoTime());
    }

    private void tick() {
        for (Worn on : worn.values()) {
            Player player = plugin.getServer().getPlayer(on.player);
            if (player != null) {
                on.hidden = hidden(player);
            }
        }
        renderer.tick();
    }

    /** Lying flat, gliding, asleep, invisible or a spectator: nothing to draw on a back. */
    private static boolean hidden(Player player) {
        Pose pose = player.getPose();
        return pose == Pose.SWIMMING || pose == Pose.FALL_FLYING || pose == Pose.SLEEPING || pose == Pose.DYING
                || player.getGameMode() == GameMode.SPECTATOR || player.hasPotionEffect(PotionEffectType.INVISIBILITY)
                || player.isDead();
    }

    /** What a player had on when they last left: read from the database, then drawn. */
    public void joined(Player player) {
        UUID id = player.getUniqueId();
        database.cosmetics(id).thenAcceptAsync(stored -> {
            Player online = plugin.getServer().getPlayer(id);
            if (online == null) {
                return;
            }
            for (StoredCosmetic cosmetic : stored) {
                ItemStack item = ItemStack.deserializeBytes(cosmetic.item());
                Optional<CosmeticDefinition> definition = definition(cosmetic.itemId());
                if (definition.isEmpty()) {
                    online.sendMessage(Component.text("Your " + cosmetic.slot() + " cosmetic (" + cosmetic.itemId()
                            + ") is no longer one; /cosmetic off gives it back.", NamedTextColor.GRAY));
                    continue;
                }
                draw(online, cosmetic.itemId(), definition.get(), item);
            }
        }, mainThread).exceptionally(error -> {
            logger.log(Level.WARNING, "Could not read " + player.getName() + "'s cosmetics", error);
            return null;
        });
    }

    /** A right click with a cosmetic in hand: one is taken from the stack and put on. */
    public void putOn(Player player, CustomItem item, ItemStack hand) {
        CosmeticDefinition definition = item.definition().cosmetic();
        if (definition == null) {
            return;
        }
        ItemStack one = hand.clone();
        one.setAmount(1);
        hand.setAmount(hand.getAmount() - 1);
        UUID id = player.getUniqueId();
        StoredCosmetic stored = new StoredCosmetic(id, definition.slot().key(), item.id(), one.serializeAsBytes(),
                System.currentTimeMillis());
        database.equipCosmetic(stored).whenCompleteAsync((before, error) -> {
            Player online = plugin.getServer().getPlayer(id);
            if (error != null) {
                logger.log(Level.WARNING, "Could not put on " + item.id() + " for " + player.getName(), error);
                if (online != null) {
                    give(online, one);
                    online.sendMessage(Component.text("That could not be put on; it is back in your inventory.",
                            NamedTextColor.RED));
                }
                return;
            }
            if (online == null) {
                return;
            }
            before.ifPresent(previous -> give(online, ItemStack.deserializeBytes(previous.item())));
            draw(online, item.id(), definition, one);
            online.saveData();
            online.sendMessage(Component.text("You put on ", NamedTextColor.GRAY)
                    .append(one.effectiveName()).append(Component.text(". /cosmetic off to take it off.",
                            NamedTextColor.GRAY)));
        }, mainThread);
    }

    /** Takes off what is worn in {@code slot}, back into the inventory. */
    public void takeOff(Player player, CosmeticDefinition.Slot slot) {
        UUID id = player.getUniqueId();
        database.unequipCosmetic(id, slot.key()).whenCompleteAsync((off, error) -> {
            Player online = plugin.getServer().getPlayer(id);
            if (error != null) {
                logger.log(Level.WARNING, "Could not take off " + player.getName() + "'s cosmetic", error);
                return;
            }
            if (online == null) {
                return;
            }
            if (off.isEmpty()) {
                online.sendMessage(Component.text("You are not wearing anything there.", NamedTextColor.GRAY));
                return;
            }
            Worn on = worn.remove(id);
            if (on != null) {
                renderer.takeOff(on);
            }
            give(online, ItemStack.deserializeBytes(off.get().item()));
            online.saveData();
            online.sendMessage(Component.text("Taken off; it is back in your inventory.", NamedTextColor.GRAY));
        }, mainThread);
    }

    private void draw(Player player, String itemId, CosmeticDefinition definition, ItemStack item) {
        Worn previous = worn.remove(player.getUniqueId());
        if (previous != null) {
            renderer.takeOff(previous);
        }
        Worn on = new Worn(player.getUniqueId(), player.getEntityId(), itemId, definition, item, nextEntityId());
        on.sample = new BackPhysics.Sample(player.getX(), player.getY(), player.getZ(), player.getBodyYaw(),
                player.isSneaking(), System.nanoTime());
        on.hidden = hidden(player);
        on.latest = BackPose.of(definition, BackPhysics.Pose.REST, player.getBodyYaw(), player.isSneaking());
        worn.put(player.getUniqueId(), on);
        renderer.wear(on, player);
    }

    /** {@code viewer}'s client has just been sent {@code wearer}: the cosmetic follows a tick later, once it knows them. */
    public void tracked(Player viewer, Player wearer) {
        Worn on = worn.get(wearer.getUniqueId());
        if (on == null) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (viewer.isOnline() && worn.get(wearer.getUniqueId()) == on) {
                renderer.show(on, viewer);
            }
        }, 2);
    }

    public void untracked(Player viewer, Player wearer) {
        Worn on = worn.get(wearer.getUniqueId());
        if (on != null) {
            renderer.hide(on, viewer);
        }
    }

    /** A respawn or a change of world empties the wearer's own client: drawn for them again. */
    public void redrawFor(Player player) {
        Worn on = worn.get(player.getUniqueId());
        if (on == null) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Worn current = worn.get(player.getUniqueId());
            if (player.isOnline() && current != null) {
                draw(player, current.itemId, current.definition, current.item);
            }
        }, 3);
    }

    public void left(Player player) {
        Worn on = worn.remove(player.getUniqueId());
        if (on != null) {
            renderer.takeOff(on);
        }
    }

    /** A rebuild: each worn cosmetic takes its new definition - another offset, other physics. */
    public void contentReloaded() {
        for (Worn on : worn.values()) {
            definition(on.itemId).ifPresent(definition -> on.definition = definition);
        }
    }

    private Optional<CosmeticDefinition> definition(String itemId) {
        return items.get(itemId).map(item -> item.definition().cosmetic());
    }

    /**
     * An entity id from the server's own counter, so a display that exists only on the clients
     * never shares an id with a real entity. Paper keeps this under its "unsafe" values, deprecated
     * but there for exactly this.
     */
    @SuppressWarnings("deprecation")
    private int nextEntityId() {
        return plugin.getServer().getUnsafe().nextEntityId();
    }

    private static void give(Player player, ItemStack stack) {
        for (ItemStack left : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    public List<String> describe(Player player) {
        Worn on = worn.get(player.getUniqueId());
        return on == null ? List.of() : List.of(on.definition.slot().key() + ": " + on.itemId);
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        physics.shutdownNow();
        renderer.close();
        worn.clear();
    }
}
