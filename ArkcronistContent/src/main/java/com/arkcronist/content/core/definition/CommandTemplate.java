package com.arkcronist.content.core.definition;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A command written in content, with its placeholders filled in for one click.
 *
 * <p>The values come from the server, never from what a player typed: a name, a UUID, a world's
 * name, coordinates. A name is still checked before it goes into a console command - Java names are
 * {@code [A-Za-z0-9_]}, and Bedrock players through Floodgate carry a {@code .} or {@code *} prefix -
 * so nothing a name could contain splits one command into two or adds a selector.</p>
 */
public final class CommandTemplate {

    /** What a player's name may be to be put into a command: Java's characters, Floodgate's prefixes. */
    public static final Pattern SAFE_NAME = Pattern.compile("[.*]?[A-Za-z0-9_]{1,32}");
    private static final Pattern WORLD = Pattern.compile("[A-Za-z0-9_\\-./]{1,64}");
    /** A dimension's key, {@code minecraft:overworld}, as {@code execute in} takes it. */
    private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+");
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-z]+)%");

    private CommandTemplate() {
    }

    /** Whether {@code name} can be put into a command as it is. */
    public static boolean safeName(String name) {
        return name != null && SAFE_NAME.matcher(name).matches();
    }

    /**
     * {@code command} with each {@code %key%} of {@code values} replaced; a placeholder not given is
     * left as it is. A leading slash is dropped, as the server's dispatcher takes commands without it.
     * Only the values the command uses are checked: a world whose name could not go into a command
     * does not stop a sign that never names it.
     *
     * @throws IllegalArgumentException when a value the command uses is not one that is safe in it
     */
    public static String fill(String command, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(command.strip());
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = values.get(key);
            if (value != null) {
                check(key, value);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(out);
        String filled = out.toString();
        return filled.startsWith("/") ? filled.substring(1) : filled;
    }

    private static void check(String key, String value) {
        boolean ok = switch (key) {
            case "player", "killer" -> safeName(value);
            case "world" -> WORLD.matcher(value).matches();
            case "dimension" -> DIMENSION.matcher(value).matches() && value.length() <= 128;
            default -> value.matches("[A-Za-z0-9\\-.]{1,64}");
        };
        if (!ok) {
            throw new IllegalArgumentException("'" + value + "' cannot go into a command as %" + key + "%");
        }
    }
}
