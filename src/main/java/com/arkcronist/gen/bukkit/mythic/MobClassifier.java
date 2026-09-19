package com.arkcronist.gen.bukkit.mythic;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides, from what MythicMobs says about a mob, whether the world should produce it and where.
 *
 * <h2>This guesses, and the guessing is the whole risk</h2>
 *
 * <p>A pack author names and shapes their mobs for themselves, not for a plugin reading them later.
 * So every rule below is a heuristic, and heuristics are how a plugin ends up doing something nobody
 * asked for in silence. Two things follow from that, and they are the design rather than caveats to
 * it: the verdicts are all inspectable ({@code /ag mythic} prints every one), and the only verdict
 * this acts on without being asked is {@link Role#PROP} - which is a refusal.</p>
 *
 * <h2>Why PROP matters more than the rest put together</h2>
 *
 * <p>{@code skeleton_mage_proj} is the mage's bolt: an {@code ARMOR_STAND} with no health and no AI.
 * Put it in a spawn list and the world fills with invisible armour stands. Every pack has some -
 * {@code spdr_stomp_vfx}, {@code Lava_Geyser}, {@code cursed_arrow_vfx} - and they are the one class
 * of mistake here that is both easy to make and expensive to undo. Catching them is worth more than
 * classifying anything else correctly.</p>
 */
public final class MobClassifier {

    /** What the world should do with a mob. */
    public enum Role {
        /** A projectile, a marker, a visual effect. Never spawn it. */
        PROP,
        /** Somebody's companion. Not an enemy. */
        PET,
        /** An ordinary enemy: fine for garrisons and for the ambient spawner. */
        HOSTILE,
        /** Far too strong for a random spawn. Belongs to a structure. */
        BOSS,
        /** Nothing said enough to decide. Left out of everything until somebody says otherwise. */
        UNKNOWN
    }

    /** Where a mob belongs. */
    public enum Habitat { OVERWORLD, NETHER, END, ANY }

    /** Health at or above which a mob is treated as a boss, when the config names no other figure. */
    public static final double DEFAULT_BOSS_HEALTH = 250.0;

    /**
     * Base entity types that are never an enemy, whatever the mob is called.
     *
     * <p>{@code ARMOR_STAND} is the one that matters. The rest are the bodies packs borrow for a
     * visual effect - a chicken carrying a particle trail, a wolf standing in for a thrown rock -
     * and a wolf with a thousand health is still not something to put in a spawn table.</p>
     */
    private static final Set<String> PROP_TYPES = Set.of(
            "ARMOR_STAND", "AREA_EFFECT_CLOUD", "MARKER", "INTERACTION", "TEXT_DISPLAY",
            "ITEM_DISPLAY", "BLOCK_DISPLAY", "FALLING_BLOCK", "ARROW", "SNOWBALL", "FIREBALL",
            "SMALL_FIREBALL", "DRAGON_FIREBALL", "SHULKER_BULLET", "WITHER_SKULL", "LLAMA_SPIT",
            "TRIDENT", "EGG", "ENDER_PEARL", "EXPERIENCE_ORB", "ITEM", "PAINTING", "LEASH_KNOT");

    /** Word fragments that mark a name as machinery rather than a creature. */
    private static final Set<String> PROP_WORDS = Set.of(
            "vfx", "fx", "proj", "projectile", "marker", "hitbox", "collider", "trap", "aura",
            "beam", "ray", "trail", "spawner", "controller", "dummy", "decoy", "stump", "rubble",
            "boulder", "geyser", "crate", "extras", "showcase");

    private static final Set<String> PET_WORDS = Set.of("pet", "pets", "companion", "mount", "minion");

    /**
     * Vanilla types a discovered mob must never be made the stand-in for.
     *
     * <p>A pack author picks a body for how it looks and moves, not to say which vanilla mob should
     * stop appearing. A hostile built on a {@code WOLF} because it needed four legs is a real thing
     * packs do - and keying the swap table on that body would replace every wolf in the world, which
     * is not remotely what choosing the body meant.</p>
     *
     * <p>So these mobs are never stood in for. The discovered mob is still perfectly usable: it joins
     * its habitat's ambient pool and turns up in the world that way, and it can be named by hand in
     * {@code hostile-mobs.biome-table} or in the entity table by anybody who really does mean it. The
     * only thing refused is guessing it from the body.</p>
     *
     * <p>The list is the passive and neutral half of the vanilla roster, kept here rather than asked
     * of Bukkit so this class stays testable without a server. {@link
     * com.arkcronist.gen.bukkit.mobs.HostileSwapListener} asks Bukkit the same question a second
     * time, with {@code org.bukkit.entity.Enemy}, at the point where it matters.</p>
     */
    private static final Set<String> NEVER_STOOD_IN_FOR = Set.of(
            // Farm and wild animals.
            "COW", "MOOSHROOM", "SHEEP", "PIG", "CHICKEN", "RABBIT", "HORSE", "DONKEY", "MULE",
            "SKELETON_HORSE", "ZOMBIE_HORSE", "LLAMA", "TRADER_LLAMA", "CAMEL", "GOAT", "PANDA",
            "POLAR_BEAR", "FOX", "WOLF", "CAT", "OCELOT", "PARROT", "BEE", "SNIFFER", "ARMADILLO",
            "TURTLE", "FROG", "TADPOLE", "AXOLOTL", "STRIDER", "HAPPY_GHAST",
            // Water.
            "SQUID", "GLOW_SQUID", "DOLPHIN", "COD", "SALMON", "TROPICAL_FISH", "PUFFERFISH",
            // Bats, villagers and the built things.
            "BAT", "VILLAGER", "WANDERING_TRADER", "IRON_GOLEM", "SNOW_GOLEM", "ALLAY",
            "SNIFFER_EGG", "PLAYER", "NPC");

    /**
     * Whether a discovered mob built on this vanilla type may be made its stand-in.
     *
     * @see #NEVER_STOOD_IN_FOR
     */
    public static boolean standsInFor(String entityType) {
        return entityType != null && !entityType.isBlank()
                && !NEVER_STOOD_IN_FOR.contains(entityType.trim().toUpperCase(Locale.ROOT));
    }

    private static final Set<String> BOSS_WORDS = Set.of(
            "boss", "king", "queen", "lord", "elder", "archon", "overlord", "titan", "ancient",
            "warden", "matron", "champion", "monarch", "emperor", "god", "avatar", "leviathan");

    /** Vanilla types that only exist below. */
    private static final Set<String> NETHER_TYPES = Set.of(
            "BLAZE", "ZOMBIFIED_PIGLIN", "PIG_ZOMBIE", "WITHER_SKELETON", "MAGMA_CUBE", "GHAST",
            "PIGLIN", "PIGLIN_BRUTE", "HOGLIN", "ZOGLIN", "STRIDER", "WITHER");

    /** Vanilla types that only exist out there. */
    private static final Set<String> END_TYPES = Set.of("SHULKER", "ENDERMITE", "ENDER_DRAGON");

    private static final Set<String> NETHER_WORDS = Set.of(
            "nether", "lava", "hell", "hellish", "magma", "blaze", "infernal", "inferno", "ember",
            "cinder", "brimstone", "flame", "fire", "scorch", "ash", "demon", "imp", "soul");

    private static final Set<String> END_WORDS = Set.of(
            "end", "ender", "void", "chorus", "shulker", "astral", "abyss", "cosmic", "starlit");

    private MobClassifier() {
    }

    /** A verdict, with the reason it was reached, because a verdict nobody can question is worse. */
    public record Verdict(Role role, Habitat habitat, String because) {
    }

    /** Classifies one mob against the default boss threshold. */
    public static Verdict classify(MobFacts facts) {
        return classify(facts, DEFAULT_BOSS_HEALTH);
    }

    /**
     * Classifies one mob.
     *
     * @param bossHealth health at or above which a mob counts as a boss
     */
    public static Verdict classify(MobFacts facts, double bossHealth) {
        List<String> words = words(facts.name() + " " + facts.displayName());

        // Props first and unconditionally. Everything below this assumes it is looking at something
        // alive, and the cost of getting this one wrong is a world full of invisible armour stands.
        if (PROP_TYPES.contains(facts.entityType())) {
            return new Verdict(Role.PROP, Habitat.ANY,
                    "built on " + facts.entityType() + ", which is scenery rather than a creature");
        }
        for (String word : words) {
            if (PROP_WORDS.contains(word)) {
                return new Verdict(Role.PROP, Habitat.ANY, "its name contains '" + word + "'");
            }
        }

        Habitat habitat = habitat(facts, words);

        for (String word : words) {
            if (PET_WORDS.contains(word)) {
                return new Verdict(Role.PET, habitat, "its name contains '" + word + "'");
            }
        }

        if (facts.health() >= bossHealth && bossHealth > 0.0) {
            return new Verdict(Role.BOSS, habitat,
                    "health " + trim(facts.health()) + " is at or over the boss threshold "
                            + trim(bossHealth));
        }
        for (String word : words) {
            if (BOSS_WORDS.contains(word)) {
                return new Verdict(Role.BOSS, habitat, "its name contains '" + word + "'");
            }
        }

        if (facts.entityType().isEmpty() && facts.health() <= 0.0) {
            // Nothing came back but a name. Guessing from a name alone is how a pet ends up in a
            // spawn table, so this says so instead.
            return new Verdict(Role.UNKNOWN, habitat,
                    "MythicMobs gave no entity type and no health, so there is nothing to judge");
        }

        return new Verdict(Role.HOSTILE, habitat, facts.entityType().isEmpty()
                ? "health " + trim(facts.health()) + " and nothing marking it as anything else"
                : "built on " + facts.entityType() + " and nothing marking it as anything else");
    }

    private static Habitat habitat(MobFacts facts, List<String> words) {
        if (NETHER_TYPES.contains(facts.entityType())) {
            return Habitat.NETHER;
        }
        if (END_TYPES.contains(facts.entityType())) {
            return Habitat.END;
        }
        // Names are weaker evidence than a base type, so they only get asked afterwards. The End is
        // checked first: a mob called "ender_flame" belongs out there rather than below.
        for (String word : words) {
            if (END_WORDS.contains(word)) {
                return Habitat.END;
            }
        }
        for (String word : words) {
            if (NETHER_WORDS.contains(word)) {
                return Habitat.NETHER;
            }
        }
        return Habitat.OVERWORLD;
    }

    /**
     * A name broken into the words it is made of.
     *
     * <p>Whole words only, which is the difference between working and not. {@code end} appears
     * inside {@code legend}, {@code defender} and {@code bartender}; {@code imp} inside {@code
     * impaler} and {@code vampire}. Splitting on the separators a pack actually uses - underscores,
     * hyphens, spaces, and the humps of camelCase - keeps those apart.</p>
     */
    static List<String> words(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        // Split camelCase before lowering, then on everything that is not a letter or digit.
        String spaced = text.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        String[] parts = spaced.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        List<String> words = new java.util.ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isEmpty()) {
                words.add(part);
            }
        }
        return words;
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
