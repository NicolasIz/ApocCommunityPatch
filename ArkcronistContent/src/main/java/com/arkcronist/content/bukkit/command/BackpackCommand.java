package com.arkcronist.content.bukkit.command;

import com.arkcronist.content.bukkit.item.BackpackService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** {@code /backpack} (also {@code /mochila}) - opens the backpack in hand, or the one on the back (3.4). */
public final class BackpackCommand {

    public static final String PERMISSION = "arkcontent.backpack";

    private final BackpackService backpacks;

    public BackpackCommand(BackpackService backpacks) {
        this.backpacks = backpacks;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("backpack")
                .requires(source -> source.getSender().hasPermission(PERMISSION) && source.getExecutor() instanceof Player)
                .executes(this::open)
                .build();
    }

    private int open(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getExecutor();
        if (!backpacks.openAny(player)) {
            player.sendMessage(Component.text("You have no backpack in your hand or on your back.", NamedTextColor.GRAY));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }
}
