package com.arkcronist.content.core.quest;

import com.arkcronist.content.core.quest.QuestDefinition.Kind;
import com.arkcronist.content.core.quest.QuestDefinition.Objective;
import com.arkcronist.content.core.quest.QuestDefinition.Requirement;
import com.arkcronist.content.core.quest.QuestDefinition.Reward;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quest notices: as stored, as written in YAML, and as progress counts towards them. */
class QuestDefinitionTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    static QuestDefinition everything() {
        return QuestDefinition.draft("dragon_scales", "<gold>Scales of Umbraxis")
                .withDescription(List.of("<gray>The smith pays well.", "Line two"))
                .withIcon("dragones_epicos:escama_umbraxis")
                .withRequirement(new Requirement.Skill("fighting", 10))
                .withRequirement(new Requirement.Job("hunter", 5))
                .withRequirement(new Requirement.Permission("rank.knight"))
                .withObjective(new Objective(Kind.DELIVER, "dragones_epicos:escama_umbraxis", 12))
                .withObjective(new Objective(Kind.KILL, "mythicmobs:DE_Umbraxis", 1))
                .withObjective(new Objective(Kind.BREAK, "DIAMOND_ORE", 5))
                .withReward(new Reward.Money(5000.5))
                .withReward(new Reward.Item("demo:ruby", 3))
                .withReward(new Reward.Stack("AAEC", "1 x Excalibur"))
                .withReward(new Reward.Command("say %player% did it"))
                .withRepeatable(true)
                .withBoards(List.of("demo:medieval_bulletin_board"));
    }

    @Test
    void aNoticeIsStoredAndReadBackExactly() {
        QuestDefinition quest = everything();
        String json = QuestJson.write(quest);
        assertEquals(quest, QuestJson.read(json));
        assertTrue(json.startsWith("{\"v\":1,"), json);
        assertEquals(QuestDefinition.draft("x", "X"), QuestJson.read(QuestJson.write(QuestDefinition.draft("x", "X"))));
    }

    @Test
    void whatCannotBeReadIsRefusedNotGuessed() {
        assertThrows(IllegalArgumentException.class, () -> QuestJson.read("not json"));
        assertThrows(IllegalArgumentException.class, () -> QuestJson.read("[]"));
        assertThrows(IllegalArgumentException.class, () -> QuestJson.read("{\"v\":2,\"id\":\"a\"}"), "newer format");
        assertThrows(IllegalArgumentException.class, () -> QuestJson.read("{\"v\":1,\"id\":\"Bad Id\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> QuestJson.read("{\"v\":1,\"id\":\"a\",\"objectives\":[{\"type\":\"fly\"}]}"));
    }

    @Test
    void yamlTemplatesAreReadWithEveryKindOfEntryAndTheirMistakesReported() {
        Map<String, Object> root = new Yaml().load("""
                quests:
                  dragon_scales:
                    title: "<gold>Scales"
                    description: ["<gray>One", "Two"]
                    icon: dragones_epicos:escama_umbraxis
                    repeatable: true
                    boards: [demo:medieval_bulletin_board]
                    requirements:
                      skills: {fighting: 10}
                      jobs: {hunter: 5}
                      permission: rank.knight
                    objectives:
                      - {deliver: dragones_epicos:escama_umbraxis, amount: 12}
                      - {kill: mythicmobs:DE_Umbraxis}
                      - {break: DIAMOND_ORE, amount: 5}
                    rewards:
                      money: 5000.5
                      items: ["demo:ruby 3", {item: DIAMOND, amount: 2}]
                      commands: ["say %player% did it"]
                  no_objectives:
                    title: nothing to do
                  "Bad Id": {objectives: [{kill: zombie}]}
                  odd:
                    objectives:
                      - {fly: away}
                      - {kill: zombie, amount: 0}
                """);
        List<String> problems = new ArrayList<>();
        List<QuestDefinition> quests = QuestReader.read(root.get("quests"), "demo/quests.yml", problems);
        assertEquals(List.of("dragon_scales", "odd"), quests.stream().map(QuestDefinition::id).toList());
        QuestDefinition scales = quests.get(0);
        assertEquals(List.of(new Requirement.Skill("fighting", 10), new Requirement.Job("hunter", 5),
                new Requirement.Permission("rank.knight")), scales.requirements());
        assertEquals(List.of(new Objective(Kind.DELIVER, "dragones_epicos:escama_umbraxis", 12),
                new Objective(Kind.KILL, "mythicmobs:DE_Umbraxis", 1), new Objective(Kind.BREAK, "DIAMOND_ORE", 5)),
                scales.objectives());
        assertEquals(List.of(new Reward.Money(5000.5), new Reward.Item("demo:ruby", 3), new Reward.Item("DIAMOND", 2),
                new Reward.Command("say %player% did it")), scales.rewards());
        assertTrue(scales.repeatable());
        assertTrue(scales.on("demo:medieval_bulletin_board"));
        assertFalse(scales.on("other:board"));
        assertEquals(List.of(new Objective(Kind.KILL, "zombie", 1)), quests.get(1).objectives(), "0 becomes 1");
        assertTrue(problems.stream().anyMatch(p -> p.contains("no_objectives") && p.contains("needs at least one")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("invalid id")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("neither deliver, kill nor break")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("at least 1")), problems.toString());
    }

    @Test
    void killsAndBlocksCountTowardsWhatTheyMatchAndNoFurther() {
        QuestDefinition quest = everything();
        QuestProgress fresh = QuestProgress.accepted(PLAYER, quest, 1000, 0);
        assertEquals(List.of(0, 0, 0), fresh.counts());
        assertTrue(fresh.active());

        QuestProgress umbraxis = fresh.record(quest, Kind.KILL, List.of("zombie", "mythicmobs:de_umbraxis"), 1);
        assertEquals(List.of(0, 1, 0), umbraxis.counts(), "MythicMobs types match whatever their case");
        assertSame(umbraxis, umbraxis.record(quest, Kind.KILL, List.of("mythicmobs:de_umbraxis"), 1), "already done");
        assertSame(umbraxis, umbraxis.record(quest, Kind.KILL, List.of("skeleton"), 1));
        assertSame(umbraxis, umbraxis.record(quest, Kind.DELIVER, List.of("dragones_epicos:escama_umbraxis"), 1),
                "deliveries are counted in the inventory, not here");

        QuestProgress mined = umbraxis;
        for (int i = 0; i < 7; i++) {
            mined = mined.record(quest, Kind.BREAK, List.of("minecraft:diamond_ore"), 1);
        }
        assertEquals(List.of(0, 1, 5), mined.counts(), "capped at the amount");
        assertTrue(mined.countedDone(quest));
        assertFalse(umbraxis.countedDone(quest));

        assertEquals("0,1,5", mined.encodedCounts());
        assertEquals(List.of(0, 1, 5), QuestProgress.decodeCounts("0,1,5"));
        assertEquals(List.of(3, 0), QuestProgress.decodeCounts("3,x"));

        QuestProgress done = mined.completed(2000);
        assertFalse(done.active());
        assertEquals(1, done.completions());
        assertSame(done, done.record(quest, Kind.BREAK, List.of("diamond_ore"), 1), "a finished notice counts nothing");
    }

    @Test
    void commandsReadNamesWithColonsAndSlashesAndACount() {
        assertEquals(new Counted("demo:ruby", 8), Counted.parse("demo:ruby 8", 100));
        assertEquals(new Counted("mythicmobs:SkeletonKing", 3), Counted.parse("  mythicmobs:SkeletonKing   3 ", 100));
        assertEquals(new Counted("auraskills/fighting", 5), Counted.parse("auraskills/fighting 5", 100));
        assertThrows(IllegalArgumentException.class, () -> Counted.parse("demo:ruby", 100), "no count");
        assertThrows(IllegalArgumentException.class, () -> Counted.parse("demo:ruby eight", 100));
        assertThrows(IllegalArgumentException.class, () -> Counted.parse("demo ruby 8", 100), "a name has no spaces");
        assertThrows(IllegalArgumentException.class, () -> Counted.parse("demo:ruby 0", 100));
        assertThrows(IllegalArgumentException.class, () -> Counted.parse("demo:ruby 101", 100));
    }
}
