package com.arkcronist.gen.bukkit.colour;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Writes the datapack that gives a biome its own grass and foliage colour.
 *
 * <p>Those colours are decided by the client from the biome it is told it is standing in, and there
 * is no vanilla biome whose grass is red. Paper cannot help either: its registry API can add banner
 * patterns, enchantments and mob variants, but the biome registry is not writable in 1.21.8. The
 * only way left to define a biome with colours of its own is a datapack, so the plugin writes one.</p>
 *
 * <p>It is written into each world folder and picked up the next time that world loads, which means
 * a restart. Nothing depends on it: the biome provider asks the registry for the custom key and
 * falls back to the vanilla one when it is not there, so a pack that fails to load costs the colour
 * and nothing else - the world still generates, and the server still starts.</p>
 */
public final class BiomeColourPack {

    /** Namespace the custom biomes live under. */
    public static final String NAMESPACE = "arkcronist";

    private static final String PACK_NAME = "arkcronist_colours";

    /** One biome's colours. Any field left null keeps whatever the base biome had. */
    public record Colours(String base, Integer grass, Integer foliage, Integer water,
                          Integer waterFog, Integer sky, Integer fog,
                          double temperature, double downfall, boolean precipitation) {
    }

    private BiomeColourPack() {
    }

    /**
     * Writes the pack into one world folder.
     *
     * @return true when something was written or refreshed
     */
    public static boolean install(Path worldFolder, Map<String, Colours> biomes, Logger logger) {
        if (biomes.isEmpty()) {
            return false;
        }
        try {
            Path root = worldFolder.resolve("datapacks").resolve(PACK_NAME);
            Path biomeDir = root.resolve("data").resolve(NAMESPACE).resolve("worldgen").resolve("biome");
            Files.createDirectories(biomeDir);

            // A range rather than a single number: the pack format is bumped most releases, and a
            // pack that merely claims the wrong one is ignored rather than repaired.
            write(root.resolve("pack.mcmeta"), """
                    {
                      "pack": {
                        "description": "ArkcronistGenerator biome colours",
                        "pack_format": 71,
                        "supported_formats": { "min_inclusive": 41, "max_inclusive": 99 }
                      }
                    }
                    """);

            for (Map.Entry<String, Colours> entry : biomes.entrySet()) {
                write(biomeDir.resolve(entry.getKey() + ".json"), json(entry.getValue()));
            }
            logger.info("Biome colour datapack written to " + root
                    + " - it applies the next time this world loads.");
            return true;
        } catch (IOException exception) {
            logger.warning("Could not write the biome colour datapack: " + exception.getMessage()
                    + " - biomes will use their vanilla colours.");
            return false;
        }
    }

    private static void write(Path file, String body) throws IOException {
        Files.writeString(file, body, StandardCharsets.UTF_8);
    }

    private static String json(Colours c) {
        Map<String, Integer> effects = new LinkedHashMap<>();
        effects.put("fog_color", c.fog() != null ? c.fog() : 12638463);
        effects.put("water_color", c.water() != null ? c.water() : 4159204);
        effects.put("water_fog_color", c.waterFog() != null ? c.waterFog() : 329011);
        effects.put("sky_color", c.sky() != null ? c.sky() : 7907327);
        if (c.grass() != null) {
            effects.put("grass_color", c.grass());
        }
        if (c.foliage() != null) {
            effects.put("foliage_color", c.foliage());
        }

        StringBuilder body = new StringBuilder("{\n");
        body.append("  \"has_precipitation\": ").append(c.precipitation()).append(",\n");
        body.append("  \"temperature\": ").append(c.temperature()).append(",\n");
        body.append("  \"downfall\": ").append(c.downfall()).append(",\n");
        body.append("  \"effects\": {\n");
        int i = 0;
        for (Map.Entry<String, Integer> e : effects.entrySet()) {
            body.append("    \"").append(e.getKey()).append("\": ").append(e.getValue());
            body.append(++i < effects.size() ? ",\n" : "\n");
        }
        body.append("  },\n");
        // Empty on purpose. This generator writes every block itself, so a biome here exists only to
        // carry colour; letting the game add its own carvers or features on top would put vanilla
        // caves and vanilla trees into a world that already has its own.
        body.append("  \"spawners\": {},\n");
        body.append("  \"spawn_costs\": {},\n");
        body.append("  \"carvers\": [],\n");
        body.append("  \"features\": []\n");
        body.append("}\n");
        return body.toString();
    }
}
