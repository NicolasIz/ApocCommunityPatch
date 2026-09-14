package com.arkcronist.gen.bukkit.world;

import org.bukkit.World;

import java.util.Locale;

/**
 * The naming rule that ties a generated world to its own Nether and End.
 *
 * <p>Nothing in here needs a running server. Everything about companion dimensions that can be got
 * wrong - which name belongs to which world, how to read that backwards, and which worlds are
 * entitled to companions at all - is decided here, so it can be tested rather than discovered on a
 * live server at two in the morning.</p>
 *
 * <p>The convention is the server's own: a world called {@code insane} gets {@code insane_nether} and
 * {@code insane_the_end}. Following it rather than inventing one means the worlds sit on disk where
 * anybody looking for them would expect, and every other plugin that knows the convention finds them
 * too.</p>
 */
public final class DimensionLinks {

    private DimensionLinks() {
    }

    /**
     * Whether a world should be given a Nether and an End of its own.
     *
     * <p>Only an overworld should. The generator can be attached to any world the server will make:
     * {@code ac create Foo nether -g ArkcronistGenerator:Insane} is a perfectly ordinary command, and
     * without this the plugin would answer it by giving that Nether a Nether of its own called
     * {@code Foo_nether}, plus an End, and then route portals between them. Companions are a property
     * of an overworld, so this asks the only question that matters.</p>
     */
    public static boolean wantsCompanions(World.Environment environment) {
        return environment == World.Environment.NORMAL;
    }

    /** The Nether that belongs to this world. */
    public static String netherOf(String base, String suffix) {
        return base + suffix;
    }

    /** The End that belongs to this world. */
    public static String endOf(String base, String suffix) {
        return base + suffix;
    }

    /**
     * The world a companion belongs to, or null when this name is not a companion at all.
     *
     * <p>Read longest suffix first. With the defaults it makes no difference, but a server that sets
     * both suffixes to something overlapping - {@code _end} and {@code _the_end} - would otherwise
     * have {@code insane_the_end} resolve to a world called {@code insane_the}, and the portal would
     * lead somewhere that does not exist.</p>
     */
    public static String baseOf(String name, String netherSuffix, String endSuffix) {
        String lower = name.toLowerCase(Locale.ROOT);
        String first = netherSuffix.length() >= endSuffix.length() ? netherSuffix : endSuffix;
        String second = netherSuffix.length() >= endSuffix.length() ? endSuffix : netherSuffix;
        for (String suffix : new String[]{first, second}) {
            if (!suffix.isEmpty() && lower.endsWith(suffix.toLowerCase(Locale.ROOT))
                    && lower.length() > suffix.length()) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return null;
    }

    /** Whether this name is the Nether of the given world. */
    public static boolean isNetherOf(String name, String base, String suffix) {
        return name.equalsIgnoreCase(netherOf(base, suffix));
    }

    /** Whether this name is the End of the given world. */
    public static boolean isEndOf(String name, String base, String suffix) {
        return name.equalsIgnoreCase(endOf(base, suffix));
    }

    /**
     * The seed a companion is created with.
     *
     * <p>Derived from the world it belongs to rather than random, so the Nether of an INSANE world is
     * the same Nether every time that world is made from that seed - the same promise the overworld
     * already makes. Salted so it is not simply the overworld's terrain again in netherrack.</p>
     */
    public static long seedFor(long baseSeed, boolean nether) {
        return baseSeed ^ (nether ? 0x4E45_5448_4552L : 0x5448_455F_454EL);
    }
}
