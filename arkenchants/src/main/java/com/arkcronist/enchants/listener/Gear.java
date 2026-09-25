package com.arkcronist.enchants.listener;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/** The items an entity carries that can hold enchants. */
final class Gear {

    private Gear() {
    }

    static List<ItemStack> all(LivingEntity e) {
        List<ItemStack> out = new ArrayList<>(6);
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return out;
        }
        out.add(eq.getItemInMainHand());
        for (ItemStack a : eq.getArmorContents()) {
            out.add(a);
        }
        out.add(eq.getItemInOffHand());
        return out;
    }

    static List<ItemStack> hand(LivingEntity e) {
        EntityEquipment eq = e.getEquipment();
        return eq == null ? List.of() : List.of(eq.getItemInMainHand());
    }

    static List<ItemStack> handAndArmor(LivingEntity e) {
        List<ItemStack> out = new ArrayList<>(5);
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return out;
        }
        out.add(eq.getItemInMainHand());
        for (ItemStack a : eq.getArmorContents()) {
            out.add(a);
        }
        return out;
    }
}
