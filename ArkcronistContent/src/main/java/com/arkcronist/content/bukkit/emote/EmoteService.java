package com.arkcronist.content.bukkit.emote;

import com.arkcronist.content.bukkit.EntityIds;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.animation.AnimatedModel;
import com.arkcronist.content.core.animation.Animator;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.emotes.EmoteDefinition;
import com.arkcronist.content.core.emotes.EmotePose;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 5.0: player emotes - an item with an {@code emote:} section, used, or {@code /emote <name>}.
 *
 * <p>While one plays, the player is invisible, and whoever sees them is sent empty hands and
 * armour; a rig of item displays stands where they stand, turned the way they looked, and plays
 * the animation. Its frames were worked out when the content was built ({@link EmoteDefinition}):
 * here they are only sent, on time, by a thread of its own - {@code ArkContent-Emotes}, every 50 ms
 * - straight to the clients when PacketEvents is there ({@link EmoteRenderer#offMainThread()}),
 * through the main thread otherwise. Each frame names its pose and how long to glide to it, and the
 * clients glide by themselves, at their own frame rate.</p>
 *
 * <p>It ends at the animation's end - or, held or looping, after {@code max-seconds} - and as soon
 * as the player walks, jumps, sneaks, is hurt, changes what is in their hand, teleports, leaves or
 * dies. The player is then shown as they were.</p>
 */
public final class EmoteService implements Listener {

    public static final String PERMISSION = "arkcontent.emote";
    private static final EquipmentSlot[] WORN = {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** How far the player may drift - standing on a slab edge, pushed by a hair - before it ends. */
    private static final double DRIFT = 0.1;
    /** With stop-on-move off, how far they may walk before the rig left behind ends. */
    private static final double WANDER = 2.0;

    private final Plugin plugin;
    private final ItemRegistry items;
    private final com.arkcronist.content.bukkit.item.ItemFactory factory;
    private final EmoteRenderer renderer;
    private final Executor mainThread;
    private final Logger logger;
    private final Map<UUID, Rig> playing = new ConcurrentHashMap<>();
    private final Map<UUID, Long> readyAt = new ConcurrentHashMap<>();
    private @Nullable ScheduledExecutorService clock;
    private volatile long frameNanos;

    public EmoteService(Plugin plugin, ItemRegistry items, com.arkcronist.content.bukkit.item.ItemFactory factory,
                        @Nullable EmoteRenderer packets, Executor mainThread) {
        this.plugin = plugin;
        this.items = items;
        this.factory = factory;
        this.renderer = packets != null ? packets : new EntityEmotes();
        this.mainThread = mainThread;
        this.logger = plugin.getLogger();
    }

    public void start() {
        clock = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ArkContent-Emotes");
            thread.setDaemon(true);
            return thread;
        });
        clock.scheduleAtFixedRate(this::tick, 50, 50, TimeUnit.MILLISECONDS);
        logger.info("Emotes are drawn as " + renderer.describe() + ".");
    }

    /** Every emote ends, its players shown again. Main thread, as the plugin is disabled. */
    public void shutdown() {
        if (clock != null) {
            clock.shutdownNow();
        }
        for (Rig rig : List.copyOf(playing.values())) {
            end(rig);
        }
        renderer.close();
    }

    public String describe() {
        return renderer.describe();
    }

    public int playing() {
        return playing.size();
    }

    /** Every item that plays an emote, by the name /emote knows it by: its id without the namespace. */
    public Map<String, CustomItem> emotes() {
        Map<String, CustomItem> byName = new java.util.TreeMap<>();
        for (CustomItem item : items.all()) {
            if (item.definition().emote() != null) {
                byName.putIfAbsent(item.definition().id(), item);
                byName.put(item.id(), item);
            }
        }
        return byName;
    }

    public Optional<String> playingNow(Player player) {
        Rig rig = playing.get(player.getUniqueId());
        return rig == null ? Optional.empty() : Optional.of(rig.itemId);
    }

    // ---------------------------------------------------------------- main thread

    /**
     * Starts {@code item}'s emote for {@code player}, ending the one playing; says why not, when it
     * cannot. Main thread.
     */
    public boolean play(Player player, CustomItem item) {
        EmoteDefinition emote = item.definition().emote();
        if (emote == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long ready = readyAt.get(player.getUniqueId());
        if (ready != null && now < ready) {
            player.sendActionBar(Component.text("Wait a moment before the next emote.", NamedTextColor.GRAY));
            return false;
        }
        if (player.isInsideVehicle() || player.isGliding() || player.isSwimming() || player.isSleeping()
                || player.isDead() || !((org.bukkit.entity.Entity) player).isOnGround()) {
            player.sendActionBar(Component.text("Emotes are played standing on the ground.", NamedTextColor.GRAY));
            return false;
        }
        stop(player);
        Location at = player.getLocation();
        int bones = emote.rig().bones().size();
        ItemStack[] shown = new ItemStack[bones];
        int[] ids = new int[bones];
        UUID[] uuids = new UUID[bones];
        int head = emote.playerHead() ? emote.headBone() : -1;
        for (int bone = 0; bone < bones; bone++) {
            if (!emote.drawn(bone)) {
                continue;
            }
            shown[bone] = bone == head ? head(player) : boneItem(item, emote.rig().bones().get(bone).name());
            ids[bone] = EntityIds.next(plugin.getServer(), player.getWorld());
            uuids[bone] = UUID.randomUUID();
        }
        // It appears at rest; the first frame glides it into the animation.
        Rig rig = new Rig(player.getUniqueId(), item.id(), emote, at, shown, ids, uuids, player.isInvisible(),
                EmotePose.of(emote, Animator.rest(emote.rig())));
        player.setInvisible(true);
        List<Player> viewers = new ArrayList<>(player.getTrackedBy());
        for (Player viewer : viewers) {
            bare(viewer, player);
        }
        viewers.add(player);
        renderer.start(rig, viewers);
        playing.put(player.getUniqueId(), rig);
        return true;
    }

    /** Ends {@code player}'s emote, if one plays. Main thread. */
    public boolean stop(Player player) {
        Rig rig = playing.get(player.getUniqueId());
        if (rig == null) {
            return false;
        }
        end(rig);
        return true;
    }

    private void end(Rig rig) {
        rig.end();
        if (!playing.remove(rig.player, rig)) {
            return;
        }
        renderer.remove(rig);
        Player player = plugin.getServer().getPlayer(rig.player);
        if (player != null) {
            player.setInvisible(rig.wasInvisible);
            Map<EquipmentSlot, ItemStack> worn = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : WORN) {
                worn.put(slot, player.getEquipment().getItem(slot));
            }
            for (Player viewer : player.getTrackedBy()) {
                viewer.sendEquipmentChange(player, worn);
            }
        }
        readyAt.put(rig.player, System.currentTimeMillis() + rig.emote.cooldownTicks() * 50L);
    }

    /** {@code viewer} is sent {@code player} with nothing in their hands and nothing worn. */
    private static void bare(Player viewer, Player player) {
        Map<EquipmentSlot, ItemStack> none = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : WORN) {
            none.put(slot, ItemStack.empty());
        }
        viewer.sendEquipmentChange(player, none);
    }

    /** The player's own head: a player head with their profile, so the client draws their skin. */
    private static ItemStack head(Player player) {
        ItemStack skull = ItemStack.of(Material.PLAYER_HEAD);
        skull.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(player.getPlayerProfile()));
        return skull;
    }

    /** A bone's look: a plain item carrying the bone's item model, as animated furniture's. */
    private static ItemStack boneItem(CustomItem item, String bone) {
        ResourceLocation key = AnimatedModel.boneItemModel(item.definition().itemModel(), bone);
        ItemStack stack = ItemStack.of(Material.PAPER);
        stack.editMeta(meta -> meta.setItemModel(new NamespacedKey(key.namespace(), key.path())));
        return stack;
    }

    // ---------------------------------------------------------------- ArkContent-Emotes

    /** Every 50 ms: the frames due, sent; an emote at its end, handed to the main thread to end. */
    private void tick() {
        long began = System.nanoTime();
        for (Rig rig : playing.values()) {
            if (rig.ended()) {
                continue;
            }
            try {
                for (Animator.Frame frame : rig.clock.step()) {
                    List<EmotePose.Part> pose = EmotePose.of(rig.emote, frame.pose());
                    rig.latest(pose);
                    if (renderer.offMainThread()) {
                        renderer.pose(rig, pose, frame.duration());
                    } else {
                        mainThread.execute(() -> {
                            if (!rig.ended()) {
                                renderer.pose(rig, pose, frame.duration());
                            }
                        });
                    }
                }
                if (rig.clock.over() && rig.end()) {
                    mainThread.execute(() -> end(rig));
                }
            } catch (RuntimeException failure) {
                logger.warning("The emote of " + rig.player + " stopped: " + failure);
                if (rig.end()) {
                    mainThread.execute(() -> end(rig));
                }
            }
        }
        frameNanos = System.nanoTime() - began;
    }

    /** How long the last round of frames took to send, in milliseconds. */
    public double lastTickMillis() {
        return frameNanos / 1_000_000.0;
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Optional<CustomItem> item = factory.identify(event.getItem());
        if (item.isEmpty() || item.get().definition().emote() == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission(PERMISSION)) {
            return;
        }
        Rig rig = playing.get(player.getUniqueId());
        if (rig != null && rig.itemId.equals(item.get().id())) {
            stop(player);
        } else {
            play(player, item.get());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Rig rig = playing.get(event.getPlayer().getUniqueId());
        if (rig == null || !event.hasChangedPosition()) {
            return;
        }
        Location to = event.getTo();
        double limit = rig.emote.stopOnMove() ? DRIFT : WANDER;
        if (to.getWorld() != rig.at.getWorld() || to.distanceSquared(rig.at) > limit * limit) {
            end(rig);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            stop(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            stop(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            stop(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
        readyAt.remove(event.getPlayer().getUniqueId());
    }

    /** Someone comes into sight of an emoting player: once they have been sent them, the rig, and bare hands. */
    @EventHandler
    public void onTrack(PlayerTrackEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !playing.containsKey(player.getUniqueId())) {
            return;
        }
        Player viewer = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Rig rig = playing.get(player.getUniqueId());
            if (rig != null && viewer.isOnline()) {
                bare(viewer, player);
                renderer.show(rig, viewer);
            }
        });
    }

    @EventHandler
    public void onUntrack(PlayerUntrackEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            Rig rig = playing.get(player.getUniqueId());
            if (rig != null) {
                renderer.hide(rig, event.getPlayer());
            }
        }
    }
}
