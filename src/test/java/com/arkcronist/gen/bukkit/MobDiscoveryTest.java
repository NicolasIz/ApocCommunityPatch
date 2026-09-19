package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.mythic.MobClassifier.Habitat;
import com.arkcronist.gen.bukkit.mythic.MobClassifier.Role;
import com.arkcronist.gen.bukkit.mythic.MobDiscovery;
import com.arkcronist.gen.bukkit.mythic.MobFacts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sorting a whole catalogue into what may spawn where.
 *
 * <p>The catalogue below is a slice of the real one on the server this was written for: goblins, the
 * volcanic skeletons, a Nether pack, tree ents, the ice pack, and the props each of them ships. The
 * one outcome that must never happen is a prop reaching either list.</p>
 */
class MobDiscoveryTest {

    private static final double BOSS_HEALTH = 250.0;

    private static final List<MobFacts> CATALOGUE = List.of(
            // Ordinary enemies.
            new MobFacts("am_goblin_melee", "ZOMBIE", 30.0, "Goblin", ""),
            new MobFacts("skeleton_archer", "SKELETON", 24.0, "Skeleton Archer", ""),
            new MobFacts("frostmite", "VINDICATOR", 40.0, "Frostmite", ""),
            new MobFacts("oak_tree_ent", "HUSK", 80.0, "Oak Tree Ent", ""),
            // Nether, by base type and by name.
            new MobFacts("dSkeleton_Tank_Cinder_em", "WITHER_SKELETON", 60.0, "Cinder Tank", ""),
            new MobFacts("Nether_Mushroom", "ZOMBIE", 30.0, "Nether Mushroom", ""),
            // Bosses.
            new MobFacts("Blaze_King", "BLAZE", 60.0, "Blaze King", ""),
            new MobFacts("kraken", "SQUID", 2000.0, "Kraken", ""),
            // Props, one of each shape a pack ships them in.
            new MobFacts("skeleton_mage_proj", "ARMOR_STAND", 1.0, "", ""),
            new MobFacts("cursed_arrow_vfx", "CHICKEN", 1000.0, "", ""),
            new MobFacts("oak_hurler_boulder", "WOLF", 20.0, "", ""),
            // An enemy built on a peaceful body, which packs do for how it looks and moves.
            new MobFacts("dire_beast", "WOLF", 40.0, "Dire Beast", ""),
            // A pet.
            new MobFacts("anubis_pet", "HUSK", 40.0, "Anubis", ""),
            // Nothing readable but a name.
            MobFacts.of("mystery_thing"));

    private static MobDiscovery.Result sorted() {
        return MobDiscovery.sort(CATALOGUE, BOSS_HEALTH, Set.of(), Map.of(), Map.of());
    }

    /** Every name discovery would let spawn by itself or as a stand-in, across every habitat. */
    private static List<String> everythingSpawnable(MobDiscovery.Result result) {
        List<String> spawnable = new ArrayList<>();
        result.hostiles().values().forEach(spawnable::addAll);
        result.bosses().values().forEach(spawnable::addAll);
        result.swapByHabitat().values()
                .forEach(bodies -> bodies.values().forEach(spawnable::addAll));
        return spawnable;
    }

    @Test
    @DisplayName("no prop reaches any spawn list, which is the whole point")
    void propsNeverSpawn() {
        MobDiscovery.Result result = sorted();
        List<String> spawnable = everythingSpawnable(result);

        for (String prop : List.of("skeleton_mage_proj", "cursed_arrow_vfx", "oak_hurler_boulder")) {
            assertFalse(spawnable.contains(prop), prop + " reached a spawn list");
            assertTrue(result.props().contains(prop), prop + " was not reported as a prop");
        }
    }

    @Test
    @DisplayName("hostiles are sorted by where they belong")
    void hostilesByHabitat() {
        MobDiscovery.Result result = sorted();
        List<String> overworld = result.hostilesFor(Habitat.OVERWORLD);
        List<String> nether = result.hostilesFor(Habitat.NETHER);

        assertTrue(overworld.containsAll(List.of("am_goblin_melee", "skeleton_archer", "frostmite",
                "oak_tree_ent")), "overworld pool was " + overworld);
        assertTrue(nether.containsAll(List.of("dSkeleton_Tank_Cinder_em", "Nether_Mushroom")),
                "nether pool was " + nether);
        // The one that would be noticed on a green hillside.
        assertFalse(overworld.contains("dSkeleton_Tank_Cinder_em"),
                "a volcanic skeleton reached the overworld pool");
    }

    @Test
    @DisplayName("bosses are kept out of everything that spawns on its own")
    void bossesStayApart() {
        MobDiscovery.Result result = sorted();
        List<String> hostiles = new ArrayList<>();
        result.hostiles().values().forEach(hostiles::addAll);
        result.swapByHabitat().values()
                .forEach(bodies -> bodies.values().forEach(hostiles::addAll));

        for (String boss : List.of("Blaze_King", "kraken")) {
            assertFalse(hostiles.contains(boss), boss + " can spawn on its own, so it is not a boss");
        }
        List<String> allBosses = new ArrayList<>();
        result.bosses().values().forEach(allBosses::addAll);
        assertTrue(allBosses.containsAll(List.of("Blaze_King", "kraken")), "bosses were " + allBosses);
        // Sorted the same way the hostiles are, because a Nether boss in an overworld castle is
        // the same mistake one dimension up.
        assertTrue(result.bossesFor(Habitat.NETHER).contains("Blaze_King"),
                "nether bosses were " + result.bossesFor(Habitat.NETHER));
    }

    @Test
    @DisplayName("a discovered mob replaces the vanilla mob its author built it on")
    void swapKeyedByBody() {
        MobDiscovery.Result result = sorted();
        Map<String, List<String>> overworld = result.swapFor(Habitat.OVERWORLD);

        assertEquals(List.of("am_goblin_melee"), overworld.get("ZOMBIE"));
        assertEquals(List.of("skeleton_archer"), overworld.get("SKELETON"));
        assertEquals(List.of("oak_tree_ent"), overworld.get("HUSK"));
        // A boss is not a stand-in for anything, and a prop's body must not appear at all.
        assertFalse(overworld.containsKey("BLAZE"), "a boss was made the stand-in for every blaze");
        assertFalse(overworld.containsKey("ARMOR_STAND"));
        assertFalse(overworld.containsKey("CHICKEN"));
    }

    @Test
    @DisplayName("a body shared by two habitats does not leak a nether mob into a forest")
    void swapStaysInItsHabitat() {
        // Both packs build on a zombie: the goblin is an overworld mob and the mushroom is a nether
        // one. Keyed by body alone they share the ZOMBIE bucket and half of the zombies in a forest
        // come out of the nether - which is what this asserts cannot happen.
        MobDiscovery.Result result = sorted();

        assertEquals(List.of("am_goblin_melee"), result.swapFor(Habitat.OVERWORLD).get("ZOMBIE"));
        assertEquals(List.of("Nether_Mushroom"), result.swapFor(Habitat.NETHER).get("ZOMBIE"));
        assertFalse(result.swapFor(Habitat.OVERWORLD).containsKey("WITHER_SKELETON"),
                "a volcanic skeleton stands in for overworld spawns");
        assertTrue(result.swapFor(Habitat.END).isEmpty(),
                "the end was given mobs nothing said belonged there");
    }

    @Test
    @DisplayName("an enemy built on a peaceful body does not take every wolf in the world with it")
    void peacefulBodiesAreNotStoodInFor() {
        // "Built on a wolf" is a statement about how it looks, not an instruction to stop wolves
        // appearing. The mob is still perfectly usable - it is in the ambient pool - so nothing is
        // lost by refusing to guess this one.
        MobDiscovery.Result result = sorted();

        assertFalse(result.swapFor(Habitat.OVERWORLD).containsKey("WOLF"),
                "every wolf in the world was made a dire beast");
        assertTrue(result.hostilesFor(Habitat.OVERWORLD).contains("dire_beast"),
                "refusing the swap key also dropped the mob, which was not the point");
    }

    @Test
    @DisplayName("a mob that fits anywhere is offered in every habitat")
    void anywhereReachesEveryHabitat() {
        // Told by config to fit anywhere, the goblin must be a stand-in for zombies in the nether as
        // well - otherwise ANY would mean the same as nothing.
        MobDiscovery.Result result = MobDiscovery.sort(CATALOGUE, BOSS_HEALTH, Set.of(), Map.of(),
                Map.of("am_goblin_melee", "ANY"));

        assertTrue(result.swapFor(Habitat.NETHER).get("ZOMBIE").contains("am_goblin_melee"));
        assertTrue(result.swapFor(Habitat.OVERWORLD).get("ZOMBIE").contains("am_goblin_melee"));
        assertTrue(result.swapFor(Habitat.NETHER).get("ZOMBIE").contains("Nether_Mushroom"),
                "merging in the anywhere mobs dropped the nether's own");
        assertTrue(result.hostilesFor(Habitat.END).contains("am_goblin_melee"));
    }

    @Test
    @DisplayName("pets and the unreadable are set aside, not quietly dropped")
    void setAsideAndReported() {
        MobDiscovery.Result result = sorted();
        assertEquals(List.of("anubis_pet"), result.pets());
        assertEquals(List.of("mystery_thing"), result.unknown());
        assertEquals(CATALOGUE.size(), result.total(),
                "every mob must be accounted for, judged one way or another");
    }

    @Test
    @DisplayName("config can overrule any verdict, and says so in the reason")
    void overrides() {
        MobDiscovery.Result result = MobDiscovery.sort(CATALOGUE, BOSS_HEALTH, Set.of(),
                Map.of("frostmite", "BOSS", "mystery_thing", "hostile"),
                Map.of("oak_tree_ent", "NETHER"));

        assertEquals(Role.BOSS, result.verdicts().get("frostmite").role());
        assertEquals(Role.HOSTILE, result.verdicts().get("mystery_thing").role(),
                "an override written in lower case was ignored");
        assertEquals(Habitat.NETHER, result.verdicts().get("oak_tree_ent").habitat());
        assertEquals("set in config", result.verdicts().get("frostmite").because());
        assertTrue(result.hostilesFor(Habitat.NETHER).contains("oak_tree_ent"));
        assertTrue(result.swapFor(Habitat.NETHER).get("HUSK").contains("oak_tree_ent"),
                "the swap table ignored the habitat it was told to use");
        assertFalse(result.swapFor(Habitat.OVERWORLD).containsKey("HUSK"),
                "a mob moved to the nether still replaces husks in the overworld");
    }

    @Test
    @DisplayName("an override nobody recognises leaves the judgement standing")
    void nonsenseOverride() {
        // Half-applying a typo is worse than ignoring it: the name would vanish from every list
        // with nothing to say why.
        MobDiscovery.Result result = MobDiscovery.sort(CATALOGUE, BOSS_HEALTH, Set.of(),
                Map.of("frostmite", "BOSSS"), Map.of());
        assertEquals(Role.HOSTILE, result.verdicts().get("frostmite").role());
        assertTrue(result.hostilesFor(Habitat.OVERWORLD).contains("frostmite"));
    }

    @Test
    @DisplayName("an excluded name goes nowhere, whatever it was judged to be")
    void exclusions() {
        MobDiscovery.Result result = MobDiscovery.sort(CATALOGUE, BOSS_HEALTH,
                Set.of("AM_GOBLIN_MELEE"), Map.of(), Map.of());
        assertFalse(result.hostilesFor(Habitat.OVERWORLD).contains("am_goblin_melee"),
                "an excluded name still spawns; case-insensitive matching is the point");
        assertFalse(everythingSpawnable(result).contains("am_goblin_melee"));
    }

    @Test
    @DisplayName("an empty catalogue is an empty answer, not a crash")
    void nothingLoaded() {
        MobDiscovery.Result result = MobDiscovery.sort(List.of(), BOSS_HEALTH, Set.of(), Map.of(),
                Map.of());
        assertEquals(0, result.total());
        assertTrue(result.hostilesFor(Habitat.OVERWORLD).isEmpty());
        assertTrue(result.swapFor(Habitat.OVERWORLD).isEmpty());
    }
}
