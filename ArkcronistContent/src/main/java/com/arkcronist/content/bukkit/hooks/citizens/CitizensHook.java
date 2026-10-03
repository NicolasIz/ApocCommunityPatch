package com.arkcronist.content.bukkit.hooks.citizens;

import com.arkcronist.content.bukkit.hooks.NpcBridge;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.SitTrait;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Citizens NPCs holding and wearing custom items, and sitting on custom furniture:
 *
 * <pre>
 * /npc select
 * /arkcontent npc equip hand demo:ruby_sword
 * /arkcontent npc equip helmet demo:ruby_crown
 * /arkcontent npc sit             (looking at a chair)
 * </pre>
 *
 * <p>The NPC gets the same stack {@code /customgive} makes, so it is drawn with its own model;
 * Citizens saves it with the NPC. {@code /npc equip} with the item in hand works too - this only
 * spares typing, and lets a console or a script do it.</p>
 */
public final class CitizensHook implements NpcBridge {

    /** Citizens' classes are there but it is not: it failed to enable, or was disabled. */
    private static final String NOT_RUNNING = "Citizens is installed but not running - see the server log";

    @Override
    public List<String> slots() {
        return Arrays.stream(Equipment.EquipmentSlot.values()).map(slot -> slot.name().toLowerCase(Locale.ROOT)).toList();
    }

    @Override
    public @Nullable String equip(CommandSender sender, String slot, ItemStack item) {
        if (!CitizensAPI.hasImplementation()) {
            return NOT_RUNNING;
        }
        NPC npc = selected(sender);
        if (npc == null) {
            return "Select an NPC first: /npc select";
        }
        Equipment.EquipmentSlot target;
        try {
            target = Equipment.EquipmentSlot.valueOf(slot.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return "No slot '" + slot + "'; one of " + String.join(", ", slots());
        }
        npc.getOrAddTrait(Equipment.class).set(target, item);
        return null;
    }

    @Override
    public @Nullable String sit(CommandSender sender, Location seat) {
        if (!CitizensAPI.hasImplementation()) {
            return NOT_RUNNING;
        }
        NPC npc = selected(sender);
        if (npc == null) {
            return "Select an NPC first: /npc select";
        }
        npc.getOrAddTrait(SitTrait.class).setSitting(seat);
        return null;
    }

    private static @Nullable NPC selected(CommandSender sender) {
        return CitizensAPI.hasImplementation() ? CitizensAPI.getDefaultNPCSelector().getSelected(sender) : null;
    }
}
