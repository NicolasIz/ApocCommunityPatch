package com.arkcronist.content.core.geyser;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 3.3: what Geyser is told, as a file - for a Geyser on a proxy, which this plugin cannot reach by
 * its API. The plugin writes it with the .mcpack after every build; this same jar, put in that
 * Geyser's {@code extensions/} folder, reads it as Geyser starts and registers it all as the hook
 * does on a server with Geyser-Spigot.
 *
 * <p>One line per item or block, its fields separated by tabs, each field URL-encoded so no tab or
 * line break in a name can split it. Nothing here needs more than the JDK: the extension runs inside
 * Geyser, where neither Bukkit nor Gson can be counted on.</p>
 */
public final class GeyserExport {

    /** The file's first line. */
    public static final String HEADER = "arkcontent-geyser 1";
    /** The file name, beside the pack. */
    public static final String FILE = "arkcontent-content.txt";
    /** The pack's name in the extension's folder: one name, so a new copy replaces the old. */
    public static final String PACK = "arkcontent.mcpack";

    private GeyserExport() {
    }

    /**
     * What the file holds.
     *
     * @param carriers how many of the blocks are furniture carriers, for the log
     */
    public record Content(List<BedrockConverter.BedrockItem> items, List<BedrockConverter.BedrockBlock> blocks,
                          int carriers) {
    }

    public static String write(List<BedrockConverter.BedrockItem> items, List<BedrockConverter.BedrockBlock> blocks,
                               int carriers) {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        line(out, "carriers", Integer.toString(carriers));
        for (BedrockConverter.BedrockItem item : items) {
            line(out, "item", item.id(), item.name(), item.javaItem(),
                    item.customModelData() == null ? "" : item.customModelData().toString(), item.itemModel(), item.icon(),
                    item.displayName(), Boolean.toString(item.handheld()), Boolean.toString(item.byNumber()),
                    item.uses().encode());
        }
        for (BedrockConverter.BedrockBlock block : blocks) {
            StringBuilder materials = new StringBuilder();
            block.materials().forEach((key, material) -> materials.append(materials.isEmpty() ? "" : "&")
                    .append(encode(key)).append('=').append(encode(material.texture())).append(',')
                    .append(encode(material.renderMethod())));
            StringBuilder boxes = new StringBuilder();
            for (float[] box : block.boxes()) {
                boxes.append(boxes.isEmpty() ? "" : ";");
                for (int i = 0; i < box.length; i++) {
                    boxes.append(i == 0 ? "" : ",").append(box[i]);
                }
            }
            line(out, "block", block.name(), block.javaState(), block.geometry(), materials.toString(),
                    Boolean.toString(block.collision()), Boolean.toString(block.selection()), Integer.toString(block.light()),
                    Float.toString(block.destroySeconds()), Integer.toString(block.rotationY()), block.displayName(),
                    String.join("|", block.moreStates()), boxes.toString());
        }
        return out.toString();
    }

    /**
     * @throws IllegalArgumentException if the text is not such a file, or a line is cut short
     */
    public static Content read(String text) {
        String[] lines = text.split("\n");
        if (lines.length == 0 || !lines[0].trim().equals(HEADER)) {
            throw new IllegalArgumentException("not an OverlordContent export (the first line is not '" + HEADER + "')");
        }
        List<BedrockConverter.BedrockItem> items = new ArrayList<>();
        List<BedrockConverter.BedrockBlock> blocks = new ArrayList<>();
        int carriers = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] raw = line.split("\t", -1);
            String[] field = new String[raw.length];
            for (int f = 0; f < raw.length; f++) {
                field[f] = decode(raw[f]);
            }
            switch (field[0]) {
                case "carriers" -> {
                    need(field, 2, i);
                    carriers = Integer.parseInt(field[1]);
                }
                case "item" -> {
                    need(field, 9, i);
                    // The tenth field came in 3.3.2: a file written before reads as "not by number";
                    // the eleventh in 3.4: one written before, as an item used as the plain one.
                    items.add(new BedrockConverter.BedrockItem(field[1], field[2], field[3],
                            field[4].isEmpty() ? null : Integer.valueOf(field[4]), field[5], field[6], field[7],
                            Boolean.parseBoolean(field[8]), field.length > 9 && Boolean.parseBoolean(field[9]),
                            field.length > 10 ? BedrockConverter.Uses.decode(field[10]) : BedrockConverter.Uses.NONE));
                }
                case "block" -> {
                    need(field, 11, i);
                    // The twelfth and thirteenth fields came in 5.3: a shaped block's other states, and its boxes.
                    List<String> more = field.length > 11 && !field[11].isEmpty() ? List.of(field[11].split("\\|")) : List.of();
                    List<float[]> boxes = new ArrayList<>();
                    if (field.length > 12 && !field[12].isEmpty()) {
                        for (String box : field[12].split(";")) {
                            String[] numbers = box.split(",");
                            float[] parsed = new float[numbers.length];
                            for (int n = 0; n < numbers.length; n++) {
                                parsed[n] = Float.parseFloat(numbers[n]);
                            }
                            boxes.add(parsed);
                        }
                    }
                    blocks.add(new BedrockConverter.BedrockBlock(field[1], field[2], field[3], materials(raw[4]),
                            Boolean.parseBoolean(field[5]), Boolean.parseBoolean(field[6]), Integer.parseInt(field[7]),
                            Float.parseFloat(field[8]), Integer.parseInt(field[9]), field[10], more, boxes));
                }
                default -> throw new IllegalArgumentException("line " + (i + 1) + ": unknown kind '" + field[0] + "'");
            }
        }
        return new Content(List.copyOf(items), List.copyOf(blocks), carriers);
    }

    /**
     * Items in the order Geyser 2.8 needs them registered: lowest custom_model_data number first
     * (see {@link BedrockConverter#lowestNumberFirst}). Here, where the extension can reach it
     * without the converter.
     */
    public static List<BedrockConverter.BedrockItem> lowestNumberFirst(List<BedrockConverter.BedrockItem> items) {
        List<BedrockConverter.BedrockItem> sorted = new ArrayList<>(items);
        sorted.sort(java.util.Comparator.comparing((BedrockConverter.BedrockItem item) -> item.customModelData() == null
                ? Integer.MAX_VALUE : item.customModelData()));
        return sorted;
    }

    /** The materials field, still encoded: {@code key=texture,method&key=texture,method}. */
    private static Map<String, BedrockConverter.Material> materials(String encoded) {
        Map<String, BedrockConverter.Material> materials = new LinkedHashMap<>();
        String whole = decode(encoded);
        if (whole.isEmpty()) {
            return materials;
        }
        for (String entry : whole.split("&")) {
            int equals = entry.indexOf('=');
            int comma = entry.lastIndexOf(',');
            if (equals < 0 || comma < equals) {
                throw new IllegalArgumentException("a block's material '" + entry + "' is not key=texture,method");
            }
            materials.put(decode(entry.substring(0, equals)), new BedrockConverter.Material(
                    decode(entry.substring(equals + 1, comma)), decode(entry.substring(comma + 1))));
        }
        return materials;
    }

    private static void need(String[] field, int count, int index) {
        if (field.length < count) {
            throw new IllegalArgumentException("line " + (index + 1) + ": " + field.length + " field(s), " + count
                    + " expected");
        }
    }

    private static void line(StringBuilder out, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                out.append('\t');
            }
            out.append(encode(fields[i]));
        }
        out.append('\n');
    }

    private static String encode(String text) {
        return URLEncoder.encode(text == null ? "" : text, StandardCharsets.UTF_8);
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }
}
