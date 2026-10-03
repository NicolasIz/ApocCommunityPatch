package com.arkcronist.content.bukkit.hooks;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * NPCs - Citizens - as {@code /arkcontent npc} uses them, in Bukkit's own types, so the command
 * never names a Citizens class and loads whether or not Citizens is installed.
 */
public interface NpcBridge {

    /** The equipment slots an NPC has, lower case: {@code hand}, {@code helmet}... */
    List<String> slots();

    /**
     * Puts {@code item} in a slot of the NPC {@code sender} has selected ({@code /npc select}).
     *
     * @return what went wrong, or null once it is done
     */
    @Nullable String equip(CommandSender sender, String slot, ItemStack item);

    /**
     * Sits the NPC {@code sender} has selected at {@code seat}, facing its yaw.
     *
     * @return what went wrong, or null once it is done
     */
    @Nullable String sit(CommandSender sender, Location seat);
}
