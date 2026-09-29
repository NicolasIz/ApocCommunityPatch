package com.arkcronist.content.bukkit.hooks;

/**
 * A hook that wants to know when the items have been rebuilt.
 */
public interface ContentHook {

    /** Called on the main thread once a rebuild's items are live. */
    void contentReloaded();
}
