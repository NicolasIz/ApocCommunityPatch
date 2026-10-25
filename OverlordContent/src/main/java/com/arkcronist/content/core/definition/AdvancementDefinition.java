package com.arkcronist.content.core.definition;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * An advancement, as the {@code advancements:} section of a content file describes it.
 *
 * <p>Advancements are server data, not resources: the server holds the tree and each player's
 * progress, and sends both to the client. So the plugin registers them on the server; the pack
 * only carries what they show - item icons, through the items' own models, and a tab background
 * when it is one of this pack's textures.</p>
 *
 * @param namespace   the content pack's namespace; the advancement is {@code <namespace>:<id>}
 * @param title       MiniMessage
 * @param description MiniMessage
 * @param icon        an item id: one of this plugin's items, or a vanilla one ({@code minecraft:...})
 * @param parent      the advancement above it, or null for the root of a new tab
 * @param background  a root's tab background, a texture id; null on a child
 * @param toast       whether completing it shows the toast in the corner
 * @param hidden      whether it stays out of the tree until it is done
 * @param announce    MiniMessage broadcast to everyone the first time a player completes it, with
 *                    {@code <player>} and {@code <advancement>}; null for vanilla's own chat line
 * @param celebrate   whether completing it plays the advancement sound around the player and
 *                    bursts crimson particles over them
 * @param experience  experience points given on completion
 * @param trigger     what completes it
 */
public record AdvancementDefinition(String namespace, String id, String title, String description,
                                    ResourceLocation icon, Frame frame, @Nullable ResourceLocation parent,
                                    @Nullable ResourceLocation background, boolean toast, boolean hidden,
                                    @Nullable String announce, boolean celebrate, int experience, Trigger trigger,
                                    Path sourceRoot, Path source) {

    /** The frame around the icon: also how loud the toast is. */
    public enum Frame {
        TASK, GOAL, CHALLENGE;

        public static @Nullable Frame parse(String raw) {
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        public String vanillaName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What completes an advancement. */
    public sealed interface Trigger {

        /** Joining the server: the root of a tab, so everyone has the tab. Vanilla's own tick trigger. */
        record Join() implements Trigger {
        }

        /** Wearing every one of these items at once, checked by the plugin as armour changes. */
        record Wear(List<ResourceLocation> items) implements Trigger {
            public Wear {
                items = List.copyOf(items);
            }
        }

        /** Having this item in the inventory: vanilla's inventory_changed trigger, on the item's model. */
        record Obtain(ResourceLocation item) implements Trigger {
        }

        /** Only {@code /advancement grant}, or another plugin. */
        record Manual() implements Trigger {
        }
    }

    public ResourceLocation key() {
        return new ResourceLocation(namespace, id);
    }

    public String fullId() {
        return namespace + ":" + id;
    }
}
