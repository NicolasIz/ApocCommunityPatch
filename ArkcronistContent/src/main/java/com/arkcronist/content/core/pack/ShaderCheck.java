package com.arkcronist.content.core.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shaders in the built pack checked against what the 1.21.8 client actually does with them - the
 * shaders a content pack brings ({@code contents/<pack>/shaders/}) and those of every merged pack,
 * which for ItemsAdder packs are often written for a client long gone.
 *
 * <p>The facts come from the 1.21.8 client itself ({@code vanilla-shaders-1.21.8.json}): every
 * shader file it ships, the uniform blocks its includes and shaders declare and their members, and
 * which blocks each core shader's render pipelines hand it. Three kinds of finding:</p>
 *
 * <ul>
 *   <li>{@link Severity#BROKEN}: will not compile or link, or draws wrong - a loose
 *       {@code uniform mat4 ModelViewMat;} of a shader written for 1.21.4 or older (since 1.21.5
 *       only uniform blocks and samplers are set), an import nothing provides, no {@code #version}
 *       first;</li>
 *   <li>{@link Severity#UNUSED}: a core shader the 1.21.8 client never loads - renamed or merged
 *       away ({@code rendertype_translucent} and the rest went into {@code terrain} in 1.21.5), or
 *       outside the {@code minecraft} namespace;</li>
 *   <li>{@link Severity#INERT}: reads a uniform block nothing ever fills for it - neither one its
 *       pipelines declare nor one of the four the client binds for any shader that asks
 *       ({@code Projection}, {@code Fog}, {@code Globals}, {@code Lighting}: so {@code GameTime} does
 *       reach {@code terrain}, which draws every block, this plugin's liquids included). The client
 *       logs "Found unknown and unsupported uniform" and leaves it unbound.</li>
 * </ul>
 *
 * <p>Pure: the compiler hands the files over and does what {@link Mode} says with the report.</p>
 */
public final class ShaderCheck {

    /** What {@code pack.shaders} does with the shaders in the built pack. */
    public enum Mode {
        /** Keep every shader; report what is wrong. */
        KEEP,
        /** Leave out each broken shader - with its other half, so no pair is left mismatched. */
        DROP_BROKEN,
        /** Leave out every shader: a pack drawn by the vanilla client's own. */
        DROP
    }

    public enum Severity {
        BROKEN, UNUSED, INERT
    }

    public record Finding(String path, Severity severity, String message) {
    }

    /**
     * @param drop    pack-relative paths to leave out of the built pack
     * @param checked how many files a {@link #clientFormat()} client loads were read
     * @param skipped how many were not: in overlays for other versions, or under one that replaces them
     */
    public record Report(List<Finding> findings, Set<String> drop, int checked, int skipped) {
    }

    /** An overlay of the pack, in pack.mcmeta's order, and the pack formats it is for. */
    public record Overlay(String directory, int min, int max) {

        boolean appliesTo(int format) {
            return min <= format && format <= max;
        }

        /** An overlay entry of pack.mcmeta: {@code formats} as a number, [min, max] or {min_inclusive, max_inclusive}. */
        public static Overlay of(JsonObject entry) {
            String directory = entry.get("directory").getAsString();
            int min = entry.has("min_format") && entry.get("min_format").isJsonPrimitive() ? entry.get("min_format").getAsInt()
                    : Integer.MIN_VALUE;
            int max = entry.has("max_format") && entry.get("max_format").isJsonPrimitive() ? entry.get("max_format").getAsInt()
                    : Integer.MAX_VALUE;
            JsonElement formats = entry.get("formats");
            if (formats != null && formats.isJsonPrimitive()) {
                min = max = formats.getAsInt();
            } else if (formats != null && formats.isJsonArray() && formats.getAsJsonArray().size() == 2) {
                min = formats.getAsJsonArray().get(0).getAsInt();
                max = formats.getAsJsonArray().get(1).getAsInt();
            } else if (formats != null && formats.isJsonObject()) {
                JsonObject range = formats.getAsJsonObject();
                min = range.has("min_inclusive") ? range.get("min_inclusive").getAsInt() : min;
                max = range.has("max_inclusive") ? range.get("max_inclusive").getAsInt() : max;
            }
            return new Overlay(directory, min, max);
        }
    }

    private static final Pattern IMPORT = Pattern.compile("#moj_import\\s*[<\"]([^>\"]+)[>\"]");
    private static final Pattern BLOCK = Pattern.compile("uniform\\s+(\\w+)\\s*\\{[^}]*}\\s*;?", Pattern.DOTALL);
    private static final Pattern LOOSE = Pattern.compile("(?m)^\\s*uniform\\s+(?!sampler|isampler|usampler)(\\w+)\\s+(\\w+)\\s*(?:\\[[^\\]]*\\])?\\s*;");
    private static final Pattern COMMENTS = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    private static final Facts FACTS = Facts.load();

    /** The 1.21.8 client's shaders, as read from it. */
    record Facts(String version, int format, Set<String> files, Map<String, List<String>> blocks,
                 Map<String, List<String>> includes, Map<String, List<String>> bound, Set<String> builtin) {

        static Facts load() {
            try (InputStream in = ShaderCheck.class.getResourceAsStream("/vanilla-shaders-1.21.8.json")) {
                if (in == null) {
                    throw new IllegalStateException("vanilla-shaders-1.21.8.json is missing from the plugin jar");
                }
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                Set<String> files = new LinkedHashSet<>();
                root.getAsJsonArray("files").forEach(file -> files.add(file.getAsString()));
                return new Facts(root.get("version").getAsString(), root.get("pack_format").getAsInt(), files,
                        lists(root.getAsJsonObject("blocks")),
                        lists(root.getAsJsonObject("includes")), lists(root.getAsJsonObject("bound")),
                        Set.copyOf(root.getAsJsonArray("builtin").asList().stream().map(JsonElement::getAsString).toList()));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private static Map<String, List<String>> lists(JsonObject object) {
            Map<String, List<String>> map = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                List<String> values = new ArrayList<>();
                for (JsonElement value : entry.getValue().getAsJsonArray()) {
                    values.add(value.getAsString());
                }
                map.put(entry.getKey(), List.copyOf(values));
            }
            return map;
        }
    }

    private ShaderCheck() {
    }

    /** The Minecraft version the facts were read from. */
    public static String clientVersion() {
        return FACTS.version();
    }

    /** That client's resource pack format: which overlays it loads. */
    public static int clientFormat() {
        return FACTS.format();
    }

    /** Whether a pack-relative path is a shader file: {@code [<overlay>/]assets/<ns>/shaders/...}. */
    public static boolean isShader(String path) {
        return path.contains("/shaders/") && (path.startsWith("assets/") || path.indexOf("/assets/") > 0);
    }

    /**
     * Checks the shader files of a built pack.
     *
     * @param shaders pack-relative path to text, every file under {@code assets/<ns>/shaders/} of the
     *                pack and of its overlays
     */
    public static Report check(Map<String, String> shaders, Mode mode) {
        return check(shaders, mode, List.of());
    }

    /**
     * Checks the shader files a {@link #clientFormat()} client loads from a built pack: those at its
     * root, replaced by those of each overlay meant for that format, in pack.mcmeta's order. An
     * overlay for other versions, and a file an overlay replaces, are not this client's and not read.
     * Leaving a broken file out lets the one under it show through, so that one is read in turn.
     *
     * @param overlays the pack's overlays, in pack.mcmeta's order
     */
    public static Report check(Map<String, String> shaders, Mode mode, List<Overlay> overlays) {
        Map<String, String> remaining = new TreeMap<>(shaders);
        List<Finding> findings = new ArrayList<>();
        Set<String> drop = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int round = 0; round < 8; round++) {
            Map<String, String> loaded = loaded(remaining, overlays);
            List<Finding> found = new ArrayList<>();
            Set<String> broken = new LinkedHashSet<>();
            readAll(loaded, found, broken);
            for (Finding finding : found) {
                if (seen.add(finding.path() + "|" + finding.message())) {
                    findings.add(finding);
                }
            }
            if (mode != Mode.DROP_BROKEN || broken.isEmpty()) {
                break;
            }
            for (String path : broken) {
                drop.add(path);
                drop.add(otherHalf(path));
                remaining.remove(path);
                remaining.remove(otherHalf(path));
            }
        }
        if (mode == Mode.DROP) {
            drop.addAll(shaders.keySet());
        }
        drop.retainAll(shaders.keySet());
        int checked = loaded(new TreeMap<>(shaders), overlays).size();
        return new Report(List.copyOf(findings), Set.copyOf(drop), checked, shaders.size() - checked);
    }

    /**
     * What the client loads, by actual path: per path inside a layer, the file of the last layer
     * meant for its format that has it - the root first, then its overlays in order.
     */
    private static Map<String, String> loaded(Map<String, String> files, List<Overlay> overlays) {
        Map<String, String> active = new java.util.LinkedHashMap<>();
        active.put("", "");
        for (Overlay overlay : overlays) {
            if (overlay.appliesTo(FACTS.format())) {
                active.put(overlay.directory() + "/", overlay.directory() + "/");
            }
        }
        Set<String> known = new java.util.HashSet<>();
        overlays.forEach(overlay -> known.add(overlay.directory() + "/"));
        Map<String, String> byLogical = new TreeMap<>();
        for (String layer : active.keySet()) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                String path = file.getKey();
                String logical;
                if (layer.isEmpty()) {
                    int slash = path.indexOf('/');
                    if (slash > 0 && known.contains(path.substring(0, slash + 1))) {
                        continue;
                    }
                    logical = path;
                } else if (path.startsWith(layer)) {
                    logical = path.substring(layer.length());
                } else {
                    continue;
                }
                byLogical.put(logical, path);
            }
        }
        Map<String, String> loaded = new TreeMap<>();
        for (String path : byLogical.values()) {
            loaded.put(path, files.get(path));
        }
        return loaded;
    }

    private static void readAll(Map<String, String> shaders, List<Finding> findings, Set<String> brokenPaths) {
        for (Map.Entry<String, String> entry : shaders.entrySet()) {
            String path = entry.getKey();
            Location where = Location.of(path);
            if (where == null) {
                continue;
            }
            String text = entry.getValue();
            String rel = where.relative();
            boolean program = rel.endsWith(".vsh") || rel.endsWith(".fsh");
            if (rel.startsWith("core/")) {
                if (!where.namespace().equals("minecraft")) {
                    findings.add(new Finding(path, Severity.UNUSED, "core shaders are only read from the minecraft"
                            + " namespace; this one is never loaded"));
                } else if (program && !FACTS.files().contains(rel)) {
                    findings.add(new Finding(path, Severity.UNUSED, "the " + FACTS.version() + " client has no core"
                            + " shader by this name, so it is never loaded (renamed or merged since it was written:"
                            + " 1.21.5 drew solid, cutout, translucent and tripwire blocks with core/terrain)"));
                }
            }
            if (!program && !rel.endsWith(".glsl")) {
                continue;
            }
            // 3.0.5: only what the preprocessor keeps - BetterHud writes one shader for every client
            // version, the 1.21.4 declarations in the #else of an #if SHADER_VERSION >= 3.
            String code = active(COMMENTS.matcher(text).replaceAll(" "));
            List<String> broken = new ArrayList<>();
            if (program && !firstDirectiveIsVersion(code)) {
                broken.add("its first line is not #version, which the client's compiler needs");
            }
            Set<String> used = new LinkedHashSet<>();
            Matcher imports = IMPORT.matcher(code);
            while (imports.find()) {
                String target = imports.group(1).trim();
                String namespace = "minecraft";
                String file = target;
                int colon = target.indexOf(':');
                if (colon >= 0) {
                    namespace = target.substring(0, colon);
                    file = target.substring(colon + 1);
                }
                boolean vanilla = namespace.equals("minecraft") && FACTS.includes().containsKey(file);
                if (vanilla) {
                    used.addAll(FACTS.includes().get(file));
                } else if (shaders.keySet().stream().noneMatch(other -> other.endsWith(includePath(target)))) {
                    broken.add("it imports <" + target + ">, which neither the client nor any pack here provides");
                }
            }
            Matcher blocks = BLOCK.matcher(code);
            while (blocks.find()) {
                used.add(blocks.group(1));
            }
            String outsideBlocks = BLOCK.matcher(code).replaceAll(" ");
            Matcher loose = LOOSE.matcher(outsideBlocks);
            while (loose.find()) {
                String name = loose.group(2);
                String block = blockOf(name);
                broken.add("it declares 'uniform " + loose.group(1) + " " + name + ";' on its own - since 1.21.5 the"
                        + " client sets only uniform blocks and samplers"
                        + (block != null ? ", and " + name + " is in the " + block + " block (#moj_import <minecraft:"
                        + includeOf(block) + ">)" : "") + "; written for 1.21.4 or older, it draws wrong or fails");
            }
            if (!broken.isEmpty()) {
                findings.add(new Finding(path, Severity.BROKEN, String.join("; ", broken)));
                brokenPaths.add(path);
            }
            if (program && where.namespace().equals("minecraft")) {
                List<String> bound = FACTS.bound().get(rel);
                if (bound != null) {
                    for (String block : used) {
                        if (!bound.contains(block) && !FACTS.builtin().contains(block)) {
                            List<String> members = FACTS.blocks().get(block);
                            findings.add(new Finding(path, Severity.INERT, "it reads the " + block + " block"
                                    + (members == null ? "" : " (" + String.join(", ", members) + ")") + ", which the "
                                    + FACTS.version() + " client never fills for " + rel + ": it is neither one its"
                                    + " pipelines declare nor Projection, Fog, Globals or Lighting, so the client logs"
                                    + " \"Found unknown and unsupported uniform\" and leaves it unbound"));
                        }
                    }
                }
            }
        }
    }

    private static final Pattern DIRECTIVE = Pattern.compile("^\\s*#\\s*(define|undef|ifdef|ifndef|if|elif|else|endif)\\b(.*)$");
    private static final Pattern DEFINE = Pattern.compile("\\s*(\\w+)(?:\\s+(.*))?");

    /**
     * 3.0.5: the code the preprocessor keeps, as far as the shader itself says: {@code #define}s of
     * whole numbers, and {@code #if}, {@code #ifdef}, {@code #ifndef}, {@code #elif}, {@code #else},
     * {@code #endif} on them. A condition on anything else - a macro the client defines for some
     * pipelines, a function-like macro - is not known here: both of its branches are kept, as all of
     * a shader was before. Dropped lines become empty ones.
     */
    static String active(String code) {
        Map<String, Long> macros = new java.util.HashMap<>();
        java.util.Set<String> unknownMacros = new java.util.HashSet<>();
        // Per open #if: 1 this branch is taken, 0 it is not, -1 not known; and whether one was taken already.
        java.util.Deque<int[]> stack = new java.util.ArrayDeque<>();
        StringBuilder out = new StringBuilder(code.length());
        for (String line : code.split("\n", -1)) {
            Matcher directive = DIRECTIVE.matcher(line);
            boolean live = stack.stream().noneMatch(level -> level[0] == 0);
            if (!directive.matches()) {
                out.append(live ? line : "").append('\n');
                continue;
            }
            String kind = directive.group(1);
            String rest = directive.group(2).trim();
            switch (kind) {
                case "define" -> {
                    if (live) {
                        Matcher define = DEFINE.matcher(rest);
                        if (define.matches() && !rest.contains("(")) {
                            Long value = define.group(2) == null ? Long.valueOf(1) : number(define.group(2).trim());
                            if (value != null) {
                                macros.put(define.group(1), value);
                                unknownMacros.remove(define.group(1));
                            } else {
                                macros.remove(define.group(1));
                                unknownMacros.add(define.group(1));
                            }
                        }
                    }
                    out.append(live ? line : "").append('\n');
                }
                case "undef" -> {
                    if (live) {
                        macros.remove(rest);
                        unknownMacros.remove(rest);
                    }
                    out.append('\n');
                }
                case "if", "ifdef", "ifndef" -> {
                    int known = kind.equals("if") ? evaluate(rest, macros, unknownMacros)
                            : defined(rest, macros, unknownMacros, kind.equals("ifndef"));
                    stack.push(new int[]{known, known == 1 ? 1 : 0});
                    out.append('\n');
                }
                case "elif" -> {
                    int[] level = stack.peek();
                    if (level != null) {
                        if (level[1] == 1) {
                            level[0] = 0;
                        } else {
                            int known = evaluate(rest, macros, unknownMacros);
                            level[0] = level[0] == -1 && known != 1 ? -1 : known;
                            if (known == 1) {
                                level[1] = 1;
                            }
                        }
                    }
                    out.append('\n');
                }
                case "else" -> {
                    int[] level = stack.peek();
                    if (level != null) {
                        level[0] = level[0] == -1 ? -1 : level[1] == 1 ? 0 : 1;
                        level[1] = 1;
                    }
                    out.append('\n');
                }
                default -> {
                    if (!stack.isEmpty()) {
                        stack.pop();
                    }
                    out.append('\n');
                }
            }
        }
        return out.toString();
    }

    private static int defined(String name, Map<String, Long> macros, java.util.Set<String> unknownMacros, boolean negate) {
        String macro = name.trim();
        if (macros.containsKey(macro) || unknownMacros.contains(macro)) {
            return negate ? 0 : 1;
        }
        // Not defined by the shader - the client may define it, for some pipelines.
        return -1;
    }

    /** 1 true, 0 false, -1 not known: whole numbers, macros defined here, comparisons, !, &&, ||, (). */
    private static int evaluate(String expression, Map<String, Long> macros, java.util.Set<String> unknownMacros) {
        try {
            Long value = new Expression(expression, macros).parse();
            return value == null ? -1 : value != 0 ? 1 : 0;
        } catch (RuntimeException unknown) {
            return -1;
        }
    }

    private static Long number(String text) {
        try {
            return Long.decode(text.replaceAll("[uUlL]+$", ""));
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** A recursive-descent reading of a #if expression; null for anything not known here. */
    private static final class Expression {
        private final String text;
        private final Map<String, Long> macros;
        private int at;

        Expression(String text, Map<String, Long> macros) {
            this.text = text;
            this.macros = macros;
        }

        Long parse() {
            Long value = or();
            skip();
            return at == text.length() ? value : null;
        }

        private Long or() {
            Long left = and();
            while (take("||")) {
                Long right = and();
                left = left == null || right == null ? (left != null && left != 0 || right != null && right != 0 ? 1L : null)
                        : (left != 0 || right != 0 ? 1L : 0L);
            }
            return left;
        }

        private Long and() {
            Long left = comparison();
            while (take("&&")) {
                Long right = comparison();
                left = left == null || right == null ? (left != null && left == 0 || right != null && right == 0 ? 0L : null)
                        : (left != 0 && right != 0 ? 1L : 0L);
            }
            return left;
        }

        private Long comparison() {
            Long left = unary();
            for (String op : new String[]{">=", "<=", "==", "!=", ">", "<"}) {
                if (take(op)) {
                    Long right = unary();
                    if (left == null || right == null) {
                        return null;
                    }
                    return switch (op) {
                        case ">=" -> left >= right ? 1L : 0L;
                        case "<=" -> left <= right ? 1L : 0L;
                        case "==" -> left.equals(right) ? 1L : 0L;
                        case "!=" -> !left.equals(right) ? 1L : 0L;
                        case ">" -> left > right ? 1L : 0L;
                        default -> left < right ? 1L : 0L;
                    };
                }
            }
            return left;
        }

        private Long unary() {
            skip();
            if (take("!")) {
                Long value = unary();
                return value == null ? null : value == 0 ? 1L : 0L;
            }
            if (take("(")) {
                Long value = or();
                if (!take(")")) {
                    throw new IllegalStateException("unbalanced");
                }
                return value;
            }
            int start = at;
            while (at < text.length() && (Character.isLetterOrDigit(text.charAt(at)) || text.charAt(at) == '_')) {
                at++;
            }
            String word = text.substring(start, at);
            if (word.isEmpty()) {
                throw new IllegalStateException("nothing to read at " + at);
            }
            if (word.equals("defined")) {
                boolean paren = take("(");
                skip();
                int nameStart = at;
                while (at < text.length() && (Character.isLetterOrDigit(text.charAt(at)) || text.charAt(at) == '_')) {
                    at++;
                }
                String name = text.substring(nameStart, at);
                if (paren && !take(")")) {
                    throw new IllegalStateException("unbalanced");
                }
                return macros.containsKey(name) ? Long.valueOf(1) : null;
            }
            if (Character.isDigit(word.charAt(0))) {
                return number(word);
            }
            return macros.get(word);
        }

        private boolean take(String token) {
            skip();
            if (text.startsWith(token, at)) {
                at += token.length();
                return true;
            }
            return false;
        }

        private void skip() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
                at++;
            }
        }
    }

    /** Where an import's file is inside a pack layer: {@code ns:file.glsl} to {@code assets/ns/shaders/include/file.glsl}. */
    private static String includePath(String target) {
        int colon = target.indexOf(':');
        String namespace = colon >= 0 ? target.substring(0, colon) : "minecraft";
        String file = colon >= 0 ? target.substring(colon + 1) : target;
        return "assets/" + namespace + "/shaders/include/" + file;
    }

    /** The vanilla uniform block a member belongs to, or null. */
    private static String blockOf(String member) {
        for (Map.Entry<String, List<String>> block : FACTS.blocks().entrySet()) {
            if (block.getValue().contains(member)) {
                return block.getKey();
            }
        }
        return null;
    }

    /** The vanilla include that declares a block. */
    private static String includeOf(String block) {
        for (Map.Entry<String, List<String>> include : FACTS.includes().entrySet()) {
            if (include.getValue().contains(block)) {
                return include.getKey();
            }
        }
        return "...";
    }

    private static boolean firstDirectiveIsVersion(String code) {
        for (String line : code.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.startsWith("#version");
            }
        }
        return false;
    }

    /** The other stage of a program: {@code x.vsh} for {@code x.fsh} and back. */
    private static String otherHalf(String path) {
        if (path.endsWith(".vsh")) {
            return path.substring(0, path.length() - 4) + ".fsh";
        }
        if (path.endsWith(".fsh")) {
            return path.substring(0, path.length() - 4) + ".vsh";
        }
        return path;
    }

    /** Where a shader file sits: the overlay it is in (or ""), its namespace, its path under shaders/. */
    private record Location(String overlay, String namespace, String relative) {

        static Location of(String path) {
            int assets = path.startsWith("assets/") ? 0 : path.indexOf("/assets/") + 1;
            if (assets < 0) {
                return null;
            }
            String overlay = path.substring(0, assets);
            String[] parts = path.substring(assets).split("/", 4);
            if (parts.length < 4 || !parts[0].equals("assets") || !parts[2].equals("shaders")) {
                return null;
            }
            return new Location(overlay, parts[1], parts[3]);
        }
    }

    /** For tests: the facts' view of one block's members. */
    static List<String> members(String block) {
        return FACTS.blocks().getOrDefault(block, List.of());
    }

    /** For tests: the blocks the client hands a core shader. */
    static List<String> bound(String shader) {
        return FACTS.bound().getOrDefault(shader, List.of());
    }
}
