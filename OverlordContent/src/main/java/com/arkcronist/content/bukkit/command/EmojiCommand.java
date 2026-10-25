package com.arkcronist.content.bukkit.command;

import com.arkcronist.content.bukkit.emoji.EmojiRegistry;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /emojis}: every chat emoji the sender may use, drawn, with what to type for it. Clicking
 * one puts its keyword into the chat box.
 */
public final class EmojiCommand {

    private final EmojiRegistry emojis;

    public EmojiCommand(EmojiRegistry emojis) {
        this.emojis = emojis;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("emojis")
                .requires(source -> source.getSender().hasPermission("arkcontent.emojis"))
                .executes(this::list)
                .build();
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        List<EmojiRegistry.Emoji> usable = emojis.all().stream().filter(emoji -> emoji.allowed(sender)).toList();
        if (usable.isEmpty()) {
            sender.sendMessage(Component.text("No emojis are available.", NamedTextColor.GRAY));
            return 0;
        }
        TextComponent.Builder line = Component.text().append(Component.text("Emojis: ", NamedTextColor.GOLD));
        for (EmojiRegistry.Emoji emoji : usable) {
            String keyword = ":" + emoji.name() + ":";
            line.append(emoji.component()
                            .append(Component.text(" " + keyword + "  ", NamedTextColor.GRAY))
                            .clickEvent(ClickEvent.suggestCommand(keyword))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to type " + keyword))));
        }
        sender.sendMessage(line.build());
        return Command.SINGLE_SUCCESS;
    }
}
