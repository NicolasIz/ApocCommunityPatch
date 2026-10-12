package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.definition.ContentType;
import com.arkcronist.content.core.definition.GunDefinition;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.hud.HudAnchor;
import com.arkcronist.content.core.hud.HudDefinition;
import com.arkcronist.content.core.liquid.LiquidModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.7: guns, liquids and HUDs as content files describe them. */
class ContentLoaderGameplayTest {

    @TempDir
    Path contents;

    @Test
    void aGunReadsEveryPropertyAndDefaultsTheRest() throws IOException {
        write("demo/guns.yml", """
                items:
                  shotgun:
                    material: IRON_HORSE_ARMOR
                    gun:
                      damage: 2.5
                      pellets: 8
                      spread: 7
                      range: 32
                      falloff: { start: 8, min-factor: 0.2 }
                      magazine: 6
                      ammo: bullet
                      reload-seconds: 2.2
                      fire-rate: 2
                      recoil: { pitch: 6, yaw: 1.5 }
                      targets: [players, mythic_mobs]
                      damage-type: player_attack
                      particle: none
                      sounds:
                        shoot: { sound: entity.generic.explode, volume: 1.2, pitch: 1.4 }
                        empty: none
                  wand:
                    material: STICK
                    gun: {}
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(List.of(), report.problems());
        GunDefinition shotgun = item(report, "demo:shotgun").gun();
        assertEquals(2.5, shotgun.damage());
        assertEquals(8, shotgun.pellets());
        assertEquals(7, shotgun.spread());
        assertEquals(3.5, shotgun.sneakSpread(), "half the spread by default");
        assertEquals(32, shotgun.range());
        assertEquals(1, shotgun.falloff().factor(8));
        assertEquals(0.2, shotgun.falloff().factor(32), 1e-9);
        assertEquals(6, shotgun.magazine());
        assertEquals("bullet", shotgun.ammo());
        assertEquals(44, shotgun.reloadTicks());
        assertEquals(10, shotgun.fireDelayTicks(), "two shots a second");
        assertEquals(new GunDefinition.Recoil(6, 1.5), shotgun.recoil());
        assertEquals(Set.of(GunDefinition.TargetKind.PLAYERS, GunDefinition.TargetKind.MYTHIC_MOBS), shotgun.targets());
        assertEquals("minecraft:player_attack", shotgun.damageType());
        assertNull(shotgun.particle());
        assertEquals(new GunDefinition.Sound("minecraft:entity.generic.explode", 1.2f, 1.4f), shotgun.sounds().shoot());
        assertNull(shotgun.sounds().empty());
        assertEquals(GunDefinition.Sounds.DEFAULT.reload(), shotgun.sounds().reload());

        GunDefinition wand = item(report, "demo:wand").gun();
        assertEquals(5, wand.damage());
        assertEquals(1, wand.pellets());
        assertNull(wand.ammo(), "no ammunition at all");
        assertEquals(12, wand.magazine());
        assertEquals(5, wand.fireDelayTicks());
        assertEquals(3, wand.targets().size());
        assertEquals("minecraft:crit", wand.particle());
        assertEquals(1, wand.falloff().factor(64), "no falloff unless asked for");
    }

    @Test
    void gunValuesOutOfRangeAreReportedAndReplaced() throws IOException {
        write("demo/guns.yml", """
                items:
                  broken:
                    material: STICK
                    gun:
                      pellets: 500
                      magazine: 0
                      targets: [ghosts]
                      sounds: { boom: x }
                  block_gun:
                    type: custom_block
                    resource: { texture: block/x }
                    gun: {}
                """);

        LoadReport report = new ContentLoader().load(contents);

        GunDefinition broken = item(report, "demo:broken").gun();
        assertEquals(1, broken.pellets());
        assertEquals(12, broken.magazine());
        assertEquals(3, broken.targets().size());
        List<String> problems = report.problems();
        assertProblem(problems, "demo:broken", "'pellets' should be a number from 1 to 64; using 1");
        assertProblem(problems, "demo:broken", "unknown target 'ghosts'");
        assertProblem(problems, "demo:broken", "unknown sound 'boom'");
        assertProblem(problems, "demo:block_gun", "'gun' is only read on items held in the hand");
    }

    @Test
    void aLiquidIsABucketWithAFlowAndAContact() throws IOException {
        write("demo/liquids.yml", """
                items:
                  acid:
                    type: custom_liquid
                    resource: { texture: item/acid_bucket }
                    liquid:
                      texture: block/acid
                      flow-distance: 5
                      tick-rate: 8
                      contact:
                        element: acid
                        effects:
                          - { type: poison, duration: 40 }
                          - nausea
                  frost:
                    type: custom_liquid
                    liquid:
                      texture: block/frost
                      flowing-texture: block/frost_flow
                      contact: { element: frost, damage: 0 }
                  plain:
                    type: custom_liquid
                    liquid: { model: block/my_water }
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(List.of(), report.problems());
        ItemDefinition acid = item(report, "demo:acid");
        assertEquals(ContentType.CUSTOM_LIQUID, acid.type());
        assertEquals("PAPER", acid.material());
        Placement.Liquid liquid = (Placement.Liquid) acid.placement();
        assertEquals(5, liquid.flowDistance());
        assertEquals(8, liquid.tickRate());
        assertEquals(32, liquid.maxFall());
        ModelSource.Generated source = (ModelSource.Generated) liquid.source();
        assertEquals(LiquidModels.SOURCE_PARENT, source.parent());
        assertEquals("demo:block/acid_source", source.location().toString());
        assertEquals("demo:block/acid", source.textures().get(LiquidModels.TEXTURE_VARIABLE).toString());
        assertEquals(LiquidModels.FLOWING_PARENT, ((ModelSource.Generated) liquid.flowing()).parent());
        Placement.Contact contact = liquid.contact();
        assertEquals(2, contact.damage());
        assertEquals("minecraft:magic", contact.damageType());
        assertEquals(List.of(new Placement.Effect("minecraft:poison", 40, 0),
                new Placement.Effect("minecraft:nausea", 60, 0)), contact.effects(), "effects replace the element's");

        Placement.Liquid frost = (Placement.Liquid) item(report, "demo:frost").placement();
        assertEquals(0, frost.contact().damage(), "damage: 0 overrides the element");
        assertEquals("minecraft:freeze", frost.contact().damageType());
        assertEquals(200, frost.contact().freezeTicks());
        assertEquals("minecraft:slowness", frost.contact().effects().get(0).type());
        assertEquals("demo:block/frost_flow",
                ((ModelSource.Generated) frost.flowing()).textures().get(LiquidModels.TEXTURE_VARIABLE).toString());
        // Without a look of its own, the bucket is vanilla's water bucket.
        assertEquals("minecraft:item/water_bucket", item(report, "demo:frost").model().location().toString());

        Placement.Liquid plain = (Placement.Liquid) item(report, "demo:plain").placement();
        assertInstanceOf(ModelSource.Provided.class, plain.source());
        assertTrue(plain.contact().harmless());
    }

    /** 2.0: a liquid can glow (light_emission in its own parents) and hurt mobs in it too. */
    @Test
    void aLiquidCanGlowAndHurtMobs() throws IOException {
        write("demo/liquids.yml", """
                items:
                  acid:
                    type: custom_liquid
                    liquid:
                      texture: block/acid
                      glow: 12
                      contact: { element: acid, mobs: true }
                  dim:
                    type: custom_liquid
                    liquid: { texture: block/dim, glow: 40 }
                  own:
                    type: custom_liquid
                    liquid: { model: block/a, flowing-model: block/b, glow: 5 }
                """);

        LoadReport report = new ContentLoader().load(contents);

        Placement.Liquid acid = (Placement.Liquid) item(report, "demo:acid").placement();
        assertEquals(12, acid.glow());
        assertTrue(acid.contact().mobs());
        assertEquals(LiquidModels.parentFor(true, 12), ((ModelSource.Generated) acid.source()).parent());
        assertEquals(LiquidModels.parentFor(false, 12), ((ModelSource.Generated) acid.flowing()).parent());
        assertFalse(((Placement.Liquid) item(report, "demo:dim").placement()).contact().mobs(), "players only, as before");
        assertProblem(report.problems(), "demo:dim", "glow");
        assertProblem(report.problems(), "demo:own", "sets \"light_emission\" on its elements itself");
    }

    /** 2.0: HUD bars placed against the vanilla HUD, and fed by the player's own stats or AuraSkills. */
    @Test
    void hudsCanBeAnchoredAndReadStats() throws IOException {
        write("demo/textures/hud/shield.png", "png");
        write("demo/huds.yml", """
                huds:
                  shield:
                    icons: { full: hud/shield }
                    anchor: above-health
                    value: { stat: absorption }
                    bedrock: "<gold>Shield <bar> <white><value>/<max>"
                  mana:
                    icons: { full: hud/shield }
                    anchor: { at: above-food, row: 1, nudge: [2, 1] }
                    value: { auraskills: mana }
                  high:
                    icons: { full: hud/shield }
                    anchor: { at: above-food, row: 9 }
                    ascent: 3
                    value: { stat: food, max: 20 }
                  wrong:
                    icons: { full: hud/shield }
                    anchor: over-the-moon
                    value: { stat: luck }
                """);

        LoadReport report = new ContentLoader().load(contents);

        HudDefinition shield = report.huds().stream().filter(hud -> hud.id().equals("shield")).findFirst().orElseThrow();
        assertEquals(new HudAnchor(HudAnchor.Kind.ABOVE_HEALTH, 0, 0, 0), shield.anchor());
        assertEquals(-16, shield.ascent(), "computed from the anchor");
        assertTrue(shield.actionBar(), "an anchored bar goes on the action bar unless it says otherwise");
        assertEquals(new HudDefinition.Source.Stat("absorption", 0), shield.source());
        assertEquals("<gold>Shield <bar> <white><value>/<max>", shield.bedrock(), "3.0.4: what Bedrock players read");
        HudDefinition mana = report.huds().stream().filter(hud -> hud.id().equals("mana")).findFirst().orElseThrow();
        assertEquals(new HudAnchor(HudAnchor.Kind.ABOVE_FOOD, 1, 2, 1), mana.anchor());
        assertEquals(-5, mana.ascent(), "row 1 is 10 up, nudged 1 more");
        assertEquals(new HudDefinition.Source.Stat("auraskills:mana", 0), mana.source());
        assertEquals(null, mana.bedrock(), "without one, Bedrock players are not shown it");
        HudDefinition high = report.huds().stream().filter(hud -> hud.id().equals("high")).findFirst().orElseThrow();
        assertEquals(2, high.anchor().row(), "brought down to the highest row a 9 px icon can reach");
        assertProblem(report.problems(), "demo:high", "row 9 is too high");
        assertProblem(report.problems(), "demo:high", "'ascent' and 'offset' are ignored");
        assertProblem(report.problems(), "demo:wrong", "anchor 'over-the-moon'");
        assertProblem(report.problems(), "demo:wrong", "unknown stat 'luck'");
    }

    @Test
    void aLiquidWithoutALookIsRefused() throws IOException {
        write("demo/liquids.yml", """
                items:
                  nothing:
                    type: custom_liquid
                    liquid: { flow-distance: 3 }
                  no_section:
                    type: custom_liquid
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertTrue(report.items().isEmpty());
        assertProblem(report.problems(), "demo:nothing", "needs 'texture' (or 'model')");
        assertProblem(report.problems(), "demo:no_section", "needs a 'liquid' section");
    }

    @Test
    void hudsReadTheirIconsLayoutAndValue() throws IOException {
        write("demo/textures/hud/drop_full.png", "png");
        write("demo/textures/hud/drop_empty.png", "png");
        write("demo/textures/hud/star.png", "png");
        write("demo/huds.yml", """
                huds:
                  thirst:
                    icons: { full: hud/drop_full, empty: hud/drop_empty }
                    ascent: -5
                    spacing: -1
                    offset: 10
                    action-bar: true
                    value:
                      max: 20
                      per-second: -0.05
                      empty-damage: 1
                      consume: { POTION: 8, "demo:juice": 4 }
                  mana:
                    icons: { full: hud/star }
                    value: { placeholder: "%mmocore_mana%", max: "%mmocore_max_mana%" }
                  ghost:
                    icons: { full: hud/missing }
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(2, report.huds().size(), report.problems().toString());
        HudDefinition thirst = report.huds().stream().filter(hud -> hud.id().equals("thirst")).findFirst().orElseThrow();
        assertEquals("demo:thirst", thirst.fullId());
        assertEquals(List.of("full", "empty"), thirst.icons().stream().map(HudDefinition.Icon::name).toList());
        assertEquals(9, thirst.height());
        assertEquals(-5, thirst.ascent());
        assertEquals(10, thirst.segments());
        assertEquals(-1, thirst.spacing());
        assertEquals(10, thirst.offset());
        assertTrue(thirst.actionBar());
        HudDefinition.Source.Stored stored = (HudDefinition.Source.Stored) thirst.source();
        assertEquals(20, stored.start(), "starts full");
        assertEquals(-0.05, stored.perSecond());
        assertEquals(Map.of("POTION", 8.0, "demo:juice", 4.0), stored.consume());
        assertEquals(0, stored.clamp(-3));

        HudDefinition mana = report.huds().stream().filter(hud -> hud.id().equals("mana")).findFirst().orElseThrow();
        assertEquals(new HudDefinition.Source.Placeholder("%mmocore_mana%", "%mmocore_max_mana%"), mana.source());
        assertProblem(report.problems(), "demo:ghost", "textures/hud/missing.png is not in demo");
    }

    private static ItemDefinition item(LoadReport report, String id) {
        return report.items().stream().filter(item -> item.fullId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError(id + " not loaded: " + report.problems()));
    }

    private static void assertProblem(List<String> problems, String id, String text) {
        assertTrue(problems.stream().anyMatch(p -> p.contains(id) && p.contains(text)),
                id + ": expected '" + text + "' in " + problems);
    }

    private void write(String relative, String text) throws IOException {
        Path file = contents.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
