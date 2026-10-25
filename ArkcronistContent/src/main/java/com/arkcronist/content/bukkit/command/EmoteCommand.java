package com.arkcronist.content.bukkit.command;

import com.arkcronist.content.bukkit.emote.EmoteService;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/** {@code /emote [name|stop]} (also {@code /emotes}) - plays an emote, ends it, or lists them (5.0). */
public final class EmoteCommand {

    private final EmoteService emotes;

    public EmoteCommand(EmoteService emotes) {
        this.emotes = emotes;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("emote")
                .requires(source -> source.getSender().hasPermission(EmoteService.PERMISSION)
                        && source.getExecutor() instanceof Player)
                .executes(this::list)
                .then(Commands.literal("stop").executes(this::stop))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            String typed = builder.getRemainingLowerCase();
                            emotes.emotes().keySet().stream().filter(name -> name.startsWith(typed))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(this::play))
                .build();
    }

    private int list(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getExecutor();
        String names = String.join(", ", emotes.emotes().keySet().stream().filter(name -> !name.contains(":")).toList());
        player.sendMessage(Component.text(names.isEmpty() ? "There are no emotes." : "Emotes: " + names
                + " - /emote <name>; /emote stop, or sneak, to end one.", NamedTextColor.GRAY));
        return Command.SINGLE_SUCCESS;
    }

    private int stop(CommandContext<CommandSourceStack> context) {
        return emotes.stop((Player) context.getSource().getExecutor()) ? Command.SINGLE_SUCCESS : 0;
    }

    private int play(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getExecutor();
        String name = StringArgumentType.getString(context, "name").trim().toLowerCase(Locale.ROOT);
        Map<String, CustomItem> known = emotes.emotes();
        CustomItem item = known.get(name);
        if (item == null) {
            player.sendMessage(Component.text("No emote called '" + name + "'. /emote lists them.", NamedTextColor.RED));
            return 0;
        }
        return emotes.play(player, item) ? Command.SINGLE_SUCCESS : 0;
    }
}
