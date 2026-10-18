package com.arkcronist.content.bukkit.item;

import com.arkcronist.content.core.definition.DrinkDefinition;
import com.arkcronist.content.core.definition.Placement;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.logging.Logger;

/**
 * 3.4: canteens - items with a {@code drink:} section, drunk from in sips and filled again.
 *
 * <p>A canteen is drunk as a potion is: the game's own drinking, from its {@code consumable}
 * component, so it looks and sounds the same to everyone and Geyser can show it to Bedrock players.
 * When the drink is done the stack is not used up: it is put back with one more point of damage -
 * its damage bar is the sips it has left - and the sip's HUD values and potion effects are given.
 * An empty one is not drunk at all until it is filled: by clicking water, a cauldron with water
 * (which loses a level, as a bottle takes one), or a piece of furniture its {@code refill} names -
 * a trough, a well.</p>
 */
public final class DrinkService implements Listener {

    private final ItemFactory items;
    private final Logger logger;
    /** Adds to HUD values this plugin keeps; returns the HUD ids it does not know. */
    private final BiFunction<Player, Map<String, Double>, List<String>> hud;
    private final Set<String> warned = new HashSet<>();

    public DrinkService(ItemFactory items, Logger logger, BiFunction<Player, Map<String, Double>, List<String>> hud) {
        this.items = items;
        this.logger = logger;
        this.hud = hud;
    }

    private Optional<Canteen> canteen(@Nullable ItemStack stack) {
        return items.identify(stack).filter(item -> item.definition().drink() != null)
                .map(item -> new Canteen(item, item.definition().drink()));
    }

    private record Canteen(CustomItem item, DrinkDefinition drink) {
    }

    /** Sips left in a canteen stack: its max damage less its damage. */
    static int sipsLeft(ItemStack stack, DrinkDefinition drink) {
        Integer max = stack.getData(DataComponentTypes.MAX_DAMAGE);
        Integer damage = stack.getData(DataComponentTypes.DAMAGE);
        int full = max != null ? max : drink.sips();
        return Math.max(0, full - (damage != null ? damage : 0));
    }

    // ---------------------------------------------------------------- drinking

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemStack stack = event.getItem();
        Canteen canteen = canteen(stack).orElse(null);
        if (canteen == null) {
            return;
        }
        Player player = event.getPlayer();
        DrinkDefinition drink = canteen.drink();
        int left = sipsLeft(stack, drink);
        if (left <= 0) {
            event.setCancelled(true);
            empty(player, drink);
            return;
        }
        // Not used up: back in the hand with one sip fewer.
        ItemStack after = stack.clone();
        after.setAmount(1);
        Integer max = after.getData(DataComponentTypes.MAX_DAMAGE);
        if (max == null) {
            after.setData(DataComponentTypes.MAX_DAMAGE, drink.sips());
        }
        Integer damage = after.getData(DataComponentTypes.DAMAGE);
        after.setData(DataComponentTypes.DAMAGE, (damage == null ? 0 : damage) + 1);
        event.setReplacement(after);
        List<String> unknown = hud.apply(player, drink.hud());
        for (String id : unknown) {
            warn(canteen.item().id() + ": drink: there is no HUD '" + id + "' kept by this plugin; nothing is added to it");
        }
        for (Placement.Effect effect : drink.potions()) {
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.fromString(effect.type()));
            if (type == null) {
                warn(canteen.item().id() + ": drink: '" + effect.type() + "' is not a potion effect on this server");
                continue;
            }
            player.addPotionEffect(new PotionEffect(type, effect.durationTicks(), effect.amplifier()));
        }
        player.sendActionBar(Component.text((left - 1) + "/" + Math.max(left, max == null ? drink.sips() : max),
                NamedTextColor.AQUA));
    }

    // ---------------------------------------------------------------- filling

    /**
     * A click with a canteen: at water or a cauldron with water, it is filled; empty anywhere else,
     * it is not drunk from. Before the furniture's own clicks, which take the click at a trough.
     */
    @SuppressWarnings("deprecation") // Material#isInteractable: whether a click on the block does something itself
    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItem(hand);
        Canteen canteen = canteen(stack).orElse(null);
        if (canteen == null) {
            return;
        }
        DrinkDefinition drink = canteen.drink();
        Block cauldron = event.getClickedBlock();
        // A full one is drunk from at a cauldron as anywhere else.
        if (drink.fillsAtCauldron() && cauldron != null && cauldron.getType() == Material.WATER_CAULDRON
                && sipsLeft(stack, drink) < full(stack, drink)) {
            event.setCancelled(true);
            // As a bottle does: one level of the cauldron's water.
            if (cauldron.getBlockData() instanceof Levelled level) {
                if (level.getLevel() <= level.getMinimumLevel()) {
                    cauldron.setType(Material.CAULDRON);
                } else {
                    level.setLevel(level.getLevel() - 1);
                    cauldron.setBlockData(level);
                }
            }
            fill(player, hand, stack, drink);
            return;
        }
        if (drink.fillsAtWater() && sipsLeft(stack, drink) < full(stack, drink)) {
            RayTraceResult hit = player.rayTraceBlocks(5, FluidCollisionMode.SOURCE_ONLY);
            Block water = hit == null ? null : hit.getHitBlock();
            if (water != null && water.getType() == Material.WATER) {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                fill(player, hand, stack, drink);
                return;
            }
        }
        if (sipsLeft(stack, drink) <= 0) {
            // Empty: not drunk from. A block clicked still does what it does.
            event.setUseItemInHand(Event.Result.DENY);
            Block clicked = event.getClickedBlock();
            if (clicked == null || !clicked.getType().isInteractable()) {
                empty(player, drink);
            }
        }
    }

    /**
     * A click on a piece of furniture with a canteen in hand: filled, if the piece is one it fills
     * at. Asked by the furniture's click first.
     *
     * @return whether the click was this - the piece does nothing else with it
     */
    public boolean fillAt(Player player, String furnitureId) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        Canteen canteen = canteen(stack).orElse(null);
        if (canteen == null || !canteen.drink().fillsAt(furnitureId)) {
            return false;
        }
        if (sipsLeft(stack, canteen.drink()) < full(stack, canteen.drink())) {
            fill(player, EquipmentSlot.HAND, stack, canteen.drink());
        } else {
            player.sendActionBar(Component.text(full(stack, canteen.drink()) + "/" + full(stack, canteen.drink()),
                    NamedTextColor.AQUA));
        }
        return true;
    }

    private static int full(ItemStack stack, DrinkDefinition drink) {
        Integer max = stack.getData(DataComponentTypes.MAX_DAMAGE);
        return max != null ? max : drink.sips();
    }

    private void fill(Player player, EquipmentSlot hand, ItemStack stack, DrinkDefinition drink) {
        ItemStack filled = stack.clone();
        if (filled.getData(DataComponentTypes.MAX_DAMAGE) == null) {
            filled.setData(DataComponentTypes.MAX_DAMAGE, drink.sips());
        }
        filled.setData(DataComponentTypes.DAMAGE, 0);
        if (filled.getData(DataComponentTypes.CONSUMABLE) == null) {
            filled.setData(DataComponentTypes.CONSUMABLE, ItemFactory.drinking(drink));
        }
        player.getInventory().setItem(hand, filled);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 1f, 1f);
        player.sendActionBar(drink.filled() != null ? MiniMessage.miniMessage().deserialize(drink.filled())
                : Component.text("Filled: " + full(filled, drink) + "/" + full(filled, drink), NamedTextColor.AQUA));
    }

    private static void empty(Player player, DrinkDefinition drink) {
        player.sendActionBar(drink.empty() != null ? MiniMessage.miniMessage().deserialize(drink.empty())
                : Component.text("Empty - fill it at water", NamedTextColor.GRAY));
    }

    private void warn(String message) {
        if (warned.add(message)) {
            logger.warning(message);
        }
    }
}
