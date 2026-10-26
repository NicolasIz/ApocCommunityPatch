package com.arkcronist.content.core.mechanics;

import com.arkcronist.content.core.mechanics.ItemMechanics.Action;
import com.arkcronist.content.core.mechanics.ItemMechanics.Conditions;
import com.arkcronist.content.core.mechanics.ItemMechanics.Passive;
import com.arkcronist.content.core.mechanics.ItemMechanics.PotionSpec;
import com.arkcronist.content.core.mechanics.ItemMechanics.Reaction;
import com.arkcronist.content.core.mechanics.ItemMechanics.Target;
import com.arkcronist.content.core.mechanics.ItemMechanics.Trigger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 5.4: reads an item's {@code events:} and {@code mechanics:}.
 *
 * <pre>
 * events:
 *   right-click:
 *     cooldown: 5
 *     cooldown-message: "&lt;red&gt;Recharging: %seconds%s"
 *     conditions: {sneaking: false, chance: 1}
 *     actions:
 *       - lightning: target
 *       - sound: entity.lightning_bolt.thunder
 *       - message: "&lt;yellow&gt;Thunder!"
 *   hit-entity:                 # a list: several reactions, each with its own conditions
 *     - conditions: {chance: 0.2}
 *       actions:
 *         - effect: {effect: wither, seconds: 3, target: target}
 * mechanics:
 *   big-mining: 1               # 3x3
 *   auto-smelt: true
 *   soulbound: true
 *   lifesteal: 0.2
 *   held-effects: [haste]
 *   worn-effects: [{effect: night_vision, level: 1}]
 *   durability: 300
 * </pre>
 *
 * <p>A reaction with nothing but actions can be written as the list of actions alone. Anything it
 * cannot read is left out and said in {@code problems}; the rest still works.</p>
 */
public final class MechanicsReader {

    /** The longest cooldown taken: a day. */
    public static final double MAX_COOLDOWN = 86_400;
    /** The longest wait inside a reaction: a minute. */
    public static final int MAX_WAIT_TICKS = 1200;

    private MechanicsReader() {
    }

    /**
     * @param events    the {@code events:} section, or null
     * @param mechanics the {@code mechanics:} section, or null
     * @return what was read; null when both are absent or nothing in them could be read
     */
    public static @Nullable ItemMechanics read(@Nullable Object events, @Nullable Object mechanics, String prefix,
                                              List<String> problems) {
        Map<Trigger, List<Reaction>> reactions = new EnumMap<>(Trigger.class);
        if (events != null) {
            if (!(events instanceof Map<?, ?> section)) {
                problems.add(prefix + "events: should be a section of triggers (right-click, hit-entity, ...)");
            } else {
                for (Map.Entry<?, ?> entry : section.entrySet()) {
                    String name = String.valueOf(entry.getKey());
                    Optional<Trigger> trigger = Trigger.parse(name);
                    String where = prefix + "events: " + name + ": ";
                    if (trigger.isEmpty()) {
                        problems.add(prefix + "events: unknown trigger '" + name + "' (right-click, left-click,"
                                + " hit-entity, kill, break-block, consume, equip, unequip, shoot, damaged)");
                        continue;
                    }
                    List<Reaction> read = reactions(entry.getValue(), where, problems);
                    if (!read.isEmpty()) {
                        reactions.computeIfAbsent(trigger.get(), ignored -> new ArrayList<>()).addAll(read);
                    }
                }
            }
        }
        Passive passive = mechanics == null ? Passive.NONE : passive(mechanics, prefix + "mechanics: ", problems);
        ItemMechanics result = new ItemMechanics(reactions, passive);
        return result.isEmpty() ? null : result;
    }

    // ---------------------------------------------------------------- reactions

    private static List<Reaction> reactions(Object value, String where, List<String> problems) {
        List<Reaction> out = new ArrayList<>();
        if (value instanceof Map<?, ?> section) {
            Reaction reaction = reaction(section, where, problems);
            if (reaction != null) {
                out.add(reaction);
            }
        } else if (value instanceof List<?> list) {
            // A list of reactions, each a section with 'actions:' - or the actions of one reaction.
            boolean reactionsList = !list.isEmpty() && list.stream().allMatch(entry -> entry instanceof Map<?, ?> map
                    && (map.containsKey("actions") || map.containsKey("conditions")));
            if (reactionsList) {
                for (int i = 0; i < list.size(); i++) {
                    Reaction reaction = reaction((Map<?, ?>) list.get(i), where + "#" + (i + 1) + ": ", problems);
                    if (reaction != null) {
                        out.add(reaction);
                    }
                }
            } else {
                List<Action> actions = actions(list, where, problems);
                if (!actions.isEmpty()) {
                    out.add(new Reaction(actions, Conditions.ALWAYS, 0, null));
                }
            }
        } else {
            problems.add(where + "should be a section with 'actions:', or a list of actions");
        }
        return out;
    }

    private static @Nullable Reaction reaction(Map<?, ?> section, String where, List<String> problems) {
        Object actionsNode = section.get("actions");
        if (!(actionsNode instanceof List<?> list) || list.isEmpty()) {
            problems.add(where + "needs 'actions:', a list of what it does");
            return null;
        }
        List<Action> actions = actions(list, where, problems);
        if (actions.isEmpty()) {
            return null;
        }
        Conditions conditions = conditions(section.get("conditions"), where + "conditions: ", problems);
        double cooldown = number(section, "cooldown", 0, 0, MAX_COOLDOWN, where, problems);
        String message = text(section.get("cooldown-message"));
        return new Reaction(actions, conditions, cooldown, message);
    }

    private static Conditions conditions(@Nullable Object value, String where, List<String> problems) {
        if (value == null) {
            return Conditions.ALWAYS;
        }
        if (!(value instanceof Map<?, ?> section)) {
            problems.add(where + "should be a section (chance, sneaking, permission, worlds, targets, blocks)");
            return Conditions.ALWAYS;
        }
        Boolean sneaking = null;
        Object sneak = section.get("sneaking");
        if (sneak instanceof Boolean flag) {
            sneaking = flag;
        } else if (sneak != null) {
            problems.add(where + "'sneaking' should be true or false");
        }
        return new Conditions(number(section, "chance", 1, 0, 1, where, problems), sneaking,
                text(section.get("permission")), names(section.get("worlds"), false),
                names(section.get("targets"), true), names(section.get("blocks"), true));
    }

    // ---------------------------------------------------------------- actions

    private static List<Action> actions(List<?> list, String where, List<String> problems) {
        List<Action> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String at = where + "action #" + (i + 1) + ": ";
            if (!(list.get(i) instanceof Map<?, ?> entry) || entry.size() != 1) {
                problems.add(at + "should be one action, e.g. '- message: \"Hello\"'");
                continue;
            }
            Map.Entry<?, ?> only = entry.entrySet().iterator().next();
            String kind = String.valueOf(only.getKey()).trim().toLowerCase(Locale.ROOT).replace('_', '-');
            Action action = action(kind, only.getValue(), at + kind + ": ", problems);
            if (action != null) {
                out.add(action);
            }
        }
        return out;
    }

    private static @Nullable Action action(String kind, Object value, String where, List<String> problems) {
        Map<?, ?> map = value instanceof Map<?, ?> section ? section : Map.of();
        String plain = value instanceof Map<?, ?> ? null : text(value);
        switch (kind) {
            case "message", "actionbar", "action-bar", "title" -> {
                Action.Channel channel = switch (kind) {
                    case "message" -> channel(map.get("to"), where, problems);
                    case "title" -> Action.Channel.TITLE;
                    default -> Action.Channel.ACTION_BAR;
                };
                String text = plain != null ? plain : text(map.get(kind.equals("title") ? "title" : "text"));
                if (text == null && kind.equals("title")) {
                    text = text(map.get("text"));
                }
                if (text == null) {
                    problems.add(where + "needs the text to show");
                    return null;
                }
                return new Action.Message(text, channel, text(map.get("subtitle")));
            }
            case "sound" -> {
                String sound = plain != null ? plain : text(map.get("sound"));
                if (sound == null) {
                    problems.add(where + "needs the sound, e.g. entity.player.levelup");
                    return null;
                }
                return new Action.Sound(sound.toLowerCase(Locale.ROOT), (float) number(map, "volume", 1, 0, 10, where, problems),
                        (float) number(map, "pitch", 1, 0.5, 2, where, problems), target(map.get("at"), Target.SELF, where, problems));
            }
            case "particle" -> {
                String particle = plain != null ? plain : text(map.get("particle"));
                if (particle == null) {
                    problems.add(where + "needs the particle, e.g. flame");
                    return null;
                }
                return new Action.Particle(particle.toLowerCase(Locale.ROOT),
                        (int) number(map, "count", 10, 1, 500, where, problems),
                        number(map, "spread", 0.3, 0, 10, where, problems), target(map.get("at"), Target.SELF, where, problems));
            }
            case "command", "console-command", "console" -> {
                String command = plain != null ? plain : text(map.get("run"));
                if (command == null) {
                    problems.add(where + "needs the command to run");
                    return null;
                }
                boolean console = !kind.equals("command") || "console".equalsIgnoreCase(text(map.get("as")));
                return new Action.Command(command, console);
            }
            case "effect", "potion" -> {
                PotionSpec potion = potion(value, where, problems);
                return potion == null ? null : new Action.Effect(potion, target(map.get("target"), Target.SELF, where, problems));
            }
            case "damage" -> {
                double amount = value instanceof Number number ? number.doubleValue() : number(map, "amount", -1, 0, 1000, where, problems);
                if (!(amount > 0 && amount <= 1000)) {
                    problems.add(where + "needs an amount from 0 to 1000 (half hearts)");
                    return null;
                }
                return new Action.Damage(amount, target(map.get("target"), Target.TARGET, where, problems));
            }
            case "heal" -> {
                double amount = value instanceof Number number ? number.doubleValue() : number(map, "amount", -1, 0, 1000, where, problems);
                if (!(amount > 0 && amount <= 1000)) {
                    problems.add(where + "needs an amount from 0 to 1000 (half hearts)");
                    return null;
                }
                return new Action.Heal(amount, target(map.get("target"), Target.SELF, where, problems));
            }
            case "feed" -> {
                int food = value instanceof Number number ? number.intValue() : (int) number(map, "amount", -1, 1, 20, where, problems);
                if (food < 1 || food > 20) {
                    problems.add(where + "needs an amount from 1 to 20");
                    return null;
                }
                return new Action.Feed(food);
            }
            case "projectile", "shoot" -> {
                String type = plain != null ? plain : text(map.get("type"));
                if (type == null) {
                    problems.add(where + "needs the projectile: arrow, snowball, fireball, small_fireball, wither_skull,"
                            + " egg, ender_pearl, trident, wind_charge");
                    return null;
                }
                return new Action.Projectile(type.toLowerCase(Locale.ROOT), number(map, "speed", 1.6, 0.1, 5, where, problems));
            }
            case "lightning" -> {
                Target at = plain != null ? target(plain, Target.TARGET, where, problems)
                        : target(map.get("at"), Target.TARGET, where, problems);
                boolean harmless = Boolean.TRUE.equals(map.get("harmless"));
                return new Action.Lightning(at, harmless);
            }
            case "launch", "dash", "push" -> {
                return new Action.Launch(number(map, "forward", 1.2, -5, 5, where, problems),
                        number(map, "up", 0.4, -5, 5, where, problems), target(map.get("target"), Target.SELF, where, problems));
            }
            case "give", "give-item" -> {
                String raw = plain != null ? plain : text(map.get("item"));
                if (raw == null || raw.isBlank()) {
                    problems.add(where + "needs the item: demo:ruby, or diamond; \"demo:ruby 3\" for three");
                    return null;
                }
                String[] parts = raw.trim().split("\\s+");
                int amount = (int) number(map, "amount", 1, 1, 6400, where, problems);
                if (parts.length == 2) {
                    try {
                        amount = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException wrong) {
                        problems.add(where + "'" + raw + "' - the amount after the item should be a whole number");
                        return null;
                    }
                }
                if (parts.length > 2 || amount < 1 || amount > 6400) {
                    problems.add(where + "'" + raw + "' should be an item and an amount from 1 to 6400");
                    return null;
                }
                return new Action.Give(parts[0].toLowerCase(Locale.ROOT), amount);
            }
            case "take-held", "consume-item", "use-up" -> {
                int amount = value instanceof Number number ? number.intValue() : (int) number(map, "amount", 1, 1, 64, where, problems);
                return new Action.TakeHeld(Math.max(1, Math.min(64, amount)));
            }
            case "damage-item", "durability" -> {
                int amount = value instanceof Number number ? number.intValue() : (int) number(map, "amount", 1, 1, 10000, where, problems);
                return new Action.DamageItem(Math.max(1, Math.min(10000, amount)));
            }
            case "experience", "xp", "exp" -> {
                int amount = value instanceof Number number ? number.intValue() : (int) number(map, "amount", -1, 1, 100000, where, problems);
                if (amount < 1 || amount > 100000) {
                    problems.add(where + "needs an amount from 1 to 100000");
                    return null;
                }
                return new Action.Experience(amount);
            }
            case "wait", "delay" -> {
                int ticks = value instanceof Number number ? number.intValue() : (int) number(map, "ticks", -1, 1, MAX_WAIT_TICKS, where, problems);
                if (ticks < 1 || ticks > MAX_WAIT_TICKS) {
                    problems.add(where + "needs a number of ticks from 1 to " + MAX_WAIT_TICKS + " (20 a second)");
                    return null;
                }
                return new Action.Wait(ticks);
            }
            default -> {
                problems.add(where.substring(0, where.length() - kind.length() - 2) + "unknown action '" + kind
                        + "' (message, actionbar, title, sound, particle, command, console-command, effect, damage,"
                        + " heal, feed, projectile, lightning, launch, give, take-held, damage-item, experience, wait)");
                return null;
            }
        }
    }

    private static Action.Channel channel(@Nullable Object value, String where, List<String> problems) {
        String to = text(value);
        if (to == null) {
            return Action.Channel.CHAT;
        }
        return switch (to.toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "chat" -> Action.Channel.CHAT;
            case "actionbar", "action-bar" -> Action.Channel.ACTION_BAR;
            case "title" -> Action.Channel.TITLE;
            default -> {
                problems.add(where + "'to' should be chat, actionbar or title");
                yield Action.Channel.CHAT;
            }
        };
    }

    private static Target target(@Nullable Object value, Target fallback, String where, List<String> problems) {
        String raw = text(value);
        if (raw == null) {
            return fallback;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "self", "player", "me", "user" -> Target.SELF;
            case "target", "victim", "entity", "block", "other" -> Target.TARGET;
            default -> {
                problems.add(where + "'" + raw + "' is not a target - self or target");
                yield fallback;
            }
        };
    }

    // ---------------------------------------------------------------- passive

    private static Passive passive(Object value, String where, List<String> problems) {
        if (!(value instanceof Map<?, ?> section)) {
            problems.add(where + "should be a section (big-mining, auto-smelt, soulbound, lifesteal, held-effects,"
                    + " worn-effects, durability)");
            return Passive.NONE;
        }
        for (Object key : section.keySet()) {
            String name = String.valueOf(key);
            if (!Set.of("big-mining", "mining-depth", "auto-smelt", "soulbound", "lifesteal", "held-effects",
                    "worn-effects", "durability").contains(name)) {
                problems.add(where + "unknown mechanic '" + name + "' (big-mining, mining-depth, auto-smelt,"
                        + " soulbound, lifesteal, held-effects, worn-effects, durability)");
            }
        }
        int bigMining = 0;
        Object mining = section.get("big-mining");
        if (mining instanceof Boolean flag) {
            bigMining = flag ? 1 : 0;
        } else if (mining != null) {
            bigMining = (int) number(section, "big-mining", 0, 0, Passive.MAX_BIG_MINING, where, problems);
        }
        return new Passive(bigMining,
                (int) number(section, "mining-depth", 0, 0, Passive.MAX_DEPTH, where, problems),
                flag(section, "auto-smelt", where, problems), flag(section, "soulbound", where, problems),
                number(section, "lifesteal", 0, 0, 1, where, problems),
                potions(section.get("held-effects"), where + "held-effects: ", problems),
                potions(section.get("worn-effects"), where + "worn-effects: ", problems),
                (int) number(section, "durability", 0, 0, 1_000_000, where, problems));
    }

    private static List<PotionSpec> potions(@Nullable Object value, String where, List<String> problems) {
        if (value == null) {
            return List.of();
        }
        List<?> list = value instanceof List<?> l ? l : List.of(value);
        List<PotionSpec> out = new ArrayList<>();
        for (Object entry : list) {
            PotionSpec potion = potion(entry, where, problems);
            if (potion != null) {
                out.add(potion);
            }
        }
        return out;
    }

    /** {@code speed}, {@code "speed 2"} (level), or {@code {effect: speed, level: 2, seconds: 5}}. */
    private static @Nullable PotionSpec potion(Object value, String where, List<String> problems) {
        if (value instanceof Map<?, ?> map) {
            String effect = text(map.get("effect"));
            if (effect == null) {
                effect = text(map.get("type"));
            }
            if (effect == null) {
                problems.add(where + "needs the effect, e.g. speed");
                return null;
            }
            return new PotionSpec(effect.toLowerCase(Locale.ROOT), (int) number(map, "level", 1, 1, 255, where, problems),
                    number(map, "seconds", 5, 0.05, 86_400, where, problems));
        }
        String raw = text(value);
        if (raw == null || raw.isBlank()) {
            problems.add(where + "needs the effect, e.g. speed");
            return null;
        }
        String[] parts = raw.trim().split("\\s+");
        int level = 1;
        if (parts.length >= 2) {
            try {
                level = Integer.parseInt(parts[1]);
            } catch (NumberFormatException wrong) {
                problems.add(where + "'" + raw + "' - the level after the effect should be a whole number");
            }
        }
        return new PotionSpec(parts[0].toLowerCase(Locale.ROOT), Math.max(1, Math.min(255, level)), 5);
    }

    // ---------------------------------------------------------------- helpers

    private static @Nullable String text(@Nullable Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static Set<String> names(@Nullable Object value, boolean vanilla) {
        Set<String> out = new LinkedHashSet<>();
        if (value == null) {
            return out;
        }
        List<?> list = value instanceof List<?> l ? l : List.of(value);
        for (Object entry : list) {
            String name = text(entry);
            if (name != null) {
                name = name.trim().toLowerCase(Locale.ROOT);
                out.add(vanilla && name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name);
            }
        }
        return out;
    }

    private static boolean flag(Map<?, ?> section, String key, String where, List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        problems.add(where + "'" + key + "' should be true or false");
        return false;
    }

    private static double number(Map<?, ?> section, String key, double fallback, double min, double max, String where,
                                 List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() >= min
                && number.doubleValue() <= max) {
            return number.doubleValue();
        }
        problems.add(where + "'" + key + "' should be a number from " + plain(min) + " to " + plain(max)
                + (fallback >= min ? "; using " + plain(fallback) : ""));
        return fallback;
    }

    private static String plain(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15 ? String.valueOf((long) value) : String.valueOf(value);
    }
}
