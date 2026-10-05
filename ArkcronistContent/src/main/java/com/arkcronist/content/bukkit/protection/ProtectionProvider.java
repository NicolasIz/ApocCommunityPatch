package com.arkcronist.content.bukkit.protection;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * One protection plugin, asked the questions this plugin needs answered before it changes a block
 * on its own. Main thread only, as the plugins behind it expect.
 */
public interface ProtectionProvider {

    /** The plugin's name, for the log. */
    String name();

    /** Whether {@code player} may do {@code interaction} to {@code block}. */
    boolean allows(Player player, Block block, Interaction interaction);

    /**
     * Whether an explosion may destroy what is at {@code block}.
     *
     * @param origin where it went off
     * @param source what exploded - TNT, a creeper - or null for a block, such as a bed in the Nether
     */
    boolean allowsExplosion(Block block, Location origin, @Nullable Entity source);

    /**
     * Whether something that is not a player may change {@code block} from {@code from}: a mob
     * trampling farmland, water flowing in. Protection plugins refuse a change that crosses into an
     * area from outside it.
     */
    boolean allowsChange(Block block, Location from);

    /**
     * Whether a liquid of this plugin's may flow into {@code block} - over and above
     * {@link #allowsChange}, for a plugin with a switch of its own for it (WorldGuard's
     * {@code arkcontent-liquid-flow} flag).
     */
    default boolean allowsLiquidFlow(Block block) {
        return true;
    }

    /**
     * Whether a liquid of this plugin's may hurt {@code player} standing in it at {@code block}
     * (WorldGuard's {@code arkcontent-liquid-damage} flag).
     */
    default boolean allowsLiquidContact(Player player, Block block) {
        return true;
    }
}
