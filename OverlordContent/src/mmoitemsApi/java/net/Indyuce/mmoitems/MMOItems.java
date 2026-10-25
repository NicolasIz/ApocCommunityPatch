package net.Indyuce.mmoitems;

import net.Indyuce.mmoitems.manager.StatManager;

/**
 * Signatures of MMOItems 6.10's main class that the hook uses. Not packaged: at runtime this is
 * MMOItems's own class (which extends MythicLib's MMOPlugin - irrelevant to these two members).
 */
public class MMOItems {

    public static MMOItems plugin;

    public StatManager getStats() {
        throw new UnsupportedOperationException("signature stub");
    }
}
