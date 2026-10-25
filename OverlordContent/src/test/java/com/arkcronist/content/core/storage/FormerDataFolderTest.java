package com.arkcronist.content.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormerDataFolderTest {

    @TempDir
    Path plugins;

    @Test
    void anUpgradingServerKeepsEverythingUnderTheNewName() throws IOException {
        Path former = plugins.resolve("ArkcronistContent");
        Files.createDirectories(former.resolve("data"));
        Files.writeString(former.resolve("config.yml"), "pack: {}\n");
        Files.writeString(former.resolve("data/world_content.db"), "rows");
        Path folder = plugins.resolve("OverlordContent");

        assertEquals(FormerDataFolder.Outcome.MOVED, FormerDataFolder.adopt(folder, former));
        assertFalse(Files.exists(former));
        assertEquals("pack: {}\n", Files.readString(folder.resolve("config.yml")));
        assertEquals("rows", Files.readString(folder.resolve("data/world_content.db")));
        assertEquals(FormerDataFolder.Outcome.NONE, FormerDataFolder.adopt(folder, former), "once");
    }

    @Test
    void anEmptyNewFolderIsReplacedAndAFullOneWins() throws IOException {
        Path former = plugins.resolve("ArkcronistContent");
        Path folder = plugins.resolve("OverlordContent");
        Files.createDirectories(former);
        Files.writeString(former.resolve("config.yml"), "old");
        Files.createDirectories(folder);
        assertEquals(FormerDataFolder.Outcome.MOVED, FormerDataFolder.adopt(folder, former), "made empty by the server");
        assertEquals("old", Files.readString(folder.resolve("config.yml")));

        Files.createDirectories(former);
        Files.writeString(former.resolve("config.yml"), "older");
        assertEquals(FormerDataFolder.Outcome.BOTH, FormerDataFolder.adopt(folder, former));
        assertEquals("old", Files.readString(folder.resolve("config.yml")), "the new one is never overwritten");
        assertTrue(Files.exists(former.resolve("config.yml")), "nor the old one deleted");
    }

    @Test
    void aNewInstallHasNothingToMove() {
        assertEquals(FormerDataFolder.Outcome.NONE,
                FormerDataFolder.adopt(plugins.resolve("OverlordContent"), plugins.resolve("ArkcronistContent")));
    }
}
