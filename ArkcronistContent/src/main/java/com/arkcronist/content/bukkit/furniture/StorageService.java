package com.arkcronist.content.bukkit.furniture;

import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.storage.DatabaseManager;
import com.arkcronist.content.core.storage.StoredInventory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Furniture with an inventory: a right click opens it, and what is put in stays, through restarts,
 * in the database.
 *
 * <p>Like a chest, one piece of furniture has one inventory, shared: two players who open it see
 * the same slots, and an item one of them takes is gone for the other. While anyone has it open the
 * inventory lives in memory; when the last of them closes it, it is saved and let go.</p>
 *
 * <h2>Threads</h2>
 * <p>Every database read and write happens on the database's own thread, through futures - the
 * server thread never waits for SQLite. What does stay on the server thread is turning item stacks
 * into bytes and back: stacks are the server's objects, read and changed by it every tick, and
 * Paper's serialiser takes a few microseconds for a full inventory. So a save is a snapshot taken
 * on the main thread, at the moment of the close, handed over as bytes; a load comes back as bytes
 * and becomes stacks on the main thread.</p>
 *
 * <h2>When it is saved</h2>
 * <ul>
 *   <li>each time someone closes it - the last viewer or not;</li>
 *   <li>every 30 seconds while it is open and has changed, so a crash loses at most that much;</li>
 *   <li>when the plugin disables, and when its world unloads.</li>
 * </ul>
 * <p>Saves queue in order on the database thread, so the last one asked for is the one kept.</p>
 *
 * <h2>Nothing lost, nothing duplicated</h2>
 * <ul>
 *   <li>Contents that cannot be read back are never replaced by an empty inventory: it is not
 *       opened at all, and the saved bytes are left alone.</li>
 *   <li>Breaking the furniture drops what it holds - the open inventory if someone has it open,
 *       otherwise what was saved, read and deleted in one database step so it cannot drop twice.</li>
 *   <li>If the configuration shrinks {@code slots}, an inventory keeps the rows that still hold
 *       something until they are emptied.</li>
 * </ul>
 *
 * <h2>Opening and closing</h2>
 * <p>The furniture is told - see {@link Lid} - when its inventory starts being used and when it
 * stops: in the same tick as the click that opens it, before the database has even answered, and
 * when its last viewer closes it. That is when a chest's lid swings and its sound plays.</p>
 *
 * <h2>Double chests</h2>
 * <p>Two chests of furniture joined side by side open as one inventory of 54 slots: the first
 * half's 27, then its partner's - each still its own row in the database, read together and saved
 * together in one transaction. Breaking one half drops what is in its 27 slots and leaves the other
 * half's where they are.</p>
 *
 * <p>Main thread only, apart from the futures' own work.</p>
 */
public final class StorageService {

    /** What the furniture does as its inventory starts and stops being used: its lid, its sound. */
    public interface Lid {

        /** Someone opened it, nobody having it open before. */
        void opened(Block support);

        /**
         * Nobody has it open any more.
         *
         * @param animate false when the server is stopping: there is no time left to animate
         */
        void closed(Block support, boolean animate);
    }

    /** How far a player can be from the furniture and keep its inventory open, as for a chest. */
    private static final double REACH = 8;
    /** Seconds between saves of an inventory left open and changed. */
    private static final int AUTOSAVE_SECONDS = 30;
    /** Each half of a double chest. */
    private static final int HALF_SLOTS = Placement.DoubleChest.HALF_SLOTS;
    private static final int PAIR_SLOTS = 2 * HALF_SLOTS;

    /** A storage furniture: its support block. */
    record Key(UUID world, int x, int y, int z) {

        static Key of(Block block) {
            return new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    /**
     * What makes an inventory a storage furniture's. Bukkit hands it back with every inventory
     * event, which is how a click or a close is told apart from any other inventory's.
     */
    public static final class Holder implements InventoryHolder {

        private final Key key;
        private final String furnitureId;
        /** A double chest's second half; null for a single piece. */
        private final @Nullable Key partner;
        private Inventory inventory;

        Holder(Key key, String furnitureId, @Nullable Key partner) {
            this.key = key;
            this.furnitureId = furnitureId;
            this.partner = partner;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    /** One storage furniture in memory: being read from the database, or open. */
    private static final class Session {

        final Holder holder;
        final Placement.Storage spec;
        final Component title;
        /** Who asked to open it while it was being read. */
        final Set<UUID> waiting = new LinkedHashSet<>();
        /** Null until it has been read. */
        Inventory inventory;
        boolean dirty;

        Session(Holder holder, Placement.Storage spec, Component title) {
            this.holder = holder;
            this.spec = spec;
            this.title = title;
        }
    }

    private final Plugin plugin;
    private final DatabaseManager database;
    private final Logger logger;
    private final Lid lid;
    private final Map<Key, Session> sessions = new HashMap<>();
    private BukkitTask ticker;
    private int seconds;

    public StorageService(Plugin plugin, DatabaseManager database, Lid lid) {
        this.plugin = plugin;
        this.database = database;
        this.lid = lid;
        this.logger = plugin.getLogger();
    }

    /** Starts the once-a-second check: reach, and the autosave. */
    public void start() {
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }

    /**
     * Opens the furniture on {@code support} for {@code player}: at once if it is already in memory,
     * otherwise as soon as the database has answered.
     */
    public void open(Player player, Block support, String furnitureId, Placement.Storage spec, Component title) {
        Key key = Key.of(support);
        Session session = sessions.get(key);
        if (session == null) {
            Session loading = new Session(new Holder(key, furnitureId, null), spec, title);
            sessions.put(key, loading);
            loading.waiting.add(player.getUniqueId());
            lid.opened(support);
            database.loadInventory(key.world(), key.x(), key.y(), key.z())
                    .whenComplete((stored, error) -> onMain(() -> loaded(loading, stored, error)));
            return;
        }
        if (session.inventory == null) {
            session.waiting.add(player.getUniqueId());
        } else {
            show(player, session);
        }
    }

    /**
     * Opens a double chest for {@code player}: both halves' contents, read together, as one inventory
     * of 54 slots - the first half's, then the second's.
     */
    public void openPair(Player player, Block first, Block second, String furnitureId, Component title) {
        Key key = Key.of(first);
        Key partner = Key.of(second);
        Session session = sessions.get(key);
        if (session != null && session.holder.partner == null) {
            // Still open as a single chest from before it was joined: shut and saved, then read again.
            release(first);
            session = null;
        }
        if (session == null) {
            Session loading = new Session(new Holder(key, furnitureId, partner),
                    new Placement.Storage(PAIR_SLOTS, null), title);
            sessions.put(key, loading);
            sessions.put(partner, loading);
            loading.waiting.add(player.getUniqueId());
            lid.opened(first);
            database.loadInventories(key.world(), new int[] {key.x(), key.y(), key.z()},
                            new int[] {partner.x(), partner.y(), partner.z()})
                    .whenComplete((stored, error) -> onMain(() -> loadedPair(loading, stored, error)));
            return;
        }
        if (session.inventory == null) {
            session.waiting.add(player.getUniqueId());
        } else {
            show(player, session);
        }
    }

    private void loadedPair(Session session, @Nullable List<Optional<StoredInventory>> stored, @Nullable Throwable error) {
        Key key = session.holder.key;
        if (sessions.get(key) != session) {
            return;
        }
        if (error != null) {
            end(session, true);
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            logger.warning("Could not read the double chest at " + describe(key) + ": " + cause.getMessage());
            tellWaiting(session, "This chest cannot be opened right now.");
            return;
        }
        ItemStack[][] halves = new ItemStack[2][];
        for (int half = 0; half < 2; half++) {
            Optional<StoredInventory> saved = stored.get(half);
            try {
                halves[half] = saved.isEmpty() ? new ItemStack[0] : ItemStack.deserializeItemsFromBytes(saved.get().contents());
            } catch (RuntimeException exception) {
                end(session, true);
                logger.log(Level.SEVERE, "The contents saved for the chest at " + describe(half == 0 ? key
                        : session.holder.partner) + " cannot be read; the double chest stays shut and its rows in "
                        + DatabaseManager.STORAGE_TABLE + " are left untouched.", exception);
                tellWaiting(session, "This chest cannot be opened: its contents could not be read.");
                return;
            }
            if (used(halves[half]) > HALF_SLOTS) {
                // Saved when it was bigger than half a double chest: opened, it would be cut short.
                end(session, true);
                logger.warning("The chest at " + describe(half == 0 ? key : session.holder.partner) + " holds items"
                        + " past slot " + HALF_SLOTS + ", more than half a double chest; break it to get them back.");
                tellWaiting(session, "This double chest cannot be opened: one half holds more than it can show.");
                return;
            }
        }
        Inventory inventory = plugin.getServer().createInventory(session.holder, PAIR_SLOTS, session.title);
        for (int half = 0; half < 2; half++) {
            ItemStack[] items = halves[half];
            for (int slot = 0; slot < items.length && slot < HALF_SLOTS; slot++) {
                ItemStack item = items[slot];
                inventory.setItem(half * HALF_SLOTS + slot, item == null || item.isEmpty() ? null : item);
            }
        }
        session.holder.inventory = inventory;
        session.inventory = inventory;
        for (UUID id : session.waiting) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && inReach(player, key)) {
                show(player, session);
            }
        }
        session.waiting.clear();
        if (inventory.getViewers().isEmpty()) {
            end(session, true);
        }
    }

    /**
     * Whatever is open of the furniture on {@code support} - single, or a double chest it is half of
     * - is saved and shut, or forgotten while still being read: before two chests join or a double
     * chest parts, so nothing is open in a shape it no longer has.
     */
    public void release(Block support) {
        Session session = sessions.get(Key.of(support));
        if (session == null) {
            return;
        }
        if (session.inventory != null) {
            closeAndSave(session);
        }
        end(session, false);
    }

    private void loaded(Session session, @Nullable Optional<StoredInventory> stored, @Nullable Throwable error) {
        Key key = session.holder.key;
        if (sessions.get(key) != session) {
            // Broken, or the plugin stopped, while it was being read.
            return;
        }
        if (error != null) {
            end(session, true);
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            logger.warning("Could not read the storage at " + describe(key) + ": " + cause.getMessage());
            tellWaiting(session, "This storage cannot be opened right now.");
            return;
        }
        ItemStack[] items;
        try {
            items = stored == null || stored.isEmpty() ? new ItemStack[0] : ItemStack.deserializeItemsFromBytes(stored.get().contents());
        } catch (RuntimeException exception) {
            // Opened empty, it would be saved empty on close: refused instead, the bytes left as they are.
            end(session, true);
            logger.log(Level.SEVERE, "The contents saved for the storage at " + describe(key) + " cannot be read;"
                    + " it stays shut and its row in " + DatabaseManager.STORAGE_TABLE + " is left untouched.", exception);
            tellWaiting(session, "This storage cannot be opened: its contents could not be read.");
            return;
        }

        Inventory inventory = plugin.getServer().createInventory(session.holder, session.spec.sizeFor(used(items)),
                session.title);
        for (int slot = 0; slot < items.length && slot < inventory.getSize(); slot++) {
            ItemStack item = items[slot];
            inventory.setItem(slot, item == null || item.isEmpty() ? null : item);
        }
        session.holder.inventory = inventory;
        session.inventory = inventory;

        for (UUID id : session.waiting) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null && inReach(player, key)) {
                show(player, session);
            }
        }
        session.waiting.clear();
        if (inventory.getViewers().isEmpty()) {
            // Everyone who asked walked away or logged off while it loaded.
            end(session, true);
        }
    }

    private void show(Player player, Session session) {
        player.openInventory(session.inventory);
    }

    /** The session is over: forgotten, and the furniture told, if it is still the one in memory. */
    private void end(Session session, boolean animate) {
        Key key = session.holder.key;
        if (sessions.get(key) == session) {
            sessions.remove(key);
        }
        if (session.holder.partner != null && sessions.get(session.holder.partner) == session) {
            sessions.remove(session.holder.partner);
        }
        World world = plugin.getServer().getWorld(key.world());
        if (world != null && world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
            lid.closed(world.getBlockAt(key.x(), key.y(), key.z()), animate);
        }
    }

    /** Someone closed a storage inventory: it is saved, and let go if nobody else has it open. */
    public void closed(HumanEntity player, Inventory inventory) {
        if (!(inventory.getHolder(false) instanceof Holder holder)) {
            return;
        }
        Session session = sessions.get(holder.key);
        if (session == null || session.inventory != inventory) {
            return;
        }
        save(session);
        boolean othersLooking = inventory.getViewers().stream().anyMatch(viewer -> viewer != player);
        if (!othersLooking) {
            end(session, true);
        }
    }

    /** A click or drag in a storage inventory: it is saved again at the next autosave. */
    public void changed(Inventory top) {
        if (top.getHolder(false) instanceof Holder holder) {
            Session session = sessions.get(holder.key);
            if (session != null && session.inventory == top) {
                session.dirty = true;
            }
        }
    }

    /**
     * The furniture on {@code support} is being broken: whatever it holds is dropped where it stood.
     * Called for all furniture, storage or not as currently defined - a storage taken out of the
     * configuration still gives back what was put in it.
     */
    public void broken(Block support) {
        Key key = Key.of(support);
        Location at = support.getLocation().add(0.5, 0.5, 0.5);
        Session session = sessions.remove(key);
        if (session != null && session.holder.partner != null) {
            brokenHalf(session, key, at);
            return;
        }
        if (session != null && session.inventory != null) {
            // Open right now: the live inventory is the truth, newer than anything saved.
            ItemStack[] contents = session.inventory.getContents();
            session.inventory.clear();
            for (HumanEntity viewer : List.copyOf(session.inventory.getViewers())) {
                viewer.closeInventory();
            }
            drop(at, contents);
            database.deleteInventory(key.world(), key.x(), key.y(), key.z())
                    .exceptionally(error -> logged(error, "forget the storage at " + describe(key)));
            return;
        }
        // Closed, or still being read: what was saved is read and deleted in one step, then dropped.
        database.takeInventory(key.world(), key.x(), key.y(), key.z()).whenComplete((stored, error) -> onMain(() -> {
            if (error != null) {
                logged(error, "drop the contents of the storage broken at " + describe(key) + " - they stay saved");
            } else if (stored.isPresent()) {
                dropSaved(at, stored.get());
            }
        }));
    }

    /**
     * Half of a double chest broken while its double inventory is in memory: that half's 27 slots are
     * dropped, the other half's saved, and whoever had it open sees it shut. While it is still being
     * read, it is simply forgotten, and the broken half's saved contents are dropped from the
     * database as for any chest.
     */
    private void brokenHalf(Session session, Key broken, Location at) {
        sessions.remove(session.holder.key);
        sessions.remove(session.holder.partner);
        if (session.inventory == null) {
            database.takeInventory(broken.world(), broken.x(), broken.y(), broken.z()).whenComplete((stored, error) ->
                    onMain(() -> {
                        if (error == null && stored.isPresent()) {
                            dropSaved(at, stored.get());
                        }
                    }));
            return;
        }
        ItemStack[] all = session.inventory.getContents();
        boolean firstBroken = broken.equals(session.holder.key);
        ItemStack[] dropped = Arrays.copyOfRange(all, firstBroken ? 0 : HALF_SLOTS, firstBroken ? HALF_SLOTS : PAIR_SLOTS);
        ItemStack[] kept = Arrays.copyOfRange(all, firstBroken ? HALF_SLOTS : 0, firstBroken ? PAIR_SLOTS : HALF_SLOTS);
        session.inventory.clear();
        for (HumanEntity viewer : List.copyOf(session.inventory.getViewers())) {
            viewer.closeInventory();
        }
        drop(at, dropped);
        Key keep = firstBroken ? session.holder.partner : session.holder.key;
        database.saveInventory(stored(keep, session.holder.furnitureId, kept))
                .exceptionally(error -> logged(error, "save the chest at " + describe(keep)));
        database.deleteInventory(broken.world(), broken.x(), broken.y(), broken.z())
                .exceptionally(error -> logged(error, "forget the chest at " + describe(broken)));
        World world = plugin.getServer().getWorld(keep.world());
        if (world != null && world.isChunkLoaded(keep.x() >> 4, keep.z() >> 4)) {
            lid.closed(world.getBlockAt(keep.x(), keep.y(), keep.z()), false);
        }
    }

    /**
     * New storage furniture placed. Anything still saved for this position belonged to furniture
     * removed without being broken - by a world edit, a rollback - and is dropped rather than handed
     * to the newcomer.
     */
    public void placed(Block support) {
        Key key = Key.of(support);
        Location at = support.getLocation().add(0.5, 1.1, 0.5);
        database.takeInventory(key.world(), key.x(), key.y(), key.z()).whenComplete((stored, error) -> onMain(() -> {
            if (error == null && stored.isPresent()) {
                logger.warning("Storage contents were still saved at " + describe(key) + ", from furniture removed"
                        + " without being broken; they were dropped there.");
                dropSaved(at, stored.get());
            }
        }));
    }

    /** A world is unloading: its open storage is saved and closed. */
    public void worldUnloaded(World world) {
        for (Session session : List.copyOf(new LinkedHashSet<>(sessions.values()))) {
            if (session.holder.key.world().equals(world.getUID())) {
                sessions.remove(session.holder.key);
                if (session.holder.partner != null) {
                    sessions.remove(session.holder.partner);
                }
                closeAndSave(session);
            }
        }
    }

    /** On disable: every open inventory is saved - queued ahead of the database closing - and closed. */
    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        List<Session> open = List.copyOf(new LinkedHashSet<>(sessions.values()));
        sessions.clear();
        for (Session session : open) {
            closeAndSave(session);
            // The lid shuts now, so the displays are not saved with it open.
            end(session, false);
        }
    }

    /** How many storage inventories are in memory: open, or being read. */
    public int open() {
        return new LinkedHashSet<>(sessions.values()).size();
    }

    // ---------------------------------------------------------------- internals

    private void tick() {
        boolean autosave = ++seconds % AUTOSAVE_SECONDS == 0;
        // Every open inventory that changed, in one database task and one transaction.
        List<StoredInventory> autosaved = new ArrayList<>();
        for (Session session : List.copyOf(new LinkedHashSet<>(sessions.values()))) {
            if (session.inventory == null) {
                continue;
            }
            for (HumanEntity viewer : List.copyOf(session.inventory.getViewers())) {
                if (viewer instanceof Player player && !inReach(player, session.holder.key)) {
                    player.closeInventory();
                }
            }
            if (autosave && session.dirty && sessions.get(session.holder.key) == session) {
                autosaved.addAll(rows(session));
            }
        }
        if (!autosaved.isEmpty()) {
            database.saveInventories(autosaved).exceptionally(error -> logged(error, "autosave "
                    + autosaved.size() + " storage inventories"));
        }
    }

    private void closeAndSave(Session session) {
        if (session.inventory == null) {
            return;
        }
        save(session);
        for (HumanEntity viewer : List.copyOf(session.inventory.getViewers())) {
            viewer.closeInventory();
        }
    }

    /** A snapshot of the inventory, taken now, written on the database thread. */
    private void save(Session session) {
        List<StoredInventory> rows = rows(session);
        Key key = session.holder.key;
        if (rows.size() > 1) {
            // Both halves in one transaction: never one saved and the other not.
            database.saveInventories(rows).exceptionally(error -> logged(error, "save the double chest at "
                    + describe(key)));
            return;
        }
        database.saveInventory(rows.get(0)).exceptionally(error -> logged(error, "save the storage at " + describe(key)));
    }

    /** The rows that hold the inventory as it is now - two for a double chest - which is then no longer changed. */
    private List<StoredInventory> rows(Session session) {
        ItemStack[] contents = session.inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] == null) {
                contents[slot] = ItemStack.empty();
            }
        }
        session.dirty = false;
        Key key = session.holder.key;
        if (session.holder.partner != null) {
            return List.of(stored(key, session.holder.furnitureId, Arrays.copyOfRange(contents, 0, HALF_SLOTS)),
                    stored(session.holder.partner, session.holder.furnitureId,
                            Arrays.copyOfRange(contents, HALF_SLOTS, PAIR_SLOTS)));
        }
        byte[] bytes = ItemStack.serializeItemsAsBytes(contents);
        return List.of(new StoredInventory(key.world(), key.x(), key.y(), key.z(), session.holder.furnitureId,
                contents.length, bytes));
    }

    /** A half's contents as a row: empty slots kept as empty stacks, so slot numbers survive. */
    private static StoredInventory stored(Key key, String furnitureId, ItemStack[] contents) {
        ItemStack[] filled = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            filled[slot] = contents[slot] == null ? ItemStack.empty() : contents[slot];
        }
        return new StoredInventory(key.world(), key.x(), key.y(), key.z(), furnitureId, filled.length,
                ItemStack.serializeItemsAsBytes(filled));
    }

    /** Drops saved contents; contents that cannot be read are written back rather than lost. */
    private void dropSaved(Location at, StoredInventory stored) {
        try {
            drop(at, ItemStack.deserializeItemsFromBytes(stored.contents()));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "The contents saved for the storage at " + describe(Key.of(at.getBlock()))
                    + " cannot be read, so they were not dropped; they are kept in " + DatabaseManager.STORAGE_TABLE
                    + ".", exception);
            database.saveInventory(stored);
        }
    }

    private static void drop(Location at, ItemStack[] contents) {
        World world = at.getWorld();
        for (ItemStack item : contents) {
            if (item != null && !item.isEmpty()) {
                world.dropItemNaturally(at, item);
            }
        }
    }

    /** One past the last slot holding something; 0 for none. */
    private static int used(ItemStack[] items) {
        int used = 0;
        for (int slot = 0; slot < items.length; slot++) {
            if (items[slot] != null && !items[slot].isEmpty()) {
                used = slot + 1;
            }
        }
        return used;
    }

    private boolean inReach(Player player, Key key) {
        if (!player.isOnline() || !player.getWorld().getUID().equals(key.world())) {
            return false;
        }
        Location eyes = player.getLocation();
        double dx = eyes.getX() - (key.x() + 0.5);
        double dy = eyes.getY() - (key.y() + 0.5);
        double dz = eyes.getZ() - (key.z() + 0.5);
        return dx * dx + dy * dy + dz * dz <= REACH * REACH;
    }

    private void tellWaiting(Session session, String message) {
        for (UUID id : session.waiting) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                player.sendActionBar(Component.text(message, NamedTextColor.RED));
            }
        }
    }


    private void onMain(Runnable task) {
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    private <T> T logged(Throwable error, String what) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        logger.warning("Could not " + what + ": " + cause.getMessage());
        return null;
    }

    private String describe(Key key) {
        World world = plugin.getServer().getWorld(key.world());
        return (world != null ? world.getName() : key.world().toString()) + " " + key.x() + "," + key.y() + "," + key.z();
    }
}
