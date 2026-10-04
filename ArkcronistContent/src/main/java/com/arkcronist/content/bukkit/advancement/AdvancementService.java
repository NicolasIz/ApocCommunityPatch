package com.arkcronist.content.bukkit.advancement;

import com.arkcronist.content.core.advancement.AdvancementCompiler;
import com.arkcronist.content.core.advancement.WornSet;
import com.arkcronist.content.core.definition.AdvancementDefinition;
import com.arkcronist.content.core.definition.AdvancementDefinition.Trigger;
import com.arkcronist.content.core.definition.Equipment;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.storage.DatabaseManager;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.destroystokyo.paper.ParticleBuilder;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The plugin's advancements on the server: registering them, awarding what vanilla cannot - a set
 * of armour worn at once - and celebrating each one completed.
 *
 * <p><b>Registering.</b> Paper's {@code UnsafeValues#loadAdvancement} adds an advancement to the
 * running server, tree and all. Two things about it shape this class. It reloads every online
 * player's advancement progress from disk, without saving it first, so the players are saved just
 * before. And it cannot replace or remove one: an advancement whose JSON changed, or that is no
 * longer defined, only goes with a reload of the server's data ({@code /minecraft:reload}), after
 * which the plugin registers its own again. Paper also writes each one into the world's
 * {@code datapacks/bukkit/data/<ns>/advancements/} folder; since 1.21 the game reads
 * {@code advancement/}, so those copies are never loaded and cannot outlive their YAML.</p>
 *
 * <p><b>Main thread</b> for everything that touches players or the server's advancements. The
 * database write and the particle burst - packets to a list of players taken on the main thread -
 * are off it.</p>
 */
public final class AdvancementService {

    /** Crimson: the ruby of the demo's palette. */
    private static final Color CRIMSON = Color.fromRGB(0xB3122E);
    private static final Color DEEP_CRIMSON = Color.fromRGB(0x5E0716);
    private static final Color GLINT = Color.fromRGB(0xFF566A);
    /** Frames of the particle burst, one every two ticks. */
    private static final int BURST_FRAMES = 14;

    private final Plugin plugin;
    private final ItemRegistry items;
    private final ItemFactory factory;
    private final DatabaseManager database;
    private final Logger logger;

    /** What the content defines, parents first. */
    private List<AdvancementCompiler.Compiled> desired = List.of();
    /** What this server has registered, as registered. */
    private final Map<NamespacedKey, String> registered = new LinkedHashMap<>();
    private final Map<NamespacedKey, AdvancementDefinition> definitions = new LinkedHashMap<>();

    public AdvancementService(Plugin plugin, ItemRegistry items, ItemFactory factory, DatabaseManager database) {
        this.plugin = plugin;
        this.items = items;
        this.factory = factory;
        this.database = database;
        this.logger = plugin.getLogger();
    }

    // ---------------------------------------------------------------- registering

    /**
     * Brings the server's advancements in line with the content: new ones are registered at once; a
     * changed or removed one takes a reload of the server's data, which {@link #reregister()} then
     * follows.
     *
     * @return how many are registered afterwards
     */
    public int publish(List<AdvancementCompiler.Compiled> compiled) {
        this.desired = List.copyOf(compiled);
        definitions.clear();
        Map<NamespacedKey, String> wanted = new LinkedHashMap<>();
        for (AdvancementCompiler.Compiled advancement : compiled) {
            NamespacedKey key = key(advancement.key());
            wanted.put(key, advancement.json());
            definitions.put(key, advancement.definition());
        }
        boolean onlyAdded = registered.entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(wanted.get(entry.getKey())));
        if (onlyAdded) {
            register(compiled, true);
        } else {
            logger.info("Advancements changed or were removed: reloading the server's data so they can be"
                    + " registered again (this is /minecraft:reload).");
            Bukkit.reloadData();
            // The reload fires ServerResourcesReloadedEvent, which calls reregister(); in case a
            // listener ran before ours and the event did not reach it, make sure.
            if (registered.isEmpty()) {
                reregister();
            }
        }
        return registered.size();
    }

    /**
     * After the server's data was reloaded, by the plugin or by anyone with {@code /minecraft:reload}:
     * that dropped every advancement added at run time.
     */
    public void reregister() {
        registered.clear();
        // The server saved every player before reloading, and has just read them back.
        register(desired, false);
    }

    private void register(List<AdvancementCompiler.Compiled> compiled, boolean saveFirst) {
        List<AdvancementCompiler.Compiled> missing = new ArrayList<>();
        for (AdvancementCompiler.Compiled advancement : compiled) {
            if (!registered.containsKey(key(advancement.key()))) {
                missing.add(advancement);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        // Registering reloads online players' progress from disk: what they earned since the last
        // save has to be there first.
        if (saveFirst && !Bukkit.getOnlinePlayers().isEmpty()) {
            Bukkit.savePlayers();
        }
        for (AdvancementCompiler.Compiled advancement : missing) {
            NamespacedKey key = key(advancement.key());
            if (Bukkit.getAdvancement(key) != null) {
                logger.warning("Advancement " + key + " already exists on the server - from a datapack? Ours is"
                        + " not registered.");
                continue;
            }
            try {
                @SuppressWarnings("deprecation")
                Advancement loaded = Bukkit.getUnsafe().loadAdvancement(key, advancement.json());
                if (loaded != null) {
                    registered.put(key, advancement.json());
                } else {
                    logger.warning("The server did not take advancement " + key + ".");
                }
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Advancement " + key + " was refused by the server: "
                        + exception.getMessage(), exception);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkWorn(player);
        }
    }

    public int registeredCount() {
        return registered.size();
    }

    // ---------------------------------------------------------------- awarding

    /**
     * Awards every wear advancement the player now completes. Cheap: a handful of slots per
     * advancement that names sets, and nothing for one already done.
     */
    public void checkWorn(Player player) {
        for (Map.Entry<NamespacedKey, AdvancementDefinition> entry : definitions.entrySet()) {
            if (!(entry.getValue().trigger() instanceof Trigger.Wear wear) || !registered.containsKey(entry.getKey())) {
                continue;
            }
            Advancement advancement = Bukkit.getAdvancement(entry.getKey());
            if (advancement == null) {
                continue;
            }
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            if (progress.isDone() || !wears(player, wear.items())) {
                continue;
            }
            for (String criterion : List.copyOf(progress.getRemainingCriteria())) {
                progress.awardCriteria(criterion);
            }
        }
    }

    /** Whether one of the plugin's items is among those a wear advancement names. */
    public boolean isWorn(@Nullable ItemStack stack) {
        Optional<CustomItem> item = factory.identify(stack);
        return item.isPresent() && item.get().definition().equipment() != null;
    }

    /** Every piece in its slot, and each one the real thing: see {@link WornSet}. */
    boolean wears(Player player, List<ResourceLocation> pieces) {
        List<ItemDefinition> definitions = new ArrayList<>();
        for (ResourceLocation id : pieces) {
            Optional<CustomItem> custom = items.get(id.toString());
            if (custom.isEmpty()) {
                return false;
            }
            definitions.add(custom.get().definition());
        }
        return WornSet.worn(definitions, slot -> worn(player.getInventory().getItem(EquipmentSlot.valueOf(slot.name()))));
    }

    private @Nullable WornSet.Worn worn(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        Equippable equippable = stack.getData(DataComponentTypes.EQUIPPABLE);
        Equipment.Slot slot = equippable == null ? null : Equipment.Slot.parse(equippable.slot().name());
        Key asset = equippable == null ? null : equippable.assetId();
        return new WornSet.Worn(factory.identify(stack).map(CustomItem::id).orElse(null), stack.getType().name(), slot,
                asset == null ? null : new ResourceLocation(asset.namespace(), asset.value()));
    }

    // ---------------------------------------------------------------- celebrating

    /**
     * One of the plugin's advancements was completed, by whatever means - the plugin, a vanilla
     * trigger, {@code /advancement grant}. Main thread.
     */
    public void completed(Player player, Advancement advancement) {
        AdvancementDefinition definition = definitions.get(advancement.getKey());
        if (definition == null) {
            return;
        }
        if (definition.celebrate()) {
            celebrate(player, definition);
        }
        String key = advancement.getKey().toString();
        database.recordUnlock(player.getUniqueId(), key, System.currentTimeMillis())
                .whenComplete((first, error) -> {
                    if (error != null) {
                        logger.log(Level.WARNING, "Could not record that " + player.getName() + " completed " + key,
                                error);
                        return;
                    }
                    if (first && definition.announce() != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> announce(player, advancement, definition));
                    }
                });
    }

    private void announce(Player player, Advancement advancement, AdvancementDefinition definition) {
        Component message = MiniMessage.miniMessage().deserialize(definition.announce(),
                Placeholder.component("player", player.displayName()),
                // As vanilla shows it in chat: the title in brackets, the description on hover.
                Placeholder.component("advancement", advancement.displayName()));
        Bukkit.getServer().sendMessage(message);
    }

    /**
     * The challenge sound where the player stands, for everyone around - the player hears it from
     * their own toast already when there is one - and a crimson spiral and burst over them.
     */
    private void celebrate(Player player, AdvancementDefinition definition) {
        Location at = player.getLocation();
        boolean ownToast = definition.toast() && definition.frame() == AdvancementDefinition.Frame.CHALLENGE;
        List<Player> around = new ArrayList<>(at.getWorld().getNearbyPlayers(at, 48));
        for (Player listener : around) {
            if (listener != player || !ownToast) {
                listener.playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1f, 1f);
            }
        }
        burst(at.clone(), List.copyOf(around));
    }

    /**
     * The particles: packets to the players taken above, at the spot taken above, sent from a
     * scheduler thread - nothing in the world is read or changed, so the server thread does none of it.
     */
    private void burst(Location at, List<Player> receivers) {
        AtomicInteger frame = new AtomicInteger();
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task -> {
            int f = frame.getAndIncrement();
            if (f >= BURST_FRAMES) {
                task.cancel();
                return;
            }
            try {
                if (f < BURST_FRAMES - 2) {
                    // A ring that rises round the player and closes in.
                    double height = 0.1 + 2.2 * f / (BURST_FRAMES - 2);
                    double radius = 1.1 - 0.06 * f;
                    for (int i = 0; i < 10; i++) {
                        double angle = Math.PI * 2 * i / 10 + f * 0.45;
                        new ParticleBuilder(Particle.DUST)
                                .location(at.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius))
                                .color(i % 3 == 0 ? GLINT : CRIMSON, 1.3f)
                                .count(1).extra(0)
                                .receivers(receivers)
                                .spawn();
                    }
                } else {
                    // The burst at the top, crimson fading to blood.
                    new ParticleBuilder(Particle.DUST_COLOR_TRANSITION)
                            .location(at.clone().add(0, 2.2, 0))
                            .colorTransition(CRIMSON, DEEP_CRIMSON, 1.6f)
                            .count(40).offset(0.7, 0.5, 0.7).extra(0)
                            .receivers(receivers)
                            .spawn();
                }
            } catch (RuntimeException exception) {
                task.cancel();
                logger.log(Level.FINE, "Advancement particles stopped", exception);
            }
        }, 0L, 2L);
    }

    private static NamespacedKey key(ResourceLocation location) {
        return new NamespacedKey(location.namespace(), location.path());
    }
}
