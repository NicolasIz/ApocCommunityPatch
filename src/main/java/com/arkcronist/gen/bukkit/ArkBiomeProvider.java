package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.colour.BiomeColourPack;
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
    private final List<Biome> all;

    public ArkBiomeProvider(TerrainEngine engine) {
        this.engine = engine;
        List<ArkBiome> biomes = engine.biomes().all();
        this.byArkId = new Biome[biomes.size()];
        Set<Biome> unique = new LinkedHashSet<>();
        for (ArkBiome biome : biomes) {
            // A biome the colour datapack defined wins over the vanilla key it was based on. That
            // pack is the only way to give a biome grass of its own colour - Paper's registry API
            // cannot add biomes - and it is entirely optional: when it is not installed, or failed
            // to load, the lookup comes back null and the vanilla key is used exactly as before.
            Biome resolved = lookup(BiomeColourPack.NAMESPACE + ":" + biome.name);
            if (resolved == null) {
                resolved = resolve(biome.vanillaKey);
            }
            byArkId[biome.id] = resolved;
            unique.add(resolved);
        }
        this.dripstone = resolve("minecraft:dripstone_caves");
        this.lush = resolve("minecraft:lush_caves");
        unique.add(dripstone);
        unique.add(lush);
        this.all = new ArrayList<>(unique);
    }

    /** Resolves a biome key, or null when the registry does not hold it. */
    private static Biome lookup(String key) {
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
        return null;
    }

    /** Resolves a key, falling back to plains so the provider never hands back null. */
    private static Biome resolve(String key) {
        Biome found = lookup(key);
        return found == null ? Biome.PLAINS : found;
    }

    @Override
    public @NotNull Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        int surface = engine.surfaceHeight(x, z);
        if (y < surface - 16 && y < engine.settings().seaLevel - 8) {
            // Underground: give the caves their own identity, in patches rather than uniformly.
            long region = Hashing.hash(engine.seed() ^ 0xCA5E1L, x >> 7, z >> 7);
            int pick = (int) ((region >>> 20) % 10);
            // deep_dark is deliberately never reported.
            //
            // It is the only biome the server will place an ancient city in, so withholding it is
            // what takes that one structure off the server and hands it to this generator - which
            // builds it from a schematic instead. Nothing else is affected: strongholds, mineshafts,
            // trial chambers, monuments, villages and the rest do not ask for this key and generate
            // exactly as they always did.
            //
            // The cost is the biome's own ambience - its fog, its silence, its lack of ordinary mob
            // spawns. The city still arrives with all of its sculk, and its shriekers still summon
            // what they summon, because those are blocks rather than biome behaviour.
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
