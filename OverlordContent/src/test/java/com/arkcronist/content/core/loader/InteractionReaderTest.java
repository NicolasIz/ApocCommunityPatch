package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.Placement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.9.5: interactable: effect, command and guillotine, as furniture files describe them. */
class InteractionReaderTest {

    private static final Path GUILLOTINE = Path.of(
            "src/main/resources/contents/medieval_props/models/furniture/guillotina_plaza.bbmodel");

    @TempDir
    Path contents;

    // ---------------------------------------------------------------- effect

    @Test
    void anEffectReadsItsParticlesAndItsDrink() throws IOException {
        write("town/drinks.yml", """
                items:
                  mug:
                    type: custom_furniture
                    resource: {model: furniture/mug}
                    furniture:
                      interactable: effect
                      effect:
                        interval: 20
                        range: 16
                        particles:
                          - {particle: minecraft:white_ash, offset: [0.25, 0.45, -0.1], count: 3, spread: 0.08, speed: 0.01}
                          - {particle: SMALL_FLAME}
                        on-click:
                          potions:
                            - speed 30
                            - "regeneration 8.5 1"
                            - {effect: minecraft:luck, seconds: 300, amplifier: 2}
                          sound: minecraft:entity.generic.drink
                          cooldown: 10
                          message: "<gold>Cheers"
                """);
        LoadReport report = new ContentLoader().load(contents);
        assertEquals(List.of(), report.problems());
        Placement.Furniture mug = furniture(report, "town:mug");
        Placement.Ambience ambience = mug.ambience();
        assertNotNull(ambience);
        assertEquals(List.of(new Placement.Particles("minecraft:white_ash", 0.25, 0.45, -0.1, 3, 0.08, 0.01),
                new Placement.Particles("small_flame", 0, 1, 0, 1, 0.1, 0)), ambience.particles());
        assertEquals(20, ambience.intervalTicks());
        assertEquals(16, ambience.range());
        assertEquals(List.of(new Placement.Effect("minecraft:speed", 600, 0),
                new Placement.Effect("minecraft:regeneration", 170, 1),
                new Placement.Effect("minecraft:luck", 6000, 2)), ambience.potions());
        assertEquals("minecraft:entity.generic.drink", ambience.sound());
        assertEquals(200, ambience.cooldownTicks());
        assertEquals("<gold>Cheers", ambience.message());
        assertTrue(ambience.clickable());
        assertTrue(mug.interactive());
    }

    /** A brazier only burns: no click to take, so a right click is not taken from the player. */
    @Test
    void particlesAloneAreNotSomethingToClick() throws IOException {
        write("town/fire.yml", """
                items:
                  brazier:
                    type: custom_furniture
                    resource: {model: furniture/brazier}
                    furniture:
                      support: LIGHT
                      light: 14
                      interactable: effects
                      effect: {particles: [{particle: flame, offset: [0, 0.8, 0]}]}
                """);
        LoadReport report = new ContentLoader().load(contents);
        assertEquals(List.of(), report.problems());
        Placement.Furniture brazier = furniture(report, "town:brazier");
        assertEquals(10, brazier.ambience().intervalTicks());
        assertEquals(24, brazier.ambience().range());
        assertFalse(brazier.ambience().clickable());
        assertFalse(brazier.interactive());
    }

    @Test
    void whatIsWrongInAnEffectIsReportedAndLeftOut() throws IOException {
        write("town/bad.yml", """
                items:
                  nothing:
                    type: custom_furniture
                    resource: {model: furniture/a}
                    furniture: {interactable: effect}
                  empty:
                    type: custom_furniture
                    resource: {model: furniture/a}
                    furniture: {interactable: effect, effect: {interval: 5}}
                  odd:
                    type: custom_furniture
                    resource: {model: furniture/a}
                    furniture:
                      interactable: effect
                      effect:
                        interval: 0
                        particles:
                          - {particle: "flame; say hi", offset: [0, 1, 0]}
                          - {particle: smoke, offset: [0, 1], count: 500}
                          - just a string
                        on-click:
                          potions: [speed fast, "speed 9999", "Speed!? 2"]
                          sound: "Not A Sound"
                  stray:
                    type: custom_furniture
                    resource: {model: furniture/a}
                    furniture: {interactable: seat, effect: {particles: [{particle: flame}]}}
                """);
        LoadReport report = new ContentLoader().load(contents);
        List<String> problems = report.problems();
        assertProblem(problems, "nothing", "needs an 'effect:' section");
        assertProblem(problems, "empty", "nothing would happen");
        assertProblem(problems, "odd", "'interval' should be a whole number from 1 to 1200");
        assertProblem(problems, "odd", "'particle' should be a particle id");
        assertProblem(problems, "odd", "'offset' should be three numbers");
        assertProblem(problems, "odd", "'count' should be a whole number from 1 to 100");
        assertProblem(problems, "odd", "#3: should be a section");
        assertProblem(problems, "odd", "potion 'speed fast' should read 'effect seconds [amplifier]'");
        assertProblem(problems, "odd", "potion 'speed 9999': seconds 0.05-3600");
        assertProblem(problems, "odd", "is not a potion effect id");
        assertProblem(problems, "odd", "'sound' should be a sound id");
        assertProblem(problems, "stray", "effect:");
        // What could be read of it still is: the smoke, with its count put back to 1.
        Placement.Ambience odd = furniture(report, "town:odd").ambience();
        assertEquals(List.of(new Placement.Particles("smoke", 0, 1, 0, 1, 0.1, 0)), odd.particles());
        assertEquals(10, odd.intervalTicks());
        assertEquals(List.of(), odd.potions());
        assertNull(furniture(report, "town:stray").ambience());
    }

    // ---------------------------------------------------------------- command

    @Test
    void aCommandSignReadsItsCommands() throws IOException {
        write("town/signs.yml", """
                items:
                  bank:
                    type: custom_furniture
                    resource: {model: furniture/bank}
                    furniture:
                      interactable: command
                      command:
                        run:
                          - "/minecraft:tellraw %player% {\\"text\\":\\"Welcome\\"}"
                          - eco give %player% 1
                        cooldown: 4.5
                        permission: town.bank
                        message: "<gold>The bank"
                        sound: minecraft:block.chain.place
                  mail:
                    type: custom_furniture
                    resource: {model: furniture/mail}
                    furniture:
                      interactable: commands
                      command: {run: mail read, as: PLAYER}
                """);
        LoadReport report = new ContentLoader().load(contents);
        assertEquals(List.of(), report.problems());
        Placement.Commands bank = furniture(report, "town:bank").commands();
        assertEquals(List.of("minecraft:tellraw %player% {\"text\":\"Welcome\"}", "eco give %player% 1"), bank.run());
        assertFalse(bank.asPlayer());
        assertEquals(90, bank.cooldownTicks());
        assertEquals("town.bank", bank.permission());
        assertEquals("<gold>The bank", bank.message());
        assertEquals("minecraft:block.chain.place", bank.sound());

        Placement.Commands mail = furniture(report, "town:mail").commands();
        assertEquals(List.of("mail read"), mail.run());
        assertTrue(mail.asPlayer());
        assertEquals(40, mail.cooldownTicks(), "two seconds by default");
        assertNull(mail.permission());
        assertTrue(furniture(report, "town:mail").interactive());
    }

    @Test
    void aSignWithNothingToRunIsReported() throws IOException {
        write("town/bad.yml", """
                items:
                  blank:
                    type: custom_furniture
                    resource: {model: furniture/a}
                    furniture: {interactable: command}
                  empty:
                    type: custom_furniture
                    resource: {model: furniture/a}
                    furniture: {interactable: command, command: {run: ["/", "  "]}}
                  sneaky:
                    type: custom_furniture
                    resource: {model: furniture/a}
                    furniture:
                      interactable: command
                      command:
                        as: operator
                        run: ["say one\\nop everyone", {nested: true}, say hello]
                """);
        LoadReport report = new ContentLoader().load(contents);
        assertProblem(report.problems(), "blank", "needs a 'command:' section");
        assertProblem(report.problems(), "empty", "an empty or multi-line command is skipped");
        assertProblem(report.problems(), "empty", "the sign would do nothing");
        assertProblem(report.problems(), "sneaky", "an empty or multi-line command is skipped");
        assertProblem(report.problems(), "sneaky", "each command should be one line");
        assertProblem(report.problems(), "sneaky", "'as' is console or player, not 'operator'");
        Placement.Commands sneaky = furniture(report, "town:sneaky").commands();
        assertEquals(List.of("say hello"), sneaky.run(), "only the one-line command is kept");
        assertFalse(sneaky.asPlayer());
        assertNull(furniture(report, "town:blank").commands());
    }

    // ---------------------------------------------------------------- guillotine

    @Test
    void aGuillotineReadsItsTimingsAndItsLever() throws IOException {
        guillotineModel();
        write("town/square.yml", """
                items:
                  guillotine:
                    type: custom_furniture
                    furniture:
                      size: {width: 2, height: 3}
                      animated-model: furniture/guillotine
                      interactable: guillotine
                      guillotine:
                        delay-ms: 63
                        reset-after: 2.5
                        lever: {across: 1, up: 0}
                        kneel-height: 0.1
                        victim: occupant
                        commands: ["/minecraft:tellraw @a {\\"text\\":\\"%player%\\"}"]
                        impact-particles: [{particle: crit, offset: [0, 1.4, 0], count: 18, spread: 0.3, speed: 0.4}]
                """);
        LoadReport report = new ContentLoader().load(contents);
        assertEquals(List.of(), report.problems());
        Placement.Furniture furniture = furniture(report, "town:guillotine");
        Placement.Guillotine guillotine = furniture.guillotine();
        assertNotNull(guillotine);
        assertTrue(furniture.interactive());
        assertEquals("drop_blade", guillotine.drop());
        assertEquals("reset_blade", guillotine.reset());
        assertEquals(63, guillotine.delayMillis());
        // impact-ms defaults to the drop animation's length.
        assertEquals(furniture.animated().model().clips().get("drop_blade").length() * 50, guillotine.impactMillis());
        assertEquals(50, guillotine.resetTicks());
        assertEquals(1, guillotine.leverAcross());
        assertEquals(0, guillotine.leverUp());
        assertEquals(0.1f, guillotine.kneelHeight(), 1e-6);
        assertFalse(guillotine.killUser());
        assertTrue(guillotine.respectPvp());
        assertEquals(List.of("minecraft:tellraw @a {\"text\":\"%player%\"}"), guillotine.commands());
        assertEquals(List.of(new Placement.Particles("crit", 0, 1.4, 0, 18, 0.3, 0.4)), guillotine.impact());
        assertEquals("minecraft:block.anvil.land", guillotine.impactSound());
    }

    /** With nothing said, the lever is the bottom of the right-hand column, and the blade falls 63 ms on. */
    @Test
    void theDefaultsMakeAWorkingGuillotine() throws IOException {
        guillotineModel();
        write("town/square.yml", """
                items:
                  two:
                    type: custom_furniture
                    furniture: {size: {width: 2, height: 3}, animated-model: furniture/guillotine, interactable: guillotine}
                  three:
                    type: custom_furniture
                    furniture:
                      size: {width: 3, height: 3}
                      animated-model: furniture/guillotine
                      interactable: guillotine
                      guillotine: {victim: USER, respect-pvp: false}
                """);
        LoadReport report = new ContentLoader().load(contents);
        assertEquals(List.of(), report.problems());
        Placement.Guillotine two = furniture(report, "town:two").guillotine();
        assertEquals(63, two.delayMillis());
        assertEquals(1, two.leverAcross());
        assertEquals(60, two.resetTicks());
        Placement.Guillotine three = furniture(report, "town:three").guillotine();
        assertEquals(1, three.leverAcross(), "the right of -1, 0, 1");
        assertTrue(three.killUser());
        assertFalse(three.respectPvp());
    }

    @Test
    void aGuillotineThatCannotWorkIsReported() throws IOException {
        guillotineModel();
        write("town/bad.yml", """
                items:
                  flat:
                    type: custom_furniture
                    resource: {model: furniture/a}
                    furniture: {interactable: guillotine}
                  nameless:
                    type: custom_furniture
                    furniture:
                      size: {width: 2, height: 3}
                      animated-model: furniture/guillotine
                      interactable: guillotine
                      guillotine: {drop: fall}
                  single:
                    type: custom_furniture
                    furniture: {animated-model: furniture/guillotine, interactable: guillotine}
                  outside:
                    type: custom_furniture
                    furniture:
                      size: {width: 2, height: 3}
                      animated-model: furniture/guillotine
                      interactable: guillotine
                      guillotine: {lever: {across: 3, up: 0}}
                  wrong:
                    type: custom_furniture
                    furniture:
                      size: {width: 2, height: 3}
                      animated-model: furniture/guillotine
                      interactable: guillotine
                      guillotine: {victim: everyone, respect-pvp: maybe, reset: rise, delay-ms: -5}
                """);
        LoadReport report = new ContentLoader().load(contents);
        List<String> problems = report.problems();
        assertProblem(problems, "flat", "needs an 'animated-model'");
        assertProblem(problems, "nameless", "no animation 'fall'");
        assertProblem(problems, "single", "cannot be the block it stands on");
        assertProblem(problems, "outside", "is not one of its 2 x 3 blocks");
        assertProblem(problems, "wrong", "'victim' is occupant or user, not 'everyone'");
        assertProblem(problems, "wrong", "'respect-pvp' should be true or false");
        assertProblem(problems, "wrong", "no animation 'rise'; the blade stays down");
        assertProblem(problems, "wrong", "'delay-ms' should be a whole number from 0 to 5000; using 63");
        Placement.Guillotine wrong = furniture(report, "town:wrong").guillotine();
        assertFalse(wrong.killUser());
        assertTrue(wrong.respectPvp());
        assertNull(wrong.reset());
        assertEquals(63, wrong.delayMillis());
        for (String id : List.of("town:flat", "town:nameless", "town:single", "town:outside")) {
            ItemDefinition item = items(report).get(id);
            assertTrue(item == null || ((Placement.Furniture) item.placement()).guillotine() == null, id);
        }
    }

    // ---------------------------------------------------------------- helpers

    private void guillotineModel() throws IOException {
        Path model = contents.resolve("town/models/furniture/guillotine.bbmodel");
        Files.createDirectories(model.getParent());
        Files.copy(GUILLOTINE, model);
    }

    private static Map<String, ItemDefinition> items(LoadReport report) {
        return report.items().stream().collect(Collectors.toMap(ItemDefinition::fullId, Function.identity()));
    }

    private static Placement.Furniture furniture(LoadReport report, String id) {
        ItemDefinition item = items(report).get(id);
        assertNotNull(item, id + " in " + report.items().stream().map(ItemDefinition::fullId).toList()
                + "; problems " + report.problems());
        return (Placement.Furniture) item.placement();
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
