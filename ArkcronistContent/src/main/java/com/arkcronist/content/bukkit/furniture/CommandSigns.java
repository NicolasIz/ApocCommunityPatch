package com.arkcronist.content.bukkit.furniture;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.core.definition.CommandTemplate;
import com.arkcronist.content.core.definition.Placement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Furniture with {@code interactable: command}: a sign for the bank, the tavern, the smithy, that
 * runs the commands written for it when it is right-clicked - from the console, or as the player.
 *
 * <p>The placeholders are filled in from the server, never from anything a player typed:
 * {@code %player%}, {@code %uuid%}, {@code %world%}, {@code %dimension%} and the sign's
 * {@code %x% %y% %z%}. A name is checked before it goes into a console command - see
 * {@link CommandTemplate} - so nothing in one splits the command or adds a selector, and a command
 * is one line: there is no way to run a second one through the first. Every command run is logged,
 * with who clicked and where.</p>
 *
 * <p>Main thread: commands may only be dispatched there.</p>
 */
public final class CommandSigns {

    private final Plugin plugin;
    /** Until which tick each player may not use each sign again. */
    private final Map<UUID, Map<UUID, Integer>> cooldowns = new HashMap<>();

    public CommandSigns(Plugin plugin) {
        this.plugin = plugin;
    }

    /** A right click on the sign {@code piece}. */
    public void use(Player player, FurnitureService.Piece piece, CustomItem item, Placement.Commands commands) {
        if (commands.permission() != null && !player.hasPermission(commands.permission())) {
            player.sendActionBar(Component.text("You may not use this", NamedTextColor.RED));
            return;
        }
        int now = plugin.getServer().getCurrentTick();
        Map<UUID, Integer> mine = cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        Integer until = mine.get(piece.root().getUniqueId());
        if (until != null && until > now && player.getGameMode() != GameMode.CREATIVE) {
            player.sendActionBar(Component.text("Not yet: " + (int) Math.ceil((until - now) / 20.0) + "s",
                    NamedTextColor.GRAY));
            return;
        }
        if (commands.cooldownTicks() > 0) {
            mine.put(piece.root().getUniqueId(), now + commands.cooldownTicks());
        }
        Block anchor = piece.anchor();
        Map<String, String> values = values(player, anchor);
        if (!run(item.id(), commands.run(), commands.asPlayer(), player, values, player.getName())) {
            return;
        }
        Location centre = anchor.getLocation().add(0.5, 0.5, 0.5);
        if (commands.sound() != null) {
            centre.getWorld().playSound(centre, commands.sound(), 0.8f, 1f);
        }
        if (commands.message() != null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(commands.message().replace("%player%",
                    player.getName())));
        }
    }

    /** A player left: their cooldowns go with them. */
    public void forget(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    /** The placeholders for {@code player} at the piece standing on {@code anchor}. */
    static Map<String, String> values(Player player, Block anchor) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        values.put("world", anchor.getWorld().getName());
        values.put("dimension", anchor.getWorld().getKey().asString());
        values.put("x", Integer.toString(anchor.getX()));
        values.put("y", Integer.toString(anchor.getY()));
        values.put("z", Integer.toString(anchor.getZ()));
        return values;
    }

    /**
     * Fills in and runs {@code commands}, from the console or as {@code player}, logging each.
     * Nothing runs if a value cannot go into a command.
     *
     * @param who the name the log gives for whoever set this off
     * @return whether they ran
     */
    boolean run(String furniture, List<String> commands, boolean asPlayer, Player player, Map<String, String> values,
                String who) {
        List<String> filled;
        try {
            filled = commands.stream().map(command -> CommandTemplate.fill(command, values)).toList();
        } catch (IllegalArgumentException unsafe) {
            plugin.getLogger().warning(furniture + ": not run for " + who + " - " + unsafe.getMessage());
            player.sendActionBar(Component.text("This cannot be used with your name", NamedTextColor.RED));
            return false;
        }
        for (String command : filled) {
            plugin.getLogger().info(furniture + " at " + values.get("world") + " " + values.get("x") + ","
                    + values.get("y") + "," + values.get("z") + " (" + who + "): "
                    + (asPlayer ? "as " + player.getName() + ": /" : "console: /") + command);
            try {
                if (asPlayer) {
                    player.performCommand(command);
                } else {
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
                }
            } catch (RuntimeException failed) {
                plugin.getLogger().log(Level.WARNING, furniture + ": the command '" + command + "' failed", failed);
            }
        }
        return true;
    }
}
