package com.arkcronist.gen.bukkit.mythic;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Which ModelEngine blueprints are loaded, asked by reflection like everything else here.
 *
 * <h2>Why this is only a report</h2>
 *
 * <p>A pack of custom mobs is two halves: MythicMobs holds the mob - its name, body, health - and
 * ModelEngine holds the model that mob wears. Only the first half decides anything about spawning,
 * which is why {@link MythicCatalogue} is what discovery reads and this is not: a mob spawns, fights
 * and dies identically whether its model loaded or not.</p>
 *
 * <p>What a missing model changes is what the player sees, and the failure is silent and unmistakable
 * at the same time - the mob turns up as a magenta-and-black box, or as the bare vanilla body it was
 * built on, with nothing in the log to say why. That is worth a command: {@code /ag mythic models}
 * answers "did my pack's models actually load", which is the first question when a mob looks wrong
 * and the one that otherwise takes an afternoon.</p>
 *
 * <p>Nothing else in the plugin depends on this. ModelEngine absent means an empty list and no other
 * difference anywhere.</p>
 */
public final class ModelEngineModels {

    /** The API class has kept this name across R3 and R4, which is the only reason this is viable. */
    private static final String[] ENTRY_POINTS = {
            "com.ticxo.modelengine.api.ModelEngineAPI",
    };

    /**
     * Candidate ways to ask for the blueprints, newest naming first.
     *
     * <p>R4 moved them behind a repository object; R3 and earlier answered a static getter directly.
     * Both are tried and whichever answers is used, which is the same bet {@link MythicCatalogue}
     * makes and for the same reason: the alternative is compiling against one version and refusing
     * to run beside the other.</p>
     */
    private static final String[] REPOSITORIES = {"getBlueprintRepository", "getModelRegistry"};
    private static final String[] BLUEPRINTS = {"getBlueprints", "getAll", "getValues", "values"};
    private static final String[] NAME_GETTERS = {"getName", "getId", "getIdentifier"};

    private ModelEngineModels() {
    }

    /** Whether ModelEngine is on the server at all. */
    public static boolean available() {
        return Bukkit.getPluginManager().getPlugin("ModelEngine") != null;
    }

    /** The plugin version, or an empty string when it is not installed. */
    public static String version() {
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("ModelEngine");
        return plugin == null ? "" : String.valueOf(plugin.getDescription().getVersion());
    }

    /**
     * Every blueprint id ModelEngine has loaded, sorted, or an empty list when it cannot be asked.
     *
     * <p>Never throws, for the same reasons as the rest of this package: an absent plugin, a moved
     * class, a renamed method each mean an empty list.</p>
     */
    public static List<String> read() {
        if (!available()) {
            return List.of();
        }
        for (String entry : ENTRY_POINTS) {
            try {
                Class<?> api = Class.forName(entry);
                // The static getter first: when it is there it is the whole answer.
                Object direct = staticResult(api, BLUEPRINTS);
                List<String> names = namesOf(direct);
                if (!names.isEmpty()) {
                    return names;
                }
                for (String repository : REPOSITORIES) {
                    Object holder = staticResult(api, new String[]{repository});
                    if (holder == null) {
                        continue;
                    }
                    names = namesOf(instanceResult(holder, BLUEPRINTS));
                    if (!names.isEmpty()) {
                        return names;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // The next entry point, or none.
            }
        }
        return List.of();
    }

    /** A map's keys, or a collection's elements asked for their own names. */
    private static List<String> namesOf(Object value) {
        if (value == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        if (value instanceof Map<?, ?> map) {
            // Keyed by blueprint id, which is exactly the name wanted - no need to go into the value.
            for (Object key : map.keySet()) {
                if (key != null && !String.valueOf(key).isBlank()) {
                    names.add(String.valueOf(key));
                }
            }
        } else if (value instanceof Collection<?> items) {
            for (Object item : items) {
                if (item == null) {
                    continue;
                }
                String name = item instanceof String text ? text : text(item);
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(names);
    }

    private static String text(Object blueprint) {
        Object result = instanceResult(blueprint, NAME_GETTERS);
        return result == null ? "" : String.valueOf(result).trim();
    }

    private static Object staticResult(Class<?> on, String[] names) {
        for (String name : names) {
            try {
                Method method = on.getMethod(name);
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                Object result = method.invoke(null);
                if (result != null) {
                    return result;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // The next name.
            }
        }
        return null;
    }

    private static Object instanceResult(Object target, String[] names) {
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                try {
                    method.setAccessible(true);
                } catch (RuntimeException ignored) {
                    // May still be invokable.
                }
                Object result = method.invoke(target);
                if (result != null) {
                    return result;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // The next name.
            }
        }
        return null;
    }
}
