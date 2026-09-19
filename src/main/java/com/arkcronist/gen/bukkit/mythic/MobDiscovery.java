package com.arkcronist.gen.bukkit.mythic;

import com.arkcronist.gen.bukkit.mythic.MobClassifier.Habitat;
import com.arkcronist.gen.bukkit.mythic.MobClassifier.Role;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns what MythicMobs has loaded into the lists the spawner and the swap table need.
 *
 * <p>Pure on purpose: everything that decides what a server will produce happens here, from a list of
 * facts and a handful of config values, with nothing to mock and nothing to spawn. What is left
 * outside is reading the facts and using the answer.</p>
 *
 * <h2>How a discovered mob is placed</h2>
 *
 * <p>An ambient pool only needs a name, so a hostile mob joins the pool for the habitat it was judged
 * to belong to. The swap table is harder - it is keyed by the vanilla mob being replaced, and nothing
 * in a pack says which that should be - so the rule is the one the pack already answered: a mob built
 * on a zombie replaces a zombie. It is the pack author's own choice of body, which is better evidence
 * than anything this could invent.</p>
 *
 * <p>That rule alone is not enough, and the reason is worth stating because it was a real bug here
 * first: a Nether mob and an overworld mob are very often built on the same body. Both of the packs
 * this was written for put a goblin and a Nether creature on a zombie, so a single table keyed by
 * ZOMBIE offers the Nether creature as a replacement for zombies in a forest - exactly the mistake
 * the habitat judgement exists to prevent. The swap table is therefore keyed by habitat first and
 * body second, and a mob can only ever stand in for a spawn in the habitat it was judged to belong
 * to. Mobs judged to fit anywhere are merged into every habitat by {@link Result#swapFor(Habitat)}.</p>
 *
 * <h2>What it refuses to do</h2>
 *
 * <p>Props are dropped and never appear in either. A mob nothing could be learned about is dropped
 * too, and both are reported rather than silently missing. Bosses are kept apart from everything
 * that spawns on its own: a boss in an ambient pool is a boss behind every tree.</p>
 *
 * <p>And a peaceful body is never made a stand-in. A pack author picks a body for how it looks and
 * moves, so a hostile built on a {@code WOLF} means "it has four legs", not "wolves should stop
 * appearing" - and reading it the second way would empty the world of wolves. Such a mob still joins
 * its habitat's ambient pool and turns up in the world that way; only guessing it from the body is
 * refused. See {@link MobClassifier#standsInFor}.</p>
 */
public final class MobDiscovery {

    /** What discovery found, split the way the config needs it. */
    public record Result(Map<Habitat, List<String>> hostiles,
                         Map<Habitat, List<String>> bosses,
                         Map<Habitat, Map<String, List<String>>> swapByHabitat,
                         List<String> props,
                         List<String> pets,
                         List<String> unknown,
                         Map<String, MobClassifier.Verdict> verdicts) {

        /** Hostile names for a habitat, plus the ones that fit anywhere. */
        public List<String> hostilesFor(Habitat habitat) {
            return merged(hostiles, habitat);
        }

        /** Boss names for a habitat, plus the ones that fit anywhere. */
        public List<String> bossesFor(Habitat habitat) {
            return merged(bosses, habitat);
        }

        /**
         * Vanilla entity type to the discovered names that may stand in for it, in one habitat.
         *
         * <p>The habitat's own entries come first and the anywhere entries are added to them, so a
         * mob that fits anywhere is offered in the Nether and in a forest while one judged to belong
         * to the Nether is offered only there.</p>
         */
        public Map<String, List<String>> swapFor(Habitat habitat) {
            Map<String, List<String>> here = swapByHabitat.getOrDefault(habitat, Map.of());
            Map<String, List<String>> anywhere = habitat == Habitat.ANY
                    ? Map.of() : swapByHabitat.getOrDefault(Habitat.ANY, Map.of());
            if (anywhere.isEmpty()) {
                return here;
            }
            Map<String, List<String>> both = new LinkedHashMap<>();
            here.forEach((body, names) -> both.put(body, new ArrayList<>(names)));
            anywhere.forEach((body, names) -> both
                    .computeIfAbsent(body, key -> new ArrayList<>()).addAll(names));
            Map<String, List<String>> frozen = new LinkedHashMap<>();
            both.forEach((body, names) -> frozen.put(body, List.copyOf(names)));
            return Map.copyOf(frozen);
        }

        private static List<String> merged(Map<Habitat, List<String>> table, Habitat habitat) {
            List<String> all = new ArrayList<>(table.getOrDefault(habitat, List.of()));
            if (habitat != Habitat.ANY) {
                all.addAll(table.getOrDefault(Habitat.ANY, List.of()));
            }
            return List.copyOf(all);
        }

        public int total() {
            return verdicts.size();
        }
    }

    private MobDiscovery() {
    }

    /**
     * Sorts a catalogue into what may spawn where.
     *
     * @param facts      every mob MythicMobs reported
     * @param bossHealth health at or above which a mob counts as a boss; 0 turns that rule off
     * @param exclude    names never to use, whatever they were judged to be
     * @param roles      name to role, overriding the judgement
     * @param habitats   name to habitat, overriding the judgement
     */
    public static Result sort(List<MobFacts> facts, double bossHealth, Set<String> exclude,
                              Map<String, String> roles, Map<String, String> habitats) {
        Map<Habitat, List<String>> hostiles = new LinkedHashMap<>();
        Map<Habitat, List<String>> bosses = new LinkedHashMap<>();
        Map<Habitat, Map<String, List<String>>> swap = new LinkedHashMap<>();
        List<String> props = new ArrayList<>();
        List<String> pets = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        Map<String, MobClassifier.Verdict> verdicts = new LinkedHashMap<>();

        Set<String> excluded = upper(exclude);

        for (MobFacts mob : facts) {
            if (mob.name().isBlank()) {
                continue;
            }
            MobClassifier.Verdict judged = MobClassifier.classify(mob, bossHealth);
            Role role = override(roles, mob.name(), Role.class, judged.role());
            Habitat habitat = override(habitats, mob.name(), Habitat.class, judged.habitat());
            String reason = role == judged.role() && habitat == judged.habitat()
                    ? judged.because() : "set in config";
            verdicts.put(mob.name(), new MobClassifier.Verdict(role, habitat, reason));

            if (excluded.contains(mob.name().toUpperCase(Locale.ROOT))) {
                props.add(mob.name());
                continue;
            }
            switch (role) {
                case PROP -> props.add(mob.name());
                case PET -> pets.add(mob.name());
                case UNKNOWN -> unknown.add(mob.name());
                case BOSS -> bosses.computeIfAbsent(habitat, key -> new ArrayList<>()).add(mob.name());
                case HOSTILE -> {
                    hostiles.computeIfAbsent(habitat, key -> new ArrayList<>()).add(mob.name());
                    // The body its author chose is the vanilla mob it stands in for - but only
                    // where the mob itself belongs, or a Nether creature ends up replacing the
                    // zombies in a forest because both were built on a zombie. And only where the
                    // body is something hostile: a mob built on a WOLF for its looks must not take
                    // every wolf in the world with it. It still joins the ambient pool above.
                    if (MobClassifier.standsInFor(mob.entityType())) {
                        swap.computeIfAbsent(habitat, key -> new LinkedHashMap<>())
                                .computeIfAbsent(mob.entityType(), key -> new ArrayList<>())
                                .add(mob.name());
                    }
                }
                default -> unknown.add(mob.name());
            }
        }
        return new Result(freeze(hostiles), freeze(bosses), freezeSwap(swap),
                List.copyOf(props), List.copyOf(pets), List.copyOf(unknown),
                Map.copyOf(verdicts));
    }

    /** An override for this name, or the judgement when there is none or it is not a valid value. */
    private static <E extends Enum<E>> E override(Map<String, String> overrides, String name,
                                                  Class<E> type, E judged) {
        if (overrides == null || overrides.isEmpty()) {
            return judged;
        }
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null) {
                try {
                    return Enum.valueOf(type, entry.getValue().trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // A value nobody recognises is not an instruction. The judgement stands, and
                    // the caller reports the typo rather than acting on half of it.
                    return judged;
                }
            }
        }
        return judged;
    }

    private static Set<String> upper(Set<String> names) {
        if (names == null || names.isEmpty()) {
            return Set.of();
        }
        Set<String> upper = new java.util.HashSet<>(names.size());
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                upper.add(name.trim().toUpperCase(Locale.ROOT));
            }
        }
        return upper;
    }

    private static Map<Habitat, List<String>> freeze(Map<Habitat, List<String>> table) {
        Map<Habitat, List<String>> frozen = new LinkedHashMap<>();
        table.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        return Map.copyOf(frozen);
    }

    private static Map<Habitat, Map<String, List<String>>> freezeSwap(
            Map<Habitat, Map<String, List<String>>> table) {
        Map<Habitat, Map<String, List<String>>> frozen = new LinkedHashMap<>();
        table.forEach((habitat, bodies) -> {
            Map<String, List<String>> inner = new LinkedHashMap<>();
            bodies.forEach((body, names) -> inner.put(body, List.copyOf(names)));
            frozen.put(habitat, Map.copyOf(inner));
        });
        return Map.copyOf(frozen);
    }
}
