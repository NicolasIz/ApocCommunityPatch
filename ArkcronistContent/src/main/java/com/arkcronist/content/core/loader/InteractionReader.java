package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.animation.AnimatedModel;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.furniture.Footprint;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The furniture sections that say what a right click does beyond sitting and storing:
 * {@code effect:} (particles and a drink), {@code command:} (a sign that runs commands) and
 * {@code guillotine:}. Everything wrong is reported and left out or replaced by its default, never
 * thrown.
 */
final class InteractionReader {

    /** A particle id: {@code small_flame} or {@code minecraft:small_flame}. Whether it exists is the server's to say. */
    static final Pattern PARTICLE = Pattern.compile("([a-z0-9_.\\-]+:)?[a-z0-9_./\\-]+");
    private static final Pattern SOUND = Pattern.compile("([a-z0-9_.\\-]+:)?[a-z0-9_./\\-]+");
    private static final Pattern POTION = Pattern.compile("([a-z0-9_.\\-]+:)?[a-z0-9_/\\-]+");

    private InteractionReader() {
    }

    // ---------------------------------------------------------------- effect

    /**
     * {@code effect:} - {@code particles} drawn every {@code interval} ticks for players within
     * {@code range} blocks, and {@code on-click}: {@code potions}, {@code sound}, {@code cooldown}
     * (seconds) and {@code message}.
     */
    static @Nullable Placement.Ambience ambience(@Nullable Map<?, ?> section, String prefix, List<String> problems) {
        if (section == null) {
            problems.add(prefix + "interactable: effect needs an 'effect:' section with particles or on-click");
            return null;
        }
        String at = prefix + "effect: ";
        List<Placement.Particles> particles = particles(section.get("particles"), at + "particles: ", problems);
        int interval = whole(section, "interval", 10, 1, 1200, at, problems);
        int range = whole(section, "range", 24, 4, 128, at, problems);
        List<Placement.Effect> potions = new ArrayList<>();
        String sound = null;
        int cooldown = 0;
        String message = null;
        Object raw = section.get("on-click");
        if (raw instanceof Map<?, ?> click) {
            String clickAt = at + "on-click: ";
            potions = potions(click.get("potions"), clickAt, problems);
            sound = sound(click, clickAt, problems);
            cooldown = (int) Math.round(decimal(click, "cooldown", 3, 0, 3600, clickAt, problems) * 20);
            message = string(click, "message");
        } else if (raw != null) {
            problems.add(at + "'on-click' should be a section with potions, sound, cooldown and message");
        }
        if (particles.isEmpty() && potions.isEmpty() && sound == null && message == null) {
            problems.add(at + "has neither particles nor anything on a click; nothing would happen");
            return null;
        }
        return new Placement.Ambience(particles, interval, range, potions, sound, cooldown, message);
    }

    static List<Placement.Particles> particles(@Nullable Object raw, String prefix, List<String> problems) {
        List<Placement.Particles> particles = new ArrayList<>();
        if (raw == null) {
            return particles;
        }
        if (!(raw instanceof List<?> list)) {
            problems.add(prefix + "should be a list of {particle, offset, count, spread, speed}");
            return particles;
        }
        for (int i = 0; i < list.size(); i++) {
            String at = prefix + "#" + (i + 1) + ": ";
            Object entry = list.get(i);
            if (!(entry instanceof Map<?, ?> map)) {
                problems.add(at + "should be a section like {particle: small_flame, offset: [0, 1, 0]}");
                continue;
            }
            String name = string(map, "particle");
            if (name == null || !PARTICLE.matcher(name.toLowerCase(Locale.ROOT)).matches()) {
                problems.add(at + "'particle' should be a particle id such as small_flame, not '" + name + "'");
                continue;
            }
            double[] offset = offset(map.get("offset"), at, problems);
            particles.add(new Placement.Particles(name.toLowerCase(Locale.ROOT), offset[0], offset[1], offset[2],
                    whole(map, "count", 1, 1, 100, at, problems), decimal(map, "spread", 0.1, 0, 4, at, problems),
                    decimal(map, "speed", 0, 0, 2, at, problems)));
        }
        return particles;
    }

    private static double[] offset(@Nullable Object raw, String prefix, List<String> problems) {
        if (raw == null) {
            return new double[]{0, 1, 0};
        }
        if (raw instanceof List<?> list && list.size() == 3 && list.stream().allMatch(v -> v instanceof Number)) {
            double[] offset = new double[3];
            for (int i = 0; i < 3; i++) {
                offset[i] = Math.max(-4, Math.min(4, ((Number) list.get(i)).doubleValue()));
            }
            return offset;
        }
        problems.add(prefix + "'offset' should be three numbers, [across, up, forward] in blocks; using [0, 1, 0]");
        return new double[]{0, 1, 0};
    }

    /** {@code "regeneration 8"}, {@code "speed 20 1"}, or {@code {effect: speed, seconds: 20, amplifier: 1}}. */
    private static List<Placement.Effect> potions(@Nullable Object raw, String prefix, List<String> problems) {
        List<Placement.Effect> potions = new ArrayList<>();
        if (raw == null) {
            return potions;
        }
        List<?> list = raw instanceof List<?> l ? l : List.of(raw);
        for (Object entry : list) {
            String name;
            double seconds;
            int amplifier;
            if (entry instanceof Map<?, ?> map) {
                name = string(map, "effect");
                seconds = decimal(map, "seconds", 10, 0.05, 3600, prefix, problems);
                amplifier = whole(map, "amplifier", 0, 0, 9, prefix, problems);
            } else {
                String[] parts = String.valueOf(entry).trim().split("\\s+");
                name = parts[0];
                try {
                    seconds = parts.length > 1 ? Double.parseDouble(parts[1]) : 10;
                    amplifier = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                } catch (NumberFormatException unreadable) {
                    problems.add(prefix + "potion '" + entry + "' should read 'effect seconds [amplifier]'");
                    continue;
                }
                if (seconds < 0.05 || seconds > 3600 || amplifier < 0 || amplifier > 9) {
                    problems.add(prefix + "potion '" + entry + "': seconds 0.05-3600, amplifier 0-9");
                    continue;
                }
            }
            if (name == null || !POTION.matcher(name.toLowerCase(Locale.ROOT)).matches()) {
                problems.add(prefix + "'" + name + "' is not a potion effect id such as regeneration");
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            potions.add(new Placement.Effect(key.indexOf(':') < 0 ? "minecraft:" + key : key,
                    (int) Math.round(seconds * 20), amplifier));
        }
        return potions;
    }

    // ---------------------------------------------------------------- command

    /**
     * {@code command:} - {@code run} (one command or a list), {@code as} ({@code console} or
     * {@code player}), {@code cooldown} (seconds), {@code permission}, {@code message} and
     * {@code sound}.
     */
    static @Nullable Placement.Commands commands(@Nullable Map<?, ?> section, String prefix, List<String> problems) {
        if (section == null) {
            problems.add(prefix + "interactable: command needs a 'command:' section with 'run'");
            return null;
        }
        String at = prefix + "command: ";
        List<String> run = commandList(section.get("run"), at + "run: ", problems);
        if (run.isEmpty()) {
            problems.add(at + "'run' has no command; the sign would do nothing");
            return null;
        }
        String as = string(section, "as");
        boolean asPlayer = as != null && as.trim().equalsIgnoreCase("player");
        if (as != null && !asPlayer && !as.trim().equalsIgnoreCase("console")) {
            problems.add(at + "'as' is console or player, not '" + as + "'; running from the console");
        }
        String permission = string(section, "permission");
        return new Placement.Commands(run, asPlayer,
                (int) Math.round(decimal(section, "cooldown", 2, 0, 3600, at, problems) * 20),
                permission == null || permission.isBlank() ? null : permission.trim(),
                string(section, "message"), sound(section, at, problems));
    }

    private static List<String> commandList(@Nullable Object raw, String prefix, List<String> problems) {
        List<String> commands = new ArrayList<>();
        if (raw == null) {
            return commands;
        }
        List<?> list = raw instanceof List<?> l ? l : List.of(raw);
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> || entry instanceof List<?>) {
                problems.add(prefix + "each command should be one line of text");
                continue;
            }
            String command = String.valueOf(entry).strip();
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (command.isEmpty() || command.contains("\n")) {
                problems.add(prefix + "an empty or multi-line command is skipped");
                continue;
            }
            commands.add(command);
        }
        return commands;
    }

    // ---------------------------------------------------------------- guillotine

    /**
     * {@code guillotine:} - which animations {@code drop} and {@code reset} the blade, when it moves
     * ({@code delay-ms}) and lands ({@code impact-ms}), how long it stays down
     * ({@code reset-after}, seconds), which block is the {@code lever}, where the one in the stocks
     * kneels ({@code kneel-height}), on whom it falls ({@code victim}: {@code occupant} or
     * {@code user}), {@code respect-pvp}, {@code commands} and the impact's particles and sound.
     */
    static @Nullable Placement.Guillotine guillotine(@Nullable Map<?, ?> section, @Nullable Placement.Animated animated,
                                                     Footprint footprint, String prefix, List<String> problems) {
        String at = prefix + "guillotine: ";
        if (animated == null) {
            problems.add(prefix + "interactable: guillotine needs an 'animated-model' whose blade can fall");
            return null;
        }
        Map<?, ?> settings = section == null ? Map.of() : section;
        String drop = orDefault(string(settings, "drop"), "drop_blade");
        String reset = orDefault(string(settings, "reset"), "reset_blade");
        AnimatedModel.Clip dropClip = animated.model().clips().get(drop);
        if (dropClip == null) {
            problems.add(at + "the model has no animation '" + drop + "' to drop the blade (it has: "
                    + String.join(", ", animated.model().clips().keySet()) + ")");
            return null;
        }
        if (!animated.model().clips().containsKey(reset)) {
            if (settings.containsKey("reset")) {
                problems.add(at + "the model has no animation '" + reset + "'; the blade stays down until the next pull");
            }
            reset = null;
        }
        int delay = whole(settings, "delay-ms", 63, 0, 5000, at, problems);
        int impact = whole(settings, "impact-ms", dropClip.length() * 50, 0, 10_000, at, problems);
        int resetTicks = (int) Math.round(decimal(settings, "reset-after", 3, 0, 600, at, problems) * 20);
        // By default the lever is the bottom block of its rightmost column, as seen from the front.
        int first = -(footprint.width() - 1) / 2;
        int across = first + footprint.width() - 1;
        int up = 0;
        Object lever = settings.get("lever");
        if (lever instanceof Map<?, ?> map) {
            across = whole(map, "across", across, -Footprint.MAX, Footprint.MAX, at + "lever: ", problems);
            up = whole(map, "up", 0, 0, Footprint.MAX, at + "lever: ", problems);
        } else if (lever != null) {
            problems.add(at + "'lever' should be {across: <n>, up: <n>}");
        }
        if (across < first || across > first + footprint.width() - 1 || up >= footprint.height()) {
            problems.add(at + "the lever {across: " + across + ", up: " + up + "} is not one of its "
                    + footprint.width() + " x " + footprint.height() + " blocks");
            return null;
        }
        if (across == 0 && up == 0) {
            problems.add(at + "the lever cannot be the block it stands on - that one holds the stocks; give it a"
                    + " 'size' and put the lever beside");
            return null;
        }
        String victim = orDefault(string(settings, "victim"), "occupant").trim().toLowerCase(Locale.ROOT);
        if (!victim.equals("occupant") && !victim.equals("user")) {
            problems.add(at + "'victim' is occupant or user, not '" + victim + "'; using occupant");
            victim = "occupant";
        }
        Object respect = settings.get("respect-pvp");
        boolean respectPvp = !(respect instanceof Boolean bool) || bool;
        if (respect != null && !(respect instanceof Boolean)) {
            problems.add(at + "'respect-pvp' should be true or false; using true");
        }
        return new Placement.Guillotine(drop, reset, delay, impact, resetTicks, across, up,
                (float) decimal(settings, "kneel-height", 0.35, 0, 2, at, problems), victim.equals("user"), respectPvp,
                commandList(settings.get("commands"), at + "commands: ", problems),
                particles(settings.get("impact-particles"), at + "impact-particles: ", problems),
                orDefault(soundOrNull(settings, "impact-sound", at, problems), "minecraft:block.anvil.land"));
    }

    // ---------------------------------------------------------------- helpers

    private static @Nullable String sound(Map<?, ?> section, String prefix, List<String> problems) {
        return soundOrNull(section, "sound", prefix, problems);
    }

    private static @Nullable String soundOrNull(Map<?, ?> section, String key, String prefix, List<String> problems) {
        String sound = string(section, key);
        if (sound == null || sound.isBlank()) {
            return null;
        }
        String name = sound.trim().toLowerCase(Locale.ROOT);
        if (!SOUND.matcher(name).matches()) {
            problems.add(prefix + "'" + key + "' should be a sound id such as minecraft:entity.generic.drink");
            return null;
        }
        return name;
    }

    private static @Nullable String string(Map<?, ?> section, String key) {
        Object value = section.get(key);
        return value == null || value instanceof Map<?, ?> || value instanceof List<?> ? null : String.valueOf(value);
    }

    private static String orDefault(@Nullable String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int whole(Map<?, ?> section, String key, int fallback, int min, int max, String prefix,
                             List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if ((value instanceof Integer || value instanceof Long) && ((Number) value).longValue() >= min
                && ((Number) value).longValue() <= max) {
            return ((Number) value).intValue();
        }
        problems.add(prefix + "'" + key + "' should be a whole number from " + min + " to " + max + "; using " + fallback);
        return fallback;
    }

    private static double decimal(Map<?, ?> section, String key, double fallback, double min, double max, String prefix,
                                  List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() >= min
                && number.doubleValue() <= max) {
            return number.doubleValue();
        }
        problems.add(prefix + "'" + key + "' should be a number from " + min + " to " + max + "; using " + fallback);
        return fallback;
    }
}
