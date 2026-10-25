package com.arkcronist.content.bukkit.text;

import com.arkcronist.content.core.hud.Spaces;
import com.arkcronist.content.core.pack.FontImages;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Menu titles written for ItemsAdder, as components: drawn, with their styles kept. */
class GlyphComponentsTest {

    private static final FontImages IMAGES = images();

    private static FontImages images() {
        JsonArray providers = new JsonArray();
        providers.add(provider("spectra_aurelium_skills:skills_menu_book.png", ""));
        providers.add(provider("scenes:ui/scenes_spawner_small.png", "ꐒ"));
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        return FontImages.of(List.of(), FontImages.provided(List.of(new FontImages.Font("minecraft:default", font))));
    }

    private static JsonObject provider(String file, String glyph) {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", file);
        provider.addProperty("ascent", 48);
        provider.addProperty("height", 256);
        JsonArray chars = new JsonArray();
        chars.add(glyph);
        provider.add("chars", chars);
        return provider;
    }

    @Test
    void auraSkillsMenuTitleIsDrawnWithTheColourItAsksFor() {
        Component title = MiniMessage.miniMessage().deserialize("<white>:offset_-20: %img_skills_menu_book%");
        Component drawn = GlyphComponents.replace(title, IMAGES);
        assertEquals(Spaces.of(-20) + " ", PlainTextComponentSerializer.plainText().serialize(drawn));
        Component picture = find(drawn, "");
        assertEquals(null, picture.color(), "white comes from <white> around it");
        assertEquals(NamedTextColor.WHITE, colourAt(drawn, ""));
    }

    @Test
    void aTitleWithNoColourDrawsThePictureWhiteAndATintedOneKeepsItsTint() {
        // UpgradeableSpawners' title: no colour of its own, so the game's dark grey would tint the picture.
        Component spawner = LegacyComponentSerializer.legacySection().deserialize(":offset_-44::scenes_spawner_small:");
        Component drawn = GlyphComponents.replace(spawner, IMAGES);
        assertEquals(Spaces.of(-44) + "ꐒ", PlainTextComponentSerializer.plainText().serialize(drawn));
        assertEquals(NamedTextColor.WHITE, find(drawn, "ꐒ").color());
        assertEquals(null, find(drawn, Spaces.of(-44)).color(), "a space has no colour to show");

        Component tinted = MiniMessage.miniMessage().deserialize("<#ffd556><bold>:scenes_spawner_small: Spawner");
        Component replaced = GlyphComponents.replace(tinted, IMAGES);
        assertEquals(TextColor.fromHexString("#ffd556"), colourAt(replaced, "ꐒ"));
        assertEquals(TextDecoration.State.FALSE, find(replaced, "ꐒ").decoration(TextDecoration.BOLD),
                "bold would widen it by a pixel");
        assertEquals("ꐒ Spawner", PlainTextComponentSerializer.plainText().serialize(replaced));
    }

    @Test
    void aTitleWithNothingToReplaceIsLeftAlone() {
        Component title = Component.text("Shop: Tools", NamedTextColor.DARK_GRAY);
        assertSame(title, GlyphComponents.replace(title, IMAGES));
        Component unknown = Component.text(":not_an_image: %img_nope%");
        assertSame(unknown, GlyphComponents.replace(unknown, IMAGES));
    }

    /** The component whose own text is {@code content}. */
    private static Component find(Component component, String content) {
        List<Component> all = new ArrayList<>();
        collect(component, all);
        return all.stream().filter(c -> c instanceof net.kyori.adventure.text.TextComponent text
                && text.content().equals(content)).findFirst().orElseThrow(() -> new AssertionError(content));
    }

    /** The colour {@code content} is drawn in: its own, or the nearest parent's. */
    private static TextColor colourAt(Component component, String content) {
        return colourAt(component, content, null);
    }

    private static TextColor colourAt(Component component, String content, TextColor inherited) {
        TextColor colour = component.color() != null ? component.color() : inherited;
        if (component instanceof net.kyori.adventure.text.TextComponent text && text.content().equals(content)) {
            return colour;
        }
        for (Component child : component.children()) {
            TextColor found = colourAt(child, content, colour);
            if (found != null || containsText(child, content)) {
                return found;
            }
        }
        return null;
    }

    private static boolean containsText(Component component, String content) {
        List<Component> all = new ArrayList<>();
        collect(component, all);
        return all.stream().anyMatch(c -> c instanceof net.kyori.adventure.text.TextComponent text
                && text.content().equals(content));
    }

    private static void collect(Component component, List<Component> all) {
        all.add(component);
        component.children().forEach(child -> collect(child, all));
    }
}
