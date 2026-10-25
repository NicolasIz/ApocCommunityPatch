package com.arkcronist.content.bukkit.listener;

import com.arkcronist.content.bukkit.block.CustomBlock;
import com.arkcronist.content.bukkit.block.CustomBlockService;
import com.arkcronist.content.bukkit.block.ShapedBlockRegistry;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Optional;

/**
 * 5.3: picking a custom block - the middle click - picks its item, not the note block or the
 * copper block it is drawn through. A stack of it already carried is moved into the hotbar as
 * vanilla moves a picked block; in creative, one is made when none is carried.
 *
 * <p>Paper's pick event is newer than some 1.21.4 builds, so this listener is registered on its own
 * and simply left out where the event does not exist. On 1.21.4 the event does not say what was
 * picked, so the block is the one the player looks at, as the client picked it; from 1.21.5 the
 * event is a block's or an entity's, and an entity's is left alone.</p>
 */
public final class PickBlockListener implements Listener {

    /** The player's storage slots: the hotbar, then the three rows above it. */
    private static final int STORAGE = 36;
    /** Farther than any player reaches, creative's 5 blocks included; the client picked what it hit first. */
    private static final int REACH = 6;

    private final CustomBlockService cubes;
    private final ShapedBlockRegistry shaped;
    private final ItemFactory items;

    public PickBlockListener(CustomBlockService cubes, ShapedBlockRegistry shaped, ItemFactory items) {
        this.cubes = cubes;
        this.shaped = shaped;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPick(PlayerPickItemEvent event) {
        if (event.getClass().getSimpleName().contains("Entity")) {
            return;
        }
        Player player = event.getPlayer();
        Block looked = player.getTargetBlockExact(REACH);
        Optional<CustomItem> custom = looked == null ? Optional.empty() : itemAt(looked);
        if (custom.isEmpty()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < STORAGE; slot++) {
            if (items.identify(inventory.getItem(slot)).map(item -> item.id().equals(custom.get().id())).orElse(false)) {
                event.setSourceSlot(slot);
                return;
            }
        }
        event.setCancelled(true);
        if (player.getGameMode() != GameMode.CREATIVE) {
            return;
        }
        int target = event.getTargetSlot();
        ItemStack before = inventory.getItem(target);
        inventory.setItem(target, items.create(custom.get(), 1));
        if (before != null && !before.isEmpty()) {
            inventory.addItem(before);
        }
        inventory.setHeldItemSlot(target);
    }

    private Optional<CustomItem> itemAt(Block block) {
        Optional<CustomItem> drawn = shaped.byBase(block.getType());
        return drawn.isPresent() ? drawn : cubes.identify(block).map(CustomBlock::item);
    }
}
