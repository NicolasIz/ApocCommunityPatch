package com.arkcronist.gen.bukkit.mythic;

import com.arkcronist.gen.bukkit.config.ArkConfig;
import com.arkcronist.gen.bukkit.mythic.MobClassifier.Habitat;
import org.bukkit.World;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * What MythicMobs has loaded, read once and kept, so the spawn paths can use it without asking again.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Every mob pack added to the server used to mean another release of this plugin: its names went
 * into {@code hostile-mobs.table} and {@code hostile-mobs.ambient} by hand, and until that happened
 * the pack did nothing in a generated world. This asks MythicMobs what it actually has, sorts it with
 * {@link MobClassifier}, and hands the result to the same two code paths the hand-written tables feed.
 * A new pack dropped into {@code plugins/MythicMobs/Mobs/} is then live after a restart with nothing
 * edited.</p>
 *
 * <h2>Timing, which is the only subtle part</h2>
 *
 * <p>MythicMobs loads its packs during its own {@code onEnable}, and plugin enable order is not
 * something a plugin may rely on. Reading the catalogue from inside this plugin's {@code onEnable}
 * therefore finds an empty one about as often as not. {@link #scheduleFirstRead} defers the read to
 * the first server tick instead, which is after every plugin has enabled - the one point where the
 * question has a stable answer.</p>
 *
 * <p>A pack added while the server runs, or a MythicMobs reload, is not noticed on its own: there is
 * no portable event to hang that on across 4.x and 5.x, and reflection on an event class is a worse
 * bet than a command. {@code /ag mythic refresh} and {@code /ag reload} both re-read it.</p>
 *
 * <h2>Why the merged tables are cached</h2>
 *
 * <p>{@link #swapTable} is asked on every natural spawn, which on a populated server is hundreds of
 * calls a second, and merging two maps allocates. So the merge happens when something changes - a
 * read, a reload - and never on the spawn path, for exactly the reason {@link ArkConfig} parses its
 * own tables in its constructor rather than per call.</p>
 *
 * <p>The cache is keyed on the {@link ArkConfig} instance because a reload replaces that object
 * wholesale. A config this has not merged against yet is merged on first use, which also covers the
 * window between the plugin enabling and the first tick: during it the configured tables work exactly
 * as they did before this class existed, with nothing discovered added yet.</p>
 */
public final class MobRoster {

    private static final MobDiscovery.Result NOTHING = MobDiscovery.sort(List.of(),
            MobClassifier.DEFAULT_BOSS_HEALTH, java.util.Set.of(), Map.of(), Map.of());

    /** The configured tables and the discovered ones already merged, for one {@link ArkConfig}. */
    private record Merged(ArkConfig config,
                          Map<Habitat, Map<String, List<String>>> swap,
                          Map<Habitat, List<String>> pools) {
    }

    private volatile MobDiscovery.Result result = NOTHING;
    private volatile String state = "not read yet";
    private volatile boolean on;
    private volatile Merged merged;
    private volatile BiomeThemes.Result placement = BiomeThemes.Result.NONE;
    private volatile java.util.function.Supplier<List<BiomeProfile>> biomeSource;

    /** The habitat a world counts as, which is the whole of how a verdict reaches a world. */
    public static Habitat habitatOf(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> Habitat.NETHER;
            case THE_END -> Habitat.END;
            default -> Habitat.OVERWORLD;
        };
    }

    /**
     * Reads MythicMobs and sorts what it has.
     *
     * <p>Never throws. A MythicMobs that is absent, a version whose API moved again, a catalogue that
     * comes back empty - each leaves the roster empty and says why, and every caller behaves exactly
     * as it did before this feature existed.</p>
     */
    public void refresh(ArkConfig config, Logger logger) {
        on = config.autoDiscoverEnabled();
        try {
            read(config, logger);
        } finally {
            // Whatever the read did, the merged tables must describe it. In the finally block so that
            // a failure cannot leave the spawn path holding tables from the previous catalogue.
            merged = merge(config);
        }
    }

    private void read(ArkConfig config, Logger logger) {
        placement = BiomeThemes.Result.NONE;
        if (!on) {
            result = NOTHING;
            if (config.autoDiscoverConfigured()) {
                state = "off (hostile-mobs.auto-discover.enabled is false)";
                return;
            }
            // Absent rather than refused. Said out loud once, because a config.yml written before
            // this existed has no such section and saveDefaultConfig never adds one to a file that
            // is already there - so it would otherwise read as a decision nobody made.
            state = "not in your config.yml at all; add hostile-mobs.auto-discover.enabled: true";
            logger.info("Mob auto-discovery is available but your config.yml has no"
                    + " hostile-mobs.auto-discover section. Add these lines under hostile-mobs: to"
                    + " have the plugin read MythicMobs itself instead of being told every mob name:"
                    + "  auto-discover:  /  enabled: true  /  boss-health: 250  /  swap: true"
                    + "  /  ambient: true. Nothing changes until you do.");
            return;
        }
        List<MobFacts> facts;
        try {
            facts = MythicCatalogue.read();
        } catch (RuntimeException | LinkageError failure) {
            result = NOTHING;
            state = "MythicMobs could not be read: " + failure;
            logger.warning("Mob auto-discovery could not read MythicMobs (" + failure
                    + "); the configured tables are used unchanged.");
            return;
        }
        if (facts.isEmpty()) {
            result = NOTHING;
            state = MythicCatalogue.available()
                    ? "MythicMobs answered with no mobs at all"
                    : "MythicMobs is not installed, or its API could not be reached";
            logger.info("Mob auto-discovery found nothing: " + state + ".");
            return;
        }
        result = MobDiscovery.sort(facts, config.autoDiscoverBossHealth(),
                config.autoDiscoverExclude(), config.autoDiscoverRoles(),
                config.autoDiscoverHabitats());
        state = "read " + result.total() + " mobs from MythicMobs";
        logger.info("Mob auto-discovery: " + result.total() + " mobs read - "
                + count(result.hostiles()) + " hostile, " + count(result.bosses()) + " boss, "
                + result.props().size() + " prop, " + result.pets().size() + " pet, "
                + result.unknown().size() + " undecided."
                + " Overworld " + result.hostilesFor(Habitat.OVERWORLD).size()
                + ", Nether " + result.hostilesFor(Habitat.NETHER).size()
                + ", End " + result.hostilesFor(Habitat.END).size() + "."
                + " Run /ag mythic to see every verdict.");
        placement = place(config, facts, logger);
    }

    /**
     * Where the biomes come from. Set by the plugin, which knows the server and its folders; left
     * unset, discovery simply places nothing by biome.
     */
    public void biomeSource(java.util.function.Supplier<List<BiomeProfile>> source) {
        this.biomeSource = source;
    }

    /**
     * Gives the discovered hostiles biomes from their names.
     *
     * <p>Never throws: a biome list that cannot be read leaves every mob where it was before this
     * existed - in its dimension's general mix.</p>
     */
    private BiomeThemes.Result place(ArkConfig config, List<MobFacts> facts, Logger logger) {
        java.util.function.Supplier<List<BiomeProfile>> source = biomeSource;
        if (!config.autoDiscoverBiomes() || source == null) {
            return BiomeThemes.Result.NONE;
        }
        List<BiomeProfile> biomes;
        try {
            biomes = source.get();
        } catch (RuntimeException | LinkageError failure) {
            logger.warning("Could not read the server's biomes (" + failure
                    + "); discovered mobs are not given biomes.");
            return BiomeThemes.Result.NONE;
        }
        if (biomes == null || biomes.isEmpty()) {
            return BiomeThemes.Result.NONE;
        }
        Map<String, String> shown = new java.util.HashMap<>();
        for (MobFacts mob : facts) {
            shown.put(mob.name(), mob.displayName());
        }
        List<BiomeThemes.Candidate> candidates = new java.util.ArrayList<>();
        for (Habitat habitat : new Habitat[] {Habitat.OVERWORLD, Habitat.NETHER, Habitat.END}) {
            for (String name : result.hostiles().getOrDefault(habitat, List.of())) {
                candidates.add(new BiomeThemes.Candidate(name, shown.getOrDefault(name, ""),
                        habitat));
            }
        }
        BiomeThemes.Result placed = BiomeThemes.assign(candidates, biomes,
                config.autoDiscoverBiomeOverrides());
        logger.info("Biomes: read " + biomes.size() + " (the game's and every datapack's). "
                + placed.themed().size() + " of " + candidates.size()
                + " discovered hostiles were given biomes from their names; the rest spawn"
                + " anywhere in their dimension. /ag mythic biomes lists them.");
        return placed;
    }

    /** Which discovered mobs were given which biomes. Empty when none were. */
    public BiomeThemes.Result placement() {
        return placement;
    }

    /**
     * The discovered mobs that belong to this biome, or null when none do.
     *
     * @param biomeKey the biome's namespaced key, e.g. {@code terralith:alpine_grove}
     */
    public List<String> biomeMobs(String biomeKey) {
        if (biomeKey == null || placement.byBiome().isEmpty()) {
            return null;
        }
        return placement.byBiome().get(biomeKey.toLowerCase(java.util.Locale.ROOT));
    }

    /** Defers the first read to the first server tick, after every plugin has enabled. */
    public void scheduleFirstRead(org.bukkit.plugin.Plugin plugin, ArkConfig config) {
        plugin.getServer().getScheduler().runTask(plugin, () -> refresh(config, plugin.getLogger()));
    }

    /** Whether discovery is switched on at all. */
    public boolean enabled() {
        return on;
    }

    /** What happened on the last read, for {@code /ag mythic} to print. */
    public String state() {
        return state;
    }

    /** Everything that was found and how it was judged. Empty when discovery is off. */
    public MobDiscovery.Result result() {
        return result;
    }

    /**
     * The swap table that applies in this habitat: what config.yml says, plus what discovery found
     * for the entity types it says nothing about.
     *
     * @see MobTables#swap
     */
    public Map<String, List<String>> swapTable(ArkConfig config, Habitat habitat) {
        return tables(config).swap().getOrDefault(habitat, Map.of());
    }

    /**
     * The ambient pool that applies in this habitat: the configured names, then the discovered ones.
     *
     * @see MobTables#pool
     */
    public List<String> ambientPool(ArkConfig config, Habitat habitat) {
        return tables(config).pools().getOrDefault(habitat, List.of());
    }

    /**
     * What discovery alone contributes here, for {@code /ag mythic} and {@code /ag mobs} to report.
     *
     * <p>Separate from {@link #swapTable} so a report can show what was found even with
     * {@code auto-discover.swap} off, while the spawn path sees nothing.</p>
     */
    public List<String> discoveredFor(Habitat habitat) {
        return on ? result.hostilesFor(habitat) : List.of();
    }

    private Merged tables(ArkConfig config) {
        Merged current = merged;
        if (current == null || current.config() != config) {
            // A config this has not merged against yet: the first spawn after enable, before the
            // first-tick read, or a reload that happened to come from somewhere else.
            current = merge(config);
            merged = current;
        }
        return current;
    }

    private Merged merge(ArkConfig config) {
        Map<Habitat, Map<String, List<String>>> swap = new EnumMap<>(Habitat.class);
        Map<Habitat, List<String>> pools = new EnumMap<>(Habitat.class);
        boolean useSwap = on && config.autoDiscoverSwap();
        boolean useAmbient = on && config.autoDiscoverAmbient();
        // A mob given biomes of its own is left out of the general mix, or it would still turn up
        // everywhere else - less often, but a yeti in the desert all the same.
        java.util.Set<String> placed = placement.themed();
        for (Habitat habitat : Habitat.values()) {
            Map<String, List<String>> configured = configuredSwap(config, habitat);
            List<String> pool = configuredPool(config, habitat);
            swap.put(habitat, MobTables.swap(configured,
                    useSwap ? MobTables.without(result.swapFor(habitat), placed) : Map.of()));
            pools.put(habitat, MobTables.pool(pool,
                    useAmbient ? MobTables.without(result.hostilesFor(habitat), placed)
                            : List.of()));
        }
        return new Merged(config, Map.copyOf(swap), Map.copyOf(pools));
    }

    private static Map<String, List<String>> configuredSwap(ArkConfig config, Habitat habitat) {
        return switch (habitat) {
            case NETHER -> config.netherMobTable();
            case END -> config.endMobTable();
            // ANY is not a world. Nothing asks for it, and giving it the overworld's table would make
            // a bug that offered it silently correct-looking.
            case ANY -> Map.of();
            default -> config.hostileMobTable();
        };
    }

    private static List<String> configuredPool(ArkConfig config, Habitat habitat) {
        return switch (habitat) {
            case NETHER -> config.ambientNether();
            case END -> config.ambientEnd();
            case ANY -> List.of();
            default -> config.ambientOverworld();
        };
    }

    private static int count(Map<Habitat, List<String>> table) {
        int total = 0;
        for (List<String> names : table.values()) {
            total += names.size();
        }
        return total;
    }
}
