package com.arkcronist.content.core.hud;

import com.arkcronist.content.core.definition.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A bar drawn on the player's screen - mana, thirst, stamina - made of icons in the pack's font:
 * {@code segments} of them, each full, half or empty by the value it stands for.
 *
 * <p>Drawn wherever text goes: through PlaceholderAPI into TAB's scoreboard or bossbar, a
 * DeluxeMenus title, a hologram; or straight onto the action bar. The icons sit at their own
 * height ({@code ascent}) and the bar at its own place on the line ({@code offset}), and the text
 * after it carries on as if it were not there.</p>
 *
 * @param full      the icon of a full segment
 * @param half      a half segment; null to round to whole ones
 * @param empty     an empty segment; null to draw nothing for one - the bar then shrinks
 * @param height    the icons' height in font pixels; a line of chat is 9
 * @param ascent    how far above the line's baseline the icons reach; lower, or negative, moves the
 *                  bar down the screen. At most {@code height}
 * @param segments  how many icons the bar has
 * @param spacing   pixels between two icons, besides the one the font always adds; negative to
 *                  overlap
 * @param offset    pixels from where the bar is written to where it is drawn; negative moves it left
 * @param source    where its value comes from
 * @param actionBar also show it on the action bar, sent by the plugin
 * @param anchor    2.0: where on the screen it sits, against the vanilla HUD - its ascent and offset
 *                  then follow from it; null for the ascent and offset as written
 * @param betterHud 3.1: whether BetterHud draws it, and where; null to leave it to {@code huds.betterhud}
 *                  in config.yml - with BetterHud installed, a bar on the action bar is drawn by it
 * @param bedrock   3.4: how Bedrock players, who see neither the pack's font nor BetterHud, are shown
 *                  it; null for the default - a boss bar for a value this plugin keeps, nothing for
 *                  the others, which the game or another plugin already shows them
 */
public record HudDefinition(String namespace, String id, ResourceLocation full, @Nullable ResourceLocation half,
                            @Nullable ResourceLocation empty, int height, int ascent, int segments, int spacing,
                            int offset, Source source, boolean actionBar, Path sourceRoot, Path file,
                            @Nullable HudAnchor anchor, @Nullable BetterHud betterHud, @Nullable Bedrock bedrock) {

    public HudDefinition(String namespace, String id, ResourceLocation full, @Nullable ResourceLocation half,
                         @Nullable ResourceLocation empty, int height, int ascent, int segments, int spacing,
                         int offset, Source source, boolean actionBar, Path sourceRoot, Path file,
                         @Nullable HudAnchor anchor, @Nullable BetterHud betterHud) {
        this(namespace, id, full, half, empty, height, ascent, segments, spacing, offset, source, actionBar, sourceRoot,
                file, anchor, betterHud, null);
    }

    public HudDefinition(String namespace, String id, ResourceLocation full, @Nullable ResourceLocation half,
                         @Nullable ResourceLocation empty, int height, int ascent, int segments, int spacing,
                         int offset, Source source, boolean actionBar, Path sourceRoot, Path file) {
        this(namespace, id, full, half, empty, height, ascent, segments, spacing, offset, source, actionBar, sourceRoot,
                file, null, null);
    }

    public HudDefinition(String namespace, String id, ResourceLocation full, @Nullable ResourceLocation half,
                         @Nullable ResourceLocation empty, int height, int ascent, int segments, int spacing,
                         int offset, Source source, boolean actionBar, Path sourceRoot, Path file,
                         @Nullable HudAnchor anchor) {
        this(namespace, id, full, half, empty, height, ascent, segments, spacing, offset, source, actionBar, sourceRoot,
                file, anchor, null);
    }

    /**
     * 3.1: {@code betterhud:} - a bar BetterHud draws, as one of its own HUDs, instead of this
     * plugin's action bar: {@code false} keeps it on the action bar, {@code true} hands it over,
     * and a section says how: {@code { gui: [x, y], pixel: [x, y] }} puts it at a place of its own,
     * in BetterHud's terms; without one it stays where its anchor put it - or, 3.1.1, in a row of
     * its own under BetterHud's health and hunger bars, drawn as they are (PrimeHUD's).
     *
     * @param drawn     whether BetterHud draws it; null for {@code huds.betterhud} in config.yml
     * @param gui       BetterHud's {@code gui}: percent across and down the screen; null for where it was
     * @param pixel     BetterHud's {@code pixel}: pixels right and down from there; null for where it was
     * @param icon      3.1.1: the small icon a bar drawn as BetterHud's status bars shows at its left end
     *                  (about 7 x 6); null for the full icon, made smaller
     * @param color     3.1.1: that bar's colour, 0xRRGGBB; null for its icon's
     * @param hideEmpty 3.1.1: drawn only while its value is above 0, as PrimeHUD's armour bar
     * @param warnBelow 3.1.1: at or under this percent of its max it blinks, as PrimeHUD's low health;
     *                  0 for never
     */
    public record BetterHud(@Nullable Boolean drawn, @Nullable Spot gui, @Nullable Spot pixel,
                            @Nullable ResourceLocation icon, @Nullable Integer color, boolean hideEmpty,
                            int warnBelow) {

        public static final BetterHud OFF = new BetterHud(false, null, null, null, null, false, 0);
        public static final BetterHud ON = new BetterHud(true, null, null, null, null, false, 0);

        public BetterHud(boolean drawn, @Nullable Spot gui, @Nullable Spot pixel) {
            this(drawn, gui, pixel, null, null, false, 0);
        }

        /** Whether it has a place of its own on BetterHud's screen. */
        public boolean placed() {
            return gui != null || pixel != null;
        }
    }

    /**
     * 3.4: {@code bedrock:} - the bar as Bedrock players see it: a boss bar at the top of the screen,
     * named {@code title} and followed by the value, as {@code 14/20}.
     *
     * @param shown whether they are shown it
     * @param title its name; null for the HUD's id
     * @param color the boss bar's colour: pink, blue, red, green, yellow, purple or white
     */
    public record Bedrock(boolean shown, @Nullable String title, String color) {

        public static final List<String> COLORS = List.of("pink", "blue", "red", "green", "yellow", "purple", "white");
        public static final Bedrock OFF = new Bedrock(false, null, "white");
    }

    /** 3.4: what Bedrock players are shown of it - by default, a boss bar for a value this plugin keeps. */
    public Bedrock bedrockBar() {
        if (bedrock != null) {
            return bedrock;
        }
        return source instanceof Source.Stored ? new Bedrock(true, null, "blue") : Bedrock.OFF;
    }

    /** Two numbers: across, then down. */
    public record Spot(int x, int y) {
    }

    /** The icons a HUD uses, by name: full, then half and empty if it has them. */
    public List<Icon> icons() {
        List<Icon> icons = new ArrayList<>(3);
        icons.add(new Icon("full", full));
        if (half != null) {
            icons.add(new Icon("half", half));
        }
        if (empty != null) {
            icons.add(new Icon("empty", empty));
        }
        return icons;
    }

    public String fullId() {
        return namespace + ":" + id;
    }

    /** {@code <namespace>:<id>/<icon>}: what an icon's character is remembered by. */
    public String iconKey(String icon) {
        return fullId() + "/" + icon;
    }

    public record Icon(String name, ResourceLocation texture) {
    }

    /** Where a bar's value comes from. */
    public sealed interface Source {

        /**
         * Another plugin's value, read through PlaceholderAPI: {@code %mmocore_mana%} out of
         * {@code %mmocore_max_mana%}, or out of a fixed number.
         */
        record Placeholder(String value, String max) implements Source {
        }

        /**
         * A value this plugin keeps, per player, in its database - set and changed by command, by
         * eating or drinking, and by time.
         *
         * @param start       what a player starts with
         * @param max         the most it holds
         * @param perSecond   how much it changes by itself each second: negative drains, as thirst
         *                    does, positive refills, as mana does
         * @param emptyDamage damage each second while it is at 0 - dying of thirst; 0 for none
         * @param consume     what eating or drinking an item adds, by item: a material such as
         *                    {@code POTION} or a custom item's id
         */
        record Stored(double start, double max, double perSecond, double emptyDamage,
                      Map<String, Double> consume) implements Source {

            public Stored {
                consume = Map.copyOf(consume);
            }

            public double clamp(double value) {
                return Math.max(0, Math.min(max, value));
            }
        }

        /**
         * 2.0: a value the server already knows about the player, read straight from it - no
         * placeholder, no other plugin: {@code health}, {@code absorption} (the golden hearts:
         * shields), {@code armor}, {@code toughness}, {@code food}, {@code saturation}, {@code air},
         * {@code level}, {@code experience}; or another plugin's through its API,
         * {@code auraskills:mana}.
         *
         * @param max its full value; 0 for the player's own (max health, max air, AuraSkills' max mana)
         */
        record Stat(String stat, double max) implements Source {

            /** The stats there are, as written in YAML. */
            public static final List<String> NAMES = List.of("health", "absorption", "armor", "toughness", "food",
                    "saturation", "air", "level", "experience", "auraskills:mana");
        }
    }
}
