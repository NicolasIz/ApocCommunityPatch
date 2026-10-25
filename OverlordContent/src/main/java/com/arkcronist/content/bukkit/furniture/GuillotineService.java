package com.arkcronist.content.bukkit.furniture;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.core.animation.AnimatedModel;
import com.arkcronist.content.core.definition.CommandTemplate;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.furniture.PieceAxes;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * The guillotine: furniture with {@code interactable: guillotine}, a Blockbench model whose blade
 * falls.
 *
 * <p>A right click on its frame kneels the player in the stocks - a seat, as a chair's, a little
 * behind the frame, facing out of it; sneaking gets them up. A right click on the block that holds
 * the lever pulls it:</p>
 * <ol>
 *   <li>on the first server tick at least {@code delay-ms} after the click - 63 ms by default, so
 *       the second tick after it, ticks being 50 ms apart - the {@code drop} animation starts: its
 *       bones' new transformations go out to every player in sight as entity data, and their
 *       clients play the fall;</li>
 *   <li>{@code impact-ms} after that the blade lands: the impact's particles and sound, and whoever
 *       still kneels in the stocks takes a lethal blow - {@code minecraft:generic_kill}, the damage
 *       of {@code /kill}, which no armour, effect or totem stops - on the main thread. Unless
 *       {@code respect-pvp} is off it is dealt by the one who pulled the lever, so a protection
 *       plugin that keeps players from hurting each other there stops it too. Then the
 *       {@code commands}, from the console. With nobody in the stocks, the blade falls harmlessly;</li>
 *   <li>{@code reset-after} later, the {@code reset} animation winds it back up. Until it is back,
 *       the lever does nothing.</li>
 * </ol>
 *
 * <p>Main thread.</p>
 */
public final class GuillotineService {

    /** How far behind the frame the stocks' seat is, in blocks. */
    static final double KNEEL_BEHIND = 0.2;

    private final Plugin plugin;
    private final FurnitureService furniture;
    private final SeatService seats;
    private final CommandSigns commands;
    /** Until which tick each guillotine, by its root display, is busy with a fall. */
    private final Map<UUID, Integer> busy = new HashMap<>();
    private final Set<String> warned = new HashSet<>();

    public GuillotineService(Plugin plugin, FurnitureService furniture, SeatService seats, CommandSigns commands) {
        this.plugin = plugin;
        this.furniture = furniture;
        this.seats = seats;
        this.commands = commands;
    }

    /** Whether {@code piece}'s clicked block is the one that holds its lever. */
    public static boolean isLever(FurnitureService.Piece piece, Placement.Guillotine spec) {
        return piece.across() == spec.leverAcross() && piece.up() == spec.leverUp();
    }

    /** A right click on the frame, anywhere but the lever: the player kneels in the stocks. */
    public void kneel(Player player, FurnitureService.Piece piece, Placement.Guillotine spec) {
        Block anchor = piece.anchor();
        double[] behind = PieceAxes.offset(piece.yaw(), 0, 0, -KNEEL_BEHIND);
        Location sitting = anchor.getLocation().add(0.5 + behind[0], spec.kneelHeight(), 0.5 + behind[2]);
        // 3.2: the kneeler's camera passes through the frame's supports.
        seats.sit(player, anchor, sitting, piece.yaw(), furniture.pieceParts(piece.root()));
    }

    /** A right click on the lever: the blade falls. */
    public void pull(Player puller, FurnitureService.Piece piece, CustomItem item, Placement.Furniture definition,
                     Placement.Guillotine spec) {
        long clicked = System.nanoTime();
        UUID root = piece.root().getUniqueId();
        int now = plugin.getServer().getCurrentTick();
        Integer until = busy.get(root);
        if (until != null && until > now) {
            puller.sendActionBar(Component.text("The blade is not back up yet", NamedTextColor.GRAY));
            return;
        }
        busy.put(root, now + busyTicks(definition, spec));
        Block anchor = piece.anchor();
        World world = anchor.getWorld();
        Location lever = lever(piece, spec);
        world.playSound(lever, Sound.BLOCK_LEVER_CLICK, 1f, 0.6f);
        // Timed from the click itself, so the tick the click arrived in does not shift either moment.
        furniture.afterDelay(root, clicked, TimeUnit.MILLISECONDS.toNanos(spec.delayMillis()), () -> {
            if (!furniture.animate(anchor, spec.drop())) {
                warn(item.id() + ": the model has no animation '" + spec.drop() + "' any more");
            }
        });
        furniture.afterDelay(root, clicked, TimeUnit.MILLISECONDS.toNanos(spec.delayMillis() + spec.impactMillis()),
                () -> impact(puller, piece, item, spec));
    }

    /** The blade lands. */
    private void impact(Player puller, FurnitureService.Piece piece, CustomItem item, Placement.Guillotine spec) {
        Block anchor = piece.anchor();
        World world = anchor.getWorld();
        Location base = anchor.getLocation().add(0.5, 0, 0.5);
        for (Placement.Particles particle : spec.impact()) {
            Particle type = particle(item.id(), particle.particle());
            if (type == null) {
                continue;
            }
            double[] offset = PieceAxes.offset(piece.yaw(), particle.x(), particle.y(), particle.z());
            world.spawnParticle(type, base.getX() + offset[0], base.getY() + offset[1], base.getZ() + offset[2],
                    particle.count(), particle.spread(), particle.spread(), particle.spread(), particle.speed());
        }
        world.playSound(base.clone().add(0, 1.4, 0), spec.impactSound(), 1f, 1f);
        scheduleReset(anchor, piece.root().getUniqueId(), spec);

        Player victim = spec.killUser() ? puller : seats.occupant(anchor);
        if (victim == null || !victim.isValid() || victim.isDead() || !victim.getWorld().equals(world)
                || victim.getLocation().distanceSquared(base) > 16) {
            return;
        }
        DamageSource.Builder blow = DamageSource.builder(DamageType.GENERIC_KILL)
                .withDamageLocation(base.clone().add(0, 1.4, 0));
        if (spec.respectPvp() && !victim.equals(puller) && puller.isValid()) {
            // Dealt by the one who pulled the lever: a protection plugin judges it as their attack.
            blow = blow.withCausingEntity(puller).withDirectEntity(puller);
        }
        victim.damage(Float.MAX_VALUE, blow.build());
        if (!victim.isDead() && victim.getHealth() > 0) {
            // Something stopped it: a region without PvP, a plugin cancelling the damage.
            puller.sendActionBar(Component.text("The blade stops short: it cannot hurt anyone here",
                    NamedTextColor.GRAY));
            return;
        }
        if (!spec.commands().isEmpty()) {
            Map<String, String> values = CommandSigns.values(victim, anchor);
            if (puller.isValid() && CommandTemplate.safeName(puller.getName())) {
                values.put("killer", puller.getName());
            }
            commands.run(item.id(), spec.commands(), false, puller, values, puller.getName() + " on " + victim.getName());
        }
    }

    /** Where the lever is, for its sound: the middle of the block that holds it. */
    private static Location lever(FurnitureService.Piece piece, Placement.Guillotine spec) {
        double[] offset = PieceAxes.offset(piece.yaw(), spec.leverAcross(), spec.leverUp(), 0);
        return piece.anchor().getLocation().add(0.5 + offset[0], 0.5 + offset[1], 0.5 + offset[2]);
    }

    /** How long a pull keeps the lever from working again: the fall, the wait, and the reset. */
    private static int busyTicks(Placement.Furniture definition, Placement.Guillotine spec) {
        int ticks = (spec.delayMillis() + spec.impactMillis() + 49) / 50 + 1 + spec.resetTicks();
        Placement.Animated animated = definition.animated();
        AnimatedModel.Clip reset = animated == null || spec.reset() == null ? null
                : animated.model().clips().get(spec.reset());
        if (reset != null) {
            ticks += reset.length();
        }
        return ticks;
    }

    /** The blade stays down {@code reset-after}, then is wound back up - if the guillotine still stands. */
    private void scheduleReset(Block anchor, UUID root, Placement.Guillotine spec) {
        if (spec.reset() == null) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            var still = plugin.getServer().getEntity(root);
            if (still != null && still.isValid()) {
                furniture.animate(anchor, spec.reset());
            }
        }, Math.max(1, spec.resetTicks()));
    }

    private @Nullable Particle particle(String furniture, String name) {
        NamespacedKey key = NamespacedKey.fromString(name);
        Particle type = key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.PARTICLE_TYPE).get(key);
        if (type == null || type.getDataType() != Void.class) {
            warn(furniture + ": '" + name + "' is not a particle the guillotine can draw (one with no data); left out");
            return null;
        }
        return type;
    }

    private void warn(String message) {
        if (warned.add(message)) {
            plugin.getLogger().warning("Guillotine: " + message);
        }
    }
}
