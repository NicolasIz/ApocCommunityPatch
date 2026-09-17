package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.datapack.DatapackInstaller;
import com.arkcronist.gen.bukkit.datapack.DatapackInstaller.Outcome;
import com.arkcronist.gen.bukkit.datapack.DatapackInstaller.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Copying datapacks into the world folder.
 *
 * <p>Real zip files in a temporary folder rather than a stubbed filesystem: the thing being tested
 * is what lands on disk, and half of what can go wrong - a pack with no pack.mcmeta, a pack that
 * overwrites another - only exists in the file itself.</p>
 */
class DatapackInstallerTest {

    private static final int SERVER = 81;

    @Test
    @DisplayName("a compatible pack is copied and an incompatible one is left where it is")
    void copiesOnlyWhatWillLoad(@TempDir Path root) throws IOException {
        Path source = Files.createDirectories(root.resolve("source"));
        Path target = root.resolve("world/datapacks");

        pack(source.resolve("good.zip"), meta(81, 81));
        pack(source.resolve("future.zip"), meta(101, 101));
        pack(source.resolve("ancient.zip"), meta(48, 60));

        List<String> log = new ArrayList<>();
        List<Result> results = DatapackInstaller.install(source, target, SERVER, log::add);

        assertEquals(Outcome.INSTALLED, outcomeOf(results, "good.zip"));
        assertEquals(Outcome.INCOMPATIBLE, outcomeOf(results, "future.zip"));
        assertEquals(Outcome.INCOMPATIBLE, outcomeOf(results, "ancient.zip"));

        assertTrue(Files.exists(target.resolve("good.zip")));
        // The point of refusing. A pack the server will not load is worse sitting in the world
        // folder than absent: it appears in /datapack list and does nothing.
        assertFalse(Files.exists(target.resolve("future.zip")));
        assertFalse(Files.exists(target.resolve("ancient.zip")));

        assertTrue(log.stream().anyMatch(line -> line.contains("future.zip") && line.contains("101")),
                "the log did not say which version the pack wanted: " + log);
    }

    @Test
    @DisplayName("a pack already in the world folder is left alone")
    void doesNotOverwrite(@TempDir Path root) throws IOException {
        Path source = Files.createDirectories(root.resolve("source"));
        Path target = Files.createDirectories(root.resolve("world/datapacks"));

        pack(source.resolve("pack.zip"), meta(81, 81));
        Files.writeString(target.resolve("pack.zip"), "an admin's own edited copy");

        List<Result> results = DatapackInstaller.install(source, target, SERVER, line -> { });

        assertEquals(Outcome.PRESENT, outcomeOf(results, "pack.zip"));
        assertEquals("an admin's own edited copy",
                Files.readString(target.resolve("pack.zip")),
                "an existing pack was overwritten");
    }

    @Test
    @DisplayName("something that is not a datapack is reported, not copied")
    void skipsNonDatapacks(@TempDir Path root) throws IOException {
        Path source = Files.createDirectories(root.resolve("source"));
        Path target = root.resolve("world/datapacks");

        // A resource pack, or a world download, or a zip of schematics: all things that end up in
        // that folder by mistake.
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(source.resolve("wrong.zip")))) {
            out.putNextEntry(new ZipEntry("assets/minecraft/textures/block/stone.png"));
            out.write(new byte[]{1, 2, 3});
            out.closeEntry();
        }

        List<String> log = new ArrayList<>();
        List<Result> results = DatapackInstaller.install(source, target, SERVER, log::add);

        assertEquals(Outcome.UNREADABLE, outcomeOf(results, "wrong.zip"));
        assertFalse(Files.exists(target.resolve("wrong.zip")));
    }

    @Test
    @DisplayName("two packs replacing the same vanilla files are called out")
    void findsCollisions(@TempDir Path root) throws IOException {
        Path source = Files.createDirectories(root.resolve("source"));
        Path target = root.resolve("world/datapacks");

        // The real case this came from: one pack revamps villages, another revamps villages and
        // several other structures, and both rewrite the same seven village files. Neither errors.
        // Whichever the server reads last wins, and nothing says which that was.
        pack(source.resolve("villages.zip"), meta(81, 81),
                "data/minecraft/worldgen/structure/village_plains.json",
                "data/minecraft/worldgen/structure/village_taiga.json");
        pack(source.resolve("structures.zip"), meta(81, 81),
                "data/minecraft/worldgen/structure/village_plains.json",
                "data/minecraft/worldgen/structure/village_taiga.json",
                "data/minecraft/worldgen/structure/stronghold.json");
        // Its own namespace, so it adds rather than replaces and cannot collide with anybody.
        pack(source.resolve("towers.zip"), meta(81, 81),
                "data/ominous/worldgen/structure/ominous_tower.json",
                // A tag, which the game merges rather than replaces. Two packs adding themselves to
                // the same biome tag is how tags work, not a conflict - and counting those made
                // every pair of packs that touched the same corner of the game look like one.
                "data/minecraft/tags/worldgen/biome/has_structure/village_plains.json");

        List<Result> results = DatapackInstaller.install(source, target, SERVER, line -> { });
        List<String> collisions = DatapackInstaller.collisions(target, results);

        assertEquals(1, collisions.size(), "expected exactly one colliding pair, got " + collisions);
        String warning = collisions.get(0);
        assertTrue(warning.contains("villages.zip") && warning.contains("structures.zip"), warning);
        assertTrue(warning.contains("worldgen/structure/village_plains.json"), warning);
        assertFalse(warning.contains("towers.zip"),
                "a pack in its own namespace was reported as overwriting another");
    }

    @Test
    @DisplayName("with the server's format unknown, packs are installed rather than all refused")
    void unknownServerFormatInstallsAnyway(@TempDir Path root) throws IOException {
        Path source = Files.createDirectories(root.resolve("source"));
        Path target = root.resolve("world/datapacks");
        pack(source.resolve("pack.zip"), meta(101, 101));

        List<Result> results = DatapackInstaller.install(source, target, 0, line -> { });

        assertEquals(Outcome.INSTALLED, outcomeOf(results, "pack.zip"),
                "a server that will not report its format must not block every pack");
    }

    @Test
    @DisplayName("an empty or missing source folder is not an error")
    void nothingToDo(@TempDir Path root) throws IOException {
        Path target = root.resolve("world/datapacks");
        assertTrue(DatapackInstaller.install(root.resolve("absent"), target, SERVER, l -> { }).isEmpty());
        assertTrue(DatapackInstaller.install(Files.createDirectories(root.resolve("empty")), target,
                SERVER, l -> { }).isEmpty());
    }

    @Test
    @DisplayName("a pack named for retargeting is installed with the server's own version")
    void retargets(@TempDir Path root) throws IOException {
        Path source = Files.createDirectories(root.resolve("source"));
        Path target = root.resolve("world/datapacks");

        pack(source.resolve("future.zip"), meta(101, 101),
                "data/x/worldgen/structure/tower.json");
        pack(source.resolve("other.zip"), meta(101, 101));

        List<Result> results = DatapackInstaller.install(
                source, target, SERVER, java.util.Set.of("future.zip"), line -> { });

        assertEquals(Outcome.RETARGETED, outcomeOf(results, "future.zip"));
        assertEquals(Outcome.INCOMPATIBLE, outcomeOf(results, "other.zip"),
                "a pack that was not named was forced anyway");

        // The copy now declares this server's format, so the server will load it.
        assertEquals(new com.arkcronist.gen.bukkit.datapack.PackMeta(SERVER, SERVER),
                DatapackInstaller.readMeta(target.resolve("future.zip")));

        // ...and the rest of the pack came through untouched.
        try (java.util.zip.ZipFile copy = new java.util.zip.ZipFile(
                target.resolve("future.zip").toFile())) {
            assertNotNull(copy.getEntry("data/x/worldgen/structure/tower.json"),
                    "rewriting the version dropped the pack's contents");
        }

        // The admin's own file still says what its author said.
        assertEquals(new com.arkcronist.gen.bukkit.datapack.PackMeta(101, 101),
                DatapackInstaller.readMeta(source.resolve("future.zip")),
                "the original was modified");
    }

    @Test
    @DisplayName("unreadable files are found, and the vanilla ones are called out separately")
    void findsBrokenFiles(@TempDir Path root) throws IOException {
        Path zip = root.resolve("pack.zip");
        try (java.util.zip.ZipOutputStream out =
                     new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            entry(out, "pack.mcmeta", meta(81, 81));
            entry(out, "data/mine/loot_table/ok.json", "{\"pools\": []}");
            // The real shape: "Unterminated object at line 21" in a shipped loot table.
            entry(out, "data/mine/loot_table/broken.json", "{\"pools\": [{\"entries\": [{");
            // The one that matters. This says it replaces a recipe the game ships, and it will not
            // parse, so that recipe is gone from every world on the server.
            entry(out, "data/minecraft/recipe/netherite_helmet_smithing.json", "{\"result\": ");
        }

        DatapackInstaller.Broken broken = DatapackInstaller.brokenFiles(zip);

        assertEquals(2, broken.total(), "one of the three files written is valid");
        assertEquals(List.of("data/minecraft/recipe/netherite_helmet_smithing.json"),
                broken.vanilla(), "a file replacing the game's own was not called out as such");
        assertEquals(List.of("data/mine/loot_table/broken.json"), broken.own());
    }

    @Test
    @DisplayName("a pack with nothing wrong with it reports nothing")
    void noFalseAlarms(@TempDir Path root) throws IOException {
        Path zip = root.resolve("fine.zip");
        try (java.util.zip.ZipOutputStream out =
                     new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            entry(out, "pack.mcmeta", meta(81, 81));
            entry(out, "data/mine/worldgen/structure/tower.json", "{\"type\": \"minecraft:jigsaw\"}");
            // Not JSON and not claimed to be, so not this check's business.
            entry(out, "data/mine/structure/tower.nbt", "\u0000binary");
            entry(out, "README.txt", "hello");
        }
        assertEquals(0, DatapackInstaller.brokenFiles(zip).total());
    }

    @Test
    @DisplayName("dimensions a pack adds of its own are named, because it cannot then be deleted")
    void findsDimensions(@TempDir Path root) throws IOException {
        Path zip = root.resolve("pack.zip");
        try (java.util.zip.ZipOutputStream out =
                     new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            entry(out, "pack.mcmeta", meta(81, 81));
            entry(out, "data/survivalplus/dimension/backrooms.json", "{}");
            entry(out, "data/survivalplus/dimension/the_void.json", "{}");
            entry(out, "data/survivalplus/dimension_type/backrooms_type.json", "{}");
            // Not a dimension of its own: replacing the game's is a different thing entirely, and
            // deleting the pack afterwards simply restores the game's own.
            entry(out, "data/minecraft/dimension/the_nether.json", "{}");
            // Deeper than a dimension file, and not one.
            entry(out, "data/survivalplus/worldgen/biome/wasteland.json", "{}");
            entry(out, "data/survivalplus/dimension/notes.txt", "not json");
            // A leftover the game itself ignores: "Invalid path in datapack ... ignoring". Listing
            // it would name a dimension that does not exist.
            entry(out, "data/survivalplus/dimension/the_void - old2.json", "{}");
        }

        assertEquals(List.of("survivalplus:backrooms", "survivalplus:the_void"),
                DatapackInstaller.dimensions(zip));
    }

    @Test
    @DisplayName("a pack that adds no dimension of its own says so")
    void noDimensions(@TempDir Path root) throws IOException {
        Path zip = root.resolve("pack.zip");
        try (java.util.zip.ZipOutputStream out =
                     new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))) {
            entry(out, "pack.mcmeta", meta(81, 81));
            entry(out, "data/minecraft/dimension/the_end.json", "{}");
            entry(out, "data/mine/worldgen/structure/tower.json", "{}");
        }
        assertTrue(DatapackInstaller.dimensions(zip).isEmpty(),
                "replacing the game's own dimension is not adding one");
    }

    private static void entry(java.util.zip.ZipOutputStream out, String name, String body)
            throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(body.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    @Test
    @DisplayName("the everything switch forces every pack, whatever it declares")
    void retargetAll(@TempDir Path root) throws IOException {
        Path source = Files.createDirectories(root.resolve("source"));
        Path target = root.resolve("world/datapacks");
        pack(source.resolve("future.zip"), meta(101, 101));
        pack(source.resolve("ancient.zip"), meta(48, 60));
        pack(source.resolve("fine.zip"), meta(81, 81));

        List<Result> results = DatapackInstaller.install(source, target, SERVER,
                java.util.Set.of(DatapackInstaller.EVERYTHING), line -> { });

        assertEquals(Outcome.RETARGETED, outcomeOf(results, "future.zip"));
        assertEquals(Outcome.RETARGETED, outcomeOf(results, "ancient.zip"),
                "a pack built for an older game was left out");
        // Already compatible, so there is nothing to rewrite and its own version is kept.
        assertEquals(Outcome.INSTALLED, outcomeOf(results, "fine.zip"));
    }

    @Test
    @DisplayName("a named pack is matched the way an admin would write its name")
    void forgivingNames() {
        java.util.Set<String> asked = java.util.Set.of("Reds_Structure_v1.1.0.zip");

        assertTrue(DatapackInstaller.shouldRetarget(asked, "Reds_Structure_v1.1.0.zip"));
        // What a browser does to a second download. This exact mismatch made the feature look
        // broken on a live server.
        assertTrue(DatapackInstaller.shouldRetarget(asked, "Reds_Structure_v1.1.0 (1).zip"));
        assertTrue(DatapackInstaller.shouldRetarget(asked, "reds_structure_v1.1.0.ZIP"));
        // Written without the extension, which is how people name things.
        assertTrue(DatapackInstaller.shouldRetarget(
                java.util.Set.of("Reds_Structure_v1.1.0"), "Reds_Structure_v1.1.0.zip"));

        // A different pack is still a different pack.
        assertFalse(DatapackInstaller.shouldRetarget(asked, "Reds_Structure_v1.1.2.zip"),
                "a different version was matched as the same pack");
        assertFalse(DatapackInstaller.shouldRetarget(asked, "Terralith.zip"));
        assertFalse(DatapackInstaller.shouldRetarget(java.util.Set.of(), "anything.zip"));
    }

    private static Outcome outcomeOf(List<Result> results, String file) {
        return results.stream()
                .filter(result -> result.file().equals(file))
                .findFirst()
                .orElseThrow(() -> new AssertionError(file + " was not reported at all"))
                .outcome();
    }

    private static String meta(int min, int max) {
        return "{\"pack\": {\"description\": \"test\", \"pack_format\": " + min
                + ", \"supported_formats\": [" + min + ", " + max + "]}}";
    }

    private static void pack(Path zip, String mcmeta, String... entries) throws IOException {
        Files.createDirectories(zip.getParent());
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write(mcmeta.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            for (String entry : entries) {
                out.putNextEntry(new ZipEntry(entry));
                out.write("{}".getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }
}
