package com.arkcronist.content.bukkit.command;

import com.arkcronist.content.bukkit.ArkContentPlugin;
import com.arkcronist.content.bukkit.ContentPipeline;
import com.arkcronist.content.bukkit.menu.ContentMenu;
import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.importer.ImportReport;
import com.arkcronist.content.core.pack.PackArtifact;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@code /arkcontent}: {@code reload}, {@code info} and {@code import} for admins, {@code menu} for
 * anyone allowed to browse. On its own, run by a player who may browse, it opens the menu.
 */
public final class ContentAdminCommand {

    private final ArkContentPlugin plugin;

    public ContentAdminCommand(ArkContentPlugin plugin) {
        this.plugin = plugin;
    }

    private static final String ADMIN = "arkcontent.admin";

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("arkcontent")
                .requires(source -> source.getSender().hasPermission(ADMIN)
                        || source.getSender().hasPermission(ContentMenu.BROWSE_PERMISSION))
                .executes(this::menuOrHelp)
                .then(Commands.literal("reload").requires(ContentAdminCommand::admin).executes(this::reload))
                .then(Commands.literal("info").requires(ContentAdminCommand::admin).executes(this::info))
                .then(Commands.literal("import").requires(ContentAdminCommand::admin).executes(this::importContent))
                .then(Commands.literal("menu")
                        .requires(source -> source.getSender().hasPermission(ContentMenu.BROWSE_PERMISSION))
                        .executes(this::menu))
                .build();
    }

    private static boolean admin(CommandSourceStack source) {
        return source.getSender().hasPermission(ADMIN);
    }

    private int menuOrHelp(CommandContext<CommandSourceStack> context) {
        if (viewer(context.getSource()) != null && context.getSource().getSender().hasPermission(ContentMenu.BROWSE_PERMISSION)) {
            return menu(context);
        }
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("/arkcontent reload | info | import | menu", NamedTextColor.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private int menu(CommandContext<CommandSourceStack> context) {
        Player player = viewer(context.getSource());
        if (player == null) {
            context.getSource().getSender().sendMessage(Component.text("Only a player can open the menu.", NamedTextColor.RED));
            return 0;
        }
        plugin.menus().open(player);
        return Command.SINGLE_SUCCESS;
    }

    /** Who the menu is for: the executor ({@code /execute as}), or the sender itself. */
    private static Player viewer(CommandSourceStack source) {
        Entity executor = source.getExecutor();
        if (executor instanceof Player player) {
            return player;
        }
        return source.getSender() instanceof Player player ? player : null;
    }

    /**
     * Converts the ItemsAdder packs in import/ and then rebuilds, so the imported items are live
     * when the sender hears back. Both steps run on the plugin's worker; this returns at once.
     */
    private int importContent(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("Importing ItemsAdder content from plugins/"
                + plugin.getName() + "/import/...", NamedTextColor.GRAY));

        ContentPipeline pipeline = plugin.pipeline();
        pipeline.importFromItemsAdder().whenCompleteAsync((report, error) -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                plugin.getLogger().log(Level.SEVERE, "ItemsAdder import failed", cause);
                sender.sendMessage(Component.text("Import failed: " + cause.getMessage()
                        + ". Nothing was rebuilt; see the console.", NamedTextColor.RED));
                return;
            }
            if (report.empty()) {
                sender.sendMessage(Component.text("Nothing to import. Copy ItemsAdder's contents folder, or one"
                        + " pack from it, into plugins/" + plugin.getName() + "/import/ and run this again.",
                        NamedTextColor.YELLOW));
                return;
            }
            sender.sendMessage(Component.text("Imported " + report.items() + " item(s) from " + report.converted()
                    + " file(s); " + report.resources() + " model/texture file(s) copied into contents/.",
                    NamedTextColor.GREEN));
            summarise(sender, report);
            if (report.items() > 0) {
                sender.sendMessage(Component.text("Rebuilding...", NamedTextColor.GRAY));
                tellWhenLive(sender, pipeline.rebuild());
            }
        }, pipeline.mainThread());
        return Command.SINGLE_SUCCESS;
    }

    private static void summarise(CommandSender sender, ImportReport report) {
        if (report.skipped() > 0 || !report.problems().isEmpty() || !report.notes().isEmpty()) {
            sender.sendMessage(Component.text(report.skipped() + " item(s) skipped, " + report.problems().size()
                    + " problem(s) and " + report.notes().size() + " note(s) - listed in the console.",
                    NamedTextColor.YELLOW));
        }
    }

    /**
     * Rebuilds items and pack from the contents folder. Returns at once; the sender hears back when
     * the new pack is live. config.yml is not re-read: the web server's port and address need a
     * restart to change.
     */
    private int reload(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("Rebuilding custom content...", NamedTextColor.GRAY));

        tellWhenLive(sender, plugin.pipeline().rebuild());
        return Command.SINGLE_SUCCESS;
    }

    /** Tells {@code sender} how a rebuild went, once it has gone live or failed. */
    private void tellWhenLive(CommandSender sender, CompletableFuture<ContentPipeline.Report> rebuild) {
        rebuild.whenCompleteAsync((report, error) -> {
            if (error != null) {
                sender.sendMessage(Component.text("Rebuild failed - the previous items and pack are still live."
                        + " See the console for the cause.", NamedTextColor.RED));
                return;
            }
            sender.sendMessage(Component.text(report.items() + " item(s), " + report.blocks() + " block(s), "
                    + report.emojis() + " emoji(s), pack "
                    + shortHash(report.sha1Hex())
                    + (report.changed() ? ", sent to online players" : ", unchanged")
                    + " (" + report.millis() + " ms).", NamedTextColor.GREEN));
            if (!report.problems().isEmpty()) {
                sender.sendMessage(Component.text(report.problems().size()
                        + " problem(s) - listed in the console.", NamedTextColor.YELLOW));
            }
        }, plugin.pipeline().mainThread());
    }

    private int info(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PackArtifact pack = plugin.delivery().current();
        String url = plugin.delivery().currentUrl();

        sender.sendMessage(Component.text("Custom items: " + plugin.items().size() + ", of which blocks: "
                + plugin.blocks().size() + " (of " + (NoteBlockState.CAPACITY - 1) + " note block states)",
                NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Placed blocks and furniture in loaded worlds: " + plugin.placed().size()
                + ", crops: " + plugin.crops().store().size() + ", storage open now: " + plugin.storage().open()
                + "; chat emojis: " + plugin.emojis().size(),
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(pack == null
                ? "Pack: not built yet"
                : "Pack: " + pack.entries() + " file(s), " + (pack.size() / 1024) + " KiB, sha1 " + pack.sha1Hex(),
                NamedTextColor.GRAY));
        List<String> hooks = plugin.hooks().active();
        sender.sendMessage(Component.text("Hooks: " + (hooks.isEmpty() ? "none" : String.join(", ", hooks)),
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Web server: " + (plugin.httpRunning() ? "running" : "off")
                + "; hosting: " + plugin.settings().hosting().name().toLowerCase(Locale.ROOT)
                + "; players are sent: " + (url != null ? url : pack == null ? "nothing yet" : "nothing"),
                NamedTextColor.GRAY));
        return Command.SINGLE_SUCCESS;
    }

    private static String shortHash(String sha1Hex) {
        return sha1Hex.substring(0, Math.min(8, sha1Hex.length()));
    }
}
