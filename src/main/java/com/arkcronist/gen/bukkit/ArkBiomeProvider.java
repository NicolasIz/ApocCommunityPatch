package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.math.Hashing;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps ArkcronistGenerator's biomes onto vanilla biome keys for the client.
 *
 * <p>The generator's own biome table drives terrain, materials, vegetation and structures; the
 * vanilla key only decides grass colour, fog, ambience and which mobs the server spawns naturally.
 * Several Arkcronist biomes can therefore share a vanilla key while looking nothing alike on the
 * ground.</p>
 *
 * <p>Below the surface the provider switches to cave biomes, which is what gives the underground its
 * own ambience instead of inheriting the sky above it.</p>
 */
public final class ArkBiomeProvider extends BiomeProvider {

    private final TerrainEngine engine;
    private final Biome[] byArkId;
    private final Biome dripstone;
    private final Biome lush;
    private final Biome deepDark;
    private final List<Biome> all;

    public ArkBiomeProvider(TerrainEngine engine) {
        this.engine = engine;
        List<ArkBiome> biomes = engine.biomes().all();
        this.byArkId = new Biome[biomes.size()];
        Set<Biome> unique = new LinkedHashSet<>();
        for (ArkBiome biome : biomes) {
            Biome resolved = resolve(biome.vanillaKey);
            byArkId[biome.id] = resolved;
            unique.add(resolved);
        }
        this.dripstone = resolve("minecraft:dripstone_caves");
        this.lush = resolve("minecraft:lush_caves");
        this.deepDark = resolve("minecraft:deep_dark");
        unique.add(dripstone);
        unique.add(lush);
        unique.add(deepDark);
        this.all = new ArrayList<>(unique);
    }

    private static Biome resolve(String key) {
        NamespacedKey namespaced = NamespacedKey.fromString(key);
        if (namespaced != null) {
            try {
                Biome biome = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(namespaced);
                if (biome != null) {
                    return biome;
                }
            } catch (Throwable ignored) {
                // Older or unusual builds: fall through to the legacy registry.
            }
            try {
                Biome biome = Registry.BIOME.get(namespaced);
                if (biome != null) {
                    return biome;
                }
            } catch (Throwable ignored) {
                // Fall through to plains.
            }
        }
        return Biome.PLAINS;
    }

    @Override
    public @NotNull Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        int surface = engine.surfaceHeight(x, z);
        if (y < surface - 16 && y < engine.settings().seaLevel - 8) {
            // Underground: give the caves their own identity, in patches rather than uniformly.
            long region = Hashing.hash(engine.seed() ^ 0xCA5E1L, x >> 7, z >> 7);
            int pick = (int) ((region >>> 20) % 10);
            // The deep dark has to cover an ancient city's whole vertical extent, not just the
            // floor it stands on. The server checks the biome at the structure's own start height,
            // and a city reaches well above the bedrock slice this used to be limited to - so with
            // the old bound the check was made against dripstone or stone and no city could ever
            // be placed. Kept generous on purpose: the exact start height is the game's to choose.
            if (y < engine.settings().minY + 52 && pick < 2) {
                return deepDark;
            }
            if (pick < 3) {
                return lush;
            }
            if (pick < 7) {
                return dripstone;
            }
        }
        int id = engine.terrain(x >> 4, z >> 4).biomeAt(x & 15, z & 15);
        return byArkId[id];
    }

    @Override
    public @NotNull List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        return all;
    }
}
