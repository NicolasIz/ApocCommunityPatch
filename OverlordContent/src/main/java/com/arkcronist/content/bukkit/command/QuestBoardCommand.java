package com.arkcronist.content.bukkit.command;

import com.arkcronist.content.bukkit.quest.QuestBoardService;
import com.arkcronist.content.core.quest.QuestBoardStore;
import com.arkcronist.content.core.quest.Counted;
import com.arkcronist.content.core.quest.QuestDefinition;
import com.arkcronist.content.core.quest.QuestDefinition.Kind;
import com.arkcronist.content.core.quest.QuestDefinition.Objective;
import com.arkcronist.content.core.quest.QuestDefinition.Requirement;
import com.arkcronist.content.core.quest.QuestDefinition.Reward;
import com.arkcronist.content.core.quest.QuestJson;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@code /arkcontent board}: quest board notices, for admins. A notice is built step by step and
 * hangs on no board until it is posted:
 *
 * <pre>
 *   create &lt;id&gt; &lt;title...&gt;                       a new notice, MiniMessage title
 *   desc &lt;id&gt; &lt;line...&gt;   cleardesc &lt;id&gt;          its description, a line at a time
 *   icon &lt;id&gt; &lt;item&gt;   repeatable &lt;id&gt; true|false
 *   require &lt;id&gt; skill|job &lt;name&gt; &lt;level&gt;       AuraSkills; ExcellentJobs or Jobs Reborn
 *   require &lt;id&gt; permission &lt;node&gt;
 *   objective &lt;id&gt; deliver|kill|break &lt;target&gt; &lt;amount&gt;
 *   reward &lt;id&gt; money &lt;amount&gt; | item &lt;item&gt; &lt;amount&gt; | hand | command &lt;command...&gt;
 *   clear &lt;id&gt; requirements|objectives|rewards
 *   boards &lt;id&gt; all|&lt;board furniture ids...&gt;
 *   post &lt;id&gt;   unpost &lt;id&gt;   delete &lt;id&gt;   list   info &lt;id&gt;   reset &lt;player&gt; &lt;id&gt;
 * </pre>
 *
 * <p>{@code post} also takes the id of a template from a content file's {@code quests:}, which it
 * copies in first. Every change is read and written on the database's thread; the answer comes back
 * when it is done.</p>
 */
final class QuestBoardCommand {

    private final Plugin plugin;
    private final QuestBoardStore store;
    private final QuestBoardService service;
    private final Supplier<Map<String, QuestDefinition>> templates;

    QuestBoardCommand(Plugin plugin, QuestBoardStore store, QuestBoardService service,
                      Supplier<Map<String, QuestDefinition>> templates) {
        this.plugin = plugin;
        this.store = store;
        this.service = service;
        this.templates = templates;
    }

    LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("board")
                .requires(source -> source.getSender().hasPermission(QuestBoardService.ADMIN_PERMISSION)
                        || source.getSender().hasPermission("arkcontent.admin"))
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("info").then(id().executes(this::info)))
                .then(Commands.literal("create").then(id().then(text("title").executes(this::create))))
                .then(Commands.literal("title").then(id().then(text("title").executes(context ->
                        edit(context, quest -> quest.withTitle(string(context, "title")), "title set")))))
                .then(Commands.literal("desc").then(id().then(text("line").executes(context ->
                        edit(context, quest -> {
                            List<String> lines = new ArrayList<>(quest.description());
                            lines.add(string(context, "line"));
                            return quest.withDescription(lines);
                        }, "line added")))))
                .then(Commands.literal("cleardesc").then(id().executes(context ->
                        edit(context, quest -> quest.withDescription(List.of()), "description cleared"))))
                .then(Commands.literal("icon").then(id().then(Commands.argument("item", StringArgumentType.greedyString())
                        .executes(context -> edit(context, quest -> quest.withIcon(string(context, "item").trim()),
                                "icon set")))))
                .then(Commands.literal("repeatable").then(id().then(Commands.argument("repeatable",
                                com.mojang.brigadier.arguments.BoolArgumentType.bool())
                        .executes(context -> edit(context, quest -> quest.withRepeatable(
                                com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "repeatable")),
                                "repeatable set")))))
                .then(Commands.literal("require").then(id()
                        .then(level("skill", (name, level) -> new Requirement.Skill(name, level)))
                        .then(level("job", (name, level) -> new Requirement.Job(name, level)))
                        .then(Commands.literal("permission").then(Commands.argument("node", StringArgumentType.word())
                                .executes(context -> edit(context, quest -> quest.withRequirement(
                                        new Requirement.Permission(string(context, "node"))), "requirement added"))))))
                .then(Commands.literal("objective").then(objectives(id())))
                .then(Commands.literal("reward").then(id()
                        .then(Commands.literal("money").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                .executes(context -> edit(context, quest -> quest.withReward(new Reward.Money(
                                        DoubleArgumentType.getDouble(context, "amount"))), "reward added"))))
                        .then(Commands.literal("item").then(text("item").executes(context -> {
                            Counted item = counted(context, "item", 2304, true);
                            return item == null ? 0 : edit(context,
                                    quest -> quest.withReward(new Reward.Item(item.name(), item.count())), "reward added");
                        })))
                        .then(Commands.literal("hand").executes(this::rewardHand))
                        .then(Commands.literal("command").then(text("command").executes(context ->
                                edit(context, quest -> quest.withReward(new Reward.Command(string(context, "command"))),
                                        "reward added"))))))
                .then(Commands.literal("clear").then(id().then(Commands.argument("what", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            List.of("requirements", "objectives", "rewards").forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(this::clear))))
                .then(Commands.literal("boards").then(id().then(text("boards").executes(context ->
                        edit(context, quest -> {
                            String raw = string(context, "boards").trim();
                            return quest.withBoards(raw.equalsIgnoreCase("all") ? List.of()
                                    : Arrays.stream(raw.split("[ ,]+")).filter(s -> !s.isBlank()).toList());
                        }, "boards set")))))
                .then(Commands.literal("post").then(id().executes(context -> post(context, true))))
                .then(Commands.literal("unpost").then(id().executes(context -> post(context, false))))
                .then(Commands.literal("delete").then(id().executes(this::delete)))
                .then(Commands.literal("reset").then(Commands.argument("player", StringArgumentType.word())
                        .then(id().executes(this::reset))));
    }

    // ---------------------------------------------------------------- arguments

    private ArgumentBuilder<CommandSourceStack, ?> id() {
        return Commands.argument("id", StringArgumentType.word()).suggests(ids());
    }

    private SuggestionProvider<CommandSourceStack> ids() {
        return (context, builder) -> {
            String typed = builder.getRemainingLowerCase();
            List<String> all = new ArrayList<>(service.noticeIds());
            all.addAll(templates.get().keySet());
            all.stream().distinct().filter(id -> id.startsWith(typed)).sorted().forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private static ArgumentBuilder<CommandSourceStack, ?> text(String name) {
        return Commands.argument(name, StringArgumentType.greedyString());
    }

    private static String string(CommandContext<CommandSourceStack> context, String name) {
        return StringArgumentType.getString(context, name);
    }

    private interface LevelRequirement {
        Requirement make(String name, int level);
    }

    /** {@code skill <name> <level>}: the name may be namespaced, {@code auraskills/fighting}. */
    private LiteralArgumentBuilder<CommandSourceStack> level(String kind, LevelRequirement make) {
        return Commands.literal(kind).then(text("level").executes(context -> {
            Counted level = counted(context, "level", 100000, false);
            return level == null ? 0 : edit(context, quest -> quest.withRequirement(
                    make.make(level.name().toLowerCase(Locale.ROOT), level.count())), "requirement added");
        }));
    }

    private ArgumentBuilder<CommandSourceStack, ?> objectives(ArgumentBuilder<CommandSourceStack, ?> id) {
        for (Kind kind : Kind.values()) {
            // <target> <amount>: a target is demo:ruby, coal_ore, zombie or mythicmobs:SkeletonKing.
            id.then(Commands.literal(kind.key()).then(text("target").executes(context -> {
                Counted target = counted(context, "target", 100000, kind == Kind.DELIVER);
                return target == null ? 0 : edit(context, quest -> {
                    if (quest.objectives().size() >= QuestDefinition.MAX_OBJECTIVES) {
                        throw new IllegalArgumentException("a notice has at most "
                                + QuestDefinition.MAX_OBJECTIVES + " objectives");
                    }
                    return quest.withObjective(new Objective(kind, target.name(), target.count()));
                }, "objective added");
            })));
        }
        return id;
    }

    /**
     * A name and a count typed at the end of a command, read on the server thread before anything
     * is sent to the database; null, with the reason told, when it does not read.
     *
     * @param item whether the name must be an item: a deliver objective's, an item reward's
     */
    private @Nullable Counted counted(CommandContext<CommandSourceStack> context, String argument, int max, boolean item) {
        CommandSender sender = context.getSource().getSender();
        Counted counted;
        try {
            counted = Counted.parse(string(context, argument), max);
        } catch (IllegalArgumentException unreadable) {
            sender.sendMessage(Component.text("Not done: " + unreadable.getMessage(), NamedTextColor.RED));
            return null;
        }
        if (item && !service.knownItem(counted.name())) {
            sender.sendMessage(Component.text("Not done: '" + counted.name() + "' is no item - a custom item's id,"
                    + " as demo:ruby, or a vanilla one, as cobblestone.", NamedTextColor.RED));
            return null;
        }
        return counted;
    }

    // ---------------------------------------------------------------- commands

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        store.allNotices().whenComplete((board, error) -> onMain(() -> {
            if (error != null) {
                failed(sender, error);
                return;
            }
            sender.sendMessage(Component.text(board.notices().size() + " notice(s):", NamedTextColor.GOLD));
            for (QuestBoardStore.Notice notice : board.notices()) {
                QuestDefinition quest = notice.quest();
                sender.sendMessage(Component.text((notice.posted() ? " [posted] " : " [draft]  ") + quest.id() + " - "
                        + QuestBoardService.plain(quest.title()) + " (" + quest.objectives().size() + " objective(s), "
                        + quest.rewards().size() + " reward(s))", notice.posted() ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            }
            board.unreadable().forEach(row -> sender.sendMessage(Component.text(" [unreadable] " + row.id() + ": "
                    + row.reason(), NamedTextColor.RED)));
            if (!templates.get().isEmpty()) {
                sender.sendMessage(Component.text("Templates in contents/ (post to copy one in): "
                        + String.join(", ", templates.get().keySet().stream().sorted().toList()), NamedTextColor.GRAY));
            }
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String id = string(context, "id");
        store.notice(id).whenComplete((notice, error) -> onMain(() -> {
            if (error != null) {
                failed(sender, error);
            } else if (notice.isEmpty()) {
                sender.sendMessage(Component.text("No notice '" + id + "'.", NamedTextColor.RED));
            } else {
                sender.sendMessage(Component.text(id + (notice.get().posted() ? " (posted)" : " (draft)") + ": "
                        + QuestJson.write(notice.get().quest()), NamedTextColor.GRAY));
            }
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int create(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String id = string(context, "id").toLowerCase(Locale.ROOT);
        if (!QuestDefinition.ID.matcher(id).matches()) {
            sender.sendMessage(Component.text("A notice id is a-z 0-9 _ - . only.", NamedTextColor.RED));
            return 0;
        }
        QuestDefinition draft = QuestDefinition.draft(id, string(context, "title"));
        store.create(draft, sender.getName()).whenComplete((created, error) -> onMain(() -> {
            if (error != null) {
                failed(sender, error);
                return;
            }
            if (!created) {
                sender.sendMessage(Component.text("Not done: there is a notice '" + id + "' already.",
                        NamedTextColor.RED));
                return;
            }
            service.remember(draft);
            sender.sendMessage(Component.text("Notice '" + id + "' created, not posted yet. Add objectives and"
                    + " rewards, then /arkcontent board post " + id + ".", NamedTextColor.GREEN));
        }));
        return Command.SINGLE_SUCCESS;
    }

    /** Reads a notice, changes it and writes it back, in one task on the database's thread, and reports. */
    private int edit(CommandContext<CommandSourceStack> context, Function<QuestDefinition, QuestDefinition> change,
                     String done) {
        CommandSender sender = context.getSource().getSender();
        String id = string(context, "id");
        store.edit(id, change::apply).whenComplete((changed, error) -> onMain(() -> {
            if (error != null) {
                failed(sender, error);
                return;
            }
            service.remember(changed);
            sender.sendMessage(Component.text("'" + id + "': " + done + ".", NamedTextColor.GREEN));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int rewardHand(CommandContext<CommandSourceStack> context) {
        Entity executor = context.getSource().getExecutor();
        Player player = executor instanceof Player p ? p : context.getSource().getSender() instanceof Player p2 ? p2 : null;
        if (player == null || player.getInventory().getItemInMainHand().isEmpty()) {
            context.getSource().getSender().sendMessage(Component.text("Hold the item to pay in your main hand.",
                    NamedTextColor.RED));
            return 0;
        }
        ItemStack held = player.getInventory().getItemInMainHand().clone();
        Reward.Stack reward = QuestBoardService.stackReward(held);
        return edit(context, quest -> quest.withReward(reward), "pays " + reward.label());
    }

    private int clear(CommandContext<CommandSourceStack> context) {
        String what = string(context, "what").toLowerCase(Locale.ROOT);
        return edit(context, quest -> switch (what) {
            case "requirements" -> new QuestDefinition(quest.id(), quest.title(), quest.description(), quest.icon(),
                    List.of(), quest.objectives(), quest.rewards(), quest.repeatable(), quest.boards());
            case "objectives" -> new QuestDefinition(quest.id(), quest.title(), quest.description(), quest.icon(),
                    quest.requirements(), List.of(), quest.rewards(), quest.repeatable(), quest.boards());
            case "rewards" -> new QuestDefinition(quest.id(), quest.title(), quest.description(), quest.icon(),
                    quest.requirements(), quest.objectives(), List.of(), quest.repeatable(), quest.boards());
            default -> throw new IllegalArgumentException("clear requirements, objectives or rewards");
        }, what + " cleared");
    }

    private int post(CommandContext<CommandSourceStack> context, boolean posted) {
        CommandSender sender = context.getSource().getSender();
        String id = string(context, "id");
        // A template from contents/ not in the database yet is copied in, then posted.
        QuestDefinition template = templates.get().get(id);
        store.post(id, posted, template, sender.getName()).whenComplete((quest, error) -> onMain(() -> {
            if (error != null) {
                failed(sender, error);
                return;
            }
            service.remember(quest);
            sender.sendMessage(Component.text("'" + id + "' " + (posted ? "is posted on the boards." : "is taken down."),
                    NamedTextColor.GREEN));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private int delete(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String id = string(context, "id");
        store.delete(id).whenComplete((existed, error) -> onMain(() -> {
            if (error != null) {
                failed(sender, error);
                return;
            }
            service.forget(id);
            sender.sendMessage(existed ? Component.text("'" + id + "' deleted, with everyone's progress.", NamedTextColor.GREEN)
                    : Component.text("No notice '" + id + "'.", NamedTextColor.RED));
        }));
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("deprecation")
    private int reset(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String id = string(context, "id");
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(string(context, "player"));
        store.reset(player.getUniqueId(), id).whenComplete((existed, error) -> onMain(() -> {
            if (error != null) {
                failed(sender, error);
                return;
            }
            Player online = player.getPlayer();
            if (online != null) {
                service.left(online);
                service.joined(online);
            }
            sender.sendMessage(Component.text(existed ? player.getName() + "'s progress with '" + id + "' is forgotten."
                    : player.getName() + " never took '" + id + "'.", existed ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }));
        return Command.SINGLE_SUCCESS;
    }

    private void onMain(Runnable task) {
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    private static void failed(CommandSender sender, Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && (cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)) {
            cause = cause.getCause();
        }
        sender.sendMessage(Component.text("Not done: " + cause.getMessage(), NamedTextColor.RED));
    }
}
