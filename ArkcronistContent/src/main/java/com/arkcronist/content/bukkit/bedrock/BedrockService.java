package com.arkcronist.content.bukkit.bedrock;

import com.arkcronist.content.bukkit.furniture.FurnitureService;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.core.geyser.BedrockConverter;
import com.arkcronist.content.core.geyser.BedrockText;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Furniture for Bedrock players. Geyser draws no item display, which is what a piece is to a Java
 * player, so a Bedrock player is sent, where the piece stands, the note block states Geyser was
 * told to draw as that piece's blocks - the carriers the converter cut its model into, turned as
 * the piece is turned. They go to Bedrock players alone, as block changes only their client sees:
 * the world, and every Java player, keep the barriers and light blocks.
 *
 * <p>A carrier goes where a support of the piece stands; a part of the model above or beside its
 * supports - a chair's back - only where the world has air. Everything here runs on the main
 * thread; a block change is a packet, and the pieces are found from the displays' own data.</p>
 */
public final class BedrockService implements Listener {

    /** Ticks after a chunk is sent before its carriers are: the chunk itself goes out first. */
    private static final long AFTER_CHUNK = 1;
    /** And after a Bedrock player touched a carrier, whose block the server may have sent back. */
    private static final long[] AFTER_TOUCH = {2, 10};

    private final Plugin plugin;
    private final FurnitureService furniture;
    private final ItemFactory items;
    private final Predicate<UUID> isBedrock;
    /** What Geyser was given as it started; null while it has not been, or when it started without it. */
    private final Supplier<BedrockConverter.Build> registered;
    /** The carriers Geyser knows, by piece and quarter turn. Main thread. */
    private Map<String, List<BedrockConverter.Carrier>[]> carriers = Map.of();
    private final Set<String> registeredIds = new HashSet<>();
    /** The pieces whose roots are loaded, by the chunk their anchor is in. Main thread. */
    private final Map<ChunkKey, Map<UUID, Piece>> pieces = new HashMap<>();
    private final Map<UUID, Piece> byRoot = new HashMap<>();
    private final Set<UUID> bedrockOnline = new HashSet<>();
    private boolean started;
    /** How carriers are sent: through Bukkit, or past plugins that rewrite block states (3.3.5). */
    private BlockChangeSender carriersThrough = BlockChangeSender.BUKKIT;
    private String carrierRoute = "Bukkit";
    /** Carrier blocks sent so far, for /arkcontent bedrock. */
    private long sent;
    private boolean warnedNew;

    private record ChunkKey(UUID world, int x, int z) {
        static ChunkKey of(Block block) {
            return new ChunkKey(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4);
        }
    }

    /** A piece as Bedrock players are shown it: where it was placed, and which carriers. */
    private record Piece(UUID root, ChunkKey chunk, Block anchor, List<BedrockConverter.Carrier> carriers) {
    }

    /**
     * @param isBedrock  whether a player plays on Bedrock
     * @param registered what Geyser was given as it started; null while it has not been, or when it
     *                   started without it
     */
    public BedrockService(Plugin plugin, FurnitureService furniture, ItemFactory items, Predicate<UUID> isBedrock,
                          Supplier<BedrockConverter.Build> registered) {
        this.plugin = plugin;
        this.registered = registered;
        this.furniture = furniture;
        this.items = items;
        this.isBedrock = isBedrock;
        furniture.onRootAppeared(this::appeared);
        furniture.onRootGoing(this::going);
    }

    /**
     * 3.3.5: carriers sent another way than through Bukkit - past CraftEngine, which shows every note
     * block of another instrument as a harp one, and so every carrier as another piece's.
     *
     * @param route what /arkcontent bedrock says they go through
     */
    public void sendCarriersThrough(BlockChangeSender sender, String route) {
        this.carriersThrough = sender;
        this.carrierRoute = route;
    }

    /** Whether the player plays on Bedrock. Any thread. */
    public boolean isBedrock(Player player) {
        return isBedrock.test(player.getUniqueId());
    }

    /**
     * After a rebuild. The carriers Bedrock players can be shown are those Geyser was given as it
     * started, until a restart; {@code latest} is only compared with them, to say when a rebuild
     * added furniture they will see after one. Main thread.
     */
    public void update(BedrockConverter.Build latest) {
        BedrockConverter.Build registered = this.registered.get();
        if (registered == null) {
            return;
        }
        if (!started) {
            started = true;
            Map<String, List<BedrockConverter.Carrier>[]> byPiece = new HashMap<>();
            for (BedrockConverter.Carrier carrier : registered.carriers()) {
                @SuppressWarnings("unchecked")
                List<BedrockConverter.Carrier>[] quarters = byPiece.computeIfAbsent(carrier.furnitureId(),
                        id -> new List[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()});
                quarters[carrier.quarter()].add(carrier);
            }
            carriers = byPiece;
            registered.items().forEach(item -> registeredIds.add(item.id()));
            scanLoaded();
        }
        if (!warnedNew) {
            long added = latest.items().stream().map(BedrockConverter.BedrockItem::id)
                    .filter(id -> !registeredIds.contains(id)).count();
            if (added > 0) {
                warnedNew = true;
                plugin.getLogger().info("[bedrock] " + added + " item(s) from this rebuild reach Bedrock players after"
                        + " a restart: Geyser takes custom items and blocks only as it starts.");
            }
        }
    }

    /** Every piece already standing in a loaded chunk - those the first build found. */
    private void scanLoaded() {
        for (World world : plugin.getServer().getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (furniture.isPieceRoot(display)) {
                    appeared(display);
                }
            }
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isBedrock(player)) {
                bedrockOnline.add(player.getUniqueId());
                showAround(player);
            }
        }
    }

    private void appeared(ItemDisplay root) {
        if (carriers.isEmpty() || !root.isValid()) {
            return;
        }
        String id = furniture.furnitureId(root);
        List<BedrockConverter.Carrier>[] quarters = id == null ? null : carriers.get(id);
        if (quarters == null) {
            return;
        }
        int quarter = Math.floorMod(Math.round(root.getLocation().getYaw() / 90f), 4);
        Block anchor = furniture.anchorOf(root);
        Piece piece = new Piece(root.getUniqueId(), ChunkKey.of(anchor), anchor, turned(quarters, quarter));
        forget(root.getUniqueId());
        byRoot.put(piece.root(), piece);
        pieces.computeIfAbsent(piece.chunk(), key -> new HashMap<>()).put(piece.root(), piece);
        if (!bedrockOnline.isEmpty()) {
            // The display goes up before its supports on placing: a tick later both are there.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> showToViewers(piece), 1);
        }
    }

    /**
     * The carriers of a turn: its own, or (3.5) for a piece one block across with none for its turn,
     * the unturned piece's - which stands on the same blocks.
     */
    private static List<BedrockConverter.Carrier> turned(List<BedrockConverter.Carrier>[] quarters, int quarter) {
        return quarters[quarter].isEmpty() ? quarters[0] : quarters[quarter];
    }

    private void going(ItemDisplay root) {
        Piece piece = forget(root.getUniqueId());
        if (piece == null || bedrockOnline.isEmpty()) {
            return;
        }
        // The supports are gone a tick later, and the server sends them as air; the carriers above
        // them are only on the client, and the world's blocks are sent back in their place.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player player : viewers(piece)) {
                for (BedrockConverter.Carrier carrier : piece.carriers()) {
                    Block block = at(piece, carrier);
                    player.sendBlockChange(block.getLocation(), block.getBlockData());
                }
            }
        }, 1);
    }

    private @Nullable Piece forget(UUID root) {
        Piece piece = byRoot.remove(root);
        if (piece != null) {
            Map<UUID, Piece> inChunk = pieces.get(piece.chunk());
            if (inChunk != null) {
                inChunk.remove(root);
                if (inChunk.isEmpty()) {
                    pieces.remove(piece.chunk());
                }
            }
        }
        return piece;
    }

    private static Block at(Piece piece, BedrockConverter.Carrier carrier) {
        return piece.anchor().getRelative(carrier.dx(), carrier.dy(), carrier.dz());
    }

    /** Sends the piece's carriers to one player, where the world still has room for them. */
    private void show(Player player, Piece piece) {
        Map<Location, BlockData> changes = new HashMap<>();
        for (BedrockConverter.Carrier carrier : piece.carriers()) {
            Block block = at(piece, carrier);
            Material type = block.getType();
            boolean fits = carrier.support() ? type == Material.BARRIER || type == Material.LIGHT : type.isAir();
            if (fits) {
                changes.put(block.getLocation(), plugin.getServer().createBlockData(carrier.state().asBlockData()));
            }
        }
        if (!changes.isEmpty()) {
            carriersThrough.send(player, changes);
            sent += changes.size();
        }
    }

    private List<Player> viewers(Piece piece) {
        List<Player> viewers = new ArrayList<>();
        World world = plugin.getServer().getWorld(piece.chunk().world());
        if (world == null) {
            return viewers;
        }
        for (Player player : world.getPlayers()) {
            if (bedrockOnline.contains(player.getUniqueId())
                    && player.isChunkSent(Chunk.getChunkKey(piece.chunk().x(), piece.chunk().z()))) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    private void showToViewers(Piece piece) {
        if (byRoot.get(piece.root()) != piece) {
            return;
        }
        for (Player player : viewers(piece)) {
            show(player, piece);
        }
    }

    /** Every piece in the chunks the player has been sent. */
    private void showAround(Player player) {
        UUID world = player.getWorld().getUID();
        for (Map.Entry<ChunkKey, Map<UUID, Piece>> entry : pieces.entrySet()) {
            ChunkKey chunk = entry.getKey();
            if (chunk.world().equals(world) && player.isChunkSent(Chunk.getChunkKey(chunk.x(), chunk.z()))) {
                entry.getValue().values().forEach(piece -> show(player, piece));
            }
        }
    }

    /** For /arkcontent bedrock. Main thread. */
    public List<String> status() {
        BedrockConverter.Build given = registered.get();
        List<String> lines = new ArrayList<>();
        if (given == null) {
            lines.add("Geyser has not been given the content - it started before the first build, or the build failed.");
        } else {
            long pieceKinds = carriers.size();
            lines.add("Geyser was given " + given.items().size() + " item(s), " + given.blocks().size() + " block(s) ("
                    + given.carriers().size() + " furniture carrier(s) for " + pieceKinds + " piece(s)), and "
                    + given.packId() + ".mcpack.");
            if (!given.unpictured().isEmpty()) {
                lines.add(given.unpictured().size() + " item(s) with no picture, shown as a question mark:"
                        + " output/bedrock/items-without-picture.txt");
            }
        }
        lines.add(bedrockOnline.size() + " Bedrock player(s) online; " + byRoot.size() + " piece(s) loaded that they"
                + " are shown; " + sent + " carrier block(s) sent since the start, through " + carrierRoute + ".");
        return lines;
    }

    /**
     * 3.4: {@code /arkcontent bedrock look} - what Bedrock players are shown of the piece the player
     * looks at, and why not, where they are not. A Bedrock player asking is sent its blocks again.
     * Main thread.
     */
    public List<String> look(Player player) {
        List<String> lines = new ArrayList<>();
        Block part = furniture.target(player).orElse(null);
        ItemDisplay root = part == null ? null : furniture.pieceRoot(part);
        String id = root == null ? null : furniture.furnitureId(root);
        if (root == null || id == null) {
            lines.add("Look at a piece of furniture, within reach, and ask again.");
            return lines;
        }
        int quarter = Math.floorMod(Math.round(root.getLocation().getYaw() / 90f), 4);
        Block anchor = furniture.anchorOf(root);
        lines.add(id + " at " + anchor.getX() + " " + anchor.getY() + " " + anchor.getZ() + ", turned " + quarter * 90
                + " degrees.");
        if (registered.get() == null) {
            lines.add("Geyser has not been given the content: Bedrock players see none of it.");
            return lines;
        }
        List<BedrockConverter.Carrier>[] quarters = carriers.get(id);
        if (quarters == null) {
            lines.add(registeredIds.contains(id)
                    ? "It has no blocks for Bedrock players: it is left out (bedrock.skip-furniture), it is too big"
                    + " to be cut into blocks, or the note block states ran out - the log of the last build says which."
                    : "Geyser does not know it: it was added after Geyser started. Restart the proxy (or the server,"
                    + " with Geyser-Spigot) and Bedrock players see it.");
            return lines;
        }
        Piece piece = byRoot.get(root.getUniqueId());
        List<BedrockConverter.Carrier> list = piece != null ? piece.carriers() : turned(quarters, quarter);
        int fit = 0;
        Map<String, Integer> kept = new java.util.TreeMap<>();
        for (BedrockConverter.Carrier carrier : list) {
            Block block = anchor.getRelative(carrier.dx(), carrier.dy(), carrier.dz());
            Material type = block.getType();
            if (carrier.support() ? type == Material.BARRIER || type == Material.LIGHT : type.isAir()) {
                fit++;
            } else {
                kept.merge(type.getKey().getKey(), 1, Integer::sum);
            }
        }
        if (quarters[quarter].isEmpty()) {
            lines.add("Shown unturned: there were not enough note block states to turn it as well.");
        }
        lines.add(list.size() + " block(s) for Bedrock players, " + fit + " of them where they go"
                + (kept.isEmpty() ? "." : "; " + (list.size() - fit) + " left out where the world has " + kept
                + " in their place."));
        if (piece == null) {
            lines.add("Not among the pieces shown yet: it is, once its chunk loads again.");
            return lines;
        }
        if (isBedrock(player)) {
            show(player, piece);
            lines.add("You play on Bedrock: its blocks were sent to you again, through " + carrierRoute + ".");
        } else {
            lines.add("You play on Java and see the piece itself; " + viewers(piece).size()
                    + " Bedrock player(s) near enough are sent its blocks, through " + carrierRoute + ".");
        }
        return lines;
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isBedrock(player)) {
            return;
        }
        bedrockOnline.add(player.getUniqueId());
        // Stacks made before their item had a custom_model_data number get it: Geyser 2.8 sees by it.
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (ItemStack stack : contents) {
            changed |= stack != null && items.repair(stack);
        }
        if (changed) {
            inventory.setContents(contents);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        bedrockOnline.remove(event.getPlayer().getUniqueId());
    }

    /** A chunk reaches a Bedrock player: the carriers in it, and in those around it, a tick later. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkSent(PlayerChunkLoadEvent event) {
        Player player = event.getPlayer();
        if (pieces.isEmpty() || !bedrockOnline.contains(player.getUniqueId())) {
            return;
        }
        Chunk chunk = event.getChunk();
        UUID world = chunk.getWorld().getUID();
        List<Piece> near = new ArrayList<>();
        // A piece is filed by its anchor, and reaches at most a few blocks into the next chunk.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Map<UUID, Piece> inChunk = pieces.get(new ChunkKey(world, chunk.getX() + dx, chunk.getZ() + dz));
                if (inChunk != null) {
                    near.addAll(inChunk.values());
                }
            }
        }
        if (near.isEmpty()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                near.forEach(piece -> {
                    if (byRoot.get(piece.root()) == piece && player.getWorld().getUID().equals(world)) {
                        show(player, piece);
                    }
                });
            }
        }, AFTER_CHUNK);
    }

    /**
     * A Bedrock player touched a carrier: the server answers a refused break or use with the block
     * it really has there - a barrier - which would take the piece's block off their screen.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTouch(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null || byRoot.isEmpty() || !bedrockOnline.contains(event.getPlayer().getUniqueId())) {
            return;
        }
        Material type = clicked.getType();
        if (type != Material.BARRIER && type != Material.LIGHT) {
            return;
        }
        ItemDisplay root = furniture.pieceRoot(clicked);
        Piece piece = root == null ? null : byRoot.get(root.getUniqueId());
        if (piece == null) {
            return;
        }
        Player player = event.getPlayer();
        for (long delay : AFTER_TOUCH) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && byRoot.get(piece.root()) == piece) {
                    show(player, piece);
                }
            }, delay);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        if (byRoot.isEmpty()) {
            return;
        }
        for (Entity entity : event.getEntities()) {
            if (entity instanceof ItemDisplay) {
                forget(entity.getUniqueId());
            }
        }
    }

    /**
     * Menu titles drawn with pictures and negative spaces, which a Bedrock client has neither of:
     * just their words. After every other title rewrite.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !bedrockOnline.contains(player.getUniqueId())) {
            return;
        }
        Component override = event.titleOverride();
        Component title = override != null ? override : event.getView().title();
        String text = PlainTextComponentSerializer.plainText().serialize(title);
        String plain = BedrockText.plain(text);
        if (!plain.equals(text)) {
            event.titleOverride(Component.text(plain));
        }
    }
}
