package com.arkcronist.content.bukkit.emoji;

import com.arkcronist.content.core.definition.EmojiDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.permissions.Permissible;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Every emoji currently loaded, by the name typed between colons.
 *
 * <p>Swapped whole by each rebuild and read without locks, so the chat thread - chat is handled
 * off the main thread - never waits for it and never sees half of a rebuild.</p>
 */
public final class EmojiRegistry {

    /** {@code :name:}. The same characters a name may have, so nothing else is ever matched. */
    private static final Pattern KEYWORD = Pattern.compile(":([a-z0-9_]+):");

    /**
     * @param glyph      the private-use character the pack draws as the image
     * @param permission needed to use it, or null for everyone
     */
    public record Emoji(String name, String glyph, @Nullable String permission, String id) {

        public boolean allowed(Permissible who) {
            return permission == null || who.hasPermission(permission);
        }

        /**
         * The glyph as a component. White, so the image keeps its own colours whatever colour the
         * text around it is, and undecorated, so bold text does not smear it. Hovering shows what
         * was typed.
         */
        public Component component() {
            Style style = Style.style(NamedTextColor.WHITE)
                    .decoration(TextDecoration.BOLD, false)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.UNDERLINED, false)
                    .decoration(TextDecoration.STRIKETHROUGH, false)
                    .decoration(TextDecoration.OBFUSCATED, false)
                    .hoverEvent(HoverEvent.showText(Component.text(":" + name + ":", NamedTextColor.GRAY)));
            return Component.text(glyph, style);
        }
    }

    private volatile Map<String, Emoji> byName = Map.of();

    /** Replaces everything. Called once per rebuild, on the main thread. */
    public void replace(Map<EmojiDefinition, Integer> characters) {
        Map<String, Emoji> next = new HashMap<>();
        characters.forEach((definition, character) -> next.put(definition.name(), new Emoji(definition.name(),
                new String(Character.toChars(character)), definition.permission(), definition.fullId())));
        this.byName = Map.copyOf(next);
    }

    public Optional<Emoji> get(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /** Sorted by name. */
    public List<Emoji> all() {
        return byName.values().stream().sorted(Comparator.comparing(Emoji::name)).toList();
    }

    public Collection<String> names() {
        return byName.keySet();
    }

    public int size() {
        return byName.size();
    }

    /**
     * {@code text} with every {@code :name:} that {@code who} may use turned into its glyph. Anything
     * else between colons - a time, a smiley, an emoji they may not use - is left as it was typed.
     */
    public Component replace(Component text, Permissible who) {
        Map<String, Emoji> current = byName;
        if (current.isEmpty()) {
            return text;
        }
        return text.replaceText(TextReplacementConfig.builder()
                .match(KEYWORD)
                .replacement((match, typed) -> {
                    Emoji emoji = current.get(match.group(1));
                    return emoji != null && emoji.allowed(who) ? emoji.component() : typed;
                })
                .build());
    }
}
