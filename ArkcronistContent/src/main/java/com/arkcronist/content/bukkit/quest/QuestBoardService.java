package com.arkcronist.content.bukkit.quest;

import com.arkcronist.content.bukkit.EngineSettings;
import com.arkcronist.content.bukkit.hooks.HookManager;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.bukkit.menu.ContentMenus;
import com.arkcronist.content.bukkit.menu.Shop;
import com.arkcronist.content.bukkit.text.FontImageRegistry;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.quest.QuestBoardStore;
import com.arkcronist.content.core.quest.QuestDefinition;
import com.arkcronist.content.core.quest.QuestDefinition.Kind;
import com.arkcronist.content.core.quest.QuestDefinition.Objective;
import com.arkcronist.content.core.quest.QuestDefinition.Requirement;
import com.arkcronist.content.core.quest.QuestDefinition.Reward;
import com.arkcronist.content.core.quest.QuestProgress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The quest boards: what a right click on one shows, what taking and handing in a notice does, and
 * how kills and broken blocks count towards the notices players have taken.
 *
 * <h2>Threads</h2>
 * <p>Every read and write of the notices and of players' progress runs on the database's thread
 * ({@link QuestBoardStore}); this class only queues them and carries on when they answer, on the
 * server thread. A board is opened once its notices and the player's progress have been read. What
 * must happen on the server thread does: checking requirements against other plugins, counting and
 * taking items from an inventory, paying money and items.</p>
 *
 * <h2>Nothing paid twice</h2>
 * <p>Handing a notice in takes the items first, then asks the database to mark the notice done -
 * a statement that only succeeds while it is still under way - and pays only once it has. Should
 * it fail, the items go back. A second click on the same notice waits for the first to finish.</p>
 *
 * <h2>Counting</h2>
 * <p>Kills and broken blocks count in memory, for players online, and are written every
 * {@code save-seconds}, when the player leaves, and when the notice is handed in.</p>
 */
public final class QuestBoardService {

    /** Builds, posts and removes notices: {@code /arkcontent board}. */
    public static final String ADMIN_PERMISSION = "arkcontent.board.admin";
    /** Taking notices from a board. */
    public static final String USE_PERMISSION = "arkcontent.board";

    private final Plugin plugin;
    private final QuestBoardStore store;
    private final QuestItems items;
    private final HookManager hooks;
    private final EngineSettings.QuestBoards settings;
    private final FontImageRegistry fontImages;
    private final Logger logger;

    /** Notices known, by id: read at start, then with every board opened and every admin change. */
    private final Map<String, QuestDefinition> notices = new HashMap<>();
    /** Online players' progress with each notice they ever took. */
    private final Map<UUID, Map<String, QuestProgress>> progress = new HashMap<>();
    /** Progress counted and not written yet, by player and notice. */
    private final Map<UUID, Set<String>> dirty = new HashMap<>();
    /** Take or hand-in clicks the database has not answered yet: player and notice. */
    private final Set<String> pending = new HashSet<>();
    private @Nullable BukkitTask saver;

    public QuestBoardService(Plugin plugin, QuestBoardStore store, ItemRegistry registry, ItemFactory factory,
                             HookManager hooks, EngineSettings.QuestBoards settings, FontImageRegistry fontImages) {
        this.plugin = plugin;
        this.store = store;
        this.items = new QuestItems(registry, factory);
        this.hooks = hooks;
        this.settings = settings;
        this.fontImages = fontImages;
        this.logger = plugin.getLogger();
    }

    /** Reads the notices and online players' progress, and starts the periodic save. */
    public void start() {
        refreshNotices();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            joined(player);
        }
        long period = settings.saveSeconds() * 20L;
        saver = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> saveDirty(), period, period);
    }

    /** On disable: what was counted is queued for writing, ahead of the database closing. */
    public void stop() {
        if (saver != null) {
            saver.cancel();
            saver = null;
        }
        saveDirty();
    }

    /**
     * On disable: open boards are shut. Their listener is about to go, and a board left open would
     * be an ordinary chest whose notices could be taken out.
     */
    public void closeAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof QuestBoardMenu) {
                player.closeInventory();
            }
        }
    }

    /** Reads every notice into memory, for counting kills and blocks against them. */
    public void refreshNotices() {
        store.allNotices().whenComplete((board, error) -> onMain(() -> {
            if (error != null) {
                logged(error, "read the quest board notices");
                return;
            }
            notices.clear();
            board.notices().forEach(notice -> notices.put(notice.quest().id(), notice.quest()));
            board.unreadable().forEach(row -> logger.warning("Quest notice '" + row.id() + "' cannot be read and is"
                    + " left out: " + row.reason()));
        }));
    }

    /** A notice changed or appeared: kept in memory at once. */
    public void remember(QuestDefinition quest) {
        notices.put(quest.id(), quest);
    }

    public void forget(String id) {
        notices.remove(id);
        progress.values().forEach(map -> map.remove(id));
    }

    // ---------------------------------------------------------------- players

    public void joined(Player player) {
        UUID id = player.getUniqueId();
        store.progress(id).whenComplete((rows, error) -> onMain(() -> {
            if (error != null) {
                logged(error, "read the quest progress of " + player.getName());
                return;
            }
            if (plugin.getServer().getPlayer(id) == null) {
                return;
            }
            // Counted meanwhile, before the rows came: kept over what was read.
            Map<String, QuestProgress> mine = progress.computeIfAbsent(id, ignored -> new LinkedHashMap<>());
            rows.forEach(mine::putIfAbsent);
        }));
    }

    public void left(Player player) {
        saveDirty(player.getUniqueId());
        progress.remove(player.getUniqueId());
    }

    private Map<String, QuestProgress> progressOf(Player player) {
        return progress.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashMap<>());
    }

    // ---------------------------------------------------------------- opening a board

    /**
     * Opens a board for {@code player}, once its notices and the player's progress have been read.
     *
     * @param board the board furniture: notices naming boards hang only on the ones they name
     */
    public void open(Player player, Block support, CustomItem board, Placement.QuestBoard spec) {
        if (!player.hasPermission(USE_PERMISSION)) {
            player.sendActionBar(Component.text("You may not use quest boards", NamedTextColor.RED));
            return;
        }
        UUID id = player.getUniqueId();
        Component title = spec.title() != null ? MiniMessage.miniMessage().deserialize(spec.title())
                : board.displayName() != null ? board.displayName() : Component.text("Quest Board");
        player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1f);
        store.board(id).whenComplete((read, error) -> onMain(() -> {
            Player online = plugin.getServer().getPlayer(id);
            if (online == null) {
                return;
            }
            if (error != null) {
                logged(error, "read the quest board for " + online.getName());
                online.sendActionBar(Component.text("The quest board cannot be read right now", NamedTextColor.RED));
                return;
            }
            read.notices().forEach(notice -> notices.put(notice.quest().id(), notice.quest()));
            Map<String, QuestProgress> mine = progressOf(online);
            // What the database says, unless counted here since: memory is newer for what is under way.
            read.progress().forEach((quest, row) -> {
                QuestProgress known = mine.get(quest);
                if (known == null || !known.active() || !row.active()) {
                    mine.put(quest, row);
                }
            });
            List<QuestDefinition> posted = read.notices().stream().map(QuestBoardStore.Notice::quest)
                    .filter(quest -> quest.on(board.id())).toList();
            QuestBoardMenu menu = new QuestBoardMenu(this, board.id(), posted, ContentMenus.textured(fontImages, true,
                    settings.background(), settings.backgroundWidth(), title));
            menu.render(online);
            online.openInventory(menu.getInventory());
        }));
    }

    /** The online player's progress with a notice, or null. */
    @Nullable QuestProgress progress(Player player, String questId) {
        return progressOf(player).get(questId);
    }

    /** The notices the player has under way, from any board. */
    List<QuestDefinition> active(Player player) {
        List<QuestDefinition> active = new ArrayList<>();
        progressOf(player).forEach((id, row) -> {
            QuestDefinition quest = notices.get(id);
            if (row.active() && quest != null) {
                active.add(quest);
            }
        });
        return active;
    }

    QuestItems items() {
        return items;
    }

    /** Whether {@code name} is an item a notice can ask for or pay: a custom item's id, or a vanilla material. Main thread. */
    public boolean knownItem(String name) {
        return items.known(name);
    }

    // ---------------------------------------------------------------- requirements

    /** One requirement, as it stands for a player. */
    record Check(boolean met, String text) {
    }

    Check check(Player player, Requirement requirement) {
        return switch (requirement) {
            case Requirement.Permission permission -> new Check(player.hasPermission(permission.node()),
                    "Permission " + permission.node());
            case Requirement.Skill skill -> level(hooks.skillLevel(player, skill.skill()),
                    QuestItems.words(skill.skill().substring(skill.skill().indexOf('/') + 1)), skill.level(),
                    "AuraSkills");
            case Requirement.Job job -> level(hooks.jobLevel(player, job.job()),
                    QuestItems.words(job.job()) + " (job)", job.level(), "ExcellentJobs or Jobs Reborn");
        };
    }

    private static Check level(OptionalInt level, String what, int wanted, String needs) {
        if (level.isEmpty()) {
            return new Check(false, what + " " + wanted + " - unknown: needs " + needs);
        }
        return new Check(level.getAsInt() >= wanted, what + " level " + wanted + " (you: " + level.getAsInt() + ")");
    }

    // ---------------------------------------------------------------- taking a notice

    void take(Player player, QuestDefinition quest, QuestBoardMenu menu) {
        String key = player.getUniqueId() + ":" + quest.id();
        if (pending.contains(key)) {
            return;
        }
        QuestProgress known = progressOf(player).get(quest.id());
        if (known != null && known.active()) {
            say(player, "You have taken this notice already", NamedTextColor.YELLOW);
            return;
        }
        if (known != null && !quest.repeatable()) {
            say(player, "You have done this one already", NamedTextColor.YELLOW);
            return;
        }
        for (Requirement requirement : quest.requirements()) {
            Check check = check(player, requirement);
            if (!check.met()) {
                say(player, "Not yet: " + check.text(), NamedTextColor.RED);
                return;
            }
        }
        long under = progressOf(player).values().stream().filter(QuestProgress::active).count();
        if (under >= settings.maxActive()) {
            say(player, "You have " + under + " notices under way already - hand one in first", NamedTextColor.RED);
            return;
        }
        pending.add(key);
        QuestProgress fresh = QuestProgress.accepted(player.getUniqueId(), quest, System.currentTimeMillis(),
                known == null ? 0 : known.completions());
        UUID id = player.getUniqueId();
        store.accept(fresh, quest.repeatable()).whenComplete((taken, error) -> onMain(() -> {
            pending.remove(key);
            Player online = plugin.getServer().getPlayer(id);
            if (error != null) {
                logged(error, "record a notice taken");
                if (online != null) {
                    say(online, "The notice could not be taken - try again", NamedTextColor.RED);
                }
                return;
            }
            if (online == null) {
                return;
            }
            switch (taken) {
                case TAKEN -> {
                    progressOf(online).put(quest.id(), fresh);
                    online.playSound(online, Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.8f, 1f);
                    say(online, "Taken: " + plain(quest.title()), NamedTextColor.GREEN);
                }
                case ALREADY -> say(online, "You have taken this notice already", NamedTextColor.YELLOW);
                case DONE -> say(online, "You have done this one already", NamedTextColor.YELLOW);
            }
            menu.refresh(online);
        }));
    }

    // ---------------------------------------------------------------- handing in

    /** What is still missing to hand a notice in; empty when it can be. */
    List<String> missing(Player player, QuestDefinition quest, QuestProgress row) {
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective objective = quest.objectives().get(i);
            if (objective.kind() == Kind.DELIVER) {
                int have = items.count(player, objective.target());
                if (have < objective.amount()) {
                    missing.add((objective.amount() - have) + " more " + items.name(objective.target()));
                }
            } else if (row.count(i) < objective.amount()) {
                missing.add((objective.amount() - row.count(i)) + " more " + target(objective));
            }
        }
        return missing;
    }

    void handIn(Player player, QuestDefinition quest, QuestBoardMenu menu) {
        String key = player.getUniqueId() + ":" + quest.id();
        if (pending.contains(key)) {
            return;
        }
        QuestProgress row = progressOf(player).get(quest.id());
        if (row == null || !row.active()) {
            say(player, "You have not taken this notice", NamedTextColor.YELLOW);
            return;
        }
        List<String> missing = missing(player, quest, row);
        if (!missing.isEmpty()) {
            say(player, "Not done yet: " + String.join(", ", missing), NamedTextColor.RED);
            return;
        }
        // The items are taken now, on this tick, and given back should the database refuse.
        List<ItemStack> taken = new ArrayList<>();
        for (Objective objective : quest.objectives()) {
            if (objective.kind() == Kind.DELIVER) {
                taken.addAll(items.take(player, objective.target(), objective.amount()));
            }
        }
        pending.add(key);
        QuestProgress done = row.completed(System.currentTimeMillis());
        UUID id = player.getUniqueId();
        store.complete(done).whenComplete((completed, error) -> onMain(() -> {
            pending.remove(key);
            Player online = plugin.getServer().getPlayer(id);
            if (error != null || !Boolean.TRUE.equals(completed)) {
                if (error != null) {
                    logged(error, "record a notice handed in");
                }
                if (online != null) {
                    QuestItems.give(online, taken);
                    say(online, "The notice could not be handed in - your items are back", NamedTextColor.RED);
                    menu.refresh(online);
                } else {
                    logger.warning("A player left while handing in '" + quest.id() + "', and it was refused; the "
                            + taken.size() + " stack(s) they handed in could not be given back.");
                }
                return;
            }
            if (online == null) {
                logger.warning("A player left while handing in '" + quest.id() + "'; it is recorded as done, and"
                        + " only its money and commands could be paid.");
            }
            pay(online, id, quest);
            if (online != null) {
                progressOf(online).put(quest.id(), done);
                Set<String> unsaved = dirty.get(id);
                if (unsaved != null) {
                    unsaved.remove(quest.id());
                }
                online.playSound(online, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                say(online, "Done: " + plain(quest.title()), NamedTextColor.GOLD);
                menu.refresh(online);
            }
        }));
    }

    /** Pays a notice's rewards. Money and commands reach a player who has left; items need them here. */
    private void pay(@Nullable Player player, UUID id, QuestDefinition quest) {
        String name = player != null ? player.getName() : plugin.getServer().getOfflinePlayer(id).getName();
        for (Reward reward : quest.rewards()) {
            switch (reward) {
                case Reward.Money money -> {
                    Shop shop = hooks.shop();
                    if (player == null || shop == null || !shop.deposit(player, money.amount())) {
                        logger.warning("Quest '" + quest.id() + "' pays " + money.amount() + " to " + name
                                + ", which could not be paid: it needs Vault and an economy plugin, and the player"
                                + " online.");
                        if (player != null) {
                            say(player, "Your money reward could not be paid - tell an admin", NamedTextColor.RED);
                        }
                    }
                }
                case Reward.Item item -> {
                    if (player != null) {
                        List<ItemStack> stacks = items.stacks(item.item(), item.amount());
                        if (stacks.isEmpty()) {
                            logger.warning("Quest '" + quest.id() + "' pays item '" + item.item() + "', which is not"
                                    + " loaded - not paid.");
                        }
                        QuestItems.give(player, stacks);
                    }
                }
                case Reward.Stack stack -> {
                    ItemStack read = QuestItems.stack(stack);
                    if (read == null) {
                        logger.warning("Quest '" + quest.id() + "' pays an item whose saved bytes cannot be read.");
                    } else if (player != null) {
                        QuestItems.give(player, List.of(read));
                    }
                }
                case Reward.Command command -> {
                    if (name != null) {
                        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                                command.command().replace("%player%", name));
                    }
                }
            }
        }
    }

    /** Gives up a notice under way. */
    void abandon(Player player, QuestDefinition quest, QuestBoardMenu menu) {
        String key = player.getUniqueId() + ":" + quest.id();
        if (pending.contains(key)) {
            return;
        }
        pending.add(key);
        UUID id = player.getUniqueId();
        store.abandon(id, quest.id()).thenCompose(ignored -> store.progress(id))
                .whenComplete((rows, error) -> onMain(() -> {
                    pending.remove(key);
                    Player online = plugin.getServer().getPlayer(id);
                    if (online == null) {
                        return;
                    }
                    if (error != null) {
                        logged(error, "give up a notice");
                        return;
                    }
                    Map<String, QuestProgress> mine = progressOf(online);
                    mine.remove(quest.id());
                    QuestProgress row = rows.get(quest.id());
                    if (row != null) {
                        mine.put(quest.id(), row);
                    }
                    say(online, "Given up: " + plain(quest.title()), NamedTextColor.GRAY);
                    menu.refresh(online);
                }));
    }

    // ---------------------------------------------------------------- counting

    /**
     * Something happened that kill or break objectives may count: {@code names} are what it is
     * called - {@code zombie}, {@code mythicmobs:de_umbraxis}, {@code coal_ore}, {@code demo:ruby_block}.
     */
    public void record(Player player, Kind kind, Collection<String> names) {
        Map<String, QuestProgress> mine = progress.get(player.getUniqueId());
        if (mine == null || mine.isEmpty()) {
            return;
        }
        for (Map.Entry<String, QuestProgress> entry : mine.entrySet()) {
            QuestProgress row = entry.getValue();
            QuestDefinition quest = notices.get(entry.getKey());
            if (quest == null || !row.active()) {
                continue;
            }
            QuestProgress counted = row.record(quest, kind, names, 1);
            if (counted == row) {
                continue;
            }
            entry.setValue(counted);
            dirty.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(quest.id());
            for (int i = 0; i < quest.objectives().size(); i++) {
                Objective objective = quest.objectives().get(i);
                if (objective.kind() == kind && counted.count(i) != row.count(i)) {
                    player.sendActionBar(Component.text(plain(quest.title()) + ": " + counted.count(i) + "/"
                            + objective.amount() + " " + target(objective), counted.count(i) >= objective.amount()
                            ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                }
            }
        }
    }

    private void saveDirty() {
        for (UUID id : List.copyOf(dirty.keySet())) {
            saveDirty(id);
        }
    }

    private void saveDirty(UUID id) {
        Set<String> quests = dirty.remove(id);
        Map<String, QuestProgress> mine = progress.get(id);
        if (quests == null || quests.isEmpty() || mine == null) {
            return;
        }
        List<QuestProgress> rows = new ArrayList<>();
        quests.forEach(quest -> {
            QuestProgress row = mine.get(quest);
            if (row != null && row.active()) {
                rows.add(row);
            }
        });
        if (!rows.isEmpty()) {
            store.saveCounts(rows).exceptionally(error -> {
                logged(error, "save quest progress");
                return null;
            });
        }
    }

    /** A creature's MythicMobs type, or null. */
    @Nullable String mythicType(org.bukkit.entity.Entity entity) {
        return hooks.mythicType(entity);
    }

    // ---------------------------------------------------------------- words

    /** An amount of money as the economy writes it. */
    String money(double amount) {
        Shop shop = hooks.shop();
        return shop != null ? shop.format(amount) : String.format(java.util.Locale.ROOT, "%.2f", amount);
    }

    /** An objective's target as players read it. */
    String target(Objective objective) {
        String target = objective.target();
        if (target.toLowerCase(java.util.Locale.ROOT).startsWith("mythicmobs:")) {
            return target.substring("mythicmobs:".length()).replace('_', ' ');
        }
        return items.known(target) ? items.name(target) : QuestItems.words(target.contains(":")
                ? target.substring(target.indexOf(':') + 1) : target);
    }

    /** The ids of the notices known, for suggestions. */
    public java.util.Set<String> noticeIds() {
        return java.util.Set.copyOf(notices.keySet());
    }

    /** An exact stack as a notice's reward. */
    public static Reward.Stack stackReward(ItemStack stack) {
        return QuestItems.reward(stack);
    }

    /** MiniMessage as plain text. */
    public static String plain(String miniMessage) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    private static void say(Player player, String text, NamedTextColor colour) {
        player.sendActionBar(Component.text(text, colour));
    }

    private void onMain(Runnable task) {
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    private void logged(Throwable error, String what) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        logger.log(Level.WARNING, "Could not " + what + ": " + cause.getMessage());
    }
}
