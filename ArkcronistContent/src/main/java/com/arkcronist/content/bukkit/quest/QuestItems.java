package com.arkcronist.content.bukkit.quest;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.quest.QuestDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The items of quest notices: what a notice asks to be handed in, counted in and taken from a
 * player's inventory, and what it pays. An item is named either by a custom item's id
 * ({@code demo:ruby}) or by a vanilla material ({@code COBBLESTONE}, {@code minecraft:cobblestone});
 * a custom item never counts as the vanilla material it is made of. Main thread.
 */
final class QuestItems {

    private final ItemRegistry registry;
    private final ItemFactory factory;

    QuestItems(ItemRegistry registry, ItemFactory factory) {
        this.registry = registry;
        this.factory = factory;
    }

    /** A custom item by id, if {@code name} is one that is loaded. */
    Optional<CustomItem> custom(String name) {
        return name.contains(":") && !name.toLowerCase(Locale.ROOT).startsWith("minecraft:")
                ? registry.find(name) : Optional.empty();
    }

    /** A vanilla material, if {@code name} is one. */
    static @Nullable Material vanilla(String name) {
        Material material = Material.matchMaterial(name.trim());
        return material != null && material.isItem() && !material.isAir() ? material : null;
    }

    /** Whether {@code name} is an item anything can be counted or given as. */
    boolean known(String name) {
        return custom(name).isPresent() || vanilla(name) != null;
    }

    /** How many of the item the player carries. */
    int count(Player player, String name) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (matches(stack, name)) {
                count += stack.getAmount();
            }
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (matches(offHand, name)) {
            count += offHand.getAmount();
        }
        return count;
    }

    /**
     * Takes {@code amount} of the item from the player's inventory.
     *
     * @return the stacks taken, to give back should the hand-in fail
     */
    List<ItemStack> take(Player player, String name, int amount) {
        List<ItemStack> taken = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        int left = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!matches(stack, name)) {
                continue;
            }
            int used = Math.min(left, stack.getAmount());
            ItemStack copy = stack.clone();
            copy.setAmount(used);
            taken.add(copy);
            left -= used;
            stack.setAmount(stack.getAmount() - used);
            inventory.setItem(slot, stack.getAmount() <= 0 ? null : stack);
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (left > 0 && matches(offHand, name)) {
            int used = Math.min(left, offHand.getAmount());
            ItemStack copy = offHand.clone();
            copy.setAmount(used);
            taken.add(copy);
            offHand.setAmount(offHand.getAmount() - used);
            inventory.setItemInOffHand(offHand.getAmount() <= 0 ? null : offHand);
        }
        return taken;
    }

    private boolean matches(@Nullable ItemStack stack, String name) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Optional<CustomItem> custom = custom(name);
        Optional<CustomItem> held = factory.identify(stack);
        if (custom.isPresent()) {
            return held.isPresent() && held.get().id().equals(custom.get().id());
        }
        Material material = vanilla(name);
        return material != null && stack.getType() == material && held.isEmpty();
    }

    /** The stacks a reward of {@code amount} of the item is, each no larger than the item stacks to. */
    List<ItemStack> stacks(String name, int amount) {
        List<ItemStack> stacks = new ArrayList<>();
        Optional<CustomItem> custom = custom(name);
        Material material = custom.isPresent() ? custom.get().material() : vanilla(name);
        if (material == null) {
            return stacks;
        }
        int max = Math.max(1, material.getMaxStackSize());
        for (int left = amount; left > 0; left -= max) {
            int size = Math.min(max, left);
            stacks.add(custom.isPresent() ? factory.create(custom.get(), size) : ItemStack.of(material, size));
        }
        return stacks;
    }

    /** An exact stack a notice pays, read back from its bytes; null when they cannot be read. */
    static @Nullable ItemStack stack(QuestDefinition.Reward.Stack reward) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(reward.base64()));
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** A stack as a notice keeps it. */
    static QuestDefinition.Reward.Stack reward(ItemStack stack) {
        return new QuestDefinition.Reward.Stack(Base64.getEncoder().encodeToString(stack.serializeAsBytes()),
                stack.getAmount() + " x " + plain(stack));
    }

    /** Gives stacks to the player; what does not fit is dropped at their feet. */
    static void give(Player player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            for (ItemStack left : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
    }

    /** What players call an item: a custom item's name, a material's in words. */
    String name(String item) {
        Optional<CustomItem> custom = custom(item);
        if (custom.isPresent()) {
            Component name = custom.get().displayName();
            return name != null ? PlainTextComponentSerializer.plainText().serialize(name) : custom.get().id();
        }
        Material material = vanilla(item);
        return material != null ? words(material.getKey().getKey()) : item;
    }

    /** A stack's name, plain. */
    static String plain(ItemStack stack) {
        return PlainTextComponentSerializer.plainText().serialize(stack.effectiveName());
    }

    /** {@code coal_ore} as {@code Coal ore}. */
    static String words(String key) {
        String spaced = key.replace('_', ' ').toLowerCase(Locale.ROOT);
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
