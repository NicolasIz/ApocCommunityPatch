package com.arkcronist.gen.bukkit.mobs;

import com.arkcronist.gen.core.math.Hashing;

/**
 * How much of the world's hostile mobs are custom and how much stays vanilla.
 *
 * <h2>Why a dial and not a switch</h2>
 *
 * <p>The swap began as all-or-nothing: an entity type named in the table never appeared as itself
 * again. That reads fine for one type and badly for a whole world - a server where {@code ZOMBIE} is
 * in the table has no zombies in it at all, and with auto-discovery filling in every body a pack
 * happens to use, "no zombies" quietly becomes "no vanilla hostile mobs anywhere". Somebody who
 * installed a goblin pack wanted goblins <em>as well as</em> zombies, not instead of them.</p>
 *
 * <p>So the replacement is a chance. At 1.0 nothing changes and the table means what it always meant;
 * below that, the rest of the spawns are left exactly as the game made them, which is the one outcome
 * this whole subsystem is built to fall back to anyway.</p>
 *
 * <h2>Two ways of rolling, and the difference matters</h2>
 *
 * <p>A natural spawn is already unpredictable, so it rolls a plain random number and the mix comes out
 * right across a night's worth of spawns.</p>
 *
 * <p>A structure's garrison cannot. The castle a player walks into has to hold the same guards every
 * time that chunk is generated - it is chosen from the position rather than from a shared random for
 * exactly that reason - and a coin flip here would mean the same castle had goblins or zombies
 * depending on when its chunk happened to load. {@link #replacesAt} hashes the position instead, so
 * the mix is the same proportion over a world's worth of structures while any one of them is fixed.</p>
 */
public final class MobMix {

    /** Salt of its own, so this decision cannot correlate with which mob was picked. */
    private static final long SALT = 0x4D1_5E2AL;

    private MobMix() {
    }

    /**
     * Whether a spawn is replaced, given a roll.
     *
     * @param chance 1.0 replaces everything the table names, 0.0 nothing, in between a mix
     * @param roll   a number in [0, 1)
     */
    public static boolean replaces(double chance, double roll) {
        // Written so the edges are exact rather than nearly right: at 1.0 every roll replaces, at 0.0
        // none does, whatever rounding does in between.
        if (chance >= 1.0) {
            return true;
        }
        if (chance <= 0.0) {
            return false;
        }
        return roll < chance;
    }

    /**
     * The same decision, taken from a position so it never changes for that position.
     *
     * @see #replaces
     */
    public static boolean replacesAt(double chance, int x, int y, int z) {
        if (chance >= 1.0) {
            return true;
        }
        if (chance <= 0.0) {
            return false;
        }
        // Floor-modded into [0, 10000) and scaled, which is plenty of resolution for a percentage and
        // avoids turning the hash into a double at all.
        long bucket = Math.floorMod(Hashing.hash3(SALT, x, y, z), 10_000L);
        return bucket < Math.round(chance * 10_000.0);
    }
}
