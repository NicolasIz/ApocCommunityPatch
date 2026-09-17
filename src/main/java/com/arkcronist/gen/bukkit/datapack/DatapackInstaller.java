package com.arkcronist.gen.bukkit.datapack;

import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.packs.DataPack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Puts datapacks where the server will actually read them, and says plainly when it cannot.
 *
 * <h2>Why the plugin does this at all</h2>
 *
 * <p>A datapack that replaces a dimension - Incendium for the Nether, Nullscape for the End - is the
 * only way to give this generator's companion worlds terrain it does not generate itself. Both
 * replace {@code minecraft:the_nether} and {@code minecraft:the_end} outright, so once the server
 * has them, <em>every</em> Nether and End it creates uses them, including the ones this plugin makes
 * a moment after an overworld. Nothing has to be wired per world; the dimension is simply different.
 *
 * <h2>The part that cannot be worked around</h2>
 *
 * <p>World generation registries are built from the datapack folder before any plugin is loaded, and
 * they are frozen once the first world opens. A plugin cannot add a worldgen datapack to the server
 * it is already running on - not through this class, not through {@code /datapack enable}, not at
 * all. So this copies the files into place and says, once, that the server has to be restarted for
 * them to take effect. It is one restart, the first time.</p>
 *
 * <h2>What it will not do</h2>
 *
 * <p>No datapack is shipped inside the plugin. These are other people's work under their own terms -
 * Stardust Labs, for one, permits any server to run Incendium and Nullscape and forbids
 * redistributing them - so the admin supplies the files and this moves them. The folder it reads
 * from is created on first start with a note in it saying so.</p>
 */
public final class DatapackInstaller {

    /** Where an admin drops the zips. */
    public static final String SOURCE_FOLDER = "datapacks";

    /** Stands for every pack, whatever version it declares. */
    public static final String EVERYTHING = "*";

    /**
     * Whether this pack was asked to be forced onto the server's version.
     *
     * <p>Matched without regard to case, with or without the {@code .zip}, and ignoring a browser's
     * {@code " (1)"} on a second download. File names are the wrong thing to ask an admin to get
     * exactly right: a pack that does not match its entry is simply not forced, which looks
     * identical to the feature not working at all - and that is precisely what happened on a real
     * server, with "Reds_Structure_v1.1.0 (1).zip" sitting next to an entry that said
     * "Reds_Structure_v1.1.0.zip".</p>
     */
    public static boolean shouldRetarget(java.util.Set<String> retarget, String file) {
        if (retarget.contains(EVERYTHING)) {
            return true;
        }
        String bare = simplify(file);
        for (String entry : retarget) {
            if (simplify(entry).equals(bare)) {
                return true;
            }
        }
        return false;
    }

    /** A file name reduced to what an admin would call the pack. */
    private static String simplify(String name) {
        String simple = name.trim().toLowerCase(Locale.ROOT);
        if (simple.endsWith(".zip")) {
            simple = simple.substring(0, simple.length() - 4);
        }
        int bracket = simple.lastIndexOf(" (");
        if (bracket > 0 && simple.endsWith(")")) {
            String inside = simple.substring(bracket + 2, simple.length() - 1);
            if (!inside.isEmpty() && inside.chars().allMatch(Character::isDigit)) {
                simple = simple.substring(0, bracket);
            }
        }
        return simple.trim();
    }

    private DatapackInstaller() {
    }

    /** What happened to one pack. */
    public record Result(String file, PackMeta meta, Outcome outcome) {
    }

    public enum Outcome {
        /** Copied into the world folder; takes effect after a restart. */
        INSTALLED,
        /** Already in the world folder, left alone. */
        PRESENT,
        /** Built for a different game version; not copied. */
        INCOMPATIBLE,
        /** Built for a different game version, and installed anyway because config said to. */
        RETARGETED,
        /** Not a datapack, or its pack.mcmeta could not be read. */
        UNREADABLE,
        /** The copy itself failed. */
        FAILED
    }

    /**
     * Installs every pack in {@code source} into {@code target}, and reports on each.
     *
     * @param serverFormat the data pack format this server loads, from {@link #serverFormat}
     * @param log          where human-readable progress goes
     */
    public static List<Result> install(Path source, Path target, int serverFormat, Consumer<String> log) {
        return install(source, target, serverFormat, java.util.Set.of(), log);
    }

    /**
     * As above, but {@code retarget} names packs to install even though their declared version does
     * not match, rewriting the copy's {@code pack.mcmeta} so the server will load it.
     *
     * <p>Only the copy is touched; the file the admin put in the plugin folder is never modified.
     * Whether this produces a working pack or a building full of holes depends entirely on what the
     * pack is made of, which is what {@link PackBlocks} is for.</p>
     */
    public static List<Result> install(Path source, Path target, int serverFormat,
                                       java.util.Set<String> retarget, Consumer<String> log) {
        List<Result> results = new ArrayList<>();
        if (!Files.isDirectory(source)) {
            return results;
        }

        List<Path> zips = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(source, "*.zip")) {
            entries.forEach(zips::add);
        } catch (IOException exception) {
            log.accept("Could not read " + source + ": " + exception.getMessage());
            return results;
        }
        zips.sort(java.util.Comparator.comparing(path -> path.getFileName().toString()
                .toLowerCase(Locale.ROOT)));

        for (Path zip : zips) {
            results.add(installOne(zip, target, serverFormat, retarget, log));
        }
        return results;
    }

    private static Result installOne(Path zip, Path target, int serverFormat,
                                     java.util.Set<String> retarget, Consumer<String> log) {
        String name = zip.getFileName().toString();

        PackMeta meta = readMeta(zip);
        if (meta == null) {
            log.accept("'" + name + "' has no readable pack.mcmeta, so it is not a datapack. Skipped.");
            return new Result(name, null, Outcome.UNREADABLE);
        }

        PackMeta.Verdict verdict = meta.verdictFor(serverFormat);
        boolean forced = serverFormat > 0 && verdict != PackMeta.Verdict.COMPATIBLE
                && shouldRetarget(retarget, name);
        if (serverFormat > 0 && verdict != PackMeta.Verdict.COMPATIBLE && !forced) {
            // Refusing to copy is the point. A pack the server will not load still appears in the
            // world folder and in /datapack list, which is exactly how an evening gets spent looking
            // for a structure that was never going to generate.
            log.accept("'" + name + "' is built for data pack format " + meta.range()
                    + " and this server is format " + serverFormat + " ("
                    + (verdict == PackMeta.Verdict.TOO_NEW ? "newer game needed" : "older game")
                    + "). Not installed - it would sit there without loading.");
            return new Result(name, meta, Outcome.INCOMPATIBLE);
        }

        Path destination = target.resolve(name);
        if (Files.exists(destination)) {
            return new Result(name, meta, Outcome.PRESENT);
        }

        try {
            Files.createDirectories(target);
            if (forced) {
                copyWithFormat(zip, destination, serverFormat);
            } else {
                Files.copy(zip, destination, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException exception) {
            log.accept("Could not copy '" + name + "' into " + target + ": " + exception.getMessage());
            return new Result(name, meta, Outcome.FAILED);
        }
        if (forced) {
            log.accept("Installed '" + name + "' with the version it declares rewritten from "
                    + meta.range() + " to " + serverFormat + ", because the config asked for it."
                    + " Your own copy is untouched. The server will load it now; whether it works"
                    + " is a different question, and the block check answers that one.");
            return new Result(name, meta, Outcome.RETARGETED);
        }
        log.accept("Installed '" + name + "' (format " + meta.range() + ").");
        return new Result(name, meta, Outcome.INSTALLED);
    }

    /**
     * The datapack files that override the same vanilla definitions as each other.
     *
     * <p>Two packs that both rewrite {@code data/minecraft/worldgen/structure/village_plains.json}
     * do not merge and do not error: whichever the server reads last wins, and which that is depends
     * on load order. The pack of the two an admin thought they were getting may be the one that
     * loses, silently and only in the half of the game the other pack also touched. It is worth one
     * line in the log.</p>
     *
     * <p>Only {@code data/minecraft/} counts. Two packs with their own namespaces are adding their
     * own things and cannot collide; overriding the game's own files is the only way to overwrite
     * somebody else.</p>

     * <p>Tags are the exception and are not counted. A tag file from a datapack is <em>merged</em>
     * into the game's own list rather than replacing it, so two packs both adding to
     * {@code has_structure/village_plains} is how tags are meant to be used, not a conflict.
     * Counting them turns every pair of packs that touch the same part of the game into a warning -
     * Incendium and Nullscape, written by the same people to be run together, share one stone tag -
     * and a warning that cries wolf is worse than no warning.</p>
     *
     * @return one line per colliding pair, empty when there are none
     */
    public static List<String> collisions(Path folder, List<Result> installed) {
        List<String> warnings = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<java.util.Set<String>> overrides = new ArrayList<>();

        for (Result result : installed) {
            if (result.outcome() != Outcome.INSTALLED && result.outcome() != Outcome.PRESENT) {
                continue;
            }
            java.util.Set<String> vanillaFiles = vanillaOverrides(folder.resolve(result.file()));
            if (vanillaFiles.isEmpty()) {
                continue;
            }
            names.add(result.file());
            overrides.add(vanillaFiles);
        }

        for (int a = 0; a < names.size(); a++) {
            for (int b = a + 1; b < names.size(); b++) {
                java.util.Set<String> shared = new java.util.TreeSet<>(overrides.get(a));
                shared.retainAll(overrides.get(b));
                if (shared.isEmpty()) {
                    continue;
                }
                String example = shared.iterator().next();
                warnings.add("'" + names.get(a) + "' and '" + names.get(b) + "' both replace "
                        + shared.size() + " of the game's own files (" + example
                        + (shared.size() > 1 ? ", ..." : "")
                        + "). Only one of them wins, and which is not defined. Keep one.");
            }
        }
        return warnings;
    }

    /** Every {@code data/minecraft/...} file a pack replaces. */
    private static java.util.Set<String> vanillaOverrides(Path zip) {
        java.util.Set<String> found = new java.util.HashSet<>();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.startsWith("data/minecraft/") && name.endsWith(".json")
                        && !name.startsWith("data/minecraft/tags/")) {
                    found.add(name);
                }
            }
        } catch (IOException | IllegalStateException exception) {
            return java.util.Set.of();
        }
        return found;
    }

    /**
     * What a retargeted pack's pack.mcmeta is replaced with: the server's own format, and a
     * description that says who changed it, so nobody later mistakes it for the original.
     */
    private static final String RETARGETED_META =
            "{\n"
            + "  \"pack\": {\n"
            + "    \"description\": \"Retargeted by ArkcronistGenerator\",\n"
            + "    \"pack_format\": %1$d,\n"
            + "    \"min_format\": %1$d,\n"
            + "    \"max_format\": %1$d\n"
            + "  }\n"
            + "}\n";

    /**
     * Copies a datapack, replacing the version its {@code pack.mcmeta} declares.
     *
     * <p>Every other entry is copied through byte for byte. The whole change is the one number the
     * server reads to decide whether to load the pack at all.</p>
     */
    private static void copyWithFormat(Path from, Path to, int format) throws IOException {
        try (ZipFile in = new ZipFile(from.toFile());
             java.util.zip.ZipOutputStream out =
                     new java.util.zip.ZipOutputStream(Files.newOutputStream(to))) {
            Enumeration<? extends ZipEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                out.putNextEntry(new ZipEntry(entry.getName()));
                if (!entry.isDirectory()) {
                    if ("pack.mcmeta".equals(entry.getName())) {
                        out.write(RETARGETED_META.formatted(format)
                                .getBytes(StandardCharsets.UTF_8));
                    } else {
                        try (InputStream source = in.getInputStream(entry)) {
                            source.transferTo(out);
                        }
                    }
                }
                out.closeEntry();
            }
        }
    }

    /**
     * The dimensions a datapack adds of its own, by name.
     *
     * <p>Worth saying out loud at install time, because a pack that adds one cannot simply be
     * deleted again. The first time the server loads it, the world writes that dimension into its
     * own {@code level.dat}; take the zip away afterwards and the world still names a dimension type
     * that no longer exists, so the server refuses to open it and stops with "Failed to load
     * datapacks, can't proceed with server load". The world is not damaged and nothing is lost - but
     * the only ways out are to put the pack back or to edit {@code level.dat} by hand, and neither
     * is something to discover on a live server at the moment it will not start.</p>
     */
    public static List<String> dimensions(Path zip) {
        java.util.SortedSet<String> found = new java.util.TreeSet<>();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".json")) {
                    continue;
                }
                // data/<namespace>/dimension/<name>.json
                String[] parts = name.split("/");
                if (parts.length == 4 && parts[0].equals("data") && parts[2].equals("dimension")
                        && !parts[1].equals("minecraft")) {
                    String id = parts[3].substring(0, parts[3].length() - 5);
                    // The game ignores a file whose name is not a valid id - one pack ships
                    // "final_destination - old2.json" beside the real one - and listing those as
                    // dimensions would name dimensions that do not exist.
                    if (validId(parts[1]) && validId(id)) {
                        found.add(parts[1] + ":" + id);
                    }
                }
            }
        } catch (IOException | IllegalStateException | SecurityException exception) {
            return List.of();
        }
        return List.copyOf(found);
    }

    /** Whether this is a name the game will accept in a resource location. */
    private static boolean validId(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == '/';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /** A datapack's files that the game will refuse to read. */
    public record Broken(List<String> vanilla, List<String> own) {

        public int total() {
            return vanilla.size() + own.size();
        }
    }

    /**
     * The files in a datapack that are not valid JSON.
     *
     * <p>Split by whose they are, because the two are not the same kind of problem. A pack's own
     * broken file costs that pack a feature. A broken file under {@code data/minecraft/} costs the
     * <em>game</em> a feature: the pack has declared it is replacing something vanilla, the
     * replacement does not parse, and what was there before is gone. One pack in the wild shipped
     * four netherite armour recipes whose result was an empty id, and the whole server lost the
     * ability to craft netherite armour - in every world, including ones the pack had nothing to do
     * with. The only sign was a stack trace among two hundred startup lines.</p>
     *
     * <p>This catches the malformed half of that. An empty id is valid JSON and is not caught here;
     * the game reports those itself, and the warning below tells the admin where to look.</p>
     */
    public static Broken brokenFiles(Path zip) {
        List<String> vanilla = new ArrayList<>();
        List<String> own = new ArrayList<>();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".json")) {
                    continue;
                }
                String text;
                try (InputStream in = file.getInputStream(entry)) {
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException exception) {
                    continue;
                }
                if (!JsonShape.wellFormed(text)) {
                    if (entry.getName().startsWith("data/minecraft/")) {
                        vanilla.add(entry.getName());
                    } else {
                        own.add(entry.getName());
                    }
                }
            }
        } catch (IOException | IllegalStateException | SecurityException exception) {
            return new Broken(List.of(), List.of());
        }
        return new Broken(vanilla, own);
    }

    /** The declared version range of a datapack zip, or null when it is not one. */
    public static PackMeta readMeta(Path zip) {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            ZipEntry entry = file.getEntry("pack.mcmeta");
            if (entry == null) {
                return null;
            }
            try (InputStream in = file.getInputStream(entry)) {
                return PackMeta.read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException | IllegalStateException exception) {
            return null;
        }
    }

    /**
     * The data pack format this server loads.
     *
     * <p>Taken from the game's own built-in pack rather than a table of version numbers kept in this
     * plugin, so it stays right on a server this build has never seen.</p>
     *
     * @return the format, or 0 when the server will not say
     */
    public static int serverFormat(Server server) {
        try {
            DataPack vanilla = server.getDataPackManager().getDataPack(NamespacedKey.minecraft("vanilla"));
            if (vanilla != null) {
                return vanilla.getPackFormat();
            }
            for (DataPack pack : server.getDataPackManager().getDataPacks()) {
                if (pack.getSource() == DataPack.Source.DEFAULT
                        || pack.getSource() == DataPack.Source.BUILT_IN) {
                    return pack.getPackFormat();
                }
            }
        } catch (RuntimeException | LinkageError exception) {
            // An older or unusual server that does not expose its packs. Reported by the caller as
            // "cannot check", which is honest, rather than guessed at.
            return 0;
        }
        return 0;
    }

    /**
     * Whether the server has any datapack of its own loaded at all.
     *
     * <p>Asked when a world is created that wants its terrain from the datapacks: if the answer is
     * no, that world is about to be generated as plain vanilla and the person who asked for it will
     * not find out until they walk around in it. The mistake behind that is almost always the same
     * one - the zip was put in the new world's own datapacks folder, which the game never reads, or
     * the server has not been restarted since the pack was copied into place.</p>
     *
     * <p>Only the game's own packs are discounted. Anything from a folder or a zip counts, because
     * working out whether a particular pack replaces the overworld means reading its contents, and
     * an admin who has installed nothing at all is the case worth catching.</p>
     */
    public static boolean anyInstalled(Server server) {
        try {
            for (DataPack pack : server.getDataPackManager().getDataPacks()) {
                if (pack.getSource() != DataPack.Source.DEFAULT
                        && pack.getSource() != DataPack.Source.BUILT_IN
                        && pack.getSource() != DataPack.Source.FEATURE
                        && pack.isEnabled()) {
                    return true;
                }
            }
        } catch (RuntimeException | LinkageError exception) {
            // A server that will not say. Better to keep quiet than to warn about nothing.
            return true;
        }
        return false;
    }

    /** The note left in the source folder so an admin finds out what it is for. */
    public static final String README = """
            Datapacks dropped in this folder are copied into the server's world folder when the
            plugin starts, so they load with the game's own world generation.

            What belongs here
            -----------------
            Any datapack zip. The ones this was built for:

              Incendium   - replaces the Nether. Every Nether the server creates uses it,
                            including the one this plugin makes for each generated world.
              Nullscape   - replaces the End, the same way.
              Structure packs - anything that adds or overrides overworld structures. This
                            generator lets the server place its own structures, so datapack
                            structures generate alongside the ones it builds itself.

            Nothing is downloaded for you and nothing is bundled with the plugin: these are other
            people's work under their own licences, and several of them allow you to run the pack
            on any server while forbidding anyone to redistribute it. Get the zip from its author
            and put it here.

            Two things worth knowing
            ------------------------
            1. A datapack only takes effect after a server restart. World generation is built
               before plugins load, so the first start after you add a pack copies it into place
               and the second start is when it does anything. The log says so each time.

            2. A pack built for a different game version is not copied at all, and the log says
               which version it wanted. The server would have listed it and quietly refused to
               load it, which is far harder to notice.
            """;
}
