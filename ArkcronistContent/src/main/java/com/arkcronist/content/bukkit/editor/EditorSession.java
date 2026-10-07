package com.arkcronist.content.bukkit.editor;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.furniture.DisplayTransform;
import com.arkcronist.content.core.furniture.EditorLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One admin editing one piece of furniture: the values as they were when the menu opened, as they
 * are now, and the menu showing them. Main thread only.
 */
final class EditorSession implements InventoryHolder {

    /** Where a save goes. */
    enum Scope {
        /** The item's YAML file: every piece of this furniture. */
        TYPE,
        /** The database, for the block this piece stands on. */
        PIECE
    }

    final UUID player;
    final UUID display;
    final UUID world;
    final int x;
    final int y;
    final int z;
    final CustomItem item;
    final Placement.Furniture furniture;
    /** What the piece was drawn with when the menu opened. */
    final DisplayTransform opened;
    /** What its type's YAML says. */
    final DisplayTransform typeValues;
    final Scope openedScope;
    /** contents/-relative path of the item's file, for the menu. */
    final String file;

    DisplayTransform current;
    DisplayTransform.Part part = DisplayTransform.Part.TRANSLATION;
    Scope scope;
    /** Saved or cancelled: the close that follows does nothing more. */
    boolean finished;

    private final Inventory inventory;

    EditorSession(UUID player, UUID display, UUID world, int x, int y, int z, CustomItem item,
                  Placement.Furniture furniture, DisplayTransform opened, Scope scope, String file) {
        this.player = player;
        this.display = display;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.item = item;
        this.furniture = furniture;
        this.opened = opened;
        this.typeValues = DisplayTransform.of(furniture.display());
        this.openedScope = scope;
        this.scope = scope;
        this.current = opened;
        this.file = file;
        this.inventory = Bukkit.createInventory(this, EditorLayout.SIZE,
                Component.text("Editing " + item.id(), NamedTextColor.DARK_GRAY));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** Whether saving would change anything: other values, or the same ones kept somewhere else. */
    boolean changed() {
        return !current.equals(opened) || scope != openedScope;
    }

    /** Animated furniture is posed bone by bone from its type's values, so it is edited for every piece. */
    boolean pieceScopeAllowed() {
        return furniture.animated() == null;
    }

    /** What the piece is drawn with: the edited values, under the type's transform name. */
    Placement.Display drawn() {
        return current.applyTo(furniture.display());
    }

    // ---------------------------------------------------------------- menu

    void render(ItemFactory items) {
        ItemStack filler = filler();
        for (int slot = 0; slot < EditorLayout.SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        for (DisplayTransform.Part each : DisplayTransform.Part.values()) {
            inventory.setItem(EditorLayout.selector(each), selector(each));
        }
        inventory.setItem(EditorLayout.INFO, info(items));
        inventory.setItem(EditorLayout.SCOPE, scopeButton());
        inventory.setItem(EditorLayout.HELP, icon(Material.BOOK, "How it works", NamedTextColor.GOLD, false, List.of(
                "Pick translation, scale or rotation above,",
                "then move each axis with the panes:",
                "dark takes away, light adds.",
                "Shift-click: ten times the step.",
                "The piece moves as you click.",
                "Close the menu or press Save to keep it;",
                "Cancel puts it back as it was.")));
        for (DisplayTransform.Axis axis : DisplayTransform.Axis.values()) {
            inventory.setItem(EditorLayout.value(axis), value(axis));
            for (int step = 0; step < EditorLayout.STEPS.length; step++) {
                inventory.setItem(EditorLayout.button(axis, step, false), pane(axis, -EditorLayout.STEPS[step]));
                inventory.setItem(EditorLayout.button(axis, step, true), pane(axis, EditorLayout.STEPS[step]));
            }
        }
        inventory.setItem(EditorLayout.DEFAULT_PART, icon(Material.BUCKET, "Default " + part.label.toLowerCase(),
                NamedTextColor.YELLOW, false, List.of(part.label + " back to "
                        + DisplayTransform.yaml(DisplayTransform.IDENTITY.get(part)) + ".")));
        inventory.setItem(EditorLayout.REVERT, icon(Material.CLOCK, "Revert", NamedTextColor.YELLOW, false,
                List.of("Every value back to what it was", "when this menu opened.")));
        inventory.setItem(EditorLayout.SAVE, icon(Material.EMERALD, "Save", NamedTextColor.GREEN, changed(),
                List.of(changed() ? "Keep this and close." : "Nothing changed yet.",
                        scope == Scope.TYPE ? "Written to contents/" + file + "," : "Kept for this piece only,",
                        scope == Scope.TYPE ? "then the pack is rebuilt." : "in the database.")));
        inventory.setItem(EditorLayout.CANCEL, icon(Material.BARRIER, "Cancel", NamedTextColor.RED, false,
                List.of("Put the piece back as it was", "and close without saving.")));
    }

    private ItemStack selector(DisplayTransform.Part each) {
        Material material = switch (each) {
            case TRANSLATION -> Material.ARROW;
            case SCALE -> Material.SLIME_BALL;
            case ROTATION -> Material.COMPASS;
        };
        boolean selected = each == part;
        return icon(material, each.label, selected ? NamedTextColor.AQUA : NamedTextColor.GRAY, selected, List.of(
                DisplayTransform.yaml(current.get(each)) + " " + each.unit,
                selected ? "Being edited." : "Click to edit."));
    }

    private ItemStack info(ItemFactory items) {
        ItemStack stack = items.create(item, 1);
        List<Component> lore = new ArrayList<>();
        lore.add(line(item.id(), NamedTextColor.DARK_GRAY));
        lore.add(line("translation " + DisplayTransform.yaml(current.translation()), NamedTextColor.GRAY));
        lore.add(line("scale " + DisplayTransform.yaml(current.scale()), NamedTextColor.GRAY));
        lore.add(line("rotation " + DisplayTransform.yaml(current.rotation()), NamedTextColor.GRAY));
        lore.add(line(changed() ? "Changed - not saved yet." : "As saved.", changed() ? NamedTextColor.YELLOW
                : NamedTextColor.GREEN));
        lore.add(line("At " + x + ", " + y + ", " + z, NamedTextColor.DARK_GRAY));
        stack.editMeta(meta -> meta.lore(lore));
        return stack;
    }

    private ItemStack scopeButton() {
        if (scope == Scope.TYPE) {
            List<String> lines = new ArrayList<>(List.of("contents/" + file,
                    "Pieces given a look of their own keep it."));
            lines.add(pieceScopeAllowed() ? "Click: this piece only." : "Animated: edited for every piece.");
            return icon(Material.WRITABLE_BOOK, "Saving to: every " + item.definition().id(), NamedTextColor.GOLD,
                    false, lines);
        }
        return icon(Material.NAME_TAG, "Saving to: this piece only", NamedTextColor.LIGHT_PURPLE, false, List.of(
                "Kept in the database for the block", "at " + x + ", " + y + ", " + z + ".",
                "Click: every " + item.definition().id() + "."));
    }

    private ItemStack value(DisplayTransform.Axis axis) {
        Material material = switch (axis) {
            case X -> Material.RED_CONCRETE;
            case Y -> Material.LIME_CONCRETE;
            case Z -> Material.BLUE_CONCRETE;
        };
        float value = DisplayTransform.component(current.get(part), axis);
        float before = DisplayTransform.component(opened.get(part), axis);
        return icon(material, part.label + " " + axis + ": " + DisplayTransform.number(value) + " " + part.unit,
                colour(axis), false, List.of("Was " + DisplayTransform.number(before) + " " + part.unit
                        + " when opened."));
    }

    private ItemStack pane(DisplayTransform.Axis axis, double step) {
        boolean increase = step > 0;
        Material material = switch (axis) {
            case X -> increase ? Material.PINK_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            case Y -> increase ? Material.LIME_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE;
            case Z -> increase ? Material.LIGHT_BLUE_STAINED_GLASS_PANE : Material.BLUE_STAINED_GLASS_PANE;
        };
        return icon(material, axis + " " + EditorLayout.label(step) + " " + part.unit, colour(axis), false,
                List.of("Shift-click: " + EditorLayout.label(step * EditorLayout.SHIFT_FACTOR) + " " + part.unit));
    }

    private static TextColor colour(DisplayTransform.Axis axis) {
        return switch (axis) {
            case X -> NamedTextColor.RED;
            case Y -> NamedTextColor.GREEN;
            case Z -> NamedTextColor.BLUE;
        };
    }

    private static ItemStack icon(Material material, String name, TextColor colour, boolean glint, List<String> lines) {
        ItemStack stack = ItemStack.of(material);
        stack.editMeta(meta -> {
            meta.itemName(Component.text(name, colour));
            meta.lore(lines.stream().map(text -> line(text, NamedTextColor.GRAY)).toList());
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return stack;
    }

    private static Component line(String text, TextColor colour) {
        return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack filler() {
        ItemStack stack = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        stack.editMeta(meta -> meta.setHideTooltip(true));
        return stack;
    }
}
