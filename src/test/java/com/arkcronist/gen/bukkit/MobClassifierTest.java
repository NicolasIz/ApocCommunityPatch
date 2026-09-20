package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.mythic.MobClassifier;
import com.arkcronist.gen.bukkit.mythic.MobClassifier.Habitat;
import com.arkcronist.gen.bukkit.mythic.MobClassifier.Role;
import com.arkcronist.gen.bukkit.mythic.MobFacts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Judging a mob from what its pack happens to say about it.
 *
 * <p>The names below are real: they come from the packs installed on the server this was written
 * for. A classifier tested only against names invented alongside it proves nothing, because the
 * whole difficulty is that pack authors name things for themselves.</p>
 */
class MobClassifierTest {

    private static Role roleOf(String name, String type, double health) {
        return MobClassifier.classify(new MobFacts(name, type, health, "", "")).role();
    }

    private static Habitat homeOf(String name, String type) {
        return MobClassifier.classify(new MobFacts(name, type, 20.0, "", "")).habitat();
    }

    @Test
    @DisplayName("an armour stand is scenery however much health it has")
    void armourStandsAreNeverEnemies() {
        // The one that costs a world: skeleton_mage_proj is the mage's bolt. In a spawn list it
        // fills the map with invisible armour stands.
        assertEquals(Role.PROP, roleOf("skeleton_mage_proj", "ARMOR_STAND", 1.0));
        assertEquals(Role.PROP, roleOf("Lava_Geyser", "ARMOR_STAND", 40.0));
        assertEquals(Role.PROP, roleOf("spdr_stomp_vfx", "ARMOR_STAND", 2.0));
        // Even a name that reads like an enemy loses to the base type.
        assertEquals(Role.PROP, roleOf("dread_knight", "ARMOR_STAND", 900.0));
    }

    @Test
    @DisplayName("effects wearing an animal's body are still effects")
    void effectsBorrowingLivingBodies() {
        // Real cases: the cursed pack's effects are chickens with a thousand health, and the tree
        // ent pack's thrown rocks and decoys are wolves.
        assertEquals(Role.PROP, roleOf("cursed_arrow_vfx", "CHICKEN", 1000.0));
        assertEquals(Role.PROP, roleOf("vfx_rock_rubble", "WOLF", 20.0));
        assertEquals(Role.PROP, roleOf("oak_hurler_boulder", "WOLF", 20.0));
        assertEquals(Role.PROP, roleOf("aspen_decoy_tree_ent", "WOLF", 40.0));
        assertEquals(Role.PROP, roleOf("sakura_tree_ent_stump", "WOLF", 40.0));
        assertEquals(Role.PROP, roleOf("spider_trap", "HUSK", 10.0));
    }

    @Test
    @DisplayName("ordinary enemies come out hostile")
    void ordinaryEnemies() {
        assertEquals(Role.HOSTILE, roleOf("am_goblin_melee", "ZOMBIE", 30.0));
        assertEquals(Role.HOSTILE, roleOf("skeleton_archer", "SKELETON", 24.0));
        assertEquals(Role.HOSTILE, roleOf("frostmite", "VINDICATOR", 40.0));
        assertEquals(Role.HOSTILE, roleOf("oak_tree_ent", "HUSK", 80.0));
        assertEquals(Role.HOSTILE, roleOf("Hellhound", "ZOMBIFIED_PIGLIN", 45.0));
    }

    @Test
    @DisplayName("a boss is caught by its health before anybody has to name it one")
    void bossesByHealth() {
        assertEquals(Role.BOSS, roleOf("kraken", "SQUID", 2000.0));
        assertEquals(Role.BOSS, roleOf("some_unnamed_thing", "ZOMBIE", 250.0));
        // Just under the line stays an ordinary enemy.
        assertEquals(Role.HOSTILE, roleOf("tough_goblin", "ZOMBIE", 249.0));
    }

    @Test
    @DisplayName("a boss is caught by its name when its health does not say")
    void bossesByName() {
        assertEquals(Role.BOSS, roleOf("castle_lord", "EVOKER", 40.0));
        assertEquals(Role.BOSS, roleOf("Blaze_King", "BLAZE", 60.0));
        assertEquals(Role.BOSS, roleOf("corrupted_golem_king", "IRON_GOLEM", 100.0));
        assertEquals(Role.BOSS, roleOf("astral_archon", "WITHER_SKELETON", 90.0));
        assertEquals(Role.BOSS, roleOf("deep_leviathan", "ELDER_GUARDIAN", 80.0));
        assertEquals(Role.BOSS, roleOf("witch_matron", "WITCH", 60.0));
    }

    @Test
    @DisplayName("pets are not enemies")
    void pets() {
        assertEquals(Role.PET, roleOf("anubis_pet", "HUSK", 40.0));
        assertEquals(Role.PET, roleOf("phoenix_PET", "BLAZE", 60.0));
        assertEquals(Role.PET, roleOf("Blaze_Minion", "BLAZE", 20.0));
        assertEquals(Role.PET, roleOf("piglin_mech_mount", "HOGLIN", 80.0));
    }

    @Test
    @DisplayName("a name alone is not enough to call something an enemy")
    void namesAloneDecideNothing() {
        // A pack this build cannot read properly gives back a name and nothing else. Guessing from
        // that is how somebody's pet ends up in a spawn table.
        assertEquals(Role.UNKNOWN, MobClassifier.classify(MobFacts.of("mystery_thing")).role());
        // But a name that says prop or boss is still worth acting on.
        assertEquals(Role.PROP, MobClassifier.classify(MobFacts.of("something_vfx")).role());
        assertEquals(Role.BOSS, MobClassifier.classify(MobFacts.of("frost_king")).role());
    }

    @Test
    @DisplayName("the base entity type places a mob before its name gets a say")
    void habitatFromType() {
        assertEquals(Habitat.NETHER, homeOf("Lava_Mite", "ZOMBIFIED_PIGLIN"));
        assertEquals(Habitat.NETHER, homeOf("dSkeleton_Tank_Cinder_em", "WITHER_SKELETON"));
        assertEquals(Habitat.END, homeOf("void_walker", "SHULKER"));
        assertEquals(Habitat.OVERWORLD, homeOf("am_goblin_melee", "ZOMBIE"));
    }

    @Test
    @DisplayName("a name places a mob when its base type says nothing")
    void habitatFromName() {
        assertEquals(Habitat.NETHER, homeOf("Nether_Mushroom", "ZOMBIE"));
        // And the price of whole-word matching, kept here on purpose: 'hellbark' is one word, so
        // the 'hell' inside it does not count and this tree ent is placed in the overworld. That is
        // the same rule that stops 'legend' counting as the End, and it cannot have one without the
        // other. A pack whose naming loses out this way is what the habitat override in config is
        // for; guessing at fragments would cost far more than it saves.
        assertEquals(Habitat.OVERWORLD, homeOf("hellbark_tree_ent", "HUSK"));
        assertEquals(Habitat.NETHER, homeOf("Dark_Imp", "ZOMBIE"));
        assertEquals(Habitat.END, homeOf("ender_wraith", "ZOMBIE"));
        assertEquals(Habitat.END, homeOf("astral_archon", "ZOMBIE"));
        assertEquals(Habitat.OVERWORLD, homeOf("frostmite", "VINDICATOR"));
    }

    @Test
    @DisplayName("a harmless figure on a passive body is scenery, not a mob")
    void decorativeNpc() {
        // From a real pack of decorative NPCs: a pig with the AI stripped out, no damage, and a
        // farmer model on top. Forty-five of them were judged HOSTILE and would have been spawned
        // around players as monsters.
        MobFacts farmer = new MobFacts("scene_farmer_ground", "PIG", 5.0, "", "", 0.0);
        assertEquals(Role.PROP, MobClassifier.classify(farmer).role());

        // Both halves have to be true. A caster does its harm through skills and still has a
        // hostile body, so no damage on its own must not condemn it.
        MobFacts mage = new MobFacts("skeleton_mage", "SKELETON", 30.0, "", "", 0.0);
        assertEquals(Role.HOSTILE, MobClassifier.classify(mage).role());

        // And a passive body on its own is a look, not a role: a goblin on a pig that hits back
        // is still a goblin.
        MobFacts goblin = new MobFacts("am_goblin_melee", "PIG", 20.0, "", "", 4.0);
        assertEquals(Role.HOSTILE, MobClassifier.classify(goblin).role());
    }

    @Test
    @DisplayName("a MythicMobs that will not say the damage is not taken as zero")
    void damageUnknownIsNotZero() {
        // The five-argument constructor is every caller written before damage was read, and the
        // reader uses the same value when no getter answers. If unknown collapsed into zero, a
        // MythicMobs version without the getter would turn every passive-bodied mob on the server
        // into scenery and empty the spawn pools.
        MobFacts sinDato = new MobFacts("am_goblin_melee", "PIG", 20.0, "", "");
        assertEquals(MobFacts.UNKNOWN, sinDato.damage());
        assertEquals(Role.HOSTILE, MobClassifier.classify(sinDato).role());
    }

    @Test
    @DisplayName("the Nether's own place names place a mob there")
    void habitatFromNetherBiomeName() {
        // A pack that fills the Nether biome by biome names its mobs after the biomes, and every
        // one of those names reads as overworld to the words above: a forest, a valley, a waste.
        // An enderman expansion doing exactly that is what put these here.
        assertEquals(Habitat.NETHER, homeOf("enderman_crimson_forest", "ENDERMAN"));
        assertEquals(Habitat.NETHER, homeOf("enderman_warped_forest", "ENDERMAN"));
        assertEquals(Habitat.NETHER, homeOf("enderman_soulsand_valley", "ENDERMAN"));
        assertEquals(Habitat.NETHER, homeOf("basalt_crawler", "SPIDER"));
        assertEquals(Habitat.NETHER, homeOf("bastion_raider", "VINDICATOR"));
        // And the overworld biomes keep theirs, which is the whole point of adding only the names
        // that belong to one place and no other.
        assertEquals(Habitat.OVERWORLD, homeOf("enderman_dark_oak", "ENDERMAN"));
        assertEquals(Habitat.OVERWORLD, homeOf("enderman_flower_fields", "ENDERMAN"));
        assertEquals(Habitat.OVERWORLD, homeOf("enderman_ice_spikes", "ENDERMAN"));
    }

    @Test
    @DisplayName("a word inside another word is not that word")
    void wholeWordsOnly() {
        // This is where a substring match quietly ruins everything: 'end' lives inside 'legend' and
        // 'defender', 'imp' inside 'impaler', 'ash' inside 'ashen' - which is fine - but also
        // inside 'washer'.
        assertEquals(Habitat.OVERWORLD, homeOf("legend_knight", "ZOMBIE"));
        assertEquals(Habitat.OVERWORLD, homeOf("village_defender", "ZOMBIE"));
        assertEquals(Habitat.OVERWORLD, homeOf("bone_impaler", "SKELETON"));
        assertNotEquals(Role.PROP, roleOf("trapper_goblin", "ZOMBIE", 30.0),
                "'trap' inside 'trapper' must not make a goblin scenery");
    }

    @Test
    @DisplayName("camelCase is read as words too")
    void camelCase() {
        assertEquals(Role.PROP, roleOf("SpiderStompVfx", "HUSK", 20.0));
        assertEquals(Role.BOSS, roleOf("FrostKing", "STRAY", 90.0));
        assertEquals(Habitat.NETHER, homeOf("LavaWraith", "ZOMBIE"));
    }

    @Test
    @DisplayName("every verdict says why, because one nobody can question is worse than none")
    void verdictsExplainThemselves() {
        for (MobFacts facts : new MobFacts[]{
                new MobFacts("skeleton_mage_proj", "ARMOR_STAND", 1.0, "", ""),
                new MobFacts("castle_lord", "EVOKER", 40.0, "", ""),
                new MobFacts("am_goblin_melee", "ZOMBIE", 30.0, "", ""),
                MobFacts.of("mystery_thing")}) {
            String because = MobClassifier.classify(facts).because();
            assertTrue(because != null && !because.isBlank(),
                    facts.name() + " was judged without a reason");
        }
    }

    @Test
    @DisplayName("a boss threshold of zero turns the health rule off instead of catching everything")
    void thresholdOff() {
        assertEquals(Role.HOSTILE,
                MobClassifier.classify(new MobFacts("big_thing", "ZOMBIE", 5000.0, "", ""), 0.0).role());
    }

    @Test
    @DisplayName("an interface part is scenery however alive its body looks")
    void interfacePartsAreProps() {
        // A health bar pack builds its bar out of an invisible, invincible, AI-less mob that follows
        // its owner. Nothing about the body says so - the one this was written for is a VEX, which
        // is a perfectly ordinary enemy elsewhere - and before the faction was read, discovery put
        // it in the ambient pool.
        MobFacts bar = new MobFacts("Healthbar_base_em_template", "VEX", 0.0, "xd", "GUI");
        MobClassifier.Verdict v = MobClassifier.classify(bar);
        assertEquals(Role.PROP, v.role(), "a GUI part was judged " + v.role() + ": " + v.because());
        assertTrue(v.because().contains("GUI"), "the reason did not name the faction: " + v.because());

        // The name alone is enough too, for a pack that files nothing under a faction.
        for (String name : new String[]{"boss_healthbar", "mob_nameplate", "shop_gui_anchor",
                "skeleton_template"}) {
            assertEquals(Role.PROP, MobClassifier.classify(
                    new MobFacts(name, "VEX", 40.0, "", "")).role(), name + " was not caught");
        }
        // And an ordinary faction is not a refusal.
        assertNotEquals(Role.PROP, MobClassifier.classify(
                new MobFacts("am_goblin_melee", "ZOMBIE", 30.0, "Goblin", "Skeleton")).role());
    }

    @Test
    @DisplayName("a peaceful body is never something to stand in for")
    void peacefulBodiesAreNotSwapKeys() {
        // A pack author picks a body for how it looks and moves. Reading "built on a wolf" as "replace
        // every wolf in the world" is the plugin inventing an instruction nobody gave.
        for (String body : new String[]{"COW", "SHEEP", "WOLF", "SQUID", "BAT", "VILLAGER",
                "IRON_GOLEM", "DOLPHIN", "STRIDER", "cow"}) {
            assertFalse(MobClassifier.standsInFor(body), body + " was made a stand-in");
        }
        for (String body : new String[]{"ZOMBIE", "SKELETON", "BLAZE", "MAGMA_CUBE", "GHAST",
                "WITHER_SKELETON", "PILLAGER", "zombie"}) {
            assertTrue(MobClassifier.standsInFor(body), body + " cannot be stood in for");
        }
        // Nothing to judge is not a body either.
        assertFalse(MobClassifier.standsInFor(""));
        assertFalse(MobClassifier.standsInFor(null));
    }
}
