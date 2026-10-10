package com.arkcronist.content.bukkit.quest;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.core.menu.Page;
import com.arkcronist.content.core.quest.QuestDefinition;
import com.arkcronist.content.core.quest.QuestDefinition.Kind;
import com.arkcronist.content.core.quest.QuestDefinition.Objective;
import com.arkcronist.content.core.quest.QuestDefinition.Requirement;
import com.arkcronist.content.core.quest.QuestDefinition.Reward;
import com.arkcronist.content.core.quest.QuestProgress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * One player's view of a quest board: the notices posted on it, 45 to a page, over the board's
 * parchment picture.
 *
 * <pre>
 *   rows 1-5   the notices: what each asks, of whom, what it pays, and where the player stands
 *   row 6      [prev] [board] [mine] [ ] [info] [ ] [ ] [close] [next]
 * </pre>
 *
 * <p>A click takes a notice, or hands in one the player has taken; a shift-click on a taken notice
 * gives it up. The notices were read from the database when the board was opened; the player's
 * progress is the one kept in memory while they are online. Main thread.</p>
 */
final class QuestBoardMenu implements InventoryHolder {

    static final int SIZE = 54;
    static final int PAGE_SIZE = 45;
    static final int PREVIOUS = 45;
    static final int BOARD = 46;
    static final int MINE = 47;
    static final int INFO = 49;
    static final int CLOSE = 52;
    static final int NEXT = 53;

    private static final TextColor INK = TextColor.color(0x4a3420);

    enum Outcome { NOTHING, CLOSE }

    private final QuestBoardService service;
    private final String boardId;
    private final List<QuestDefinition> posted;
    private final Inventory inventory;
    private final QuestDefinition[] shown = new QuestDefinition[PAGE_SIZE];
    private boolean mine;
    private int page;
    private int pages = 1;

    QuestBoardMenu(QuestBoardService service, String boardId, List<QuestDefinition> posted, Component title) {
        this.service = service;
        this.boardId = boardId;
        this.posted = List.copyOf(posted);
        this.inventory = Bukkit.createInventory(this, SIZE, title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** Draws the board again if the player still has it open. */
    void refresh(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder(false) == this) {
            render(player);
        }
    }

    void render(Player player) {
        List<QuestDefinition> listed = mine ? service.active(player) : posted;
        Page<QuestDefinition> current = Page.of(listed, page, PAGE_SIZE);
        page = current.index();
        pages = current.count();
        inventory.clear();
        Arrays.fill(shown, null);
        for (int slot = 0; slot < current.entries().size(); slot++) {
            QuestDefinition quest = current.entries().get(slot);
            shown[slot] = quest;
            inventory.setItem(slot, notice(player, quest));
        }
        if (listed.isEmpty()) {
            inventory.setItem(22, button(Material.MAP, mine ? "You have no notices under way" : "No notices posted",
                    NamedTextColor.GRAY, false, List.of(mine ? "Take one from the board." : "Come back later.")));
        }
        if (current.hasPrevious()) {
            inventory.setItem(PREVIOUS, button(Material.ARROW, "Previous page", NamedTextColor.YELLOW, false, List.of()));
        }
        if (current.hasNext()) {
            inventory.setItem(NEXT, button(Material.ARROW, "Next page", NamedTextColor.YELLOW, false, List.of()));
        }
        inventory.setItem(BOARD, button(Material.OAK_SIGN, "The board (" + posted.size() + ")",
                mine ? NamedTextColor.WHITE : NamedTextColor.GOLD, !mine, List.of("Notices posted here")));
        int active = service.active(player).size();
        inventory.setItem(MINE, button(Material.WRITABLE_BOOK, "My notices (" + active + ")",
                mine ? NamedTextColor.GOLD : NamedTextColor.WHITE, mine, List.of("Notices you have taken")));
        inventory.setItem(INFO, button(Material.BOOK, "Page " + (current.index() + 1) + " of " + current.count(),
                NamedTextColor.AQUA, false, List.of("Click a notice to take it,", "or to hand it in once done.",
                        "Shift-click a taken notice", "to give it up.")));
        inventory.setItem(CLOSE, button(Material.BARRIER, "Close", NamedTextColor.RED, false, List.of()));
    }

    Outcome click(Player player, int slot, ClickType click) {
        if (slot >= 0 && slot < PAGE_SIZE) {
            QuestDefinition quest = shown[slot];
            if (quest == null || click == ClickType.DOUBLE_CLICK) {
                return Outcome.NOTHING;
            }
            QuestProgress row = service.progress(player, quest.id());
            if (row != null && row.active()) {
                if (click.isShiftClick()) {
                    service.abandon(player, quest, this);
                } else {
                    service.handIn(player, quest, this);
                }
            } else {
                service.take(player, quest, this);
            }
            return Outcome.NOTHING;
        }
        if (slot == BOARD && mine) {
            mine = false;
            page = 0;
        } else if (slot == MINE && !mine) {
            mine = true;
            page = 0;
        } else if (slot == PREVIOUS && page > 0) {
            page--;
        } else if (slot == NEXT && page < pages - 1) {
            page++;
        } else if (slot == CLOSE) {
            return Outcome.CLOSE;
        } else {
            return Outcome.NOTHING;
        }
        player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.1f);
        render(player);
        return Outcome.NOTHING;
    }

    /** A notice as the board shows it. */
    private ItemStack notice(Player player, QuestDefinition quest) {
        QuestItems items = service.items();
        ItemStack stack;
        Optional<CustomItem> custom = quest.icon() == null ? Optional.empty() : items.custom(quest.icon());
        Material material = quest.icon() == null ? null : QuestItems.vanilla(quest.icon());
        if (custom.isPresent()) {
            stack = items.stacks(quest.icon(), 1).getFirst();
        } else {
            stack = ItemStack.of(material != null ? material : Material.PAPER);
        }
        QuestProgress row = service.progress(player, quest.id());
        boolean taken = row != null && row.active();
        List<String> missing = taken ? service.missing(player, quest, row) : List.of();
        List<Component> lore = new ArrayList<>();
        for (String line : quest.description()) {
            lore.add(text(MiniMessage.miniMessage().deserialize(line)).colorIfAbsent(NamedTextColor.GRAY));
        }
        if (!quest.requirements().isEmpty()) {
            gap(lore);
            lore.add(line("Requirements", NamedTextColor.GOLD));
            for (Requirement requirement : quest.requirements()) {
                QuestBoardService.Check check = service.check(player, requirement);
                lore.add(line((check.met() ? " ✔ " : " ✘ ") + check.text(),
                        check.met() ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
        }
        gap(lore);
        lore.add(line("Objectives", NamedTextColor.GOLD));
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective objective = quest.objectives().get(i);
            String what = service.target(objective);
            String text = switch (objective.kind()) {
                case DELIVER -> " • Bring " + objective.amount() + " " + what
                        + " (you have " + Math.min(items.count(player, objective.target()), 9999) + ")";
                case KILL -> " • Kill " + objective.amount() + " " + what
                        + (taken ? ": " + row.count(i) + "/" + objective.amount() : "");
                case BREAK -> " • Break " + objective.amount() + " " + what
                        + (taken ? ": " + row.count(i) + "/" + objective.amount() : "");
            };
            boolean done = taken && (objective.kind() == Kind.DELIVER
                    ? items.count(player, objective.target()) >= objective.amount() : row.count(i) >= objective.amount());
            lore.add(line(text, done ? NamedTextColor.GREEN : NamedTextColor.WHITE));
        }
        if (!quest.rewards().isEmpty()) {
            gap(lore);
            lore.add(line("Rewards", NamedTextColor.GOLD));
            boolean more = false;
            for (Reward reward : quest.rewards()) {
                switch (reward) {
                    case Reward.Money money -> lore.add(line(" • " + service.money(money.amount()), NamedTextColor.YELLOW));
                    case Reward.Item item -> lore.add(line(" • " + item.amount() + " x " + items.name(item.item()),
                            NamedTextColor.YELLOW));
                    case Reward.Stack exact -> lore.add(line(" • " + exact.label(), NamedTextColor.YELLOW));
                    case Reward.Command ignored -> more = true;
                }
            }
            if (more) {
                lore.add(line(" • ...and something more", NamedTextColor.YELLOW));
            }
        }
        gap(lore);
        boolean ready = taken && missing.isEmpty();
        if (ready) {
            lore.add(line("Click to hand it in!", NamedTextColor.GREEN));
        } else if (taken) {
            lore.add(line("Taken - still to do: " + String.join(", ", missing), NamedTextColor.YELLOW));
            lore.add(line("Shift-click to give it up", NamedTextColor.DARK_GRAY));
        } else if (row != null && !quest.repeatable()) {
            lore.add(line("Done", NamedTextColor.DARK_GREEN));
        } else {
            lore.add(line(row != null ? "Done " + row.completions() + " time(s) - click to take it again"
                    : "Click to take it", NamedTextColor.AQUA));
        }
        Component name = text(MiniMessage.miniMessage().deserialize(quest.title())).colorIfAbsent(INK);
        stack.editMeta(meta -> {
            meta.customName(name);
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(ready ? Boolean.TRUE : null);
        });
        stack.setAmount(1);
        return stack;
    }

    /** A blank line between sections - none above the first. */
    private static void gap(List<Component> lore) {
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
    }

    private static Component text(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private static Component line(String text, NamedTextColor colour) {
        return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack button(Material material, String name, NamedTextColor colour, boolean glint,
                                    List<String> lines) {
        ItemStack stack = ItemStack.of(material);
        stack.editMeta(meta -> {
            meta.itemName(Component.text(name, colour));
            if (!lines.isEmpty()) {
                meta.lore(lines.stream().map(line -> line(line, NamedTextColor.GRAY)).toList());
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return stack;
    }

    String boardId() {
        return boardId;
    }
}
