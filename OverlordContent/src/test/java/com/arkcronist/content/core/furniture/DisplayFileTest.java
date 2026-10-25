package com.arkcronist.content.core.furniture;

import com.arkcronist.content.core.definition.Placement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The editor's save: a backup first, the file replaced whole, nothing outside contents/. */
class DisplayFileTest {

    private static final DisplayTransform EDITED = new DisplayTransform(new Placement.Vec3(0, 0.25f, 0),
            Placement.Vec3.ONE, new Placement.Vec3(0, 45, 0));
    private static final String STOOL = "items:\n  stool:\n    furniture:\n      display:\n"
            + "        translation: [0, 0.5, 0]   # floor\n";

    @Test
    void theFileIsReplacedAndItsOldTextKeptAsABackup(@TempDir Path data) throws Exception {
        Path contents = Files.createDirectories(data.resolve("contents/demo"));
        Path file = contents.resolve("stool.yml");
        Files.writeString(file, "﻿" + STOOL);
        Path backups = data.resolve("data/editor-backups");

        DisplayFile.Saved saved = DisplayFile.save(data.resolve("contents"), backups, file, "stool", EDITED,
                Instant.parse("2026-10-07T12:00:00Z"));

        String written = Files.readString(file);
        assertTrue(written.startsWith("﻿"), "a byte order mark stays");
        assertTrue(written.contains("translation: [0, 0.25, 0]  # floor\n"), written);
        assertTrue(written.contains("rotation: [0, 45, 0]\n"), written);
        assertNotNull(saved.backup());
        assertEquals(backups.resolve("demo/stool.yml.20261007-120000-000.bak"), saved.backup());
        assertArrayEquals(("﻿" + STOOL).getBytes(StandardCharsets.UTF_8), Files.readAllBytes(saved.backup()));
        try (Stream<Path> left = Files.list(contents)) {
            assertEquals(1, left.count(), "no temporary file is left next to it");
        }

        // The same values again: nothing to write, no backup.
        DisplayFile.Saved again = DisplayFile.save(data.resolve("contents"), backups, file, "stool", EDITED,
                Instant.parse("2026-10-07T12:00:01Z"));
        assertNull(again.backup());
    }

    @Test
    void onlyTheNewestBackupsAreKept(@TempDir Path data) throws Exception {
        Path contents = Files.createDirectories(data.resolve("contents"));
        Path file = contents.resolve("stool.yml");
        Files.writeString(file, STOOL);
        Path backups = data.resolve("backups");
        Files.createDirectories(backups);
        Files.writeString(backups.resolve("notes.txt"), "mine");
        for (int i = 0; i < DisplayFile.BACKUPS_KEPT + 5; i++) {
            DisplayTransform transform = EDITED.adjust(DisplayTransform.Part.TRANSLATION, DisplayTransform.Axis.X, 0.01 * (i + 1));
            DisplayFile.save(contents, backups, file, "stool", transform, Instant.parse("2026-10-07T12:00:00Z").plusSeconds(i));
        }
        try (Stream<Path> list = Files.list(backups)) {
            assertEquals(DisplayFile.BACKUPS_KEPT + 1, list.count(), "ten backups, and the file that was not one");
        }
        assertTrue(Files.exists(backups.resolve("stool.yml.20261007-120014-000.bak")), "the newest is kept");
        assertTrue(Files.notExists(backups.resolve("stool.yml.20261007-120000-000.bak")), "the oldest went");
    }

    @Test
    void aFileOutsideContentsOrUnreadableIsLeftAlone(@TempDir Path data) throws Exception {
        Path contents = Files.createDirectories(data.resolve("contents"));
        Path outside = data.resolve("config.yml");
        Files.writeString(outside, STOOL);
        DisplayYaml.EditException refused = assertThrows(DisplayYaml.EditException.class, () ->
                DisplayFile.save(contents, data.resolve("backups"), contents.resolve("../config.yml"), "stool", EDITED,
                        Instant.now()));
        assertTrue(refused.getMessage().contains("not a file under contents/"), refused.getMessage());
        assertEquals(STOOL, Files.readString(outside));

        Path latin1 = contents.resolve("latin1.yml");
        Files.write(latin1, ("# café\n" + STOOL).getBytes(StandardCharsets.ISO_8859_1));
        assertThrows(DisplayYaml.EditException.class, () ->
                DisplayFile.save(contents, data.resolve("backups"), latin1, "stool", EDITED, Instant.now()));
        assertThrows(DisplayYaml.EditException.class, () ->
                DisplayFile.save(contents, data.resolve("backups"), contents.resolve("gone.yml"), "stool", EDITED,
                        Instant.now()));
        assertTrue(Files.notExists(data.resolve("backups")), "nothing refused leaves a backup");
    }
}
