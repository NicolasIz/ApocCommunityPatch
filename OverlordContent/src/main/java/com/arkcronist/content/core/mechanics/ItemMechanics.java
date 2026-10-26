package com.arkcronist.content.core.mechanics;

import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 5.4: what an item does besides being held - {@code events:}, things that happen when the player
 * does something with it, and {@code mechanics:}, what it does all along.
 *
 * <p>Everything here is as written: sound, particle and effect names are checked against the server
 * when the build is published, not here.</p>
 *
 * @param events  per trigger, the reactions it sets off, each with its own conditions; tried in order
 * @param passive what the item does without being used
 */
public record ItemMechanics(Map<Trigger, List<Reaction>> events, Passive passive) {

    public ItemMechanics {
        Map<Trigger, List<Reaction>> copy = new EnumMap<>(Trigger.class);
        events.forEach((trigger, reactions) -> copy.put(trigger, List.copyOf(reactions)));
        events = java.util.Collections.unmodifiableMap(copy);
    }

    public List<Reaction> on(Trigger trigger) {
        return events.getOrDefault(trigger, List.of());
    }

    public boolean isEmpty() {
        return events.isEmpty() && passive.isEmpty();
    }

    /** What sets reactions off, as written under {@code events:}. */
    public enum Trigger {
        /** A right click with it in the main hand: in the air, on a block or on an entity. */
        RIGHT_CLICK("right-click"),
        /** A left click with it in the main hand, at the air or a block. */
        LEFT_CLICK("left-click"),
        /** Hitting an entity with it, in melee; the entity is the target. */
        HIT_ENTITY("hit-entity"),
        /** Killing an entity with it in the main hand; the entity is the target. */
        KILL("kill"),
        /** Breaking a block with it; the block is the target. */
        BREAK_BLOCK("break-block"),
        /** Eating or drinking it. */
        CONSUME("consume"),
        /** Putting it on, as armour. */
        EQUIP("equip"),
        /** Taking it off. */
        UNEQUIP("unequip"),
        /** Shooting a bow or crossbow that is this item; the arrow is the target. */
        SHOOT("shoot"),
        /** Being hurt while holding it or wearing it; whoever hurt the player is the target. */
        DAMAGED("damaged");

        private final String yamlName;

        Trigger(String yamlName) {
            this.yamlName = yamlName;
        }

        public String yamlName() {
            return yamlName;
        }

        public static Optional<Trigger> parse(String raw) {
            String name = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            for (Trigger trigger : values()) {
                if (trigger.yamlName.equals(name)) {
                    return Optional.of(trigger);
                }
            }
            return switch (name) {
                case "right-click-air", "right-click-block", "use", "interact" -> Optional.of(RIGHT_CLICK);
                case "left-click-air", "left-click-block", "swing" -> Optional.of(LEFT_CLICK);
                case "hit", "attack", "attack-entity" -> Optional.of(HIT_ENTITY);
                case "kill-entity", "kill-mob" -> Optional.of(KILL);
                case "break", "mine", "block-break" -> Optional.of(BREAK_BLOCK);
                case "eat", "drink" -> Optional.of(CONSUME);
                case "wear" -> Optional.of(EQUIP);
                case "shoot-bow", "bow-shoot" -> Optional.of(SHOOT);
                case "hurt", "damage", "take-damage" -> Optional.of(DAMAGED);
                default -> Optional.empty();
            };
        }
    }

    /** Who or where an action is done to. */
    public enum Target {
        /** The player using the item. */
        SELF,
        /**
         * What the trigger is about: the entity hit, killed or hurting the player; the block broken;
         * on a click, the entity or block looked at.
         */
        TARGET
    }

    /**
     * One reaction to a trigger.
     *
     * @param actions         done in order; a {@link Action.Wait} holds the rest back
     * @param conditions      all must hold, or nothing is done
     * @param cooldownSeconds after it is done, how long before this reaction can be done again by
     *                        the same player; 0 for none
     * @param cooldownMessage told on the action bar when it is tried too soon, {@code %seconds%} the
     *                        time left; null to say nothing
     */
    public record Reaction(List<Action> actions, Conditions conditions, double cooldownSeconds,
                           @Nullable String cooldownMessage) {

        public Reaction {
            actions = List.copyOf(actions);
        }
    }

    /**
     * When a reaction is done.
     *
     * @param chance     0-1, tried each time
     * @param sneaking   true only while sneaking, false only while not; null either way
     * @param permission the player needs it; null for anyone
     * @param worlds     world names, lower case; empty for every world
     * @param targets    entity types the target must be ({@code zombie}); empty for any
     * @param blocks     block types the target block must be ({@code stone}); empty for any
     */
    public record Conditions(double chance, @Nullable Boolean sneaking, @Nullable String permission, Set<String> worlds,
                             Set<String> targets, Set<String> blocks) {

        public static final Conditions ALWAYS = new Conditions(1, null, null, Set.of(), Set.of(), Set.of());

        public Conditions {
            worlds = Set.copyOf(worlds);
            targets = Set.copyOf(targets);
            blocks = Set.copyOf(blocks);
        }
    }

    /** A potion effect, as written: {@code speed}, level 1 and up, for so many seconds. */
    public record PotionSpec(String effect, int level, double seconds) {
    }

    /** One thing a reaction does. */
    public sealed interface Action {

        /** {@code message:} - said to the player, in chat, on the action bar or as a title. */
        record Message(String text, Channel channel, @Nullable String subtitle) implements Action {
        }

        /** Where a message is shown. */
        enum Channel { CHAT, ACTION_BAR, TITLE }

        /** {@code sound:} - played at the player or the target, for everyone near. */
        record Sound(String sound, float volume, float pitch, Target at) implements Action {
        }

        /** {@code particle:} - shown at the player or the target. */
        record Particle(String particle, int count, double spread, Target at) implements Action {
        }

        /** {@code command:} - run by the player, or by the console. */
        record Command(String command, boolean console) implements Action {
        }

        /** {@code effect:} - a potion effect given to the player or the target. */
        record Effect(PotionSpec potion, Target target) implements Action {
        }

        /** {@code damage:} - hearts taken from the target (or the player), as if the player hit it. */
        record Damage(double amount, Target target) implements Action {
        }

        /** {@code heal:} - health given back, in half hearts. */
        record Heal(double amount, Target target) implements Action {
        }

        /** {@code feed:} - hunger given back, in half drumsticks, and as much saturation. */
        record Feed(int food) implements Action {
        }

        /** {@code projectile:} - shot from the player's eyes where they look. */
        record Projectile(String type, double speed) implements Action {
        }

        /** {@code lightning:} - at the target, or the player; harmless lightning only shows and sounds. */
        record Lightning(Target at, boolean harmless) implements Action {
        }

        /** {@code launch:} - the player (or target) pushed along where the player looks, and up. */
        record Launch(double forward, double up, Target target) implements Action {
        }

        /** {@code give:} - an item, custom ({@code demo:ruby}) or vanilla ({@code diamond}). */
        record Give(String item, int amount) implements Action {
        }

        /** {@code take-held:} - so many of the item used up. */
        record TakeHeld(int amount) implements Action {
        }

        /** {@code damage-item:} - the item worn down, as a tool is by use. */
        record DamageItem(int amount) implements Action {
        }

        /** {@code experience:} - experience points given. */
        record Experience(int amount) implements Action {
        }

        /** {@code wait:} - the actions after it are done this many ticks later (20 a second). */
        record Wait(int ticks) implements Action {
        }
    }

    /**
     * What the item does without being used.
     *
     * @param bigMining   blocks around the one broken that break with it: 1 is 3x3, 2 is 5x5; 0 for none
     * @param miningDepth how many blocks deep beyond the one broken; 0 for a flat area
     * @param autoSmelt   what it breaks drops smelted, as a furnace would make it
     * @param soulbound   kept by the player when they die
     * @param lifesteal   the share of the damage it does given back to the player as health, 0-1
     * @param held        effects on the player while it is in the main hand
     * @param worn        effects on the player while it is worn as armour
     * @param durability  how many uses it has, for an item that has none of its own; 0 to leave it as is
     */
    public record Passive(int bigMining, int miningDepth, boolean autoSmelt, boolean soulbound, double lifesteal,
                          List<PotionSpec> held, List<PotionSpec> worn, int durability) {

        public static final Passive NONE = new Passive(0, 0, false, false, 0, List.of(), List.of(), 0);

        /** The widest area mined at once: 5x5. */
        public static final int MAX_BIG_MINING = 2;
        public static final int MAX_DEPTH = 3;

        public Passive {
            held = List.copyOf(held);
            worn = List.copyOf(worn);
        }

        public boolean isEmpty() {
            return equals(NONE);
        }
    }
}
