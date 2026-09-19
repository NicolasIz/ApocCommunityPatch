package com.arkcronist.gen.bukkit.mythic;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Asks MythicMobs what mobs it has loaded, so nobody has to list them by hand.
 *
 * <p>Every pack added to a server used to mean a config edit here, and every config edit meant a
 * chance to mistype a name into a list that fails silently. MythicMobs already knows every mob it
 * parsed; this reads that list.</p>
 *
 * <h2>Written to survive an API it cannot see</h2>
 *
 * <p>Like {@link com.arkcronist.gen.bukkit.mobs.MythicBridge}, all of this is reflection: MythicMobs
 * has moved its packages between major versions and a plugin compiled against one will not load
 * beside the other. It goes further than the bridge does, though, because the bridge needs one
 * method and this needs six, on classes whose shape is not documented anywhere this build can check.
 * So every field is attempted through a list of plausible method names and any that fails is left
 * empty rather than abandoning the mob - a mob known only by its name is still worth listing, and
 * {@link MobClassifier} is written to say "I cannot judge this" rather than guess when that is all
 * it gets.</p>
 */
public final class MythicCatalogue {

    private static final String[] ENTRY_POINTS = {
            "io.lumine.mythic.bukkit.MythicBukkit",
            "io.lumine.xikage.mythicmobs.MythicMobs",
    };

    /** Getters that have held the mob list, newest naming first. */
    private static final String[] MOB_LISTS = {"getMobTypes", "getMobNames", "getMobs"};
    private static final String[] MANAGERS = {"getMobManager", "getMobExecutor"};

    private static final String[] NAME_GETTERS = {"getInternalName", "getName", "getMobName"};
    private static final String[] TYPE_GETTERS = {"getEntityType", "getEntityTypeName", "getType"};
    private static final String[] HEALTH_GETTERS = {"getHealth", "getMaxHealth", "getBaseHealth"};
    private static final String[] DISPLAY_GETTERS = {"getDisplayName", "getName"};
    private static final String[] FACTION_GETTERS = {"getFaction"};

    private MythicCatalogue() {
    }

    /**
     * Whether MythicMobs is on the server at all.
     *
     * <p>Only enough to tell an empty answer apart from an absent plugin, which is the difference
     * between "your packs did not load" and "there is nothing to load them". An empty
     * {@link #read()} means one or the other and the operator needs to know which.</p>
     */
    public static boolean available() {
        return Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
    }

    /**
     * Every mob MythicMobs has loaded, or an empty list when it cannot be asked.
     *
     * <p>Never throws. An absent plugin, a moved package, a renamed method: all of them mean an
     * empty list and a generator that behaves exactly as it did before this existed.</p>
     */
    public static List<MobFacts> read() {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            return List.of();
        }
        for (String entry : ENTRY_POINTS) {
            try {
                Class<?> type = Class.forName(entry);
                Object instance = type.getMethod("inst").invoke(null);
                if (instance == null) {
                    continue;
                }
                Object manager = firstResult(type, instance, MANAGERS);
                if (manager == null) {
                    continue;
                }
                Object list = firstResult(manager.getClass(), manager, MOB_LISTS);
                if (!(list instanceof Collection<?> mobs) || mobs.isEmpty()) {
                    continue;
                }
                return factsOf(mobs);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // The next entry point, or none.
            }
        }
        return List.of();
    }

    private static List<MobFacts> factsOf(Collection<?> mobs) {
        List<MobFacts> facts = new ArrayList<>(mobs.size());
        for (Object mob : mobs) {
            if (mob == null) {
                continue;
            }
            // A manager that hands back plain names rather than mob objects is a valid answer; it
            // just means the classifier has less to go on, which it is built to say out loud.
            if (mob instanceof String name) {
                if (!name.isBlank()) {
                    facts.add(MobFacts.of(name));
                }
                continue;
            }
            String name = text(mob, NAME_GETTERS);
            if (name.isBlank()) {
                continue;
            }
            facts.add(new MobFacts(name, text(mob, TYPE_GETTERS), number(mob, HEALTH_GETTERS),
                    text(mob, DISPLAY_GETTERS), text(mob, FACTION_GETTERS)));
        }
        facts.sort(Comparator.comparing(f -> f.name().toLowerCase(java.util.Locale.ROOT)));
        return List.copyOf(facts);
    }

    /** The first of these no-argument getters that returns anything. */
    private static Object firstResult(Class<?> on, Object target, String[] names) {
        for (String name : names) {
            try {
                Method method = on.getMethod(name);
                try {
                    method.setAccessible(true);
                } catch (RuntimeException ignored) {
                    // May still be invokable.
                }
                Object value = method.invoke(target);
                if (value != null) {
                    return value;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Try the next name.
            }
        }
        return null;
    }

    /** A getter's value as text, unwrapping the Optionals and placeholder objects packs use. */
    private static String text(Object mob, String[] names) {
        Object value = firstResult(mob.getClass(), mob, names);
        if (value instanceof Optional<?> maybe) {
            value = maybe.orElse(null);
        }
        if (value == null) {
            return "";
        }
        // MythicMobs wraps display names in its own placeholder type, whose toString is the text.
        String text = String.valueOf(value).trim();
        return "null".equals(text) ? "" : text;
    }

    /** A getter's value as a number, or 0 when it is not one. */
    private static double number(Object mob, String[] names) {
        Object value = firstResult(mob.getClass(), mob, names);
        if (value instanceof Optional<?> maybe) {
            value = maybe.orElse(null);
        }
        if (value instanceof Number figure) {
            return figure.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
