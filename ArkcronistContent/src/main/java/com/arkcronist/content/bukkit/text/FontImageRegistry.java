package com.arkcronist.content.bukkit.text;

import com.arkcronist.content.core.pack.FontImages;

/** The font images of the live build, by name. Replaced as a whole by each rebuild; read from any thread. */
public final class FontImageRegistry {

    private volatile FontImages current = FontImages.EMPTY;

    public FontImages current() {
        return current;
    }

    public void replace(FontImages images) {
        this.current = images;
    }
}
