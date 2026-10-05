package com.arkcronist.content.bukkit.hooks.worldguard;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Logger;

/**
 * This plugin's own WorldGuard flags, both allowed unless a region says otherwise:
 *
 * <pre>
 * /rg flag spawn arkcontent-liquid-flow deny     liquids do not run into the region
 * /rg flag spawn arkcontent-liquid-damage deny   liquids hurt no one in it
 * </pre>
 *
 * <p>WorldGuard takes new flags only before it enables, so they are registered from onLoad. A flag
 * of the same name another plugin registered first is used as it is, if it is a state flag.</p>
 */
public final class WorldGuardFlags {

    public static final String LIQUID_FLOW_NAME = "arkcontent-liquid-flow";
    public static final String LIQUID_DAMAGE_NAME = "arkcontent-liquid-damage";

    private static volatile @Nullable StateFlag liquidFlow;
    private static volatile @Nullable StateFlag liquidDamage;

    private WorldGuardFlags() {
    }

    /** Registers both flags. Main thread, from onLoad. */
    public static Boolean register(Logger logger) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        liquidFlow = register(registry, LIQUID_FLOW_NAME, logger);
        liquidDamage = register(registry, LIQUID_DAMAGE_NAME, logger);
        return liquidFlow != null || liquidDamage != null;
    }

    private static @Nullable StateFlag register(FlagRegistry registry, String name, Logger logger) {
        StateFlag flag = new StateFlag(name, true);
        try {
            registry.register(flag);
            return flag;
        } catch (FlagConflictException | IllegalStateException exception) {
            Flag<?> existing = registry.get(name);
            if (existing instanceof StateFlag state) {
                return state;
            }
            logger.warning("WorldGuard flag " + name + " could not be registered: " + exception.getMessage());
            return null;
        }
    }

    static @Nullable StateFlag liquidFlow() {
        return liquidFlow;
    }

    static @Nullable StateFlag liquidDamage() {
        return liquidDamage;
    }
}
