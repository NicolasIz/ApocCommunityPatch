package com.arkcronist.content.bukkit.command;

import com.arkcronist.content.bukkit.ArkContentPlugin;
import com.arkcronist.content.bukkit.ContentPipeline;
import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.pack.PackArtifact;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /arkcontent reload} and {@code /arkcontent info}.
 */
public final class ContentAdminCommand {

    private final ArkContentPlugin plugin;

    public ContentAdminCommand(ArkContentPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("arkcontent")
                .requires(source -> source.getSender().hasPermission("arkcontent.admin"))
                .then(Commands.literal("reload").executes(this::reload))
                .then(Commands.literal("info").executes(this::info))
                .build();
    }

    /**
     * Rebuilds items and pack from the contents folder. Returns at once; the sender hears back when
     * the new pack is live. config.yml is not re-read: the web server's port and address need a
     * restart to change.
     */
    private int reload(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("Rebuilding custom content...", NamedTextColor.GRAY));

        ContentPipeline pipeline = plugin.pipeline();
        pipeline.rebuild().whenCompleteAsync((report, error) -> {
            if (error != null) {
                sender.sendMessage(Component.text("Rebuild failed - the previous items and pack are still live."
                        + " See the console for the cause.", NamedTextColor.RED));
                return;
            }
            sender.sendMessage(Component.text(report.items() + " item(s), " + report.blocks() + " block(s), pack "
                    + shortHash(report.sha1Hex())
                    + (report.changed() ? ", sent to online players" : ", unchanged")
                    + " (" + report.millis() + " ms).", NamedTextColor.GREEN));
            if (!report.problems().isEmpty()) {
                sender.sendMessage(Component.text(report.problems().size()
                        + " problem(s) - listed in the console.", NamedTextColor.YELLOW));
            }
        }, pipeline.mainThread());
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PackArtifact pack = plugin.delivery().current();
        String url = plugin.delivery().currentUrl();

        sender.sendMessage(Component.text("Custom items: " + plugin.items().size() + ", of which blocks: "
                + plugin.blocks().size() + " (of " + (NoteBlockState.CAPACITY - 1) + " note block states)",
                NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Placed blocks and furniture in loaded worlds: " + plugin.placed().size(),
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(pack == null
                ? "Pack: not built yet"
                : "Pack: " + pack.entries() + " file(s), " + (pack.size() / 1024) + " KiB, sha1 " + pack.sha1Hex(),
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(plugin.httpRunning()
                ? "Web server: " + (url != null ? url : "running, waiting for the first build")
                : "Web server: off", NamedTextColor.GRAY));
        return Command.SINGLE_SUCCESS;
    }

    private static String shortHash(String sha1Hex) {
        return sha1Hex.substring(0, Math.min(8, sha1Hex.length()));
    }
}
