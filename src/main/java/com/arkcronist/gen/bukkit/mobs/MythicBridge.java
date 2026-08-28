package com.arkcronist.gen.bukkit.mobs;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Asks MythicMobs for a mob, without compiling against it.
 *
 * <p>Everything here goes through reflection, and that is a deliberate choice rather than a
 * shortcut. Two reasons:</p>
 *
 * <ul>
 *   <li>MythicMobs moved its whole package between major versions - {@code io.lumine.xikage.mythicmobs}
 *       in 4.x, {@code io.lumine.mythic.bukkit} in 5.x - so a plugin compiled against one will not
 *       even load beside the other. Reflection asks at runtime and works with whichever is
 *       installed.</li>
 *   <li>The generator has to keep working when MythicMobs is not installed at all. A hard dependency
 *       would make an ordinary vanilla install impossible; this way an absent MythicMobs costs the
 *       custom mobs and nothing else - the world still generates and the server still starts.</li>
 * </ul>
 *
 * <p>Resolved once at startup, like {@link com.arkcronist.gen.bukkit.BlockBridge}, so the spawn path
 * never does a class lookup.</p>
 */
public final class MythicBridge {

    /** MythicMobs 5.x first, then 4.x. The first that loads decides which API is in front of us. */
    private static final String[] ENTRY_POINTS = {
            "io.lumine.mythic.bukkit.MythicBukkit",
            "io.lumine.xikage.mythicmobs.MythicMobs",
    };

    private static volatile Object apiHelper;
    private static volatile Method spawnMethod;
    private static volatile boolean levelled;
    private static volatile String version = "";

    private MythicBridge() {
    }

    /**
     * Finds the API, or decides once and for all that it is not there.
     *
     * <p>Never throws. Any failure - plugin absent, package moved again, method renamed - leaves the
     * bridge unavailable and the caller falls back to vanilla.</p>
     */
    public static synchronized void initialize(Logger logger) {
        apiHelper = null;
        spawnMethod = null;
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            logger.info("MythicMobs is not installed; hostile mobs stay vanilla.");
            return;
        }
        for (String entry : ENTRY_POINTS) {
            try {
                Class<?> type = Class.forName(entry);
                Object instance = type.getMethod("inst").invoke(null);
                if (instance == null) {
                    continue;
                }
                // Asked of the resolved class, not of instance.getClass(). They are usually the
                // same and when they are not this is the difference between working and not: if the
                // runtime class is an internal, non-public one, getMethod still hands back the
                // public method but invoking it throws IllegalAccessException, because it is the
                // declaring class that has to be reachable and it is not.
                Object helper = type.getMethod("getAPIHelper").invoke(instance);
                if (helper == null) {
                    continue;
                }
                Method spawn = findSpawn(helper.getClass());
                if (spawn == null) {
                    continue;
                }
                // Same trap one level down, and here there is no public class to ask instead - the
                // helper is whatever getAPIHelper returned. Opening the method up covers the case;
                // if the JVM refuses, the invoke below would have failed anyway and the bridge
                // reports itself unavailable rather than throwing on every spawn.
                try {
                    spawn.setAccessible(true);
                } catch (RuntimeException ignored) {
                    // Left as it is; it may still be invokable.
                }
                apiHelper = helper;
                spawnMethod = spawn;
                levelled = spawn.getParameterCount() == 3;
                version = String.valueOf(Bukkit.getPluginManager().getPlugin("MythicMobs").getDescription()
                        .getVersion());
                logger.info("MythicMobs " + version + " found through " + entry
                        + "; custom hostile mobs are available.");
                return;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Try the next entry point; a miss here is not an error worth a stack trace.
            }
        }
        logger.warning("MythicMobs is installed but its API could not be reached. "
                + "Hostile mobs stay vanilla. Check that the version is 4.x or 5.x.");
    }

    /**
     * The spawn call, preferring the one that takes a level.
     *
     * <p>Both signatures have existed across versions: {@code spawnMythicMob(String, Location)} and
     * {@code spawnMythicMob(String, Location, int)}. The levelled one is asked for first so that a
     * tier can be passed through where the API accepts it.</p>
     */
    private static Method findSpawn(Class<?> helper) {
        Method plain = null;
        for (Method method : helper.getMethods()) {
            if (!method.getName().equals("spawnMythicMob")) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 3 && parameters[0] == String.class
                    && parameters[1] == Location.class && parameters[2] == int.class) {
                return method;
            }
            if (parameters.length == 2 && parameters[0] == String.class
                    && parameters[1] == Location.class) {
                plain = method;
            }
        }
        return plain;
    }

    /** Whether a mob can actually be asked for. */
    public static boolean available() {
        return apiHelper != null && spawnMethod != null;
    }

    /** The MythicMobs version in front of us, for {@code /ag} to report. Empty when unavailable. */
    public static String version() {
        return version;
    }

    /**
     * Spawns one mob by its MythicMobs name.
     *
     * @return the entity, or null when MythicMobs is absent, the name is unknown to it, or the call
     *         failed - in every one of which the caller must leave the vanilla mob alone
     */
    public static Entity spawn(String mobName, Location location, int level) {
        Object helper = apiHelper;
        Method spawn = spawnMethod;
        if (helper == null || spawn == null || mobName == null || mobName.isBlank()) {
            return null;
        }
        try {
            Object result = levelled
                    ? spawn.invoke(helper, mobName, location, Math.max(1, level))
                    : spawn.invoke(helper, mobName, location);
            return result instanceof Entity entity ? entity : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }
}
