package com.arkcronist.gen.bukkit.command;

import com.arkcronist.gen.bukkit.ArkWorld;
import com.arkcronist.gen.bukkit.loot.LootFiller;
import com.arkcronist.gen.bukkit.ArkcronistPlugin;
import com.arkcronist.gen.bukkit.BlockBridge;
import com.arkcronist.gen.bukkit.mobs.MythicBridge;
import com.arkcronist.gen.core.bench.TerrainBenchmark;
import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.prefab.Prefab;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.structure.Structure;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainCache;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** {@code /ag} - the plugin's control surface: info, diagnostics, locate, benchmark and reload. */
public final class AgCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§8[§bArkcronist§8] §r";

    private final ArkcronistPlugin plugin;

    public AgCommand(ArkcronistPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             String @NotNull [] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(sender);
            case "version" -> version(sender);
            case "presets" -> presets(sender);
            case "info" -> info(sender);
            case "biome" -> biome(sender, args);
            case "locate" -> locate(sender, args);
            case "structures" -> structures(sender);
            case "prefabs" -> prefabs(sender);
            case "stats" -> stats(sender);
            case "bench" -> bench(sender, args);
            case "top" -> top(sender);
            case "reload" -> reload(sender);
            default -> {
                sender.sendMessage(PREFIX + "§cUnknown subcommand '" + sub + "'.");
                help(sender);
            }
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(PREFIX + "§bArkcronistGenerator §7commands:");
        sender.sendMessage("§8 - §f/ag info §7shows the world's preset, seed and terrain data here");
        sender.sendMessage("§8 - §f/ag biome [x z] §7names the generator biome at a position");
        sender.sendMessage("§8 - §f/ag locate [structure] §7finds the nearest structure");
        sender.sendMessage("§8 - §f/ag structures §7lists the structure catalogue");
        sender.sendMessage("§8 - §f/ag prefabs §7lists the .schem trees, ships and landmarks loaded");
        sender.sendMessage("§8 - §f/ag presets §7lists BASE, CHAOTIC and INSANE");
        sender.sendMessage("§8 - §f/ag stats §7cache and memory diagnostics");
        sender.sendMessage("§8 - §f/ag bench [preset] [chunks] §7runs a generation benchmark");
        sender.sendMessage("§8 - §f/ag top §7teleports you to the surface");
        sender.sendMessage("§8 - §f/ag reload §7reloads config.yml");
        sender.sendMessage("§7Create a world with §f-g ArkcronistGenerator:INSANE");
    }

    private void version(CommandSender sender) {
        sender.sendMessage(PREFIX + "Version §b" + plugin.getPluginMeta().getVersion()
                + " §7| block states resolved: §f" + BlockBridge.size());
        // Worth a line of its own: when the custom mobs do not turn up on a server, the first thing
        // to know is whether this plugin ever managed to reach MythicMobs at all. Without it the
        // answer was only in the startup log, which by then has scrolled away.
        sender.sendMessage("§7 Custom hostile mobs: " + (MythicBridge.available()
                ? "§aMythicMobs " + MythicBridge.version()
                        + " §7| presets §f" + plugin.arkConfig().hostileMobPresets()
                        + " §7| reasons §f" + plugin.arkConfig().hostileMobReasons()
                        + " §7| " + plugin.arkConfig().hostileMobTable().size() + " vanilla types replaced"
                : "§enot available §7(MythicMobs missing or its API could not be reached)"));
    }

    private void presets(CommandSender sender) {
        sender.sendMessage(PREFIX + "Presets:");
        for (Preset preset : Preset.values()) {
            sender.sendMessage("§8 - §b" + preset + " §7" + preset.description());
        }
    }

    private ArkWorld worldOf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cRun this from in-game, or name a world.");
            return null;
        }
        ArkWorld world = plugin.worlds().find(player.getWorld().getName());
        if (world == null) {
            sender.sendMessage(PREFIX + "§cThis world is not generated by ArkcronistGenerator.");
        }
        return world;
    }

    private void info(CommandSender sender) {
        ArkWorld world = worldOf(sender);
        if (world == null) {
            return;
        }
        Player player = (Player) sender;
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        ArkBiome biome = world.engine().biomeAt(x, z);
        int surface = world.engine().surfaceHeight(x, z);
        int water = world.engine().waterLevel(x, z);

        sender.sendMessage(PREFIX + "World §f" + world.name() + " §7preset §b" + world.preset()
                + " §7seed §f" + world.seed());
        companions(sender, world.name());
        sender.sendMessage("§7 Biome §f" + biome.name + " §8(" + biome.category
                + " → " + biome.vanillaKey + ")");
        sender.sendMessage("§7 Surface §f" + surface + " §7water §f" + water
                + " §7sea level §f" + world.engine().settings().seaLevel);
        if (surface < water) {
            sender.sendMessage("§7 Underwater here: §b" + (water - surface) + " blocks deep");
        }
        sender.sendMessage("§7 Trees §f" + biome.trees.length + " species §7| structures allowed: §f"
                + (biome.structures.isEmpty() ? "none" : biome.structures.toString()));
    }

    /**
     * Which Nether and End belong to this world, and whether they are actually there.
     *
     * <p>Worth a line because the failure is invisible otherwise: the worlds are created once, and if
     * the server refused - a name it would not take, a world manager that objected, a full disk -
     * nothing about walking around the overworld looks any different, and the first anybody knows is
     * a player coming out of a portal in the wrong Nether.</p>
     */
    private void companions(CommandSender sender, String name) {
        if (!plugin.arkConfig().ownDimensions()) {
            sender.sendMessage("§7 Own Nether and End: §eoff §8(dimensions.enabled)");
            return;
        }
        StringBuilder line = new StringBuilder("§7 Own dimensions: ");
        line.append(part(plugin.arkConfig().ownNether(),
                com.arkcronist.gen.bukkit.world.DimensionLinks.netherOf(name,
                        plugin.arkConfig().netherSuffix())));
        line.append(" §7| ");
        line.append(part(plugin.arkConfig().ownEnd(),
                com.arkcronist.gen.bukkit.world.DimensionLinks.endOf(name,
                        plugin.arkConfig().endSuffix())));
        if (!plugin.arkConfig().linkPortals()) {
            line.append(" §7| portals §enot routed §8(dimensions.link-portals)");
        }
        sender.sendMessage(line.toString());
    }

    private String part(boolean wanted, String name) {
        if (!wanted) {
            return "§8" + name + " (off)";
        }
        return plugin.getServer().getWorld(name) == null
                ? "§c" + name + " (missing)" : "§a" + name;
    }

    private void biome(CommandSender sender, String[] args) {
        ArkWorld world = worldOf(sender);
        if (world == null) {
            return;
        }
        Player player = (Player) sender;
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        if (args.length >= 3) {
            try {
                x = Integer.parseInt(args[1]);
                z = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(PREFIX + "§cCoordinates must be whole numbers.");
                return;
            }
        }
        ArkBiome biome = world.engine().biomeAt(x, z);
        sender.sendMessage(PREFIX + "Biome at §f" + x + ", " + z + "§7: §b" + biome.name
                + " §8(" + biome.vanillaKey + ")");
    }

    private void structures(CommandSender sender) {
        sender.sendMessage(PREFIX + "Structure catalogue:");
        ArkWorld world = plugin.worlds().all().stream().findFirst().orElse(null);
        if (world == null) {
            sender.sendMessage("§7 No Arkcronist world is loaded yet.");
            return;
        }
        for (Structure structure : world.structures().structures()) {
            sender.sendMessage("§8 - §f" + structure.id() + " §7(" + structure.tag()
                    + ", radius " + structure.radius() + ")");
        }
    }

    private void prefabs(CommandSender sender) {
        PrefabRegistry registry = plugin.prefabs();
        if (registry.size() == 0) {
            sender.sendMessage(PREFIX + "§cNo prefabs are loaded. Put .schem files in "
                    + plugin.getDataFolder().getName() + "/prefabs/<trees|ships|ruins>/ and restart.");
            return;
        }
        sender.sendMessage(PREFIX + registry.size() + " prefabs loaded:");
        for (String category : registry.categories()) {
            List<Prefab> prefabs = registry.category(category);
            sender.sendMessage("§8 " + category + " §7(" + prefabs.size() + ")");
            for (Prefab prefab : prefabs) {
                sender.sendMessage("§8  - §f" + prefab.id + " §7" + prefab.width + "x" + prefab.height
                        + "x" + prefab.length + ", " + prefab.sizeClass + ", " + prefab.solidCount + " blocks");
            }
        }
    }

    private void locate(CommandSender sender, String[] args) {
        ArkWorld world = worldOf(sender);
        if (world == null) {
            return;
        }
        Player player = (Player) sender;
        StructureTag tag = null;
        if (args.length >= 2) {
            try {
                tag = StructureTag.valueOf(args[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(PREFIX + "§cUnknown structure type. Try: "
                        + Arrays.toString(StructureTag.values()));
                return;
            }
        }
        final StructureTag target = tag;
        // "Nothing found within range" is the wrong answer when the family is not in this world at
        // all - it sends someone looking for something that was never going to be there. The
        // ancient city is the case that matters: it exists only when its schematic is present.
        if (target != null) {
            boolean registered = false;
            for (com.arkcronist.gen.core.structure.Structure structure : world.structures().structures()) {
                if (structure.tag() == target) {
                    registered = true;
                    break;
                }
            }
            if (!registered) {
                sender.sendMessage(PREFIX + "§c" + target + " is not registered in this world.");
                if (target == StructureTag.ANCIENT_CITY) {
                    sender.sendMessage("§7 The ancient city is built from a schematic. Check that "
                            + "§fplugins/ArkcronistGenerator/prefabs/ancient_city/§7 has a .schem in "
                            + "it, then restart. §f/ag prefabs§7 lists what loaded.");
                }
                return;
            }
        }
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        sender.sendMessage(PREFIX + "Searching…");
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            int rings = target == null ? 6
                    : com.arkcronist.gen.core.structure.StructurePlacer.searchRings(target);
            int[] found = world.structures().locate(x, z, target, rings);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (found == null) {
                    sender.sendMessage(PREFIX + "§cNothing found within range.");
                } else {
                    sender.sendMessage(PREFIX + "Nearest match at §f" + found[0] + ", " + found[1]
                            + ", " + found[2] + " §8(" + distance(x, z, found[0], found[2]) + " blocks away)");
                }
            });
        });
    }

    private static int distance(int x0, int z0, int x1, int z1) {
        double dx = x1 - x0;
        double dz = z1 - z0;
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    private void stats(CommandSender sender) {
        sender.sendMessage(PREFIX + "Diagnostics:");
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        sender.sendMessage("§7 Heap in use: §f" + usedMb + " MB");
        for (ArkWorld world : plugin.worlds().all()) {
            TerrainCache cache = world.engine().cache();
            sender.sendMessage("§8 - §f" + world.name() + " §7[" + world.preset() + "] cache "
                    + cache.size() + " chunks, hit rate "
                    + String.format(Locale.ROOT, "%.1f%%", cache.hitRate() * 100.0)
                    + ", queued mob groups " + world.mobQueue().size());
        }

        // Loot is the one part of generation with no visible trace when it fails: the chests are
        // there, they are just empty, and nothing is logged. These numbers say whether the
        // containers were found at all, and separate the ones left empty on purpose from the ones
        // that failed - without that split, a low fill-chance looks exactly like a bug.
        long[] loot = LootFiller.counters();
        sender.sendMessage("§7 Containers seen: §f" + loot[0]
                + "§7, filled: §f" + loot[1]
                + "§7, empty by fill-chance: §f" + loot[4]
                + "§7, block replaced first: §f" + loot[2]
                + "§7, from a vanilla table: §f" + loot[3]);
        long unaccounted = loot[0] - loot[1] - loot[4];
        if (loot[0] > 0 && unaccounted > 0) {
            sender.sendMessage("§c " + unaccounted
                    + " container(s) could not be filled - the block did not read back as a container.");
        }
        java.util.Set<String> themes = plugin.lootRules().names();
        sender.sendMessage("§7 Loot themes configured: §f"
                + (themes.isEmpty() ? "none (built-in rules everywhere)" : String.join(", ", themes)));
    }

    private void bench(CommandSender sender, String[] args) {
        Preset preset = args.length >= 2 ? Preset.parse(args[1]) : plugin.arkConfig().defaultPreset();
        int chunks = 64;
        if (args.length >= 3) {
            try {
                chunks = Math.max(8, Math.min(2048, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignored) {
                // keep the default
            }
        }
        long seed = sender instanceof Player player ? player.getWorld().getSeed() : 12345L;
        final int total = chunks;
        sender.sendMessage(PREFIX + "Benchmarking " + preset + " over " + total + " chunks…");
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            TerrainBenchmark.Result result = TerrainBenchmark.run(seed, preset, total, 100000, 100000,
                    plugin.prefabs());
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    sender.sendMessage(PREFIX + "§f" + result.describe()));
        });
    }

    private void top(CommandSender sender) {
        ArkWorld world = worldOf(sender);
        if (world == null) {
            return;
        }
        Player player = (Player) sender;
        Location location = player.getLocation();
        int surface = world.engine().surfaceHeight(location.getBlockX(), location.getBlockZ());
        int water = world.engine().waterLevel(location.getBlockX(), location.getBlockZ());
        location.setY(Math.max(surface, water) + 1.5);
        player.teleport(location);
        sender.sendMessage(PREFIX + "Moved you to y=" + location.getBlockY());
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("arkcronist.admin")) {
            sender.sendMessage(PREFIX + "§cYou do not have permission to do that.");
            return;
        }
        plugin.reload();
        sender.sendMessage(PREFIX + "Configuration reloaded. Existing chunks keep their terrain; "
                + "changes apply to newly generated chunks.");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String @NotNull [] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("help", "info", "biome", "locate", "structures", "prefabs",
                    "presets", "stats", "bench", "top", "reload", "version"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("bench")) {
            for (Preset preset : Preset.values()) {
                options.add(preset.name());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("locate")) {
            for (StructureTag tag : StructureTag.values()) {
                options.add(tag.name());
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
