package com.arkcronist.content.bukkit.editor;

import com.arkcronist.content.bukkit.ArkContentPlugin;
import com.arkcronist.content.bukkit.ContentPipeline;
import com.arkcronist.content.bukkit.furniture.FurnitureService;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.furniture.DisplayFile;
import com.arkcronist.content.core.furniture.DisplayTransform;
import com.arkcronist.content.core.furniture.EditorLayout;
import com.arkcronist.content.core.storage.FurnitureTransform;
import com.arkcronist.content.core.storage.FurnitureTransformStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * {@code /arkcontent editor}: moves, scales and turns a placed piece of furniture from a chest menu,
 * live, and keeps the result.
 *
 * <p>Threads: every click changes the display's transformation on the main thread, in the same
 * tick, so the admin sees the piece move behind the menu. Nothing is written then. Saving - the
 * Save button, or closing the menu with something changed - hands the writing to the
 * {@code ArkContent-Worker} thread and returns at once:</p>
 * <ul>
 *   <li>for every piece of the furniture, the worker writes the three values into the item's YAML
 *       file (backed up first, see {@link DisplayFile}), in line with rebuilds; the main thread then
 *       rebuilds and redraws the loaded pieces;</li>
 *   <li>for this piece only, the worker hands the row to the {@code ArkContent-DB} thread, which
 *       owns the one SQLite connection; memory has the values at once.</li>
 * </ul>
 *
 * <p>One admin edits one piece at a time, and a piece is not opened again until its last save has
 * landed. Main thread only.</p>
 */
public final class FurnitureEditor {

    /** How far {@code /arkcontent editor <id>} looks for a piece of that furniture. */
    private static final double SEARCH_RADIUS = 8;
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    private final ArkContentPlugin plugin;
    private final FurnitureService furniture;
    private final FurnitureTransformStore transforms;
    private final Map<UUID, EditorSession> byPlayer = new HashMap<>();
    private final Map<UUID, EditorSession> byDisplay = new HashMap<>();
    /** Pieces whose save has not landed yet. */
    private final Set<UUID> saving = new HashSet<>();
    /**
     * Furniture saved for every piece and not yet rebuilt and redrawn: until then the loaded type
     * still says the old values, so none of its pieces is opened.
     */
    private final Set<String> savingTypes = new HashSet<>();
    /** Saves in flight, waited for on shutdown. Completed off the main thread, hence concurrent. */
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();

    public FurnitureEditor(ArkContentPlugin plugin, FurnitureService furniture, FurnitureTransformStore transforms) {
        this.plugin = plugin;
        this.furniture = furniture;
        this.transforms = transforms;
    }

    /**
     * Opens the editor on the furniture {@code player} is looking at - or, given an id, the nearest
     * loaded piece of that furniture when they are not looking at one.
     *
     * @return why it could not open, or null when it did
     */
    public @Nullable String open(Player player, @Nullable String wanted) {
        String id = null;
        if (wanted != null) {
            Optional<CustomItem> item = plugin.items().find(wanted.trim());
            if (item.isEmpty()) {
                return "No custom item '" + wanted.trim() + "'.";
            }
            if (!(item.get().placement() instanceof Placement.Furniture)) {
                return item.get().id() + " is not furniture.";
            }
            id = item.get().id();
        }
        ItemDisplay display = furniture.target(player).map(furniture::display).orElse(null);
        if (id != null && (display == null || !id.equals(furniture.furnitureId(display)))) {
            display = nearest(player, id);
        }
        if (display == null) {
            return id == null ? "Look at a piece of furniture first, or name one: /arkcontent editor <id>."
                    : "No " + id + " within " + (int) SEARCH_RADIUS + " blocks; look at one or go closer.";
        }
        return open(player, display);
    }

    private @Nullable String open(Player player, ItemDisplay display) {
        String id = furniture.furnitureId(display);
        Optional<CustomItem> item = id == null ? Optional.empty() : plugin.items().get(id);
        if (item.isEmpty() || !(item.get().placement() instanceof Placement.Furniture definition)) {
            return "That furniture (" + id + ") is no longer defined in contents/; there is nothing to edit.";
        }
        if (furniture.drawnByModelEngine(definition)) {
            return id + " is drawn by ModelEngine; change its look in the ModelEngine blueprint.";
        }
        EditorSession other = byDisplay.get(display.getUniqueId());
        if (other != null && !other.player.equals(player.getUniqueId())) {
            Player editing = plugin.getServer().getPlayer(other.player);
            return (editing != null ? editing.getName() : "Someone") + " is editing that piece.";
        }
        if (saving.contains(display.getUniqueId()) || savingTypes.contains(id)) {
            return "The last edit of " + id + " is still being saved and rebuilt; try again in a moment.";
        }
        EditorSession mine = byPlayer.get(player.getUniqueId());
        if (mine != null) {
            finish(mine, true);
        }

        Block anchor = furniture.anchorOf(display);
        FurnitureTransform own = transforms.at(anchor.getWorld().getUID(), anchor.getX(), anchor.getY(), anchor.getZ());
        boolean hasOwn = own != null && own.furnitureId().equals(id) && definition.animated() == null;
        DisplayTransform opened = DisplayTransform.of(furniture.effectiveDisplay(display, definition));
        EditorSession session = new EditorSession(player.getUniqueId(), display.getUniqueId(), anchor.getWorld().getUID(),
                anchor.getX(), anchor.getY(), anchor.getZ(), item.get(), definition, opened,
                hasOwn ? EditorSession.Scope.PIECE : EditorSession.Scope.TYPE, relative(item.get()));
        // Still while it is edited: an animation would pose the bones over the values shown.
        furniture.stopAnimation(display);
        furniture.apply(display, definition, session.drawn());
        session.render(plugin.itemFactory());
        byPlayer.put(session.player, session);
        byDisplay.put(session.display, session);
        player.openInventory(session.getInventory());
        return null;
    }

    /** The loaded root display of furniture {@code id} closest to the player, within reach. */
    private @Nullable ItemDisplay nearest(Player player, String id) {
        Location at = player.getLocation();
        return at.getWorld().getNearbyEntitiesByType(ItemDisplay.class, at, SEARCH_RADIUS).stream()
                .filter(display -> furniture.isPieceRoot(display) && id.equals(furniture.furnitureId(display)))
                .min(Comparator.comparingDouble(display -> display.getLocation().distanceSquared(at)))
                .orElse(null);
    }

    private String relative(CustomItem item) {
        Path source = item.definition().source().toAbsolutePath().normalize();
        Path contents = plugin.pipeline().contentsDir().toAbsolutePath().normalize();
        return (source.startsWith(contents) ? contents.relativize(source) : source.getFileName()).toString()
                .replace('\\', '/');
    }

    // ---------------------------------------------------------------- menu events

    /** The editor shown in {@code view}, if that is what it is. */
    static @Nullable EditorSession of(InventoryView view) {
        return view.getTopInventory().getHolder(false) instanceof EditorSession session ? session : null;
    }

    /** A click on one of the menu's own slots. Every click is already cancelled by the listener. */
    void click(Player player, EditorSession session, int slot, ClickType click) {
        if (session.finished) {
            return;
        }
        ItemDisplay display = live(session);
        if (display == null) {
            finish(session, false);
            closeLater(player, session);
            return;
        }
        EditorLayout.Action action = EditorLayout.at(slot);
        // A double click arrives as two clicks and then a third event; number keys and drops are not presses.
        if (action == null || !(click == ClickType.LEFT || click == ClickType.RIGHT || click == ClickType.SHIFT_LEFT
                || click == ClickType.SHIFT_RIGHT)) {
            return;
        }
        switch (action) {
            case EditorLayout.Adjust adjust -> {
                session.current = EditorLayout.click(session.current, session.part, adjust, click.isShiftClick());
                furniture.apply(display, session.furniture, session.drawn());
            }
            case EditorLayout.Select select -> session.part = select.part();
            case EditorLayout.Button button -> {
                switch (button) {
                    case SCOPE -> {
                        if (!session.pieceScopeAllowed()) {
                            player.sendActionBar(Component.text("Animated furniture is edited for every piece.",
                                    NamedTextColor.YELLOW));
                            return;
                        }
                        session.scope = session.scope == EditorSession.Scope.TYPE
                                ? EditorSession.Scope.PIECE : EditorSession.Scope.TYPE;
                    }
                    case DEFAULT_PART -> {
                        session.current = session.current.with(session.part, DisplayTransform.IDENTITY.get(session.part));
                        furniture.apply(display, session.furniture, session.drawn());
                    }
                    case REVERT -> {
                        session.current = session.opened;
                        furniture.apply(display, session.furniture, session.drawn());
                    }
                    case SAVE, CANCEL -> {
                        finish(session, button == EditorLayout.Button.SAVE);
                        closeLater(player, session);
                        return;
                    }
                }
            }
        }
        session.render(plugin.itemFactory());
    }

    /** The menu was closed - Escape, a disconnect, another inventory: what changed is saved. */
    void closed(EditorSession session) {
        if (!session.finished) {
            finish(session, true);
        }
    }

    /** Closing a view from inside its own click event is not safe; next tick is. */
    private void closeLater(Player player, EditorSession session) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (of(player.getOpenInventory()) == session) {
                player.closeInventory();
            }
        });
    }

    private @Nullable ItemDisplay live(EditorSession session) {
        Entity entity = plugin.getServer().getEntity(session.display);
        return entity instanceof ItemDisplay display && display.isValid() ? display : null;
    }

    // ---------------------------------------------------------------- saving

    /** Ends a session: saves what changed when {@code save}, puts the piece back as it was otherwise. */
    private void finish(EditorSession session, boolean save) {
        session.finished = true;
        byPlayer.remove(session.player, session);
        byDisplay.remove(session.display, session);
        Player player = plugin.getServer().getPlayer(session.player);
        ItemDisplay display = live(session);
        if (display == null) {
            tell(player, "The piece is gone - broken or unloaded - so nothing was saved.", NamedTextColor.RED);
            return;
        }
        if (!save) {
            furniture.apply(display, session.furniture, session.opened.applyTo(session.furniture.display()));
            tell(player, "Changes discarded; the piece is back as it was.", NamedTextColor.GRAY);
            return;
        }
        if (!session.changed()) {
            tell(player, "Nothing changed, nothing saved.", NamedTextColor.GRAY);
            return;
        }
        if (session.scope == EditorSession.Scope.TYPE) {
            saveType(session, player);
        } else {
            savePiece(session, player);
        }
    }

    /** Every piece: the YAML file on the worker, then a rebuild and the loaded pieces redrawn. */
    private void saveType(EditorSession session, @Nullable Player player) {
        ContentPipeline pipeline = plugin.pipeline();
        saving.add(session.display);
        savingTypes.add(session.item.id());
        CompletableFuture<DisplayFile.Saved> write = pipeline.saveDisplay(session.item.definition(), session.current)
                .thenApply(saved -> {
                    // Still on the worker: the piece follows its type again, so a look of its own goes -
                    // from memory now, its row on the database's thread.
                    if (transforms.at(session.world, session.x, session.y, session.z) != null) {
                        transforms.forget(session.world, session.x, session.y, session.z);
                        track(transforms.delete(session.world, session.x, session.y, session.z));
                    }
                    return saved;
                });
        track(write);
        String what = session.item.id();
        write.whenCompleteAsync((saved, error) -> guarded("the save of " + what, () -> {
            saving.remove(session.display);
            if (error != null || saved.backup() == null) {
                savingTypes.remove(what);
            }
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                plugin.getLogger().log(Level.WARNING, "The furniture editor could not save " + what + " to contents/"
                        + session.file + ": " + cause.getMessage());
                ItemDisplay display = live(session);
                if (display != null) {
                    furniture.apply(display, session.furniture, session.opened.applyTo(session.furniture.display()));
                }
                tell(player, "Not saved: " + cause.getMessage() + ". The piece is back as it was and contents/ is"
                        + " unchanged" + (session.pieceScopeAllowed() ? "; \"this piece only\" still works." : "."),
                        NamedTextColor.RED);
                return;
            }
            plugin.getLogger().info("Furniture editor: " + (player != null ? player.getName() : "an admin")
                    + " set " + what + " to translation " + DisplayTransform.yaml(session.current.translation())
                    + ", scale " + DisplayTransform.yaml(session.current.scale()) + ", rotation "
                    + DisplayTransform.yaml(session.current.rotation()) + " in contents/" + session.file
                    + (saved.backup() == null ? "" : " (previous text: " + plugin.getDataFolder().toPath()
                    .toAbsolutePath().relativize(saved.backup().toAbsolutePath()) + ")") + ".");
            if (saved.backup() == null) {
                tell(player, "contents/" + session.file + " already says so; nothing to rebuild.", NamedTextColor.GREEN);
                furniture.refresh(what);
                return;
            }
            tell(player, "Saved to contents/" + session.file + " (the old text is in data/editor-backups/)."
                    + " Rebuilding...", NamedTextColor.GREEN);
            pipeline.rebuild().whenCompleteAsync((report, failed) -> guarded("the redraw of " + what, () -> {
                savingTypes.remove(what);
                if (failed != null) {
                    tell(player, "The file is saved, but the rebuild failed - see the console.", NamedTextColor.RED);
                    return;
                }
                int redrawn = furniture.refresh(what);
                tell(player, "Live: every " + what + " is drawn with the new values (" + redrawn
                        + " loaded piece(s) redrawn).", NamedTextColor.GREEN);
            }), pipeline.mainThread());
        }), pipeline.mainThread());
    }

    /**
     * Runs a step that follows a save on the main thread. A future swallows what its callbacks
     * throw; here it is logged instead, so a save never goes quiet.
     */
    private void guarded(String what, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().log(Level.WARNING, "The furniture editor failed in " + what, error);
        }
    }

    /** This piece only: memory now; the row handed by the worker to the database's thread. */
    private void savePiece(EditorSession session, @Nullable Player player) {
        ContentPipeline pipeline = plugin.pipeline();
        CompletableFuture<?> write;
        boolean asType = session.current.equals(session.typeValues);
        if (asType) {
            // The same as its type: no look of its own to keep.
            transforms.forget(session.world, session.x, session.y, session.z);
            write = CompletableFuture.supplyAsync(() -> transforms.delete(session.world, session.x, session.y,
                    session.z), pipeline.worker()).thenCompose(deleted -> deleted);
        } else {
            FurnitureTransform own = new FurnitureTransform(session.world, session.x, session.y, session.z,
                    session.item.id(), session.current);
            transforms.remember(own);
            write = CompletableFuture.supplyAsync(() -> transforms.write(own), pipeline.worker())
                    .thenCompose(written -> written);
        }
        saving.add(session.display);
        track(write);
        write.whenCompleteAsync((ignored, error) -> guarded("the save of a piece", () -> {
            saving.remove(session.display);
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                tell(player, "Could not write it to the database (" + cause.getMessage() + "); the piece keeps this"
                        + " look until the server restarts.", NamedTextColor.RED);
                return;
            }
            tell(player, asType ? "Saved: this piece follows its type again."
                    : "Saved for this piece only; other " + session.item.id() + " are unchanged.", NamedTextColor.GREEN);
        }), pipeline.mainThread());
    }

    private void track(CompletableFuture<?> future) {
        pending.add(future);
        future.whenComplete((ignored, error) -> pending.remove(future));
    }

    private static void tell(@Nullable Player player, String message, NamedTextColor colour) {
        if (player != null && player.isOnline()) {
            player.sendMessage(Component.text(message, colour));
        }
    }

    // ---------------------------------------------------------------- lifecycle

    public int open() {
        return byPlayer.size();
    }

    /**
     * On disable, before the worker and the database stop: open editors are saved as if closed,
     * and every save in flight is waited for - at most {@value #SHUTDOWN_WAIT_SECONDS} seconds.
     */
    public void shutdown() {
        for (EditorSession session : List.copyOf(byPlayer.values())) {
            finish(session, true);
            Player player = plugin.getServer().getPlayer(session.player);
            if (player != null && of(player.getOpenInventory()) == session) {
                player.closeInventory();
            }
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SHUTDOWN_WAIT_SECONDS);
        for (CompletableFuture<?> save : List.copyOf(pending)) {
            try {
                save.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException exception) {
                plugin.getLogger().warning("A furniture editor save did not finish within " + SHUTDOWN_WAIT_SECONDS
                        + " seconds of shutdown; it may be lost.");
                return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception failed) {
                // Already reported where it failed.
            }
        }
    }
}
