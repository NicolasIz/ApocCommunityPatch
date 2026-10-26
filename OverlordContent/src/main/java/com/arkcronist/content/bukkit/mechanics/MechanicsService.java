package com.arkcronist.content.bukkit.mechanics;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.bukkit.protection.Interaction;
import com.arkcronist.content.bukkit.protection.Protection;
import com.arkcronist.content.core.definition.CommandTemplate;
import com.arkcronist.content.core.mechanics.ItemMechanics;
import com.arkcronist.content.core.mechanics.ItemMechanics.Action;
import com.arkcronist.content.core.mechanics.ItemMechanics.Conditions;
import com.arkcronist.content.core.mechanics.ItemMechanics.PotionSpec;
import com.arkcronist.content.core.mechanics.ItemMechanics.Reaction;
import com.arkcronist.content.core.mechanics.ItemMechanics.Target;
import com.arkcronist.content.core.mechanics.ItemMechanics.Trigger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * 5.4: does what items' {@code events:} and {@code mechanics:} say - the reactions a trigger sets off,
 * and the effects an item gives while held or worn.
 *
 * <p>Whatever an action does to someone else - damage, an effect, a push, lightning on them - is
 * asked of the protection plugins first, as an attack by the player; lightning that can set fire is
 * asked as building where it strikes, and only shows when that is refused. Damage is dealt as the
 * player's, so plugins that decide on PvP decide on it too.</p>
 *
 * <p>Main thread only.</p>
 */
public final class MechanicsService {

    /** How far a click's target - the entity or block looked at - is looked for. */
    public static final double REACH = 32;
    /** How long a held or worn effect lasts, refreshed every second: past the ten seconds night vision flickers in. */
    private static final int PASSIVE_TICKS = 220;

    private final Plugin plugin;
    private final ItemRegistry registry;
    private final ItemFactory items;
    private final Protection protection;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private volatile Map<String, ItemMechanics> byItem = Map.of();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    /** Who is taking damage from an action right now: their hit is not a trigger again. */
    private final Set<UUID> dealing = new HashSet<>();
    /** The effects given for what each player holds and wears, to take off when they stop. */
    private final Map<UUID, Set<PotionEffectType>> passiveGiven = new HashMap<>();
    private @Nullable BukkitTask passiveTask;

    public MechanicsService(Plugin plugin, ItemRegistry registry, ItemFactory items, Protection protection) {
        this.plugin = plugin;
        this.registry = registry;
        this.items = items;
        this.protection = protection;
        this.logger = plugin.getLogger();
    }

    // ---------------------------------------------------------------- the build

    /** The mechanics of this build's items, their names checked against the server. */
    public void publish(ItemRegistry registry) {
        Map<String, ItemMechanics> next = new HashMap<>();
        boolean passive = false;
        for (CustomItem item : registry.all()) {
            ItemMechanics mechanics = item.definition().mechanics();
            if (mechanics == null) {
                continue;
            }
            next.put(item.id(), mechanics);
            check(item.id(), mechanics);
            passive |= !mechanics.passive().held().isEmpty() || !mechanics.passive().worn().isEmpty();
        }
        byItem = Map.copyOf(next);
        if (passive && passiveTask == null) {
            passiveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::passiveEffects, 20L, 20L);
        } else if (!passive && passiveTask != null) {
            passiveTask.cancel();
            passiveTask = null;
        }
        if (!next.isEmpty()) {
            logger.info(next.size() + " item(s) with events or mechanics of their own.");
        }
    }

    /** Names the server does not know, told once each: the rest of the item still works. */
    private void check(String id, ItemMechanics mechanics) {
        List<String> problems = new ArrayList<>();
        mechanics.events().forEach((trigger, reactions) -> reactions.forEach(reaction -> reaction.actions().forEach(action -> {
            switch (action) {
                case Action.Effect effect when effect(effect.potion().effect()) == null ->
                        problems.add("unknown effect '" + effect.potion().effect() + "'");
                case Action.Particle particle when particle(particle.particle()) == null ->
                        problems.add("unknown particle '" + particle.particle() + "'");
                case Action.Projectile projectile when ProjectileKinds.of(projectile.type()) == null ->
                        problems.add("unknown projectile '" + projectile.type() + "' (" + ProjectileKinds.NAMES + ")");
                case Action.Give give when registry.get(give.item()).isEmpty() && vanilla(give.item()) == null ->
                        problems.add("no custom or vanilla item called '" + give.item() + "' to give");
                default -> {
                }
            }
        })));
        for (PotionSpec potion : mechanics.passive().held()) {
            if (effect(potion.effect()) == null) {
                problems.add("held-effects: unknown effect '" + potion.effect() + "'");
            }
        }
        for (PotionSpec potion : mechanics.passive().worn()) {
            if (effect(potion.effect()) == null) {
                problems.add("worn-effects: unknown effect '" + potion.effect() + "'");
            }
        }
        for (String problem : problems) {
            if (warned.add(id + problem)) {
                logger.warning(id + ": " + problem + " - left out");
            }
        }
    }

    public Optional<ItemMechanics> of(@Nullable ItemStack stack) {
        if (byItem.isEmpty() || stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return items.identify(stack).map(item -> byItem.get(item.id()));
    }

    /** The item's id and mechanics, when it has any. */
    public Optional<Map.Entry<String, ItemMechanics>> withId(@Nullable ItemStack stack) {
        if (byItem.isEmpty() || stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return items.identify(stack).flatMap(item -> {
            ItemMechanics mechanics = byItem.get(item.id());
            return mechanics == null ? Optional.empty() : Optional.of(Map.entry(item.id(), mechanics));
        });
    }

    public boolean any() {
        return !byItem.isEmpty();
    }

    // ---------------------------------------------------------------- triggers

    /**
     * What a trigger is about.
     *
     * @param slot   where the item is: what take-held and damage-item act on
     * @param entity the entity hit, killed, looked at or hurting the player
     * @param block  the block broken or looked at
     */
    public record Context(EquipmentSlot slot, @Nullable Entity entity, @Nullable Block block) {

        /** Where the target is: the entity's middle, else the block's. */
        @Nullable Location at() {
            if (entity != null) {
                return entity.getLocation().add(0, entity.getHeight() / 2, 0);
            }
            return block == null ? null : block.getLocation().add(0.5, 0.5, 0.5);
        }
    }

    /**
     * Sets off the reactions {@code trigger} has on the item in {@code context.slot()} of {@code player}.
     *
     * @return whether any was done
     */
    public boolean fire(Player player, String itemId, ItemMechanics mechanics, Trigger trigger, Context context) {
        List<Reaction> reactions = mechanics.on(trigger);
        boolean any = false;
        for (int i = 0; i < reactions.size(); i++) {
            Reaction reaction = reactions.get(i);
            if (!holds(reaction.conditions(), player, context)) {
                continue;
            }
            String key = player.getUniqueId() + "|" + itemId + "|" + trigger + "|" + i;
            long now = System.currentTimeMillis();
            Long until = cooldowns.get(key);
            if (until != null && until > now) {
                if (reaction.cooldownMessage() != null) {
                    String seconds = String.valueOf((long) Math.ceil((until - now) / 1000.0));
                    player.sendActionBar(text(reaction.cooldownMessage().replace("%seconds%", seconds), player, itemId, context));
                }
                continue;
            }
            if (reaction.cooldownSeconds() > 0) {
                cooldowns.put(key, now + (long) (reaction.cooldownSeconds() * 1000));
            }
            run(player, itemId, reaction.actions(), 0, context);
            any = true;
        }
        return any;
    }

    private boolean holds(Conditions conditions, Player player, Context context) {
        if (conditions.chance() < 1 && ThreadLocalRandom.current().nextDouble() >= conditions.chance()) {
            return false;
        }
        if (conditions.sneaking() != null && conditions.sneaking() != player.isSneaking()) {
            return false;
        }
        if (conditions.permission() != null && !player.hasPermission(conditions.permission())) {
            return false;
        }
        if (!conditions.worlds().isEmpty() && !conditions.worlds().contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (!conditions.targets().isEmpty() && (context.entity() == null
                || !conditions.targets().contains(context.entity().getType().getKey().getKey()))) {
            return false;
        }
        return conditions.blocks().isEmpty() || context.block() != null
                && conditions.blocks().contains(context.block().getType().getKey().getKey());
    }

    /** The actions from {@code from} on; a wait hands the rest to a later tick. */
    private void run(Player player, String itemId, List<Action> actions, int from, Context context) {
        for (int i = from; i < actions.size(); i++) {
            Action action = actions.get(i);
            if (action instanceof Action.Wait wait) {
                int next = i + 1;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && !player.isDead()) {
                        Entity entity = context.entity() != null && context.entity().isValid() ? context.entity() : null;
                        run(player, itemId, actions, next, new Context(context.slot(), entity, context.block()));
                    }
                }, wait.ticks());
                return;
            }
            try {
                act(player, itemId, action, context);
            } catch (RuntimeException failed) {
                if (warned.add(itemId + action.getClass().getSimpleName() + failed.getClass())) {
                    logger.warning(itemId + ": an action failed (" + action + "): " + failed);
                }
            }
        }
    }

    // ---------------------------------------------------------------- actions

    private void act(Player player, String itemId, Action action, Context context) {
        switch (action) {
            case Action.Message message -> {
                Component text = text(message.text(), player, itemId, context);
                switch (message.channel()) {
                    case CHAT -> player.sendMessage(text);
                    case ACTION_BAR -> player.sendActionBar(text);
                    case TITLE -> player.showTitle(Title.title(text, message.subtitle() == null ? Component.empty()
                            : text(message.subtitle(), player, itemId, context)));
                }
            }
            case Action.Sound sound -> {
                Location at = where(sound.at(), player, context);
                if (at != null) {
                    at.getWorld().playSound(at, sound.sound(), SoundCategory.PLAYERS, sound.volume(), sound.pitch());
                }
            }
            case Action.Particle shown -> {
                Location at = where(shown.at(), player, context);
                Particle particle = particle(shown.particle());
                if (at != null && particle != null) {
                    spawn(at, particle, shown.count(), shown.spread(), player, context);
                }
            }
            case Action.Command command -> runCommand(player, itemId, command, context);
            case Action.Effect effect -> {
                LivingEntity target = living(effect.target(), player, context);
                PotionEffectType type = effect(effect.potion().effect());
                if (target != null && type != null && mayTouch(player, target)) {
                    target.addPotionEffect(new PotionEffect(type, Math.max(1, (int) Math.round(effect.potion().seconds() * 20)),
                            effect.potion().level() - 1));
                }
            }
            case Action.Damage damage -> {
                LivingEntity target = living(damage.target(), player, context);
                if (target == null || target.isDead()) {
                    return;
                }
                if (target == player) {
                    player.damage(damage.amount());
                } else if (protection.allowsAttack(player, target)) {
                    dealing.add(player.getUniqueId());
                    try {
                        target.damage(damage.amount(), player);
                    } finally {
                        dealing.remove(player.getUniqueId());
                    }
                }
            }
            case Action.Heal heal -> {
                LivingEntity target = living(heal.target(), player, context);
                if (target != null && !target.isDead() && (target == player || mayTouch(player, target))) {
                    AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
                    double top = max == null ? 20 : max.getValue();
                    target.setHealth(Math.min(top, target.getHealth() + heal.amount()));
                }
            }
            case Action.Feed feed -> {
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + feed.food()));
                player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + feed.food()));
            }
            case Action.Projectile shot -> {
                Class<? extends Projectile> kind = ProjectileKinds.of(shot.type());
                if (kind != null) {
                    Vector velocity = player.getEyeLocation().getDirection().multiply(shot.speed());
                    Projectile projectile = player.launchProjectile(kind, velocity);
                    if (projectile instanceof AbstractArrow arrow) {
                        // Not a free arrow to pick up.
                        arrow.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
                    }
                }
            }
            case Action.Lightning lightning -> {
                Location at = where(lightning.at(), player, context);
                if (at == null) {
                    return;
                }
                boolean harmless = lightning.harmless()
                        || context.entity() != null && lightning.at() == Target.TARGET && !mayTouch(player, context.entity())
                        || !protection.allows(player, at.getBlock(), Interaction.BUILD);
                if (harmless) {
                    at.getWorld().strikeLightningEffect(at);
                } else {
                    at.getWorld().strikeLightning(at);
                }
            }
            case Action.Launch launch -> {
                Entity target = launch.target() == Target.SELF ? player : context.entity();
                if (target == null || target != player && !mayTouch(player, target)) {
                    return;
                }
                Vector along = target == player ? player.getLocation().getDirection()
                        : target.getLocation().toVector().subtract(player.getLocation().toVector());
                along.setY(0);
                if (along.lengthSquared() < 1e-6) {
                    along = player.getLocation().getDirection().setY(0);
                }
                Vector push = along.lengthSquared() < 1e-6 ? new Vector() : along.normalize().multiply(launch.forward());
                push.setY(launch.up());
                target.setVelocity(push);
            }
            case Action.Give give -> give(player, give.item(), give.amount());
            case Action.TakeHeld take -> {
                ItemStack held = player.getInventory().getItem(context.slot());
                if (!held.isEmpty()) {
                    held.setAmount(Math.max(0, held.getAmount() - take.amount()));
                    player.getInventory().setItem(context.slot(), held.getAmount() > 0 ? held : null);
                }
            }
            case Action.DamageItem wear -> {
                if (!player.getInventory().getItem(context.slot()).isEmpty()) {
                    player.damageItemStack(context.slot(), wear.amount());
                }
            }
            case Action.Experience experience -> player.giveExp(experience.amount());
            case Action.Wait ignored -> {
            }
        }
    }

    /** Whether the player may do something to {@code entity}: as an attack, to anyone but themselves. */
    private boolean mayTouch(Player player, Entity entity) {
        return entity == player || protection.allowsAttack(player, entity);
    }

    private @Nullable LivingEntity living(Target target, Player player, Context context) {
        if (target == Target.SELF) {
            return player;
        }
        return context.entity() instanceof LivingEntity living ? living : null;
    }

    private @Nullable Location where(Target target, Player player, Context context) {
        if (target == Target.SELF) {
            return player.getLocation().add(0, 1, 0);
        }
        return context.at();
    }

    private void spawn(Location at, Particle particle, int count, double spread, Player player, Context context) {
        Class<?> data = particle.getDataType();
        Object extra = null;
        if (data == Particle.DustOptions.class) {
            extra = new Particle.DustOptions(Color.fromRGB(0xD63048), 1.2f);
        } else if (data == BlockData.class) {
            extra = context.block() != null ? context.block().getBlockData() : Material.STONE.createBlockData();
        } else if (data == ItemStack.class) {
            ItemStack held = player.getInventory().getItem(context.slot());
            extra = held.isEmpty() ? new ItemStack(Material.STONE) : held;
        } else if (data == Color.class) {
            extra = Color.fromRGB(0xD63048);
        } else if (data != Void.class) {
            // A particle that needs more than this can say: shown as a plain one.
            particle = Particle.ENCHANTED_HIT;
        }
        at.getWorld().spawnParticle(particle, at, count, spread, spread, spread, 0.02, extra);
    }

    private void runCommand(Player player, String itemId, Action.Command command, Context context) {
        String filled;
        try {
            filled = CommandTemplate.fill(command.command(), values(player, itemId, context));
        } catch (IllegalArgumentException unsafe) {
            if (warned.add(itemId + unsafe.getMessage())) {
                logger.warning(itemId + ": a command was not run for " + player.getName() + " - " + unsafe.getMessage());
            }
            return;
        }
        if (command.console()) {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), filled);
        } else {
            player.performCommand(filled);
        }
    }

    /** What {@code %player%}, {@code %target%} and the rest stand for. */
    private Map<String, String> values(Player player, String itemId, Context context) {
        Map<String, String> values = new HashMap<>();
        Location here = player.getLocation();
        values.put("player", player.getName());
        values.put("uuid", player.getUniqueId().toString());
        values.put("world", player.getWorld().getName());
        values.put("x", String.valueOf(here.getBlockX()));
        values.put("y", String.valueOf(here.getBlockY()));
        values.put("z", String.valueOf(here.getBlockZ()));
        values.put("item", itemId);
        Location at = context.at();
        if (context.entity() != null) {
            values.put("target", context.entity() instanceof Player other ? other.getName()
                    : context.entity().getType().getKey().getKey());
        } else if (context.block() != null) {
            values.put("target", context.block().getType().getKey().getKey());
        }
        if (at != null) {
            values.put("tx", String.valueOf(at.getBlockX()));
            values.put("ty", String.valueOf(at.getBlockY()));
            values.put("tz", String.valueOf(at.getBlockZ()));
        }
        return values;
    }

    private Component text(String source, Player player, String itemId, Context context) {
        String filled = source;
        for (Map.Entry<String, String> value : values(player, itemId, context).entrySet()) {
            filled = filled.replace("%" + value.getKey() + "%", value.getValue());
        }
        return miniMessage.deserialize(filled);
    }

    private void give(Player player, String id, int amount) {
        Optional<CustomItem> custom = registry.get(id);
        ItemStack one;
        if (custom.isPresent()) {
            one = items.create(custom.get(), 1);
        } else {
            Material material = vanilla(id);
            if (material == null) {
                return;
            }
            one = new ItemStack(material);
        }
        int left = amount;
        int stack = Math.max(1, one.getMaxStackSize());
        while (left > 0) {
            ItemStack part = one.clone();
            part.setAmount(Math.min(stack, left));
            left -= part.getAmount();
            player.getInventory().addItem(part).values()
                    .forEach(over -> player.getWorld().dropItemNaturally(player.getLocation(), over));
        }
    }

    private static @Nullable Material vanilla(String id) {
        Material material = Material.matchMaterial(id);
        return material != null && material.isItem() && !material.isAir() ? material : null;
    }

    static @Nullable PotionEffectType effect(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.EFFECT.get(key);
    }

    static @Nullable Particle particle(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.PARTICLE_TYPE.get(key);
    }

    // ---------------------------------------------------------------- passive

    /** Whether the player's hit is an action's damage, which sets nothing off again. */
    public boolean dealing(Player player) {
        return dealing.contains(player.getUniqueId());
    }

    /** Held and worn effects, refreshed every second; those no longer held or worn taken off. */
    private void passiveEffects() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Map<PotionEffectType, Integer> wanted = new HashMap<>();
            of(player.getInventory().getItemInMainHand()).ifPresent(mechanics -> add(wanted, mechanics.passive().held()));
            for (ItemStack worn : player.getInventory().getArmorContents()) {
                of(worn).ifPresent(mechanics -> add(wanted, mechanics.passive().worn()));
            }
            Set<PotionEffectType> before = passiveGiven.getOrDefault(player.getUniqueId(), Set.of());
            for (PotionEffectType type : before) {
                if (!wanted.containsKey(type)) {
                    PotionEffect current = player.getPotionEffect(type);
                    // Only ours: ambient, without particles, and no longer than we give it.
                    if (current != null && current.isAmbient() && !current.hasParticles()
                            && current.getDuration() <= PASSIVE_TICKS) {
                        player.removePotionEffect(type);
                    }
                }
            }
            wanted.forEach((type, amplifier) -> {
                PotionEffect current = player.getPotionEffect(type);
                // A stronger one from elsewhere - a potion - is left as it is.
                if (current == null || current.getAmplifier() < amplifier
                        || current.getAmplifier() == amplifier && current.getDuration() <= PASSIVE_TICKS) {
                    player.addPotionEffect(new PotionEffect(type, PASSIVE_TICKS, amplifier, true, false, true));
                }
            });
            if (wanted.isEmpty()) {
                passiveGiven.remove(player.getUniqueId());
            } else {
                passiveGiven.put(player.getUniqueId(), Set.copyOf(wanted.keySet()));
            }
        }
    }

    private static void add(Map<PotionEffectType, Integer> wanted, List<PotionSpec> potions) {
        for (PotionSpec potion : potions) {
            PotionEffectType type = effect(potion.effect());
            if (type != null) {
                wanted.merge(type, potion.level() - 1, Math::max);
            }
        }
    }

    /** A player gone: their cooldowns and effects forgotten. */
    public void forget(Player player) {
        String prefix = player.getUniqueId() + "|";
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
        passiveGiven.remove(player.getUniqueId());
    }

    public void shutdown() {
        if (passiveTask != null) {
            passiveTask.cancel();
            passiveTask = null;
        }
    }
}
