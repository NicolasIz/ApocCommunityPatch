package com.arkcronist.content.bukkit.command;

import com.arkcronist.content.bukkit.ArkContentPlugin;
import com.arkcronist.content.bukkit.ContentPipeline;
import com.arkcronist.content.bukkit.hooks.NpcBridge;
import com.arkcronist.content.bukkit.hud.HudService;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.liquid.LiquidRegistry;
import com.arkcronist.content.bukkit.liquid.LiquidService;
import com.arkcronist.content.bukkit.menu.ContentMenu;
import com.arkcronist.content.bukkit.menu.Shop;
import com.arkcronist.content.bukkit.sanity.SanityChecker;
import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.hud.HudDefinition;
import com.arkcronist.content.core.hud.HudLayout;
import com.arkcronist.content.core.importer.ImportReport;
import com.arkcronist.content.core.liquid.TripwireState;
import com.arkcronist.content.core.pack.PackArtifact;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * {@code /arkcontent}: {@code reload}, {@code info}, {@code import}, {@code editor},
 * {@code animate}, {@code hud} and {@code liquids} for admins,
 * {@code menu} for anyone allowed to browse, {@code shop} for buyers when Vault is installed, and
 * {@code npc} for admins when Citizens is. On its own, run by a player who may browse, it opens the
 * menu.
 */
public final class ContentAdminCommand {

    private final ArkContentPlugin plugin;

    public ContentAdminCommand(ArkContentPlugin plugin) {
        this.plugin = plugin;
    }

    private static final String ADMIN = "arkcontent.admin";

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("arkcontent")
                .requires(source -> source.getSender().hasPermission(ADMIN)
                        || source.getSender().hasPermission(ContentMenu.BROWSE_PERMISSION))
                .executes(this::menuOrHelp)
                .then(Commands.literal("reload").requires(ContentAdminCommand::admin).executes(this::reload))
                .then(Commands.literal("info").requires(ContentAdminCommand::admin).executes(this::info))
                .then(Commands.literal("cmd").requires(ContentAdminCommand::admin)
                        .then(Commands.argument("item", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    String typed = builder.getRemainingLowerCase();
                                    plugin.items().all().stream().map(CustomItem::id)
                                            .filter(id -> id.startsWith(typed)).sorted().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(this::modelData)))
                .then(Commands.literal("import").requires(ContentAdminCommand::admin).executes(this::importContent))
                .then(Commands.literal("showcase").requires(ContentAdminCommand::admin)
                        .then(Commands.argument("namespace", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String typed = builder.getRemainingLowerCase();
                                    plugin.items().all().stream()
                                            .filter(item -> item.placement() instanceof Placement.Furniture)
                                            .map(item -> item.id().substring(0, item.id().indexOf(':')))
                                            .distinct().filter(namespace -> namespace.startsWith(typed)).sorted()
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> showcase(context,
                                        StringArgumentType.getString(context, "namespace")))))
                .then(Commands.literal("animate").requires(ContentAdminCommand::admin)
                        .then(Commands.argument("animation", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    Player player = viewer(context.getSource());
                                    if (player != null) {
                                        plugin.furniture().target(player).map(plugin.furniture()::clips)
                                                .orElse(List.of()).forEach(builder::suggest);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(this::animate)))
                .then(Commands.literal("hud").requires(ContentAdminCommand::admin)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    plugin.getServer().getOnlinePlayers().forEach(online -> builder.suggest(online.getName()));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("hud", io.papermc.paper.command.brigadier.argument.ArgumentTypes.key())
                                        .suggests((context, builder) -> {
                                            plugin.hudRegistry().all().forEach(hud -> builder.suggest(hud.definition().fullId()));
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> hud(context, null, 0))
                                        .then(Commands.argument("change", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    List.of("set", "add", "take").forEach(builder::suggest);
                                                    return builder.buildFuture();
                                                })
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                                        .executes(context -> hud(context,
                                                                StringArgumentType.getString(context, "change"),
                                                                DoubleArgumentType.getDouble(context, "amount"))))))))
                .then(Commands.literal("liquids").requires(ContentAdminCommand::admin).executes(this::liquids))
                .then(Commands.literal("vehicles").requires(ContentAdminCommand::admin).executes(this::vehicles)
                        .then(Commands.literal("reset").executes(context -> {
                            plugin.vehicles().resetTiming();
                            context.getSource().getSender().sendMessage(Component.text("Vehicle timings reset.",
                                    NamedTextColor.GRAY));
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(Commands.literal("bedrock").requires(ContentAdminCommand::admin).executes(this::bedrock)
                        .then(Commands.literal("look").executes(this::bedrockLook)))
                .then(Commands.literal("legacy").requires(ContentAdminCommand::admin)
                        .executes(context -> legacy(context, false, 32))
                        .then(Commands.literal("look").executes(this::legacyLook))
                        .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 256))
                                .executes(context -> legacy(context, false,
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "radius"))))
                        .then(Commands.literal("remove")
                                .executes(context -> legacy(context, true, 32))
                                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 256))
                                        .executes(context -> legacy(context, true,
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "radius"))))))
                .then(Commands.literal("map").requires(ContentAdminCommand::admin)
                        .executes(context -> map(context, false, 160))
                        .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(40, 640))
                                .executes(context -> map(context, false,
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "radius"))))
                        .then(Commands.literal("spawn")
                                .executes(context -> map(context, true, 160))
                                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(40, 640))
                                        .executes(context -> map(context, true,
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "radius"))))))
                .then(Commands.literal("integrity").requires(ContentAdminCommand::admin)
                        .executes(this::integrity)
                        .then(Commands.literal("now").executes(this::integrityNow))
                        .then(Commands.literal("recovered").executes(this::recovered))
                        .then(Commands.literal("claim")
                                .then(Commands.argument("id", com.mojang.brigadier.arguments.LongArgumentType.longArg(1))
                                        .executes(this::claim))))
                .then(Commands.literal("editor").requires(ContentAdminCommand::admin)
                        .executes(context -> editor(context, null))
                        .then(Commands.argument("furniture", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    String typed = builder.getRemainingLowerCase();
                                    plugin.items().all().stream()
                                            .filter(item -> item.placement() instanceof Placement.Furniture)
                                            .map(CustomItem::id).filter(id -> id.startsWith(typed)).sorted()
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> editor(context, StringArgumentType.getString(context, "furniture")))))
                .then(Commands.literal("menu")
                        .requires(source -> source.getSender().hasPermission(ContentMenu.BROWSE_PERMISSION))
                        .executes(this::menu))
                .then(Commands.literal("shop")
                        .requires(source -> source.getSender().hasPermission(Shop.PERMISSION))
                        .executes(this::shop))
                .then(new QuestBoardCommand(plugin, plugin.questStore(), plugin.questBoards(),
                        () -> plugin.pipeline().questTemplates()).build());
        NpcBridge npcs = plugin.hooks().npcs();
        if (npcs != null) {
            root.then(Commands.literal("npc").requires(ContentAdminCommand::admin)
                    .then(Commands.literal("equip")
                            .then(Commands.argument("slot", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        npcs.slots().forEach(builder::suggest);
                                        return builder.buildFuture();
                                    })
                                    .then(Commands.argument("item", StringArgumentType.greedyString())
                                            .suggests((context, builder) -> {
                                                String typed = builder.getRemainingLowerCase();
                                                plugin.items().all().stream().map(CustomItem::id)
                                                        .filter(id -> id.startsWith(typed)).sorted().forEach(builder::suggest);
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> npcEquip(context, npcs)))))
                    .then(Commands.literal("sit").executes(context -> npcSit(context, npcs))));
        }
        return root.build();
    }

    private int shop(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Player player = viewer(context.getSource());
        Shop shop = plugin.hooks().shop();
        if (player == null) {
            sender.sendMessage(Component.text("Only a player can shop.", NamedTextColor.RED));
            return 0;
        }
        if (shop == null) {
            sender.sendMessage(Component.text("The shop needs Vault and an economy plugin.", NamedTextColor.RED));
            return 0;
        }
        plugin.menus().openShop(player, shop);
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /arkcontent npc equip <slot> <item>}: the selected NPC gets a custom item. */
    private int npcEquip(CommandContext<CommandSourceStack> context, NpcBridge npcs) {
        CommandSender sender = context.getSource().getSender();
        String id = StringArgumentType.getString(context, "item").trim();
        Optional<CustomItem> item = plugin.items().find(id);
        if (item.isEmpty()) {
            sender.sendMessage(Component.text("No custom item '" + id + "'.", NamedTextColor.RED));
            return 0;
        }
        String problem = npcs.equip(sender, StringArgumentType.getString(context, "slot"),
                plugin.itemFactory().create(item.get(), 1));
        sender.sendMessage(problem == null
                ? Component.text("Equipped " + item.get().id() + ".", NamedTextColor.GREEN)
                : Component.text(problem, NamedTextColor.RED));
        return problem == null ? Command.SINGLE_SUCCESS : 0;
    }

    /** {@code /arkcontent npc sit}: the selected NPC sits on the furniture the player is looking at. */
    private int npcSit(CommandContext<CommandSourceStack> context, NpcBridge npcs) {
        CommandSender sender = context.getSource().getSender();
        Player player = viewer(context.getSource());
        if (player == null) {
            sender.sendMessage(Component.text("Only a player can aim at furniture.", NamedTextColor.RED));
            return 0;
        }
        Optional<Block> target = plugin.furniture().target(player);
        Optional<Placement.Furniture> definition = target.flatMap(plugin.furniture()::definition);
        if (definition.isEmpty()) {
            sender.sendMessage(Component.text("Look at a piece of furniture first.", NamedTextColor.RED));
            return 0;
        }
        Block block = target.get();
        float height = definition.get().seat() != null ? definition.get().seat().height() : 0.5f;
        Location seat = block.getLocation().add(0.5, height, 0.5);
        seat.setYaw(plugin.furniture().facing(block).orElse(0f));
        String problem = npcs.sit(sender, seat);
        sender.sendMessage(problem == null
                ? Component.text("Sitting.", NamedTextColor.GREEN)
                : Component.text(problem, NamedTextColor.RED));
        return problem == null ? Command.SINGLE_SUCCESS : 0;
    }

    private static boolean admin(CommandSourceStack source) {
        return source.getSender().hasPermission(ADMIN);
    }

    private int menuOrHelp(CommandContext<CommandSourceStack> context) {
        if (viewer(context.getSource()) != null && context.getSource().getSender().hasPermission(ContentMenu.BROWSE_PERMISSION)) {
            return menu(context);
        }
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("/arkcontent reload | info | import | editor [furniture] | animate <animation>"
                + " | menu | shop"
                + (plugin.hooks().npcs() != null ? " | npc" : ""), NamedTextColor.GOLD));
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

    /** Plays one of the animations of the furniture the player is looking at: a way to try a model out. */
    private int animate(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Player player = viewer(context.getSource());
        if (player == null) {
            sender.sendMessage(Component.text("Only a player can aim at furniture.", NamedTextColor.RED));
            return 0;
        }
        String name = StringArgumentType.getString(context, "animation");
        Optional<Block> target = plugin.furniture().target(player);
        if (target.isEmpty()) {
            sender.sendMessage(Component.text("Look at a piece of furniture first.", NamedTextColor.RED));
            return 0;
        }
        if (!plugin.furniture().animate(target.get(), name)) {
            List<String> clips = plugin.furniture().clips(target.get());
            sender.sendMessage(Component.text(clips.isEmpty()
                    ? "That furniture has no animated model."
                    : "No animation '" + name + "'; it has: " + String.join(", ", clips), NamedTextColor.RED));
            return 0;
        }
        sender.sendMessage(Component.text("Playing '" + name + "'.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /arkcontent showcase <namespace>}: every piece of furniture of a pack, placed in rows in
     * front of the player, facing them - a spawn's worth of furniture in one go, to look over or to
     * measure the server with. Pieces on a chest or a bed are left out, and so is any piece without
     * the room it needs.
     */
    private int showcase(CommandContext<CommandSourceStack> context, String namespace) {
        CommandSender sender = context.getSource().getSender();
        Player player = viewer(context.getSource());
        if (player == null) {
            sender.sendMessage(Component.text("Only a player can have furniture set out in front of them.",
                    NamedTextColor.RED));
            return 0;
        }
        List<CustomItem> pieces = plugin.items().all().stream()
                .filter(item -> item.id().startsWith(namespace + ":"))
                .filter(item -> item.placement() instanceof Placement.Furniture spec
                        && (spec.support() == Placement.Support.BARRIER || spec.support() == Placement.Support.LIGHT))
                .toList();
        if (pieces.isEmpty()) {
            sender.sendMessage(Component.text("No furniture on a barrier or light block in '" + namespace + "'.",
                    NamedTextColor.RED));
            return 0;
        }
        sender.sendMessage(Component.text("Setting out " + pieces.size() + " piece(s) of " + namespace + ", "
                + Showcase.PER_TICK + " a tick...", NamedTextColor.GRAY));
        Showcase.start(plugin, plugin.furniture(), plugin.storage(), player, pieces, result ->
                sender.sendMessage(Component.text(String.format(Locale.ROOT,
                        "Set out %d piece(s) of %s in %d row(s) over %d tick(s), at most %.1f ms in one tick%s.",
                        result.placed(), namespace, result.rows(), result.ticks(), result.slowestTickMillis(),
                        result.skipped() > 0 ? "; " + result.skipped() + " had no room" : ""), NamedTextColor.GREEN)));
        return Command.SINGLE_SUCCESS;
    }

    /** 4.0: {@code /arkcontent vehicles} - how many, and what they cost each tick. */
    private int vehicles(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        int[] counts = plugin.vehicles().counts();
        double[] timing = plugin.vehicles().timing();
        sender.sendMessage(Component.text(String.format(Locale.ROOT,
                "Vehicles: %d in loaded chunks (%d driven), %d parked elsewhere. Main thread %.1f µs a tick;"
                        + " ArkContent-Physics %.1f µs a vehicle step over %.0f step(s).",
                counts[0], counts[2], counts[1], timing[0], timing[1], timing[2]), NamedTextColor.GRAY));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /arkcontent editor [furniture]}: the furniture editor on the piece the player is looking
     * at, or the nearest piece of the furniture named.
     */
    private int editor(CommandContext<CommandSourceStack> context, @Nullable String furniture) {
        CommandSender sender = context.getSource().getSender();
        Player player = viewer(context.getSource());
        if (player == null) {
            sender.sendMessage(Component.text("Only a player can aim at furniture.", NamedTextColor.RED));
            return 0;
        }
        String problem = plugin.editor().open(player, furniture);
        if (problem != null) {
            sender.sendMessage(Component.text(problem, NamedTextColor.RED));
            return 0;
        }
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
                sender.sendMessage(Component.text("Nothing to import. Copy ItemsAdder's contents folder, one"
                        + " pack from it, or its generated resource pack zip into plugins/" + plugin.getName()
                        + "/import/ and run this again.",
                        NamedTextColor.YELLOW));
                return;
            }
            sender.sendMessage(Component.text("Imported " + report.items() + " item(s) from " + report.converted()
                    + " file(s); " + report.resources() + " asset file(s) copied into contents/ byte for byte"
                    + (report.packs() == 0 ? "." : "; " + report.packs() + " generated pack(s) of " + report.packFiles()
                    + " file(s) taken whole into packs/."),
                    NamedTextColor.GREEN));
            summarise(sender, report);
            if (report.items() > 0 || report.packs() > 0) {
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
                    + report.emojis() + " emoji(s), " + report.recipes() + " recipe(s), pack "
                    + shortHash(report.sha1Hex())
                    + (report.changed() ? ", sent to online players" : ", unchanged")
                    + " (" + report.millis() + " ms).", NamedTextColor.GREEN));
            if (!report.problems().isEmpty()) {
                sender.sendMessage(Component.text(report.problems().size()
                        + " problem(s) - listed in the console.", NamedTextColor.YELLOW));
            }
        }, plugin.pipeline().mainThread());
    }

    /**
     * The material and custom_model_data number an item is also drawn by, for plugins that only
     * take those two (pack.custom-model-data).
     */
    private int modelData(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String id = StringArgumentType.getString(context, "item");
        Optional<CustomItem> item = plugin.items().find(id);
        if (item.isEmpty()) {
            sender.sendMessage(Component.text("No item '" + id + "'.", NamedTextColor.RED));
            return 0;
        }
        Optional<Integer> number = plugin.itemFactory().modelData(item.get());
        if (number.isEmpty()) {
            sender.sendMessage(Component.text(item.get().id() + " has no custom_model_data number: its material, "
                    + item.get().material() + ", has its own vanilla definition (or pack.custom-model-data is off).",
                    NamedTextColor.YELLOW));
            return 0;
        }
        sender.sendMessage(Component.text(item.get().id() + ": material " + item.get().material()
                + ", custom_model_data " + number.get(), NamedTextColor.GOLD));
        return number.get();
    }

    /** {@code /arkcontent hud <player> <hud> [set|add|take <amount>]}: shows or changes a bar's value. */
    private int hud(CommandContext<CommandSourceStack> context, @Nullable String change, double amount) {
        CommandSender sender = context.getSource().getSender();
        String name = StringArgumentType.getString(context, "player");
        Player player = plugin.getServer().getPlayerExact(name);
        // 3.4: a namespaced id - demo:thirst - which a plain word argument turned down; a bare one reads
        // as minecraft:thirst, and is looked up bare.
        net.kyori.adventure.key.Key key = context.getArgument("hud", net.kyori.adventure.key.Key.class);
        Optional<HudLayout> hud = plugin.hudRegistry().find(key.asString())
                .or(() -> key.namespace().equals(net.kyori.adventure.key.Key.MINECRAFT_NAMESPACE)
                        ? plugin.hudRegistry().find(key.value()) : Optional.empty());
        if (player == null || hud.isEmpty()) {
            sender.sendMessage(Component.text(player == null ? "No player '" + name + "' online."
                    : "No HUD '" + key.asString() + "'.", NamedTextColor.RED));
            return 0;
        }
        HudService huds = plugin.huds();
        String id = hud.get().definition().fullId();
        if (change != null) {
            boolean stored = hud.get().definition().source() instanceof HudDefinition.Source.Stored;
            if (!stored) {
                sender.sendMessage(Component.text(id + " shows another plugin's value through a placeholder;"
                        + " change it there.", NamedTextColor.RED));
                return 0;
            }
            switch (change.toLowerCase(Locale.ROOT)) {
                case "set" -> huds.set(player.getUniqueId(), hud.get(), amount);
                case "add" -> huds.add(player.getUniqueId(), hud.get(), amount);
                case "take" -> huds.add(player.getUniqueId(), hud.get(), -amount);
                default -> {
                    sender.sendMessage(Component.text("Use set, add or take.", NamedTextColor.RED));
                    return 0;
                }
            }
            plugin.actionBars().send(player);
        }
        double[] value = huds.valueAndMax(player, hud.get());
        sender.sendMessage(Component.text(player.getName() + " - " + id + ": " + value[0] + " / " + value[1],
                NamedTextColor.GOLD));
        return (int) Math.round(value[0]);
    }

    /** {@code /arkcontent liquids}: the liquids loaded, and the sources poured in each world. */
    private int liquids(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("Liquids: " + plugin.liquidRegistry().all().size() + " of "
                + TripwireState.LIQUIDS + " (" + String.join(", ", plugin.liquidRegistry().all().stream()
                .map(LiquidRegistry.Liquid::id).sorted().toList()) + ")", NamedTextColor.GOLD));
        int total = 0;
        for (World world : plugin.getServer().getWorlds()) {
            Map<Long, LiquidService.Source> sources = plugin.liquids().sources(world);
            if (!sources.isEmpty()) {
                sender.sendMessage(Component.text("  " + world.getName() + ": " + sources.size() + " source(s)",
                        NamedTextColor.YELLOW));
                total += sources.size();
            }
        }
        return total;
    }

    /** 3.0: what Geyser was given, and what Bedrock players are being shown. */
    private int bedrock(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (plugin.bedrock() == null) {
            sender.sendMessage(Component.text(plugin.pipeline().bedrockActive()
                    ? "Bedrock content is made (output/bedrock/), but Geyser-Spigot is not on this server to hand it to."
                    : "Bedrock content is off: Geyser-Spigot is not installed (bedrock.enabled: auto), or it was turned"
                    + " off in config.yml.", NamedTextColor.YELLOW));
            return 0;
        }
        plugin.bedrock().status().forEach(line -> sender.sendMessage(Component.text(line, NamedTextColor.GOLD)));
        return 1;
    }

    /** 3.4: what Bedrock players are shown of the piece looked at. */
    private int bedrockLook(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Player player = viewer(context.getSource());
        if (player == null) {
            sender.sendMessage(Component.text("Only a player can look at a piece.", NamedTextColor.RED));
            return 0;
        }
        if (plugin.bedrock() == null) {
            return bedrock(context);
        }
        plugin.bedrock().look(player).forEach(line -> sender.sendMessage(Component.text(line, NamedTextColor.GOLD)));
        return 1;
    }

    // ---------------------------------------------------------------- integrity (2.0)

    private int integrity(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        SanityChecker.Status audit = plugin.sanity().last();
        if (!plugin.sanity().enabled()) {
            sender.sendMessage(Component.text("Integrity check: off (sanity.enabled in config.yml).", NamedTextColor.GOLD));
            return 0;
        }
        if (audit == null) {
            sender.sendMessage(Component.text("Integrity check: first audit pending (every "
                    + plugin.settings().sanity().intervalSeconds() + " s). /arkcontent integrity now runs one.",
                    NamedTextColor.GOLD));
            return 0;
        }
        sender.sendMessage(Component.text(String.format(Locale.ROOT, "Integrity check: last audit %d s ago, %d chunk(s)"
                        + " in %.1f ms over %d tick(s); %d finding(s) waiting for a second look.",
                (System.currentTimeMillis() - audit.finishedAtMillis()) / 1000, audit.chunks(), audit.millis(),
                audit.ticks(), audit.suspects()), NamedTextColor.GOLD));
        sender.sendMessage(Component.text(String.format(Locale.ROOT, "Since start: %d broken piece(s) taken out,"
                        + " %d drawn again, %d left-over display(s) removed, %d row(s) and %d liquid source(s) purged,"
                        + " %d storage(s) recovered, %d piece(s) placed before 2.0 given their facing.",
                audit.piecesRemoved(), audit.piecesRedrawn(), audit.displaysRemoved(), audit.rowsPurged(),
                audit.liquidsPurged(), audit.storageRecovered(), audit.piecesAdopted()), NamedTextColor.GRAY));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 3.1: the furniture ItemsAdder left around the player, counted by item; with {@code remove},
     * taken away with the barriers it stood on.
     */
    private int legacy(CommandContext<CommandSourceStack> context, boolean remove, int radius) {
        CommandSender sender = context.getSource().getSender();
        if (!(context.getSource().getExecutor() instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(Component.text("Run it as a player: it looks around where you stand.", NamedTextColor.RED));
            return 0;
        }
        List<com.arkcronist.content.bukkit.furniture.LegacyFurniture.Piece> pieces =
                plugin.legacy().around(player.getLocation(), radius);
        if (pieces.isEmpty()) {
            sender.sendMessage(Component.text("No furniture left by ItemsAdder within " + radius + " blocks"
                    + " (in loaded chunks).", NamedTextColor.GREEN));
            return Command.SINGLE_SUCCESS;
        }
        java.util.Map<String, Integer> byId = new java.util.TreeMap<>();
        for (var piece : pieces) {
            byId.merge(piece.id() == null || piece.id().isEmpty() ? "(no id)" : piece.id(), 1, Integer::sum);
            if (remove) {
                plugin.legacy().remove(piece, null, false);
            }
        }
        sender.sendMessage(Component.text(pieces.size() + " piece(s) of furniture left by ItemsAdder within " + radius
                + " blocks" + (remove ? " - removed, with the barriers they stood on." : ":"), NamedTextColor.GOLD));
        byId.forEach((id, count) -> sender.sendMessage(Component.text("  " + id + " x" + count, NamedTextColor.GRAY)));
        if (!remove) {
            sender.sendMessage(Component.text("A punch at one, or breaking its barrier, takes it away and drops its item;"
                    + " /arkcontent legacy remove " + radius + " takes them all.", NamedTextColor.GRAY));
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 3.2: the map board's picture drawn from the world: {@code radius} blocks either side across
     * (the picture is twenty to eleven), around where the player stands or the world's spawn, with a
     * cross on the spawn; then the pack is built again, so everyone sees it.
     */
    private int map(CommandContext<CommandSourceStack> context, boolean atSpawn, int radius) {
        CommandSender sender = context.getSource().getSender();
        org.bukkit.World world;
        org.bukkit.Location centre;
        if (context.getSource().getExecutor() instanceof org.bukkit.entity.Player player) {
            world = player.getWorld();
            centre = atSpawn ? world.getSpawnLocation() : player.getLocation();
        } else if (atSpawn) {
            world = plugin.getServer().getWorlds().get(0);
            centre = world.getSpawnLocation();
        } else {
            sender.sendMessage(Component.text("Run it as a player, or use /arkcontent map spawn.", NamedTextColor.RED));
            return 0;
        }
        java.nio.file.Path pack = plugin.pipeline().contentsDir().resolve("medieval_props");
        if (!java.nio.file.Files.isDirectory(pack)) {
            sender.sendMessage(Component.text("contents/medieval_props/ is not there: the map board is one of its pieces.",
                    NamedTextColor.RED));
            return 0;
        }
        java.nio.file.Path out = pack.resolve("textures/furniture/mapa_mural_mapa.png");
        int scale = Math.max(1, Math.min(com.arkcronist.content.bukkit.furniture.SpawnMapRenderer.MAX_SCALE,
                (int) Math.ceil(radius / (com.arkcronist.content.core.furniture.SpawnMap.WIDTH / 2.0))));
        int across = com.arkcronist.content.core.furniture.SpawnMap.WIDTH * scale;
        int down = com.arkcronist.content.core.furniture.SpawnMap.HEIGHT * scale;
        sender.sendMessage(Component.text("Drawing " + across + " x " + down + " blocks around " + centre.getBlockX() + ", "
                + centre.getBlockZ() + " (" + scale + " block(s) a pixel)...", NamedTextColor.GRAY));
        new com.arkcronist.content.bukkit.furniture.SpawnMapRenderer(plugin)
                .render(world, centre.getBlockX(), centre.getBlockZ(), scale, world.getSpawnLocation(), out)
                .whenCompleteAsync((chunks, error) -> {
                    if (error != null) {
                        sender.sendMessage(Component.text("The map could not be drawn: " + rootMessage(error), NamedTextColor.RED));
                        return;
                    }
                    sender.sendMessage(Component.text("Map drawn from " + chunks + " chunk(s) into "
                            + "contents/medieval_props/textures/furniture/mapa_mural_mapa.png. Rebuilding the pack...",
                            NamedTextColor.GREEN));
                    tellWhenLive(sender, plugin.pipeline().rebuild());
                }, plugin.pipeline().mainThread());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 3.2: the model the player looks at, taken away - a leftover that nothing else recognises: one of
     * ItemsAdder's whose item no longer says so, or a display of this plugin's whose piece is gone.
     * A live piece of furniture is not touched: it is broken as any other.
     */
    private int legacyLook(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(context.getSource().getExecutor() instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(Component.text("Run it as a player, looking at the model.", NamedTextColor.RED));
            return 0;
        }
        org.bukkit.entity.Entity target = lookedAt(player, 8);
        if (target == null) {
            sender.sendMessage(Component.text("No model where you look, within 8 blocks: a display, an armour stand, an"
                    + " item frame or an interaction box.", NamedTextColor.RED));
            return 0;
        }
        String what = describe(target);
        String id = plugin.furniture().furnitureId(target);
        if (id != null && !plugin.furniture().isLeftOver(target)) {
            sender.sendMessage(Component.text(Character.toUpperCase(what.charAt(0)) + what.substring(1) + " is a piece of "
                    + id + " that is still standing: break it to take"
                    + " it away.", NamedTextColor.YELLOW));
            return 0;
        }
        var piece = plugin.legacy().pieceOf(target);
        if (piece.isPresent()) {
            plugin.legacy().remove(piece.get(), null, false);
            sender.sendMessage(Component.text("Removed " + what + ", left by ItemsAdder"
                    + (piece.get().id() == null || piece.get().id().isEmpty() ? "" : " (" + piece.get().id() + ")")
                    + ", with " + (piece.get().entities().size() - 1) + " other part(s) and "
                    + piece.get().barriers().size() + " barrier(s).", NamedTextColor.GREEN));
        } else {
            target.remove();
            sender.sendMessage(Component.text("Removed " + what + (id != null ? ", left over from " + id : "") + ".",
                    NamedTextColor.GREEN));
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * The model nearest the middle of the player's view within {@code reach} blocks - a display, an
     * armour stand, an item frame or an interaction box, not a seat - up to the first block in the
     * way, and a little past it: a model often stands inside the barrier it was placed on.
     */
    private @org.jetbrains.annotations.Nullable org.bukkit.entity.Entity lookedAt(org.bukkit.entity.Player player,
                                                                                   double reach) {
        org.bukkit.Location eye = player.getEyeLocation();
        org.bukkit.util.Vector direction = eye.getDirection().normalize();
        org.bukkit.util.RayTraceResult wall = player.getWorld().rayTraceBlocks(eye, direction, reach,
                org.bukkit.FluidCollisionMode.NEVER, true);
        double limit = wall == null ? reach : Math.min(reach, wall.getHitPosition().distance(eye.toVector()) + 1.5);
        org.bukkit.util.Vector start = eye.toVector();
        org.bukkit.util.BoundingBox around = org.bukkit.util.BoundingBox.of(start, start.clone().add(direction.clone()
                .multiply(limit))).expand(1.5);
        org.bukkit.entity.Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (org.bukkit.entity.Entity entity : player.getWorld().getNearbyEntities(around, this::lookable)) {
            org.bukkit.util.BoundingBox box = entity.getBoundingBox();
            org.bukkit.util.Vector centre = box.getVolume() > 0 ? box.getCenter() : entity.getLocation().toVector();
            org.bukkit.util.Vector relative = centre.clone().subtract(start);
            double along = relative.dot(direction);
            if (along < 0 || along > limit) {
                continue;
            }
            double off = relative.clone().subtract(direction.clone().multiply(along)).length();
            double reachOf = Math.max(0.9, Math.max(box.getWidthX(), box.getHeight()) / 2 + 0.3);
            if (off <= reachOf && off + along * 0.05 < bestScore) {
                bestScore = off + along * 0.05;
                best = entity;
            }
        }
        return best;
    }

    private boolean lookable(org.bukkit.entity.Entity entity) {
        return (entity instanceof org.bukkit.entity.Display || entity instanceof org.bukkit.entity.ArmorStand
                || entity instanceof org.bukkit.entity.ItemFrame || entity instanceof org.bukkit.entity.Interaction)
                && !(entity instanceof org.bukkit.entity.TextDisplay) && !plugin.seats().isSeat(entity);
    }

    private static String describe(org.bukkit.entity.Entity entity) {
        org.bukkit.inventory.ItemStack shown = entity instanceof org.bukkit.entity.ItemDisplay display ? display.getItemStack()
                : entity instanceof org.bukkit.entity.ItemFrame frame ? frame.getItem()
                : entity instanceof org.bukkit.entity.ArmorStand stand ? stand.getEquipment().getHelmet() : null;
        org.bukkit.Location at = entity.getLocation();
        String item = shown == null || shown.getType().isAir() ? ""
                : " showing " + shown.getType().getKey().getKey()
                + (shown.hasItemMeta() && shown.getItemMeta().hasItemModel()
                ? " (" + shown.getItemMeta().getItemModel() + ")" : "");
        String type = entity.getType().getKey().getKey().replace('_', ' ');
        return ("aeiou".indexOf(type.charAt(0)) >= 0 ? "an " : "a ") + type + item + " at " + at.getBlockX() + " "
                + at.getBlockY() + " " + at.getBlockZ();
    }

    private int integrityNow(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("Auditing the loaded chunks twice...", NamedTextColor.GRAY));
        plugin.sanity().runNow().whenComplete((status, error) -> plugin.pipeline().mainThread().execute(() -> {
            if (error != null) {
                sender.sendMessage(Component.text("The audit could not finish: " + rootMessage(error), NamedTextColor.RED));
            } else {
                integrity(context);
            }
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int recovered(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        plugin.sanity().recovered(true, 20).whenComplete((rows, error) -> plugin.pipeline().mainThread().execute(() -> {
            if (error != null) {
                sender.sendMessage(Component.text("Could not read the recovered storage: " + rootMessage(error),
                        NamedTextColor.RED));
                return;
            }
            if (rows.isEmpty()) {
                sender.sendMessage(Component.text("No recovered storage waiting.", NamedTextColor.GRAY));
                return;
            }
            sender.sendMessage(Component.text("Recovered storage waiting (newest first; /arkcontent integrity claim <id>):",
                    NamedTextColor.GOLD));
            for (var row : rows) {
                World world = plugin.getServer().getWorld(row.world());
                sender.sendMessage(Component.text(String.format(Locale.ROOT, "  #%d %s at %s %d,%d,%d - %s",
                        row.id(), row.furnitureId(), world == null ? row.world().toString() : world.getName(),
                        row.x(), row.y(), row.z(), row.reason()), NamedTextColor.YELLOW));
            }
        }));
        return Command.SINGLE_SUCCESS;
    }

    /** Hands what a recovered row held to the admin: their inventory, the rest at their feet. Once. */
    private int claim(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can take recovered items.", NamedTextColor.RED));
            return 0;
        }
        long id = com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "id");
        plugin.sanity().claim(id, player.getName()).whenComplete((row, error) -> plugin.pipeline().mainThread().execute(() -> {
            if (error != null) {
                player.sendMessage(Component.text("Could not claim #" + id + ": " + rootMessage(error), NamedTextColor.RED));
                return;
            }
            if (row.isEmpty()) {
                player.sendMessage(Component.text("#" + id + " is not waiting: unknown, or already taken.",
                        NamedTextColor.RED));
                return;
            }
            org.bukkit.inventory.ItemStack[] items;
            try {
                items = org.bukkit.inventory.ItemStack.deserializeItemsFromBytes(row.get().contents());
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Recovered storage #" + id + " cannot be read; it is marked taken"
                        + " by " + player.getName() + " and its bytes are kept in the database.", exception);
                player.sendMessage(Component.text("#" + id + " cannot be read; see the console.", NamedTextColor.RED));
                return;
            }
            int given = 0;
            for (org.bukkit.inventory.ItemStack item : items) {
                if (item == null || item.isEmpty()) {
                    continue;
                }
                given += item.getAmount();
                player.getInventory().addItem(item).values()
                        .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
            }
            plugin.getLogger().info(player.getName() + " claimed recovered storage #" + id + " (" + row.get().furnitureId()
                    + ", " + given + " item(s)).");
            player.sendMessage(Component.text("Recovered #" + id + ": " + given + " item(s) from " + row.get().furnitureId()
                    + ".", NamedTextColor.GREEN));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && (cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private int info(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PackArtifact pack = plugin.delivery().current();
        String url = plugin.delivery().currentUrl();

        sender.sendMessage(Component.text("Custom items: " + plugin.items().size() + ", of which blocks: "
                + plugin.blocks().size() + " (of " + (NoteBlockState.CAPACITY - 1) + " note block states)",
                NamedTextColor.GOLD));
        int[] ambient = plugin.ambience().counts();
        sender.sendMessage(Component.text("Furniture loaded: " + ambient[0] + ", giving off particles: " + ambient[1],
                NamedTextColor.GRAY));
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
        sender.sendMessage(Component.text("Web server: " + plugin.webHost().status()
                + "; hosting: " + plugin.settings().hosting().name().toLowerCase(Locale.ROOT)
                + "; players are sent: " + (url != null ? url : pack == null ? "nothing yet" : "nothing"),
                NamedTextColor.GRAY));
        String problem = plugin.webHost().problem();
        if (problem != null) {
            sender.sendMessage(Component.text(problem, NamedTextColor.YELLOW));
        }
        SanityChecker.Status audit = plugin.sanity().last();
        sender.sendMessage(Component.text("Sanity check: " + (!plugin.sanity().enabled() ? "off"
                : audit == null ? "first audit pending (every " + plugin.settings().sanity().intervalSeconds() + " s)"
                : String.format(Locale.ROOT, "last audit %d s ago, %d chunk(s) in %.1f ms over %d tick(s), %d finding(s)"
                        + " waiting for a second look; removed so far: %d display(s), %d row(s), %d liquid source(s)",
                (System.currentTimeMillis() - audit.finishedAtMillis()) / 1000, audit.chunks(), audit.millis(),
                audit.ticks(), audit.suspects(), audit.displaysRemoved(), audit.rowsPurged(), audit.liquidsPurged())),
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Font images by name: " + plugin.fontImages().current().size() + " ("
                + plugin.fontImages().current().defined() + " from font_images:)"
                + (plugin.settings().fontImages().titles() ? "; menu titles rewritten" : "")
                + (plugin.settings().fontImages().placeholders() ? "; %img_% answered" : ""), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Furniture with a look of their own (editor): " + plugin.transforms().size()
                + "; editors open: " + plugin.editor().open(), NamedTextColor.GRAY));
        return Command.SINGLE_SUCCESS;
    }

    private static String shortHash(String sha1Hex) {
        return sha1Hex.substring(0, Math.min(8, sha1Hex.length()));
    }
}
