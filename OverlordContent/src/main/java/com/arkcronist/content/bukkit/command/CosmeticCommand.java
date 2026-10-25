package com.arkcronist.content.bukkit.command;

import com.arkcronist.content.bukkit.cosmetic.CosmeticService;
import com.arkcronist.content.core.cosmetic.CosmeticDefinition;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /cosmetic} - what you wear - and {@code /cosmetic off} to take it off (3.0). */
public final class CosmeticCommand {

    public static final String PERMISSION = "arkcontent.cosmetic";

    private final CosmeticService cosmetics;

    public CosmeticCommand(CosmeticService cosmetics) {
        this.cosmetics = cosmetics;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("cosmetic")
                .requires(source -> source.getSender().hasPermission(PERMISSION) && source.getExecutor() instanceof Player)
                .executes(this::list)
                .then(Commands.literal("off").executes(this::off))
                .build();
    }

    private int list(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getExecutor();
        List<String> worn = cosmetics.describe(player);
        player.sendMessage(worn.isEmpty()
                ? Component.text("You wear nothing. Right click with a cosmetic in hand to put it on.", NamedTextColor.GRAY)
                : Component.text("You wear " + String.join(", ", worn) + ". /cosmetic off to take it off.",
                NamedTextColor.GRAY));
        return Command.SINGLE_SUCCESS;
    }

    private int off(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getExecutor();
        cosmetics.takeOff(player, CosmeticDefinition.Slot.BACK);
        return Command.SINGLE_SUCCESS;
    }
}
