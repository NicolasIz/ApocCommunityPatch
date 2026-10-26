package com.arkcronist.content.core.mechanics;

import com.arkcronist.content.core.mechanics.ItemMechanics.Action;
import com.arkcronist.content.core.mechanics.ItemMechanics.Target;
import com.arkcronist.content.core.mechanics.ItemMechanics.Trigger;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanicsReaderTest {

    private final List<String> problems = new ArrayList<>();

    private ItemMechanics read(String yaml) {
        Map<?, ?> root = new Yaml().load(yaml);
        return MechanicsReader.read(root.get("events"), root.get("mechanics"), "x: ", problems);
    }

    @Test
    void aReactionHasItsActionsConditionsAndCooldown() {
        ItemMechanics mechanics = read("""
                events:
                  right-click:
                    cooldown: 4
                    cooldown-message: "wait %seconds%s"
                    conditions: {sneaking: false, chance: 0.5, permission: a.b, worlds: [World], targets: [minecraft:zombie]}
                    actions:
                      - lightning: target
                      - sound: {sound: Entity.Lightning_Bolt.Thunder, volume: 2, pitch: 0.8, at: target}
                      - message: {text: "<red>Boom", to: actionbar}
                      - wait: 10
                      - command: "say %player%"
                      - console-command: "give %player% diamond"
                """);
        assertEquals(List.of(), problems);
        ItemMechanics.Reaction reaction = mechanics.on(Trigger.RIGHT_CLICK).get(0);
        assertEquals(4, reaction.cooldownSeconds());
        assertEquals("wait %seconds%s", reaction.cooldownMessage());
        assertEquals(Boolean.FALSE, reaction.conditions().sneaking());
        assertEquals(0.5, reaction.conditions().chance());
        assertEquals(java.util.Set.of("world"), reaction.conditions().worlds());
        assertEquals(java.util.Set.of("zombie"), reaction.conditions().targets(), "minecraft: taken off");
        List<Action> actions = reaction.actions();
        assertEquals(new Action.Lightning(Target.TARGET, false), actions.get(0));
        assertEquals(new Action.Sound("entity.lightning_bolt.thunder", 2f, 0.8f, Target.TARGET), actions.get(1));
        assertEquals(new Action.Message("<red>Boom", Action.Channel.ACTION_BAR, null), actions.get(2));
        assertEquals(new Action.Wait(10), actions.get(3));
        assertEquals(new Action.Command("say %player%", false), actions.get(4));
        assertEquals(new Action.Command("give %player% diamond", true), actions.get(5));
        assertTrue(mechanics.passive().isEmpty());
    }

    @Test
    void aTriggerTakesAListOfReactionsOrJustActions() {
        ItemMechanics mechanics = read("""
                events:
                  hit:
                    - conditions: {chance: 0.2}
                      actions: [{effect: {effect: wither, seconds: 3, target: target}}]
                    - actions: [{heal: 2}]
                  kill:
                    - give: "demo:ruby 3"
                    - experience: 5
                """);
        assertEquals(List.of(), problems);
        List<ItemMechanics.Reaction> hits = mechanics.on(Trigger.HIT_ENTITY);
        assertEquals(2, hits.size(), "'hit' is hit-entity");
        assertEquals(new Action.Effect(new ItemMechanics.PotionSpec("wither", 1, 3), Target.TARGET), hits.get(0).actions().get(0));
        assertEquals(new Action.Heal(2, Target.SELF), hits.get(1).actions().get(0));
        List<Action> kill = mechanics.on(Trigger.KILL).get(0).actions();
        assertEquals(new Action.Give("demo:ruby", 3), kill.get(0));
        assertEquals(new Action.Experience(5), kill.get(1));
        assertEquals(ItemMechanics.Conditions.ALWAYS, mechanics.on(Trigger.KILL).get(0).conditions());
    }

    @Test
    void passiveMechanicsAreRead() {
        ItemMechanics mechanics = read("""
                mechanics:
                  big-mining: true
                  mining-depth: 2
                  auto-smelt: true
                  soulbound: true
                  lifesteal: 0.25
                  held-effects: [haste, "speed 2"]
                  worn-effects: [{effect: night_vision, level: 1, seconds: 3}]
                  durability: 250
                """);
        assertEquals(List.of(), problems);
        ItemMechanics.Passive passive = mechanics.passive();
        assertEquals(1, passive.bigMining(), "true is 3x3");
        assertEquals(2, passive.miningDepth());
        assertTrue(passive.autoSmelt());
        assertTrue(passive.soulbound());
        assertEquals(0.25, passive.lifesteal());
        assertEquals(List.of(new ItemMechanics.PotionSpec("haste", 1, 5), new ItemMechanics.PotionSpec("speed", 2, 5)),
                passive.held());
        assertEquals("night_vision", passive.worn().get(0).effect());
        assertEquals(250, passive.durability());
        assertTrue(mechanics.events().isEmpty());
    }

    @Test
    void mistakesAreSaidAndTheRestStillWorks() {
        ItemMechanics mechanics = read("""
                events:
                  jump: [{message: hi}]
                  right-click:
                    - fly: 3
                    - message: hi
                    - damage: -2
                    - wait: 99999
                    - lightning: {at: elsewhere}
                  left-click:
                    conditions: {chance: 0.5}
                mechanics:
                  big-mining: 9
                  x-ray: true
                """);
        assertEquals(List.of(new Action.Message("hi", Action.Channel.CHAT, null), new Action.Lightning(Target.TARGET, false)),
                mechanics.on(Trigger.RIGHT_CLICK).get(0).actions());
        assertTrue(mechanics.on(Trigger.LEFT_CLICK).isEmpty());
        assertEquals(0, mechanics.passive().bigMining());
        assertProblem("unknown trigger 'jump'");
        assertProblem("unknown action 'fly'");
        assertProblem("damage: needs an amount");
        assertProblem("wait: needs a number of ticks from 1 to 1200");
        assertProblem("'elsewhere' is not a target");
        assertProblem("left-click: needs 'actions:'");
        assertProblem("'big-mining' should be a number from 0 to 2");
        assertProblem("unknown mechanic 'x-ray'");
    }

    @Test
    void nothingToReadIsNoMechanics() {
        assertNull(read("events: {}"));
        assertNull(read("other: 1"));
        assertFalse(problems.stream().anyMatch(p -> !p.isEmpty()));
    }

    @Test
    void triggersAreKnownByTheirOtherNames() {
        assertEquals(Trigger.BREAK_BLOCK, Trigger.parse("mine").orElseThrow());
        assertEquals(Trigger.CONSUME, Trigger.parse("eat").orElseThrow());
        assertEquals(Trigger.RIGHT_CLICK, Trigger.parse("RIGHT_CLICK").orElseThrow());
        assertEquals(Trigger.DAMAGED, Trigger.parse("hurt").orElseThrow());
        assertInstanceOf(java.util.Optional.class, Trigger.parse("nothing"));
        assertTrue(Trigger.parse("nothing").isEmpty());
    }

    private void assertProblem(String text) {
        assertTrue(problems.stream().anyMatch(p -> p.contains(text)), "expected '" + text + "' in " + problems);
    }
}
