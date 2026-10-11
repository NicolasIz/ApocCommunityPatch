package com.arkcronist.content.core.definition;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.9.5: what a command sign fills in, and what it refuses to put into a console command. */
class CommandTemplateTest {

    private static Map<String, String> click(String player) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", player);
        values.put("uuid", "0f3a9c1e-2b4d-4e6f-8a1b-3c5d7e9f1a2b");
        values.put("world", "world_nether");
        values.put("dimension", "minecraft:the_nether");
        values.put("x", "-120");
        values.put("y", "64");
        values.put("z", "3000001");
        return values;
    }

    @Test
    void everyPlaceholderIsFilledAndTheSlashDropped() {
        assertEquals("minecraft:tellraw Steve {\"text\":\"Steve at -120 64 3000001 in world_nether\"}",
                CommandTemplate.fill("/minecraft:tellraw %player% {\"text\":\"%player% at %x% %y% %z% in %world%\"}",
                        click("Steve")));
        assertEquals("execute in minecraft:the_nether positioned -120 64 3000001 run playsound minecraft:block.bell.use"
                        + " block @a[distance=..48]",
                CommandTemplate.fill("execute in %dimension% positioned %x% %y% %z% run playsound"
                        + " minecraft:block.bell.use block @a[distance=..48]", click("Steve")));
        assertEquals("eco give 0f3a9c1e-2b4d-4e6f-8a1b-3c5d7e9f1a2b 10",
                CommandTemplate.fill("  eco give %uuid% 10  ", click("Steve")));
    }

    @Test
    void anUnknownPlaceholderIsLeftForTheCommandsOwnPlugin() {
        assertEquals("papi parse Steve %vault_eco_balance%",
                CommandTemplate.fill("papi parse %player% %vault_eco_balance%", click("Steve")));
    }

    @Test
    void bedrockPlayersThroughFloodgateAreNamesToo() {
        assertTrue(CommandTemplate.safeName(".Bedrock_Steve"));
        assertTrue(CommandTemplate.safeName("*Pocket"));
        assertEquals("warp banco .Bedrock_Steve", CommandTemplate.fill("warp banco %player%", click(".Bedrock_Steve")));
    }

    /** Nothing in a name may add a target, a selector, an argument or a second command. */
    @Test
    void aNameThatCouldChangeTheCommandIsRefused() {
        for (String name : new String[] {"Steve @a", "@a", "Steve;op Steve", "a\nop b", "Steve run op Steve",
                "Ste\"ve", "", "Ab{}", "..Steve", "abcdefghijklmnopqrstuvwxyzabcdefg"}) {
            assertFalse(CommandTemplate.safeName(name), name);
            assertThrows(IllegalArgumentException.class, () -> CommandTemplate.fill("say %player%", click(name)), name);
        }
    }

    /** A world named "Plaza Mayor" stops only the commands that name it. */
    @Test
    void onlyTheValuesACommandUsesAreChecked() {
        Map<String, String> values = click("Steve");
        values.put("world", "Plaza Mayor");
        assertEquals("warp banco Steve", CommandTemplate.fill("warp banco %player%", values));
        assertThrows(IllegalArgumentException.class, () -> CommandTemplate.fill("mv tp %player% %world%", values));
    }

    @Test
    void otherValuesAreCheckedToo() {
        Map<String, String> world = click("Steve");
        world.put("world", "world run op Steve");
        assertThrows(IllegalArgumentException.class, () -> CommandTemplate.fill("say %world%", world));
        Map<String, String> dimension = click("Steve");
        dimension.put("dimension", "minecraft:overworld run op Steve");
        assertThrows(IllegalArgumentException.class, () -> CommandTemplate.fill("say %dimension%", dimension));
        Map<String, String> x = click("Steve");
        x.put("x", "1 2");
        assertThrows(IllegalArgumentException.class, () -> CommandTemplate.fill("say %x%", x));
    }
}
