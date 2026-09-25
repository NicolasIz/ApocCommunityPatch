package com.arkcronist.enchants.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** '&' and '§' colour codes (and &#rrggbb) to components, without the italic lore default. */
public final class Colors {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    private Colors() {
    }

    public static Component of(String legacy) {
        String s = legacy.replace('§', '&');
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static String plain(Component c) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
    }
}
