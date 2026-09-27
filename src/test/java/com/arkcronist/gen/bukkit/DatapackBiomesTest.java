package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.mythic.BiomeProfile;
import com.arkcronist.gen.bukkit.mythic.DatapackBiomes;
import com.arkcronist.gen.bukkit.mythic.MobClassifier.Habitat;
import com.arkcronist.gen.bukkit.mythic.VanillaBiomes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a datapack's biomes from its files, the way Terralith lays them out.
 *
 * <p>Terralith nests its tags: {@code c:is_snowy} lists {@code #terralith:reference/temperature/frozen_all},
 * which lists the biomes. A reader that stopped at the first level would find no snowy biome in
 * Terralith at all, and every ice mob would lose the snowy places whose names do not say so.</p>
 */
class DatapackBiomesTest {

    @TempDir
    Path folder;

    private void zip(String name, Map<String, String> files) throws IOException {
        try (OutputStream out = Files.newOutputStream(folder.resolve(name));
             ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static BiomeProfile find(List<BiomeProfile> profiles, String key) {
        return profiles.stream().filter(p -> p.key().equals(key)).findFirst().orElse(null);
    }

    @Test
    @DisplayName("biomes, their climate and nested tags are read from a zip kept one folder down")
    void readsTerralithShape() throws IOException {
        zip("Terralith.zip", Map.of(
                "Terralith/pack.mcmeta", "{}",
                "Terralith/data/terralith/worldgen/biome/alpine_grove.json",
                "{\"temperature\": -0.2, \"downfall\": 0.8, \"has_precipitation\": true}",
                "Terralith/data/terralith/worldgen/biome/cave/frostfire_caves.json",
                "{\"temperature\": 0.8, \"downfall\": 0.4}",
                "Terralith/data/c/tags/worldgen/biome/is_snowy.json",
                "{\"values\": [\"#terralith:reference/temperature/frozen_all\"]}",
                "Terralith/data/terralith/tags/worldgen/biome/reference/temperature/frozen_all.json",
                "{\"values\": [\"terralith:alpine_grove\", {\"id\": \"minecraft:ice_spikes\","
                        + " \"required\": false}]}",
                "Terralith/data/c/tags/worldgen/biome/is_cave.json",
                "{\"values\": [\"terralith:cave/frostfire_caves\"]}"));
        List<BiomeProfile> read = DatapackBiomes.read(List.of(folder));

        BiomeProfile alpine = find(read, "terralith:alpine_grove");
        assertNotNull(alpine);
        assertEquals(-0.2, alpine.temperature(), 1e-9);
        assertTrue(alpine.tags().contains("is_snowy"), alpine.tags().toString());
        assertTrue(alpine.tags().contains("frozen_all"));
        assertEquals(Habitat.OVERWORLD, alpine.habitat());

        BiomeProfile cave = find(read, "terralith:cave/frostfire_caves");
        assertNotNull(cave);
        assertTrue(cave.words().contains("caves"));
        assertTrue(cave.tags().contains("is_cave"));
    }

    @Test
    @DisplayName("a pack that is a folder is read too, and a broken file does not stop it")
    void folderPackAndBrokenFile() throws IOException {
        Path pack = folder.resolve("MyPack/data/mine/worldgen/biome");
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("ash_wastes.json"), "{\"temperature\": 1.5}");
        Files.writeString(pack.resolve("broken.json"), "{not json");
        List<BiomeProfile> read = DatapackBiomes.read(List.of(folder));
        assertNotNull(find(read, "mine:ash_wastes"));
        assertEquals(1, read.size());
    }

    @Test
    @DisplayName("a datapack's file beats the built-in vanilla table, and registry names are kept")
    void merge() {
        BiomeProfile redefined = BiomeProfile.of("minecraft:plains", java.util.Set.of("is_plains"),
                0.9, 0.4, Habitat.OVERWORLD);
        List<BiomeProfile> all = DatapackBiomes.merge(VanillaBiomes.all(), List.of(redefined),
                List.of("minecraft:plains", "somemod:glowing_marsh"));
        assertEquals(0.9, find(all, "minecraft:plains").temperature(), 1e-9);
        assertTrue(find(all, "minecraft:plains").tags().contains("is_plains"));
        assertNotNull(find(all, "somemod:glowing_marsh"));
        assertEquals(Habitat.NETHER, find(all, "minecraft:crimson_forest").habitat());
    }

    @Test
    @DisplayName("Incendium's biomes go to the Nether because its dimension file lists them")
    void incendiumDimensionFile() throws IOException {
        // No is_nether tag at all: the dimension file alone has to say it. Incendium's real one
        // lists vanilla and its own biomes in a multi_noise source.
        zip("Incendium.zip", Map.of(
                "data/incendium/worldgen/biome/ash_barrens.json", "{\"temperature\": 2.0}",
                "data/incendium/worldgen/biome/toxic_heap.json", "{\"temperature\": 2.0}",
                "data/minecraft/dimension/the_nether.json",
                "{\"type\": \"minecraft:the_nether\", \"generator\": {\"type\": \"minecraft:noise\","
                        + " \"biome_source\": {\"type\": \"minecraft:multi_noise\", \"biomes\": ["
                        + "{\"biome\": \"minecraft:nether_wastes\", \"parameters\": {}},"
                        + "{\"biome\": \"incendium:ash_barrens\", \"parameters\": {}},"
                        + "{\"biome\": \"incendium:toxic_heap\", \"parameters\": {}}]}}}"));
        List<BiomeProfile> read = DatapackBiomes.read(List.of(folder));
        assertEquals(Habitat.NETHER, find(read, "incendium:ash_barrens").habitat());
        assertEquals(Habitat.NETHER, find(read, "incendium:toxic_heap").habitat());
    }

    @Test
    @DisplayName("Nullscape's biomes go to the End, through a tag named in its dimension file")
    void nullscapeDimensionFileWithTag() throws IOException {
        zip("Nullscape.zip", Map.of(
                "data/nullscape/worldgen/biome/void_barrens.json", "{\"temperature\": 0.5}",
                "data/nullscape/worldgen/biome/shadowlands.json", "{\"temperature\": 0.5}",
                "data/nullscape/tags/worldgen/biome/all_nullscape_biomes.json",
                "{\"values\": [\"nullscape:void_barrens\", \"nullscape:shadowlands\"]}",
                "data/minecraft/dimension/the_end.json",
                "{\"type\": \"minecraft:the_end\", \"generator\": {\"biome_source\": {\"type\":"
                        + " \"minecraft:checkerboard\", \"biomes\": \"#nullscape:all_nullscape_biomes\"}}}"));
        List<BiomeProfile> read = DatapackBiomes.read(List.of(folder));
        assertEquals(Habitat.END, find(read, "nullscape:void_barrens").habitat());
        assertEquals(Habitat.END, find(read, "nullscape:shadowlands").habitat());
    }

    @Test
    @DisplayName("a mob named for ash lands in Incendium's Ash Barrens and nowhere in the overworld")
    void ashMobFindsIncendium() throws IOException {
        incendiumDimensionFile();
        List<BiomeProfile> all = DatapackBiomes.merge(VanillaBiomes.all(),
                DatapackBiomes.read(List.of(folder)), List.of());
        com.arkcronist.gen.bukkit.mythic.BiomeThemes.Result result =
                com.arkcronist.gen.bukkit.mythic.BiomeThemes.assign(List.of(
                        new com.arkcronist.gen.bukkit.mythic.BiomeThemes.Candidate("ash_wraith", "",
                                Habitat.NETHER)), all, Map.of());
        assertEquals(List.of("incendium:ash_barrens"), result.byMob().get("ash_wraith").biomes());
    }

    @Test
    @DisplayName("a missing folder is not an error")
    void missingFolder() {
        assertTrue(DatapackBiomes.read(List.of(folder.resolve("nope"))).isEmpty());
    }
}
