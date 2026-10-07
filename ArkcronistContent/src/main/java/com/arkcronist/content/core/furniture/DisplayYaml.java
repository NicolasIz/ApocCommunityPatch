package com.arkcronist.content.core.furniture;

import com.arkcronist.content.core.definition.Placement;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes an edited {@code furniture.display} back into the content file it came from, changing
 * nothing else.
 *
 * <p>Content files are written by people: comments, blank lines, key order and quoting are theirs.
 * So the file is not loaded and dumped again - that would lose all of it - but edited line by line:
 * only {@code translation}, {@code scale} and {@code rotation} under
 * {@code items.<id>.furniture.display} are replaced, a comment at the end of such a line kept, and
 * the keys that are missing added after their siblings with the file's own indentation. A
 * {@code display} or {@code furniture} written on one line as a flow map ({@code {transform: HEAD}})
 * is rewritten as one, on that line.</p>
 *
 * <p>Before anything is returned, both texts are read back and compared: the new one must give the
 * three values asked for, and everything else in it must be exactly what the old one gave. When it
 * does not - a layout this editor does not understand, a key given twice - nothing is written and
 * {@link EditException} says why.</p>
 */
public final class DisplayYaml {

    /** {@code    key: rest}, with the key quoted or not. Lines starting with "- " are list items, not keys. */
    private static final Pattern KEY = Pattern.compile(
            "^( *)(?:\"([^\"]*)\"|'([^']*)'|([A-Za-z0-9_.\\-]+))[ \\t]*:(?:[ \\t]+(.*))?[ \\t]*$");
    private static final List<String> KEYS = List.of("translation", "scale", "rotation");

    /** Why the file could not be edited in place. */
    public static final class EditException extends Exception {
        public EditException(String message) {
            super(message);
        }
    }

    private DisplayYaml() {
    }

    /**
     * {@code original} with item {@code itemId}'s {@code furniture.display} translation, scale and
     * rotation set to {@code transform}'s.
     */
    public static String write(String original, String itemId, DisplayTransform transform) throws EditException {
        String newline = original.contains("\r\n") ? "\r\n" : "\n";
        boolean endsWithNewline = original.endsWith("\n");
        List<String> lines = new ArrayList<>(List.of(original.replace("\r\n", "\n").split("\n", -1)));
        if (endsWithNewline) {
            lines.remove(lines.size() - 1);
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("translation", DisplayTransform.yaml(transform.translation()));
        values.put("scale", DisplayTransform.yaml(transform.scale()));
        values.put("rotation", DisplayTransform.yaml(transform.rotation()));

        int items = find(lines, 0, lines.size(), 0, "items");
        if (items < 0) {
            throw new EditException("the file has no 'items:' section at its top level");
        }
        int itemsEnd = blockEnd(lines, items, 0);
        int itemIndent = childIndent(lines, items, itemsEnd, 0);
        int item = itemIndent < 0 ? -1 : find(lines, items + 1, itemsEnd, itemIndent, itemId);
        if (item < 0) {
            throw new EditException("item '" + itemId + "' is not under items: in this file");
        }
        if (rest(lines.get(item)) != null) {
            flow(lines, item, List.of("furniture", "display"), values);
        } else {
            int itemEnd = blockEnd(lines, item, itemIndent);
            int found = childIndent(lines, item, itemEnd, itemIndent);
            int fieldIndent = found < 0 ? itemIndent + Math.max(2, itemIndent) : found;
            int step = fieldIndent - itemIndent;
            int furniture = find(lines, item + 1, itemEnd, fieldIndent, "furniture");
            if (furniture < 0) {
                List<String> added = new ArrayList<>();
                added.add(" ".repeat(fieldIndent) + "furniture:");
                added.add(" ".repeat(fieldIndent + step) + "display:");
                values.forEach((key, value) -> added.add(" ".repeat(fieldIndent + 2 * step) + key + ": " + value));
                lines.addAll(lastContent(lines, item, itemEnd) + 1, added);
            } else if (rest(lines.get(furniture)) != null) {
                flow(lines, furniture, List.of("display"), values);
            } else {
                display(lines, furniture, fieldIndent, step, values);
            }
        }

        String edited = String.join(newline, lines) + (endsWithNewline ? newline : "");
        verify(original, edited, itemId, transform);
        return edited;
    }

    /** The display block under a block-style {@code furniture:}. */
    private static void display(List<String> lines, int furniture, int furnitureIndent, int step,
                                Map<String, String> values) throws EditException {
        int furnitureEnd = blockEnd(lines, furniture, furnitureIndent);
        int found = childIndent(lines, furniture, furnitureEnd, furnitureIndent);
        int fieldIndent = found < 0 ? furnitureIndent + step : found;
        int display = find(lines, furniture + 1, furnitureEnd, fieldIndent, "display");
        if (display < 0) {
            List<String> added = new ArrayList<>();
            added.add(" ".repeat(fieldIndent) + "display:");
            int inner = 2 * fieldIndent - furnitureIndent;
            values.forEach((key, value) -> added.add(" ".repeat(inner) + key + ": " + value));
            lines.addAll(lastContent(lines, furniture, furnitureEnd) + 1, added);
            return;
        }
        if (rest(lines.get(display)) != null) {
            flow(lines, display, List.of(), values);
            return;
        }
        int displayEnd = blockEnd(lines, display, fieldIndent);
        int keyIndent = childIndent(lines, display, displayEnd, fieldIndent);
        if (keyIndent < 0) {
            keyIndent = fieldIndent + (fieldIndent - furnitureIndent);
        }
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> value : values.entrySet()) {
            int line = find(lines, display + 1, displayEnd, keyIndent, value.getKey());
            if (line < 0) {
                missing.add(" ".repeat(keyIndent) + value.getKey() + ": " + value.getValue());
                continue;
            }
            // A value written as a block list on the lines below goes; the new one is on this line.
            int valueEnd = blockEnd(lines, line, keyIndent);
            while (valueEnd > line + 1 && lines.get(valueEnd - 1).trim().isEmpty()) {
                valueEnd--;
            }
            String original = lines.get(line);
            // Only the old value's own lines go - its list items; comments and blank lines stay.
            for (int i = valueEnd - 1; i > line; i--) {
                String below = lines.get(i);
                if (!below.isBlank() && !below.trim().startsWith("#")) {
                    lines.remove(i);
                    displayEnd--;
                }
            }
            lines.set(line, withComment(" ".repeat(keyIndent) + value.getKey() + ": " + value.getValue(), original));
        }
        if (!missing.isEmpty()) {
            lines.addAll(lastContent(lines, display, displayEnd) + 1, missing);
        }
    }

    /**
     * A section written on one line as a flow map: read, changed along {@code path} and written back
     * on that line, also as a flow map.
     */
    @SuppressWarnings("unchecked")
    private static void flow(List<String> lines, int line, List<String> path, Map<String, String> values)
            throws EditException {
        Matcher matcher = KEY.matcher(lines.get(line));
        if (!matcher.matches()) {
            throw new EditException("line " + (line + 1) + " is not a key the editor can read");
        }
        String rest = matcher.group(5);
        String comment = comment(lines.get(line));
        String value = comment == null ? rest : rest.substring(0, rest.lastIndexOf(comment)).trim();
        if (!value.startsWith("{") || !value.endsWith("}")) {
            throw new EditException("line " + (line + 1) + " gives '" + key(matcher) + "' a value that is not a"
                    + " section; edit it by hand");
        }
        Object parsed;
        try {
            parsed = yaml().load(value);
        } catch (YAMLException exception) {
            throw new EditException("line " + (line + 1) + " could not be read: " + exception.getMessage());
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new EditException("line " + (line + 1) + " is not a section");
        }
        Map<String, Object> section = new LinkedHashMap<>((Map<String, Object>) map);
        Map<String, Object> target = section;
        for (String key : path) {
            Object child = target.get(key);
            Map<String, Object> next = child instanceof Map<?, ?> existing
                    ? new LinkedHashMap<>((Map<String, Object>) existing) : new LinkedHashMap<>();
            target.put(key, next);
            target = next;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            target.put(entry.getKey(), yaml().load(entry.getValue()));
        }
        lines.set(line, withComment(matcher.group(1) + quoted(matcher) + ": " + flowText(section), lines.get(line)));
    }

    private static String flowText(Map<String, Object> section) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        options.setWidth(Integer.MAX_VALUE);
        options.setSplitLines(false);
        return new Yaml(options).dump(section).strip();
    }

    // ------------------------------------------------------------ checking

    /** Both texts read back: the three values are the new ones, and nothing else differs. */
    @SuppressWarnings("unchecked")
    private static void verify(String original, String edited, String itemId, DisplayTransform transform)
            throws EditException {
        Object before;
        Object after;
        try {
            before = yaml().load(original);
            after = yaml().load(edited);
        } catch (YAMLException exception) {
            throw new EditException("the edited file would not be valid YAML (" + firstLine(exception.getMessage())
                    + ") - left as it was");
        }
        Map<String, Object> display = section(after, itemId);
        if (display == null || !same(display.get("translation"), transform.translation())
                || !same(display.get("scale"), transform.scale()) || !same(display.get("rotation"), transform.rotation())) {
            throw new EditException("the edit did not land where the loader reads it (a key given twice, or a"
                    + " layout the editor does not follow) - left as it was");
        }
        Object strippedBefore = strip(before, itemId);
        Object strippedAfter = strip(after, itemId);
        if (!Objects.equals(strippedBefore, strippedAfter)) {
            throw new EditException("the edit would have changed something besides the display - left as it was");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Object document, String itemId) {
        if (document instanceof Map<?, ?> root && root.get("items") instanceof Map<?, ?> items
                && items.get(itemId) instanceof Map<?, ?> item && item.get("furniture") instanceof Map<?, ?> furniture
                && furniture.get("display") instanceof Map<?, ?> display) {
            return (Map<String, Object>) display;
        }
        return null;
    }

    /** A deep copy without the three keys - and without display and furniture when nothing else was in them. */
    @SuppressWarnings("unchecked")
    private static Object strip(Object document, String itemId) {
        Object copy = deepCopy(document);
        if (copy instanceof Map<?, ?> root && root.get("items") instanceof Map<?, ?> items
                && items.get(itemId) instanceof Map<?, ?> item && item.get("furniture") instanceof Map<?, ?> furniture) {
            Map<String, Object> furnitureMap = (Map<String, Object>) furniture;
            if (furnitureMap.get("display") instanceof Map<?, ?> display) {
                Map<String, Object> displayMap = (Map<String, Object>) display;
                KEYS.forEach(displayMap::remove);
                if (displayMap.isEmpty()) {
                    furnitureMap.remove("display");
                }
            }
            if (furnitureMap.isEmpty()) {
                ((Map<String, Object>) item).remove("furniture");
            }
        }
        return copy;
    }

    private static Object deepCopy(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(key, deepCopy(value)));
            return copy;
        }
        if (node instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(element -> copy.add(deepCopy(element)));
            return copy;
        }
        return node;
    }

    private static boolean same(Object value, Placement.Vec3 vector) {
        if (!(value instanceof List<?> list) || list.size() != 3) {
            return false;
        }
        float[] wanted = {vector.x(), vector.y(), vector.z()};
        for (int i = 0; i < 3; i++) {
            if (!(list.get(i) instanceof Number number) || Math.abs(number.floatValue() - wanted[i]) > 1e-4) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ lines

    /** The first line in {@code [from, to)} that is {@code key} at exactly {@code indent}, or -1. */
    private static int find(List<String> lines, int from, int to, int indent, String key) {
        for (int i = from; i < to; i++) {
            Matcher matcher = KEY.matcher(lines.get(i));
            if (matcher.matches() && matcher.group(1).length() == indent && key.equals(key(matcher))) {
                return i;
            }
        }
        return -1;
    }

    /** The index after the last line of the block a key at {@code indent} on {@code start} owns. */
    private static int blockEnd(List<String> lines, int start, int indent) {
        int i = start + 1;
        while (i < lines.size()) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && indent(line) <= indent
                    && !(trimmed.startsWith("- ") && indent(line) == indent && indent > 0)) {
                break;
            }
            i++;
        }
        return i;
    }

    /** The indentation of the first content line of a block, or -1 for an empty block. */
    private static int childIndent(List<String> lines, int start, int end, int indent) {
        for (int i = start + 1; i < end; i++) {
            String trimmed = lines.get(i).trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && indent(lines.get(i)) > indent) {
                return indent(lines.get(i));
            }
        }
        return -1;
    }

    /** The last line of a block that is not blank or a comment: new keys go right after it. */
    private static int lastContent(List<String> lines, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            String trimmed = lines.get(i).trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return i;
            }
        }
        return start;
    }

    /** What follows a key's colon on its own line, or null when its value is on the lines below. */
    private static String rest(String line) {
        Matcher matcher = KEY.matcher(line);
        if (!matcher.matches() || matcher.group(5) == null) {
            return null;
        }
        String rest = matcher.group(5).trim();
        return rest.isEmpty() || rest.startsWith("#") ? null : rest;
    }

    /** A comment at the end of a line - {@code # ...} outside quotes - or null. */
    /**
     * {@code text} followed by the comment {@code original} ended with, in the comment's own column
     * when the new text leaves room for it - so a column of aligned comments stays aligned.
     */
    private static String withComment(String text, String original) {
        String comment = comment(original);
        if (comment == null) {
            return text;
        }
        int column = original.lastIndexOf(comment);
        return text + " ".repeat(Math.max(2, column - text.length())) + comment;
    }

    private static String comment(String line) {
        boolean single = false;
        boolean dbl = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !dbl) {
                single = !single;
            } else if (c == '"' && !single) {
                dbl = !dbl;
            } else if (c == '#' && !single && !dbl && i > 0 && Character.isWhitespace(line.charAt(i - 1))) {
                return line.substring(i).trim();
            }
        }
        return null;
    }

    private static String key(Matcher matcher) {
        return matcher.group(2) != null ? matcher.group(2) : matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
    }

    private static String quoted(Matcher matcher) {
        return matcher.group(2) != null ? "\"" + matcher.group(2) + "\""
                : matcher.group(3) != null ? "'" + matcher.group(3) + "'" : matcher.group(4);
    }

    private static int indent(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static Yaml yaml() {
        return new Yaml(new SafeConstructor(new LoaderOptions()));
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "no detail";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
