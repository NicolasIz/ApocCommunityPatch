package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.definition.EmojiDefinition;
import com.arkcronist.content.core.definition.ItemDefinition;

import java.util.List;

/**
 * What one scan of the contents folder produced.
 *
 * <p>A broken entry never stops the others from loading, so a scan always comes back with whatever
 * could be read, and a list of what could not and why. Each problem names its file (and item, when
 * there is one) so it can be fixed from the log alone.</p>
 */
public record LoadReport(List<ItemDefinition> items, List<EmojiDefinition> emojis, List<String> problems) {

    public LoadReport {
        items = List.copyOf(items);
        emojis = List.copyOf(emojis);
        problems = List.copyOf(problems);
    }

    public LoadReport(List<ItemDefinition> items, List<String> problems) {
        this(items, List.of(), problems);
    }
}
