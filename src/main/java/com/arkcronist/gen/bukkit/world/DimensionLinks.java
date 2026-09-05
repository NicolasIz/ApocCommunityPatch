package com.arkcronist.gen.bukkit.world;

import java.util.Locale;

/**
 * The naming rule that ties a generated world to its own Nether and End.
 *
 * <p>Plain strings and nothing else, on purpose. Everything about companion dimensions that can be
 * got wrong without a server running is in here - which name belongs to which world, and how to read
 * that backwards - so it can be tested rather than discovered on a live server at two in the
 * morning.</p>
 *
 * <p>The convention is the server's own: a world called {@code insane} gets {@code insane_nether} and
 * {@code insane_the_end}. Following it rather than inventing one means the worlds sit on disk where
 * anybody looking for them would expect, and every other plugin that knows the convention finds them
 * too.</p>
 */
public final class DimensionLinks {

    private DimensionLinks() {
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
