package com.arkcronist.content.bukkit.item;

import com.arkcronist.content.core.definition.BackpackDefinition;
import com.arkcronist.content.core.storage.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * 3.4: backpacks - items with a {@code backpack:} section, which hold others.
 *
 * <p>What one holds is kept in the database ({@code item_storage}) under an id the item carries in
 * its tags, given the first time it is opened or put on. So the contents go where the item goes: in
 * a chest, on someone else's back, dropped and picked up. A backpack open in two windows at once -
 * the same item, or a copy of it - is one inventory, shared, so nothing is doubled; it is written
 * back when the last of them closes, and as the plugin stops.</p>
 *
 * <p>Opened with a right click while sneaking, with it in hand, or with {@code /backpack} for the
 * one in hand or on the back. A backpack is never let into another: a click, a shift-click, a
 * number key or a drag that would put one there is refused.</p>
 */
public final class BackpackService implements Listener {

    private final Plugin plugin;
    private final ItemFactory items;
    private final DatabaseManager database;
    private final NamespacedKey storageKey;
    /** The stack a player wears on their back, if any. Main thread. */
    private Function<Player, Optional<ItemStack>> worn = player -> Optional.empty();
    /** Backpacks open now, by their storage id. Main thread. */
    private final Map<String, Holder> open = new HashMap<>();

    public BackpackService(Plugin plugin, ItemFactory items, DatabaseManager database) {
        this.plugin = plugin;
        this.items = items;
        this.database = database;
        this.storageKey = new NamespacedKey(plugin, "storage");
    }

    /** Where the backpack worn on the back is found. */
    public void wornOn(Function<Player, Optional<ItemStack>> worn) {
        this.worn = worn;
    }

    /** The window of one open backpack; its holder, so a click can be told to be in one. */
    private static final class Holder implements InventoryHolder {
        final String storageId;
        final String itemId;
        Inventory inventory;

        Holder(String storageId, String itemId) {
            this.storageId = storageId;
            this.itemId = itemId;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private @Nullable BackpackDefinition backpack(@Nullable ItemStack stack) {
        return items.identify(stack).map(item -> item.definition().backpack()).orElse(null);
    }

    /** Whether a stack is a backpack, which never goes into another. */
    public boolean isBackpack(@Nullable ItemStack stack) {
        return backpack(stack) != null;
    }

    /** The id a backpack's contents are kept under; null for a stack that has none yet. */
    private @Nullable String storageId(ItemStack stack) {
        return stack.getPersistentDataContainer().get(storageKey, PersistentDataType.STRING);
    }

    /**
     * Gives a backpack its id, if it has none - on the live stack, so it stays with it. Before one is
     * put on, so the one worn has it too.
     */
    public void ensureId(@Nullable ItemStack stack) {
        if (stack == null || backpack(stack) == null || storageId(stack) != null) {
            return;
        }
        String id = UUID.randomUUID().toString();
        stack.editPersistentDataContainer(tags -> tags.set(storageKey, PersistentDataType.STRING, id));
    }

    /**
     * A backpack taken off the back: one put on before 3.4 has no id, and was opened under its
     * wearer's - which it keeps from now on.
     */
    public void takenOff(UUID wearer, ItemStack stack) {
        if (backpack(stack) != null && storageId(stack) == null) {
            stack.editPersistentDataContainer(tags -> tags.set(storageKey, PersistentDataType.STRING, wornId(wearer)));
        }
    }

    private static String wornId(UUID wearer) {
        return "worn-" + wearer;
    }

    // ---------------------------------------------------------------- opening

    /** {@code /backpack}: the one in hand, or else the one on the back. Main thread. */
    public boolean openAny(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (backpack(hand) != null) {
            ensureId(hand);
            open(player, hand);
            return true;
        }
        Optional<ItemStack> back = worn.apply(player).filter(stack -> backpack(stack) != null);
        if (back.isPresent()) {
            ItemStack stack = back.get();
            String id = storageId(stack);
            open(player, stack, id != null ? id : wornId(player.getUniqueId()));
            return true;
        }
        return false;
    }

    private void open(Player player, ItemStack stack) {
        open(player, stack, storageId(stack));
    }

    private void open(Player player, ItemStack stack, String storageId) {
        CustomItem item = items.identify(stack).orElse(null);
        BackpackDefinition definition = item == null ? null : item.definition().backpack();
        if (item == null || definition == null) {
            return;
        }
        Holder already = open.get(storageId);
        if (already != null) {
            player.openInventory(already.inventory);
            return;
        }
        UUID viewer = player.getUniqueId();
        Component title = definition.title() != null ? MiniMessage.miniMessage().deserialize(definition.title())
                : stack.effectiveName();
        database.loadItemStorage(storageId).whenCompleteAsync((saved, error) -> {
            Player online = plugin.getServer().getPlayer(viewer);
            if (online == null) {
                return;
            }
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not read what backpack " + storageId + " holds", error);
                online.sendMessage(Component.text("That backpack could not be opened.", NamedTextColor.RED));
                return;
            }
            Holder holder = open.get(storageId);
            if (holder == null) {
                holder = new Holder(storageId, item.id());
                holder.inventory = plugin.getServer().createInventory(holder, definition.slots(), title);
                if (saved.isPresent()) {
                    ItemStack[] contents = ItemStack.deserializeItemsFromBytes(saved.get());
                    for (int i = 0; i < contents.length && i < definition.slots(); i++) {
                        holder.inventory.setItem(i, contents[i] == null || contents[i].isEmpty() ? null : contents[i]);
                    }
                }
                open.put(storageId, holder);
            }
            online.openInventory(holder.inventory);
            online.playSound(online.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1.1f);
        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
    }

    /** Sneaking, a right click with a backpack in hand opens it. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getPlayer().isSneaking()
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (backpack(hand) == null) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        ensureId(hand);
        open(event.getPlayer(), hand);
    }

    // ---------------------------------------------------------------- keeping backpacks out

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        boolean intoTop = event.getClickedInventory() == top;
        InventoryAction action = event.getAction();
        ItemStack moving = null;
        if (intoTop && (action.name().startsWith("PLACE") || action == InventoryAction.SWAP_WITH_CURSOR)) {
            moving = event.getCursor();
        } else if (!intoTop && action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            moving = event.getCurrentItem();
        } else if (intoTop && (event.getClick() == ClickType.NUMBER_KEY || action == InventoryAction.HOTBAR_SWAP)) {
            moving = event.getHotbarButton() >= 0
                    ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;
        } else if (intoTop && event.getClick() == ClickType.SWAP_OFFHAND) {
            moving = event.getWhoClicked().getInventory().getItemInOffHand();
        }
        if (moving != null && isBackpack(moving)) {
            event.setCancelled(true);
            event.getWhoClicked().sendActionBar(Component.text("A backpack does not go inside another.",
                    NamedTextColor.GRAY));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder) || !isBackpack(event.getOldCursor())) {
            return;
        }
        int size = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < size)) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- saving

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Holder holder)) {
            return;
        }
        // The one closing is still among the viewers here.
        boolean last = event.getInventory().getViewers().stream().allMatch(viewer -> viewer == event.getPlayer());
        save(holder);
        if (last) {
            open.remove(holder.storageId);
        }
    }

    private void save(Holder holder) {
        ItemStack[] contents = holder.inventory.getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? ItemStack.empty() : contents[i].clone();
        }
        database.saveItemStorage(holder.storageId, holder.itemId, copy.length, ItemStack.serializeItemsAsBytes(copy))
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "Could not save what backpack " + holder.storageId + " holds",
                            error);
                    return null;
                });
    }

    /** As the plugin stops: every open backpack written, queued ahead of the database closing. */
    public void shutdown() {
        for (Holder holder : java.util.List.copyOf(open.values())) {
            save(holder);
            for (HumanEntity viewer : java.util.List.copyOf(holder.inventory.getViewers())) {
                viewer.closeInventory();
            }
        }
        open.clear();
    }
}
