package com.arkcronist.gen.bukkit.command;

import com.arkcronist.gen.bukkit.ArkWorld;
import com.arkcronist.gen.bukkit.loot.LootFiller;
import com.arkcronist.gen.bukkit.ArkcronistPlugin;
import com.arkcronist.gen.bukkit.BlockBridge;
import com.arkcronist.gen.bukkit.mobs.MythicBridge;
import com.arkcronist.gen.bukkit.mythic.MobClassifier;
import com.arkcronist.gen.bukkit.mythic.MobDiscovery;
import com.arkcronist.gen.bukkit.mythic.MobRoster;
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
            case "mobs" -> mobs(sender);
            case "mythic" -> mythic(sender, args);
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
        sender.sendMessage("§8 - §f/ag mobs §7says whether the custom mobs apply where you stand");
        sender.sendMessage("§8 - §f/ag mythic [list|refresh|export|models|biomes|<name>] §7what was read"
                + " from MythicMobs and how each mob was judged");
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

    /**
     * Answers "why are there no custom mobs here", in one screen.
     *
     * <p>Every gate between a world and the goblins is a quiet one. The world may not be registered;
     * its preset may not be listed; MythicMobs may be missing; the ambient list may be empty because
     * the config on disk predates it; the player may be in creative, which the spawner skips. None
     * of those log anything, and the symptom for all of them is identical - nothing appears - so the
     * only way to tell them apart used to be reading the source.</p>
     */
    private void mobs(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cRun this from in-game.");
            return;
        }
        org.bukkit.World world = player.getWorld();
        com.arkcronist.gen.bukkit.config.ArkConfig config = plugin.arkConfig();
        String preset = com.arkcronist.gen.bukkit.mobs.MobWorlds.presetOf(plugin, world);
        boolean applies = com.arkcronist.gen.bukkit.mobs.MobWorlds.applies(plugin, world);
        boolean nether = world.getEnvironment() == org.bukkit.World.Environment.NETHER;

        sender.sendMessage(PREFIX + "Custom mobs in §f" + world.getName() + " §8("
                + world.getEnvironment() + ")");
        sender.sendMessage(tick(config.hostileMobsEnabled()) + " hostile-mobs.enabled");
        sender.sendMessage(tick(com.arkcronist.gen.bukkit.mobs.MythicBridge.available())
                + " MythicMobs present");

        if (preset == null) {
            sender.sendMessage("§c ✗ this world is not one of ours. Create it with"
                    + " §f-g ArkcronistGenerator:INSANE§c, or name it under"
                    + " §fhostile-mobs.adopt-worlds§c.");
        } else {
            sender.sendMessage("§a ✔ recognised, preset §f" + preset);
            if (!applies && !nether) {
                sender.sendMessage("§c ✗ but §f" + preset + "§c is not in §fhostile-mobs.presets"
                        + "§c, which is currently " + config.hostileMobPresets()
                        + ". Add it there or nothing swaps here.");
            }
        }

        // Which table and list actually apply on this side of the portal, discovery included -
        // reporting only the configured counts was the reason a working setup looked broken here.
        boolean end = world.getEnvironment() == org.bukkit.World.Environment.THE_END;
        MobClassifier.Habitat habitat = MobRoster.habitatOf(world);
        int table = plugin.roster().swapTable(config, habitat).size();
        java.util.List<String> pool = plugin.roster().ambientPool(config, habitat);
        if (nether) {
            sender.sendMessage(tick(config.netherApplies(world.getName()))
                    + " hostile-mobs.nether applies to this world");
        }
        if (end) {
            sender.sendMessage(tick(config.endApplies(world.getName()))
                    + " hostile-mobs.end applies to this world");
        }
        if (config.autoDiscoverEnabled()) {
            sender.sendMessage("§a ✔ auto-discovery is on: §f"
                    + plugin.roster().discoveredFor(habitat).size()
                    + "§7 mob(s) read for §b" + habitat + "§7. §f/ag mythic§7 for the detail.");
        }
        sender.sendMessage(tick(table > 0) + " swap table has §f" + table + "§7 entr"
                + (table == 1 ? "y" : "ies") + " §8(replaces natural spawns)");
        sender.sendMessage(tick(config.ambientEnabled() && !pool.isEmpty())
                + " ambient list has §f" + pool.size() + "§7 name(s) §8(the only thing that puts"
                + " hostiles out in daylight)");
        if (pool.isEmpty()) {
            sender.sendMessage("§7   Empty. A config.yml written before this feature existed has no"
                    + " §fhostile-mobs.ambient§7 section at all, and saveDefaultConfig never"
                    + " replaces a file that is already there. §fhostile-mobs.auto-discover.enabled:"
                    + " true§7 fills it from whatever MythicMobs has loaded instead.");
        }
        sender.sendMessage("§7 Spawn reasons swapped: §f" + config.hostileMobReasons());
        // The one number that explains "why do I still see zombies" and "why do I never see zombies",
        // which are the same question asked from opposite sides.
        double chance = config.replaceChance();
        sender.sendMessage("§7 Mix: §f" + Math.round(chance * 100.0) + "%§7 of the types above become"
                + " custom mobs, the rest stay vanilla §8(hostile-mobs.replace-chance)");
        if (chance >= 1.0) {
            sender.sendMessage("§7   At 100% a type in the table never appears as itself again."
                    + " Want both? Set §freplace-chance: 0.6§7"
                    + (config.replaceChanceConfigured() ? "." : " - your config.yml has no such key."));
        }
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            sender.sendMessage("§e ! you are in " + player.getGameMode()
                    + ", and the ambient spawner skips players who are.");
        }
        if (world.getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
            sender.sendMessage("§e ! difficulty is PEACEFUL, so nothing hostile spawns at all.");
        }
    }

    private static String tick(boolean ok) {
        return ok ? "§a ✔" : "§c ✗";
    }

    /**
     * What was read from MythicMobs, and why each mob was judged the way it was.
     *
     * <p>This exists because the classification is a set of heuristics and a heuristic nobody can
     * inspect is indistinguishable from a bug. Every verdict carries the reason it was reached, so an
     * operator who disagrees can see which rule fired and overrule exactly that one in
     * {@code hostile-mobs.auto-discover.roles} or {@code .habitats} rather than switching the whole
     * feature off.</p>
     */
    private void mythic(CommandSender sender, String[] args) {
        MobRoster roster = plugin.roster();
        MobDiscovery.Result result = roster.result();
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";

        if (what.equals("refresh")) {
            if (!sender.hasPermission("arkcronist.admin")) {
                sender.sendMessage(PREFIX + "§cYou do not have permission to do that.");
                return;
            }
            roster.refresh(plugin.arkConfig(), plugin.getLogger());
            sender.sendMessage(PREFIX + "Re-read MythicMobs: §f" + roster.state());
            return;
        }
        if (what.equals("export")) {
            if (!sender.hasPermission("arkcronist.admin")) {
                sender.sendMessage(PREFIX + "§cYou do not have permission to do that.");
                return;
            }
            export(sender, result);
            return;
        }

        if (what.equals("models")) {
            models(sender);
            return;
        }
        if (what.equals("biomes")) {
            biomes(sender, roster);
            return;
        }
        if (what.isEmpty()) {
            summary(sender, roster, result);
            return;
        }
        if (what.equals("list")) {
            if (result.total() == 0) {
                sender.sendMessage(PREFIX + "§eNothing read: " + roster.state() + ".");
                return;
            }
            for (MobClassifier.Habitat habitat : MobClassifier.Habitat.values()) {
                names(sender, habitat.name(), result.hostilesFor(habitat));
            }
            return;
        }
        // A role or a habitat names a group; anything else is taken as a mob name.
        for (MobClassifier.Role role : MobClassifier.Role.values()) {
            if (role.name().equalsIgnoreCase(what)) {
                withRole(sender, result, role);
                return;
            }
        }
        for (MobClassifier.Habitat habitat : MobClassifier.Habitat.values()) {
            if (habitat.name().equalsIgnoreCase(what)) {
                names(sender, habitat.name() + " hostiles", result.hostilesFor(habitat));
                names(sender, habitat.name() + " bosses", result.bossesFor(habitat));
                return;
            }
        }
        one(sender, result, args[1]);
    }

    private void summary(CommandSender sender, MobRoster roster, MobDiscovery.Result result) {
        com.arkcronist.gen.bukkit.config.ArkConfig config = plugin.arkConfig();
        sender.sendMessage(PREFIX + "Mob auto-discovery");
        sender.sendMessage(tick(config.autoDiscoverEnabled())
                + " hostile-mobs.auto-discover.enabled §8(" + roster.state() + ")");
        if (!config.autoDiscoverEnabled()) {
            sender.sendMessage("§7 Switched on, this reads every mob MythicMobs has loaded and sorts"
                    + " it by itself, so a new pack needs no config.yml edit.");
            if (!config.autoDiscoverConfigured()) {
                // The section is absent rather than refused, which on an upgraded server is the
                // normal case and the one that otherwise looks like a broken feature.
                sender.sendMessage("§e Your config.yml has no §fauto-discover§e section. Add this"
                        + " under §fhostile-mobs:§e (two spaces in):");
                sender.sendMessage("§8   auto-discover:");
                sender.sendMessage("§8     enabled: true");
                sender.sendMessage("§8     boss-health: 250");
                sender.sendMessage("§8     swap: true");
                sender.sendMessage("§8     ambient: true");
                sender.sendMessage("§7 Then §f/ag reload§7.");
            }
            return;
        }
        sender.sendMessage(tick(config.autoDiscoverSwap())
                + " .swap §8(fills the entity types hostile-mobs.table does not name)");
        sender.sendMessage(tick(config.autoDiscoverAmbient())
                + " .ambient §8(adds to the daylight pools)");
        sender.sendMessage("§7 Boss threshold: §f" + config.autoDiscoverBossHealth() + " health");
        sender.sendMessage("§7 Read §f" + result.total() + "§7 mobs:");
        for (MobClassifier.Habitat habitat : MobClassifier.Habitat.values()) {
            int hostiles = result.hostilesFor(habitat).size();
            int bosses = result.bossesFor(habitat).size();
            int bodies = result.swapFor(habitat).size();
            if (hostiles == 0 && bosses == 0) {
                continue;
            }
            sender.sendMessage("§8  - §b" + habitat + "§7: §f" + hostiles + "§7 hostile, §f"
                    + bosses + "§7 boss, standing in for §f" + bodies + "§7 vanilla type(s)");
        }
        com.arkcronist.gen.bukkit.mythic.BiomeThemes.Result placed = roster.placement();
        String biomeNote = placed.biomesRead() == 0 ? "no biomes read"
                : placed.themed().size() + " mob(s) given biomes from their names, out of "
                        + placed.biomesRead() + " biomes read";
        sender.sendMessage(tick(config.autoDiscoverBiomes()) + " .biomes §8(" + biomeNote
                + "; §f/ag mythic biomes§8)");
        sender.sendMessage("§7 Set aside: §f" + result.props().size() + "§7 props, §f"
                + result.pets().size() + "§7 pets, §f" + result.unknown().size()
                + "§7 undecided §8(none of these ever spawn)");
        sender.sendMessage("§7 §f/ag mythic list§7, §f/ag mythic boss§7, §f/ag mythic nether§7,"
                + " §f/ag mythic <name>§7 for the reason, §f/ag mythic models§7 for ModelEngine,"
                + " §f/ag mythic export§7 to write it all out as config.");
    }

    /**
     * Which ModelEngine blueprints loaded, which is the first question when a mob looks wrong.
     *
     * <p>A mob whose model failed to load still spawns and still fights - it simply turns up as a
     * magenta box or as the bare vanilla body underneath, with nothing in the log about it. Listing
     * what ModelEngine actually has turns that into a question with an answer.</p>
     */
    private void models(CommandSender sender) {
        if (!com.arkcronist.gen.bukkit.mythic.ModelEngineModels.available()) {
            sender.sendMessage(PREFIX + "§eModelEngine is not installed. Mobs still spawn and fight;"
                    + " they wear the vanilla body they were built on instead of a model.");
            return;
        }
        List<String> blueprints = com.arkcronist.gen.bukkit.mythic.ModelEngineModels.read();
        sender.sendMessage(PREFIX + "ModelEngine §f"
                + com.arkcronist.gen.bukkit.mythic.ModelEngineModels.version() + "§7 - §f"
                + blueprints.size() + "§7 blueprint(s) loaded");
        if (blueprints.isEmpty()) {
            sender.sendMessage("§e None. Either no model pack is installed, or ModelEngine's API"
                    + " could not be reached from here. A mob with no model shows up as a magenta"
                    + " box or as its vanilla body.");
            return;
        }
        names(sender, "blueprints", blueprints);
    }

    private void withRole(CommandSender sender, MobDiscovery.Result result, MobClassifier.Role role) {
        List<String> named = new ArrayList<>();
        result.verdicts().forEach((name, verdict) -> {
            if (verdict.role() == role) {
                named.add(name);
            }
        });
        names(sender, role.name(), named);
    }

    /** One group, printed as a count and then the names, which is all anyone reads it for. */
    private void names(CommandSender sender, String title, List<String> named) {
        if (named.isEmpty()) {
            return;
        }
        sender.sendMessage(PREFIX + "§b" + title + " §7(" + named.size() + ")");
        // Wrapped by hand: a single line of 121 names is unreadable in chat and truncated in console.
        StringBuilder line = new StringBuilder("§7 ");
        for (String name : named) {
            if (line.length() > 120) {
                sender.sendMessage(line.toString());
                line = new StringBuilder("§7 ");
            }
            line.append("§f").append(name).append("§8, ");
        }
        if (line.length() > 3) {
            sender.sendMessage(line.substring(0, line.length() - 4));
        }
    }

    /**
     * Which discovered mobs were given which biomes, from their names.
     *
     * <p>The first thing to look at when a mob turns up somewhere odd, or never turns up: every
     * placement says why, and a wrong one is corrected with one line under
     * {@code auto-discover.biomes.overrides}.</p>
     */
    private void biomes(CommandSender sender, MobRoster roster) {
        com.arkcronist.gen.bukkit.mythic.BiomeThemes.Result placed = roster.placement();
        if (placed.biomesRead() == 0) {
            sender.sendMessage(PREFIX + "§eNo biomes were read. §7" + roster.state()
                    + (plugin.arkConfig().autoDiscoverBiomes() ? ""
                            : "; hostile-mobs.auto-discover.biomes.enabled is false"));
            return;
        }
        sender.sendMessage(PREFIX + "Biomes read: §f" + placed.biomesRead() + "§7. Mobs with"
                + " biomes of their own (§f" + (int) Math.round(
                        plugin.arkConfig().autoDiscoverBiomeShare() * 100)
                + "%§7 of the spawns there):");
        java.util.List<String> anywhere = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, com.arkcronist.gen.bukkit.mythic.BiomeThemes.Placement>
                entry : new java.util.TreeMap<>(placed.byMob()).entrySet()) {
            java.util.List<String> keys = entry.getValue().biomes();
            if (keys.isEmpty()) {
                anywhere.add(entry.getKey());
                continue;
            }
            String shown = keys.size() <= 4 ? String.join(", ", keys)
                    : String.join(", ", keys.subList(0, 4)) + " +" + (keys.size() - 4);
            sender.sendMessage("§8 - §f" + entry.getKey() + "§7: §b" + shown
                    + " §8(" + entry.getValue().why() + ")");
        }
        sender.sendMessage("§7 Anywhere in their dimension (§f" + anywhere.size() + "§7): §f"
                + String.join("§7, §f", anywhere));
        sender.sendMessage("§7 To correct one: §fhostile-mobs.auto-discover.biomes.overrides."
                + "<mob>: [snowy_plains, terralith:alpine_grove]§7, or §f[ANY]§7 for everywhere.");
    }

    private void one(CommandSender sender, MobDiscovery.Result result, String name) {
        for (java.util.Map.Entry<String, MobClassifier.Verdict> entry : result.verdicts().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                MobClassifier.Verdict verdict = entry.getValue();
                sender.sendMessage(PREFIX + "§f" + entry.getKey() + " §7is §b" + verdict.role()
                        + "§7 in §b" + verdict.habitat());
                sender.sendMessage("§7 because " + verdict.because());
                com.arkcronist.gen.bukkit.mythic.BiomeThemes.Placement placed =
                        plugin.roster().placement().byMob().get(entry.getKey());
                if (placed != null) {
                    sender.sendMessage(placed.biomes().isEmpty()
                            ? "§7 Biomes: §fanywhere in its dimension §8(" + placed.why() + ")"
                            : "§7 Biomes: §f" + placed.biomes().size() + "§7 - §b"
                                    + String.join("§7, §b", placed.biomes())
                                    + " §8(" + placed.why() + ")");
                }
                sender.sendMessage("§7 To change it: §fhostile-mobs.auto-discover.roles."
                        + entry.getKey() + ": HOSTILE §7or §f.habitats." + entry.getKey()
                        + ": NETHER");
                return;
            }
        }
        sender.sendMessage(PREFIX + "§eNo mob called '" + name + "' was read from MythicMobs.");
        sender.sendMessage("§7 " + plugin.roster().state() + ". §f/ag mythic list§7 shows what was.");
    }

    /**
     * Writes everything discovery found into a file the operator can paste into config.yml.
     *
     * <p>The point is bosses, which discovery deliberately never spawns by itself: nothing in a pack
     * says which structure a boss belongs to, so the plugin cannot place them and guessing would put
     * a thousand-health mob behind a tree. Writing the names out with their habitat turns a decision
     * nobody can automate into one line of copying.</p>
     */
    private void export(CommandSender sender, MobDiscovery.Result result) {
        if (result.total() == 0) {
            sender.sendMessage(PREFIX + "§eNothing to write: " + plugin.roster().state() + ".");
            return;
        }
        StringBuilder out = new StringBuilder();
        out.append("# Written by /ag mythic export. Nothing reads this file - it is here to be\n")
                .append("# copied into config.yml, or kept as a record of what a pack contained.\n")
                .append("# ").append(result.total()).append(" mobs read from MythicMobs.\n\n")
                .append("hostile-mobs:\n");
        out.append("  ambient:\n");
        yamlList(out, "    overworld", result.hostilesFor(MobClassifier.Habitat.OVERWORLD));
        yamlList(out, "    nether", result.hostilesFor(MobClassifier.Habitat.NETHER));
        yamlList(out, "    end", result.hostilesFor(MobClassifier.Habitat.END));
        out.append("  table:  # overworld, keyed by the vanilla mob each one was built on\n");
        result.swapFor(MobClassifier.Habitat.OVERWORLD)
                .forEach((body, mobs) -> yamlList(out, "    " + body, mobs));
        out.append("  nether:\n    table:\n");
        result.swapFor(MobClassifier.Habitat.NETHER)
                .forEach((body, mobs) -> yamlList(out, "      " + body, mobs));
        out.append("  end:\n    table:\n");
        result.swapFor(MobClassifier.Habitat.END)
                .forEach((body, mobs) -> yamlList(out, "      " + body, mobs));
        out.append("\n# Bosses are never spawned by discovery - put them under boss-table, keyed by\n")
                .append("# the name of the structure boss they should replace.\n");
        for (MobClassifier.Habitat habitat : MobClassifier.Habitat.values()) {
            for (String boss : result.bossesFor(habitat)) {
                out.append("#   ").append(boss).append("  (").append(habitat).append(")\n");
            }
        }
        out.append("\n# Set aside and never spawned:\n");
        out.append("#   props: ").append(String.join(", ", result.props())).append('\n');
        out.append("#   pets: ").append(String.join(", ", result.pets())).append('\n');
        out.append("#   undecided: ").append(String.join(", ", result.unknown())).append('\n');

        java.nio.file.Path file = plugin.getDataFolder().toPath().resolve("discovered-mobs.yml");
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, out.toString());
        } catch (java.io.IOException failure) {
            sender.sendMessage(PREFIX + "§cCould not write " + file + ": " + failure.getMessage());
            return;
        }
        sender.sendMessage(PREFIX + "Wrote §f" + result.total() + "§7 mobs to §f"
                + file.getFileName() + " §7in the plugin folder.");
    }

    /** One YAML list, indented two spaces deeper than the key it belongs to. */
    private static void yamlList(StringBuilder out, String key, List<String> names) {
        if (names.isEmpty()) {
            return;
        }
        String indent = " ".repeat(key.length() - key.stripLeading().length() + 2);
        out.append(key).append(":\n");
        for (String name : names) {
            out.append(indent).append("- \"").append(name).append("\"\n");
        }
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
        if (player.getWorld().getGenerator() instanceof com.arkcronist.gen.bukkit.ArkChunkGenerator gen
                && gen.datapackTerrain()) {
            // Everything below comes from this generator's engine, and in datapack mode the engine
            // built none of what the player is standing on. Printing it without saying so offers a
            // biome and a surface height for a world that does not exist.
            sender.sendMessage("§e This world's terrain is the game's and its datapacks', not ours."
                    + " The figures below are what this generator WOULD have built here, and are"
                    + " not what you are standing on.");
        }
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
                    "presets", "stats", "mobs", "mythic", "bench", "top", "reload", "version"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("mythic")) {
            options.addAll(List.of("list", "refresh", "export", "models", "biomes"));
            for (MobClassifier.Role role : MobClassifier.Role.values()) {
                options.add(role.name().toLowerCase(Locale.ROOT));
            }
            for (MobClassifier.Habitat habitat : MobClassifier.Habitat.values()) {
                options.add(habitat.name().toLowerCase(Locale.ROOT));
            }
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
