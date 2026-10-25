package com.arkcronist.content.bukkit.hooks.lands;

import com.arkcronist.content.bukkit.protection.Interaction;
import com.arkcronist.content.bukkit.protection.ProtectionProvider;
import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.flags.type.Flags;
import me.angeschossen.lands.api.flags.type.RoleFlag;
import me.angeschossen.lands.api.land.Area;
import me.angeschossen.lands.api.land.LandWorld;
import me.angeschossen.lands.api.player.LandPlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Lands (3.0): its role flags decide what a player may do in a land, its natural flags what the
 * world may do there, and land borders stop what comes from outside - a liquid poured next door
 * does not flow in.
 *
 * <p>Interactions map onto Lands' role flags: placing and changing blocks is {@code BLOCK_PLACE},
 * breaking {@code BLOCK_BREAK}, storage {@code INTERACT_CONTAINER}, a lever or a sign that runs a
 * command {@code INTERACT_MECHANISM}, a seat {@code INTERACT_GENERAL}. In the wilderness Lands
 * answers with its wilderness settings; in a world where Lands is off, everything is allowed.
 * Messages are Lands' own business: none is sent from here.</p>
 */
public final class LandsProtection implements ProtectionProvider {

    private final LandsIntegration lands;

    public LandsProtection(Plugin plugin) {
        this.lands = LandsIntegration.of(plugin);
    }

    @Override
    public String name() {
        return "Lands";
    }

    @Override
    public boolean allows(Player player, Block block, Interaction interaction) {
        LandWorld world = lands.getWorld(block.getWorld());
        if (world == null) {
            return true;
        }
        RoleFlag flag = switch (interaction) {
            case PLACE, BUILD -> Flags.BLOCK_PLACE;
            case BREAK -> Flags.BLOCK_BREAK;
            case CONTAINER -> Flags.INTERACT_CONTAINER;
            case USE -> Flags.INTERACT_MECHANISM;
            case SIT -> Flags.INTERACT_GENERAL;
        };
        return hasRoleFlag(world, player, block.getLocation(), flag, block.getType());
    }

    /** TNT and TNT minecarts by Lands' TNT griefing flag; creepers and the rest by its entity griefing flag. */
    @Override
    public boolean allowsExplosion(Block block, Location origin, @Nullable Entity source) {
        LandWorld world = lands.getWorld(block.getWorld());
        if (world == null || lands.getArea(block.getLocation()) == null) {
            return true;
        }
        boolean tnt = source == null || source instanceof TNTPrimed || source instanceof ExplosiveMinecart;
        return world.hasNaturalFlag(block.getLocation(), tnt ? Flags.TNT_GRIEFING : Flags.ENTITY_GRIEFING);
    }

    /** Into a land only from inside that same land. */
    @Override
    public boolean allowsChange(Block block, Location from) {
        Area target = lands.getArea(block.getLocation());
        if (target == null) {
            return true;
        }
        Area source = lands.getArea(from);
        return source != null && Objects.equals(source.getLand(), target.getLand());
    }

    @Override
    public boolean allowsAttack(Player attacker, Entity target) {
        Location at = target.getLocation();
        if (target instanceof Player victim) {
            return lands.canPvP(attacker, victim, at, false, false);
        }
        LandWorld world = lands.getWorld(target.getWorld());
        if (world == null) {
            return true;
        }
        return hasRoleFlag(world, attacker, at, target instanceof Enemy ? Flags.ATTACK_MONSTER : Flags.ATTACK_ANIMAL,
                Material.AIR);
    }

    /** Through the player's Lands profile, which Lands keeps for everyone online. */
    private boolean hasRoleFlag(LandWorld world, Player player, Location at, RoleFlag flag, Material material) {
        LandPlayer landPlayer = lands.getLandPlayer(player.getUniqueId());
        return landPlayer != null
                ? world.hasRoleFlag(landPlayer, at, flag, material, false)
                : world.hasRoleFlag(player.getUniqueId(), at, flag);
    }
}
