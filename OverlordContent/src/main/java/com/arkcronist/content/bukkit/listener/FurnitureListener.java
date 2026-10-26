package com.arkcronist.content.bukkit.listener;

import com.arkcronist.content.bukkit.furniture.AmbienceService;
import com.arkcronist.content.bukkit.furniture.CommandSigns;
import com.arkcronist.content.bukkit.furniture.FurnitureService;
import com.arkcronist.content.bukkit.furniture.LegacyFurniture;
import com.arkcronist.content.bukkit.furniture.GuillotineService;
import com.arkcronist.content.bukkit.furniture.SeatService;
import com.arkcronist.content.bukkit.furniture.StorageService;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.protection.Interaction;
import com.arkcronist.content.bukkit.protection.Protection;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.storage.ChestPair;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Placing, breaking, using and tidying furniture.
 *
 * <p>A right click sits on a seat or opens a storage furniture's inventory, once every installed
 * protection plugin agrees - see {@link Protection}; a click on an invisible block reaches none of
 * them on its own.</p>
 *
 * <p>Breaking has two ways in. In creative a barrier breaks like any block and fires a
 * {@link BlockBreakEvent}; in survival neither support can be mined - a barrier is unbreakable and a
 * light block cannot even be targeted - so a punch at the furniture is turned into a
 * {@code BlockBreakEvent} here. Either way the event is fired for real, so protection plugins decide
 * as they would for any block, and the removal itself happens in one place, {@link #onBreak}.</p>
 *
 * <p>A chest or bed support is the real vanilla block, and is mined, used and blown up as one -
 * except that what it drops is the furniture's item, a chest's own inventory is never opened or
 * reached by a hopper, and a chest never becomes half of a double chest.</p>
 */
public final class FurnitureListener implements Listener {

    private final Plugin plugin;
    private final FurnitureService furniture;
    private final SeatService seats;
    private final StorageService storage;
    private final Protection protection;
    private final ItemFactory items;
    /** Opens quest boards; null until it is set. */
    private @Nullable QuestBoardOpener boards;
    /** Drinks, command signs and guillotines; null until they are set. */
    private @Nullable AmbienceService ambience;
    /** 3.4: fills the canteen in the player's hand at a piece, if it fills there; null without canteens. */
    private java.util.function.BiPredicate<Player, String> canteens;
    /** 3.4: storage pieces that show what they hold; null until set. */
    private com.arkcronist.content.bukkit.furniture.ShownContents shown;
    private @Nullable CommandSigns signs;
    private @Nullable GuillotineService guillotines;
    /** 3.1: furniture ItemsAdder left in the world; null until it is set. */
    private @Nullable LegacyFurniture legacy;

    /** What a right click on a quest board does. */
    public interface QuestBoardOpener {
        void open(Player player, Block support, CustomItem board, Placement.QuestBoard spec);
    }

    /** Who opens quest boards. */
    public void useBoards(QuestBoardOpener boards) {
        this.boards = boards;
    }

    /** Who knows the furniture ItemsAdder left behind, so breaking it takes its model too. */
    public void useLegacy(LegacyFurniture legacy) {
        this.legacy = legacy;
    }

    /** Who hands out drinks, runs command signs and drops guillotine blades. */
    public void useInteractions(AmbienceService ambience, CommandSigns signs, GuillotineService guillotines) {
        this.ambience = ambience;
        this.signs = signs;
        this.guillotines = guillotines;
    }

    public FurnitureListener(Plugin plugin, FurnitureService furniture, SeatService seats, StorageService storage,
                             Protection protection, ItemFactory items) {
        this.plugin = plugin;
        this.furniture = furniture;
        this.seats = seats;
        this.storage = storage;
        this.protection = protection;
        this.items = items;
    }

    /** A light-block support is replaceable, like air: a block placed into it would erase it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void protectLightSupports(BlockPlaceEvent event) {
        if (FurnitureService.isSupport(event.getBlockReplacedState().getType())
                && furniture.claims(event.getBlockPlaced())) {
            event.setCancelled(true);
        }
    }

    /**
     * A piece more than one block big needs every block it covers free - air, or something a block
     * replaces, like grass - claimed by nothing else, and the placer allowed to build on each.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void checkRoom(BlockPlaceEvent event) {
        Optional<CustomItem> item = items.identify(event.getItemInHand());
        if (item.isEmpty() || !(item.get().placement() instanceof Placement.Furniture spec) || spec.footprint().single()) {
            return;
        }
        Player player = event.getPlayer();
        List<Block> blocks = FurnitureService.footprint(event.getBlockPlaced(), spec,
                FurnitureService.placementYaw(player, event.getBlockPlaced(), spec));
        for (Block part : blocks.subList(1, blocks.size())) {
            boolean free = (part.isEmpty() || part.isReplaceable()) && !part.isLiquid() && !furniture.claims(part);
            if (!free) {
                event.setCancelled(true);
                player.sendActionBar(Component.text("Not enough room: this needs " + spec.footprint().width() + " x "
                        + spec.footprint().height() + (spec.footprint().depth() > 1 ? " x " + spec.footprint().depth() : "")
                        + " free blocks (across, up" + (spec.footprint().depth() > 1 ? ", back" : "") + ")", NamedTextColor.RED));
                return;
            }
            if (!protection.check(player, part, Interaction.PLACE)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** A bed is placed as a {@code BlockMultiPlaceEvent}, which reaches this too, its block the foot. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Optional<CustomItem> item = items.identify(event.getItemInHand());
        if (item.isEmpty() || !(item.get().placement() instanceof Placement.Furniture spec)) {
            if (block.getType() == Material.CHEST) {
                // A vanilla chest placed beside a furniture chest is not joined to it.
                furniture.keepSingle(block);
            }
            return;
        }
        if (FurnitureService.matches(block, spec)
                && furniture.place(block, event.getPlayer(), item.get(), spec) && spec.storage() != null) {
            storage.placed(block);
            // Beside a chest of the same furniture, as vanilla chests do: one double chest - if the
            // placer may open that one too, or the join would be a way into someone else's chest.
            furniture.partnerFor(block, item.get(), spec, event.getPlayer())
                    .filter(partner -> protection.check(event.getPlayer(), partner, Interaction.CONTAINER))
                    .ifPresent(partner -> furniture.join(block, partner, item.get(), spec));
        }
    }

    /**
     * A right click on a seat sits on it, and on storage furniture opens it; a drink is drunk, a
     * command sign runs its commands, and a guillotine kneels the player in its stocks - or, on its
     * lever, drops the blade. Sneaking, the click is
     * left to vanilla, to place a block against the furniture. Any other furniture does not react as
     * its support block would: holding a light item, a click on a light block steps its light level.
     *
     * <p>A chest opens when a vanilla chest would: not sneaking, or sneaking with both hands empty -
     * and its own inventory never does. A bed is left to vanilla altogether: sleeping in it is the
     * point.</p>
     *
     * <p>A light-block support cannot be clicked at all, so a click that lands on the floor behind
     * it, or in the air, is followed along the line of sight to find it - the same way a punch is.</p>
     */
    // Not ignoreCancelled: a click at the air arrives already cancelled.
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Block clicked = event.getClickedBlock();
        boolean clickedFurniture = clicked != null && furniture.identify(clicked).isPresent();
        Optional<Block> support = clickedFurniture ? Optional.of(clicked) : furniture.clickedThrough(event.getPlayer(), clicked);
        if (support.isPresent() && Tag.BEDS.isTagged(support.get().getType())) {
            return;
        }
        if (support.isPresent() && use(event.getPlayer(), support.get(), event.getHand())) {
            event.setCancelled(true);
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && clickedFurniture) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    /**
     * 5.3: a right click on one of a piece's hitboxes is a right click on the piece - on the block of
     * it nearest where the box was clicked.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUseHitbox(PlayerInteractEntityEvent event) {
        if (!furniture.isHitbox(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        furniture.hitboxPart(event.getRightClicked(), event.getPlayer())
                .ifPresent(support -> use(event.getPlayer(), support, event.getHand()));
    }

    /**
     * What a right click with {@code hand} on the piece standing on {@code support} does.
     *
     * @return whether the piece took the click, which nothing else then gets
     */
    private boolean use(Player player, Block support, @Nullable EquipmentSlot hand) {
        Optional<CustomItem> item = furniture.item(support);
        Placement.Furniture definition = item.map(CustomItem::placement).filter(Placement.Furniture.class::isInstance)
                .map(Placement.Furniture.class::cast).orElse(null);
        boolean interactive = definition != null && definition.interactive();
        // 5.4: other plugins hear of the click first, and may take it.
        if (item.isPresent() && hand == EquipmentSlot.HAND && !player.isSneaking()
                && !new com.arkcronist.content.api.event.FurnitureInteractEvent(support, player, item.get().id()).callEvent()) {
            return true;
        }
        // 3.4: a canteen held out to a trough or a well is filled there, and the piece does nothing else.
        if (item.isPresent() && canteens != null && hand == EquipmentSlot.HAND
                && canteens.test(player, item.get().id())) {
            return true;
        }
        boolean chest = support.getType() == Material.CHEST;
        boolean sneakingThrough = player.isSneaking()
                && !(chest && player.getInventory().getItemInMainHand().isEmpty()
                && player.getInventory().getItemInOffHand().isEmpty());
        if (interactive && !sneakingThrough) {
            // Both hands' clicks are taken, so the off hand does not place a block against the furniture.
            if (hand != EquipmentSlot.HAND) {
                return true;
            }
            if (definition.guillotine() != null) {
                useGuillotine(player, support, item.get(), definition);
            } else if (definition.seat() != null && protection.check(player, support, Interaction.SIT)) {
                float yaw = furniture.facing(support).orElse(player.getLocation().getYaw());
                // A tall piece - a throne - is sat on from its bottom row, whichever block was clicked;
                // a wide one - a bench - seats one on each of its blocks.
                Block seat = furniture.piece(support)
                        .map(piece -> support.getRelative(0, -piece.up(), 0)).orElse(support);
                // 3.2: the sitter's camera passes through the piece's own supports.
                Optional<FurnitureService.Piece> whole = furniture.piece(support);
                List<Block> piece = whole.map(found -> furniture.pieceParts(found.root())).orElse(List.of(support));
                Placement.Seat spec = definition.seat();
                if (spec.centred() && whole.isPresent()) {
                    // 3.2: one seat for the whole piece - a throne - in its middle, keyed on its anchor.
                    Location middle = whole.get().root().getLocation();
                    double[] moved = com.arkcronist.content.core.furniture.PieceAxes.offset(yaw, spec.across(), 0,
                            spec.forward());
                    Block anchor = whole.get().anchor();
                    Location sitting = new Location(anchor.getWorld(), middle.getX() + moved[0],
                            anchor.getY() + spec.height(), middle.getZ() + moved[2]);
                    seats.sit(player, anchor, sitting, yaw, piece);
                } else {
                    seats.sit(player, seat, spec, yaw, piece);
                }
            } else if (definition.storage() != null && protection.check(player, support, Interaction.CONTAINER)) {
                Optional<ChestPair> pair = furniture.pairAt(support);
                if (pair.isPresent() && definition.doubleChest() != null) {
                    World world = support.getWorld();
                    Block first = world.getBlockAt(pair.get().x(), pair.get().y(), pair.get().z());
                    Block second = world.getBlockAt(pair.get().px(), pair.get().py(), pair.get().pz());
                    Block other = first.equals(support) ? second : first;
                    if (protection.check(player, other, Interaction.CONTAINER)) {
                        storage.openPair(player, first, second, item.get().id(), doubleTitle(item.get(), definition));
                    }
                } else {
                    // 3.4: a piece that shows what it holds keeps it at its anchor, whichever block is clicked.
                    Block at = shown != null && definition.storage().show() != null
                            ? shown.storageBlock(support) : support;
                    storage.open(player, at, item.get().id(), definition.storage(),
                            title(item.get(), definition.storage()));
                }
            } else if (definition.board() != null && boards != null) {
                boards.open(player, support, item.get(), definition.board());
            } else if (definition.commands() != null && signs != null
                    && protection.check(player, support, Interaction.USE)) {
                furniture.piece(support).ifPresent(piece ->
                        signs.use(player, piece, item.get(), definition.commands()));
            } else if (definition.ambience() != null && ambience != null
                    && protection.check(player, support, Interaction.USE)) {
                furniture.piece(support).ifPresent(piece ->
                        ambience.click(player, piece, item.get(), definition.ambience()));
            }
            return true;
        }
        return false;
    }

    /** 3.4: pieces that show what they hold keep it at their anchor. */
    public void showContents(com.arkcronist.content.bukkit.furniture.ShownContents shown) {
        this.shown = shown;
    }

    /** 3.4: canteens filled at the furniture their {@code refill} names. */
    public void fillCanteens(java.util.function.BiPredicate<Player, String> canteens) {
        this.canteens = canteens;
    }

    /**
     * The lever pulls - if the player may work it - and anywhere else on the frame kneels them in
     * the stocks, if they may sit there.
     */
    private void useGuillotine(Player player, Block support, CustomItem item, Placement.Furniture definition) {
        Optional<FurnitureService.Piece> piece = furniture.piece(support);
        if (guillotines == null || piece.isEmpty()) {
            return;
        }
        Placement.Guillotine spec = definition.guillotine();
        if (GuillotineService.isLever(piece.get(), spec)) {
            if (protection.check(player, support, Interaction.USE)) {
                guillotines.pull(player, piece.get(), item, definition, spec);
            }
        } else if (protection.check(player, piece.get().anchor(), Interaction.SIT)) {
            guillotines.kneel(player, piece.get(), spec);
        }
    }

    /** A double chest's title: its own, or the single's. */
    private static Component doubleTitle(CustomItem item, Placement.Furniture definition) {
        String own = definition.doubleChest().title();
        return own != null ? MiniMessage.miniMessage().deserialize(own) : title(item, definition.storage());
    }

    private static Component title(CustomItem item, Placement.Storage spec) {
        if (spec.title() != null) {
            return MiniMessage.miniMessage().deserialize(spec.title());
        }
        return item.displayName() != null ? item.displayName() : Component.translatable("container.chest");
    }

    // ---------------------------------------------------------------- storage inventories

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStorageClick(InventoryClickEvent event) {
        storage.changed(event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStorageDrag(InventoryDragEvent event) {
        storage.changed(event.getView().getTopInventory());
    }

    /** Fires on logout and death too, so every way of leaving a storage inventory saves it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStorageClose(InventoryCloseEvent event) {
        storage.closed(event.getPlayer(), event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        storage.worldUnloaded(event.getWorld());
    }

    /**
     * A furniture chest's own inventory stays empty and shut: whatever opens it - a plugin, a
     * command - is refused, since the furniture's storage is what players use.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChestOpen(InventoryOpenEvent event) {
        if (isFurnitureChest(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    /** Hoppers, hopper minecarts and droppers neither fill nor empty a furniture chest. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent event) {
        if (isFurnitureChest(event.getSource()) || isFurnitureChest(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    private boolean isFurnitureChest(Inventory inventory) {
        if (inventory.getType() != InventoryType.CHEST || inventory.getHolder(false) instanceof StorageService.Holder) {
            return false;
        }
        Location at = inventory.getLocation();
        if (at == null || at.getWorld() == null) {
            return false;
        }
        Block block = at.getBlock();
        return block.getType() == Material.CHEST && furniture.identify(block).isPresent();
    }

    // ---------------------------------------------------------------- seats

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        seats.dismounted(event.getEntity(), event.getDismounted());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (ambience != null) {
            ambience.forget(event.getPlayer());
        }
        if (signs != null) {
            signs.forget(event.getPlayer());
        }
        seats.forget(event.getPlayer());
        Entity vehicle = event.getPlayer().getVehicle();
        if (vehicle != null && seats.isSeat(vehicle)) {
            vehicle.eject();
            vehicle.remove();
        }
    }

    /**
     * Someone sitting in a piece taller than its seat - a throne, the guillotine's stocks - has
     * their head in the barrier above the seat, and the server would have them suffocate in it. The
     * barrier is invisible, so there is nothing to see in there; only the damage is taken away.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void noSuffocationInASeat(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION) {
            return;
        }
        Entity vehicle = event.getEntity().getVehicle();
        if (vehicle != null && seats.isSeat(vehicle)) {
            event.setCancelled(true);
        }
    }

    // Not ignoreCancelled: a punch at the air arrives already cancelled.
    @EventHandler(priority = EventPriority.HIGH)
    public void onPunch(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        // Adventure and spectator players cannot break blocks, and this is a block break.
        GameMode mode = event.getPlayer().getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.CREATIVE) {
            return;
        }
        Block clicked = event.getClickedBlock();
        Optional<Block> target = clicked != null && furniture.identify(clicked).isPresent()
                ? Optional.of(clicked)
                : furniture.clickedThrough(event.getPlayer(), clicked);
        if (target.isEmpty() && clicked != null && clicked.getType() == Material.BARRIER && legacy != null
                && legacy.pieceAt(clicked).isPresent()) {
            // 3.1: a barrier ItemsAdder's furniture stands on - unbreakable in survival, like ours.
            target = Optional.of(clicked);
        }
        if (target.isEmpty() || FurnitureService.isVanillaBlock(target.get().getType())) {
            // Nothing here, or a chest or bed: mined like the vanilla block it is.
            return;
        }
        // Keeps vanilla from also breaking a clicked barrier in creative: there is one path, below.
        event.setCancelled(true);

        Block block = target.get();
        if (!protection.check(event.getPlayer(), block, Interaction.BREAK)) {
            return;
        }
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, event.getPlayer());
        breakEvent.setDropItems(false);
        if (breakEvent.callEvent()) {
            // onBreak has run as part of the event; what is left is the block.
            block.setType(Material.AIR);
        }
    }

    /**
     * 5.3: a punch at one of a piece's hitboxes breaks the piece, as a punch at its block does - the
     * block of it nearest the punch, through a {@link BlockBreakEvent} protection plugins decide.
     */
    // Not ignoreCancelled: a protection plugin guarding entities may cancel the hit; the break decides.
    @EventHandler(priority = EventPriority.HIGH)
    public void onPunchHitbox(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!furniture.isHitbox(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.CREATIVE) {
            return;
        }
        Optional<Block> target = furniture.hitboxPart(event.getEntity(), player);
        if (target.isEmpty() || furniture.identify(target.get()).isEmpty()
                || !protection.check(player, target.get(), Interaction.BREAK)) {
            return;
        }
        BlockBreakEvent breakEvent = new BlockBreakEvent(target.get(), player);
        breakEvent.setDropItems(false);
        if (breakEvent.callEvent()) {
            target.get().setType(Material.AIR);
        }
    }

    /** A chest or bed support drops the furniture's item, not its own. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void noVanillaDrop(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (FurnitureService.isVanillaBlock(block.getType()) && furniture.identify(block).isPresent()) {
            event.setDropItems(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        Optional<String> id = furniture.identify(block);
        if (id.isPresent()) {
            List<Block> parts = furniture.partsOf(block);
            parts.forEach(seats::release);
            // Each of its blocks' storage: a tall piece's may be kept at another of its blocks than this.
            parts.forEach(storage::broken);
            furniture.remove(block, id.get(), player.getGameMode() != GameMode.CREATIVE);
        } else if (legacy != null) {
            // 3.1: ItemsAdder's furniture: its model and its other barriers go with this block. In
            // creative the block can already be air here, so a piece standing in the barriers next
            // to it (one of several blocks) is looked for there too.
            Optional<LegacyFurniture.Piece> piece = legacy.pieceAt(block);
            for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
                    BlockFace.UP, BlockFace.DOWN}) {
                Block next = block.getRelative(face);
                if (piece.isPresent()) {
                    break;
                }
                if (next.getType() == Material.BARRIER && furniture.identify(next).isEmpty()) {
                    piece = legacy.pieceAt(next);
                }
            }
            piece.ifPresent(found -> legacy.remove(found, block, player.getGameMode() != GameMode.CREATIVE));
        }
    }

    /**
     * 3.1: a punch at what ItemsAdder's furniture is made of - its interaction box, or an armour
     * stand holding its item - takes the piece away, as a punch at its barrier does; protection
     * plugins decide through the {@link BlockBreakEvent} of the block it stands in. A hit on an
     * interaction box reaches plugins only as this damage event.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLegacyAttack(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (legacy == null || !(event.getDamager() instanceof Player player)) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.CREATIVE) {
            return;
        }
        Optional<LegacyFurniture.Piece> piece = legacy.pieceOf(event.getEntity());
        if (piece.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Block block = event.getEntity().getLocation().getBlock();
        if (!protection.check(player, block, Interaction.BREAK)) {
            return;
        }
        if (block.getType() == Material.BARRIER) {
            // Standing in its own barrier: broken as that block, and onBreak takes the piece with it.
            BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
            breakEvent.setDropItems(false);
            if (breakEvent.callEvent()) {
                block.setType(Material.AIR);
            }
        } else {
            legacy.remove(piece.get(), null, mode != GameMode.CREATIVE);
        }
    }

    // HIGHEST: this breaks blocks itself, so it runs as late as it can while the list may still be
    // changed, leaving other plugins every chance to cancel the explosion first.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(event.blockList(), event.getLocation(), event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.blockList(), event.getBlock().getLocation(), null);
    }

    /**
     * A bed used outside the overworld blows up - and vanilla removes both halves before the
     * explosion is even announced, so the furniture goes whether or not anyone cancels the blast.
     */
    // Not ignoreCancelled, for that reason.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedExplode(BlockExplodeEvent event) {
        BlockState exploded = event.getExplodedBlockState();
        if (exploded != null && Tag.BEDS.isTagged(exploded.getType())
                && !Tag.BEDS.isTagged(exploded.getBlock().getType())) {
            furniture.exploded(exploded.getBlock());
        }
    }

    /**
     * Barriers and light blocks shrug off explosions; chests and beds do not. Furniture standing on
     * one is taken out of the explosion's list and, if every protection plugin lets this explosion
     * destroy it, broken here - its item and its storage dropped, a bed's two halves together;
     * otherwise it is left standing.
     */
    private void explode(List<Block> destroyed, Location origin, @Nullable Entity source) {
        for (Iterator<Block> it = destroyed.iterator(); it.hasNext(); ) {
            Block block = it.next();
            if (!FurnitureService.isVanillaBlock(block.getType())) {
                continue;
            }
            Optional<String> id = furniture.identify(block);
            if (id.isEmpty()) {
                continue;
            }
            it.remove();
            if (protection.allowsExplosion(block, origin, source)) {
                List<Block> parts = furniture.partsOf(block);
                parts.forEach(seats::release);
                parts.forEach(storage::broken);
                furniture.remove(block, id.get(), true);
                block.setType(Material.AIR, false);
            }
        }
    }

    /** A wither ploughing through, or any other mob changing a block: furniture is not theirs to take. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (furniture.identify(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /**
     * Displays load with their chunk, a moment after its blocks. One whose support block is gone was
     * left behind while it was not loaded, and is removed now rather than floating forever.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        List<Entity> displays = new ArrayList<>();
        for (Entity entity : event.getEntities()) {
            if (furniture.isOrphan(entity)) {
                entity.remove();
            } else if (entity instanceof ItemDisplay) {
                displays.add(entity);
            }
        }
        if (displays.isEmpty()) {
            return;
        }
        // A tick later, so ModelEngine gets to restore its own saved models first.
        plugin.getServer().getScheduler().runTask(plugin, () -> displays.stream()
                .filter(Entity::isValid)
                .forEach(furniture::restore));
    }

    /** 5.3: hitboxes are never saved: a piece unloading takes its own with it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            furniture.unloading(entity);
        }
    }
}
