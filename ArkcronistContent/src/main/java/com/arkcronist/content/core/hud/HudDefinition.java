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
 */
public record HudDefinition(String namespace, String id, ResourceLocation full, @Nullable ResourceLocation half,
                            @Nullable ResourceLocation empty, int height, int ascent, int segments, int spacing,
                            int offset, Source source, boolean actionBar, Path sourceRoot, Path file) {

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
    }
}
