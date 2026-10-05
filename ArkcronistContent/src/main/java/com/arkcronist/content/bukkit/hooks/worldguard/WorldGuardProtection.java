package com.arkcronist.content.bukkit.hooks.worldguard;

import com.arkcronist.content.bukkit.protection.Interaction;
import com.arkcronist.content.bukkit.protection.ProtectionProvider;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.association.DelayedRegionOverlapAssociation;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractWindCharge;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.jetbrains.annotations.Nullable;

/**
 * WorldGuard regions, asked the way WorldGuard asks itself.
 *
 * <p>Each interaction maps onto the flags WorldGuard's own listeners test for the vanilla action
 * closest to it - planting is a block place, opening storage furniture is chest access - so a
 * region's flags mean the same for custom content as for vanilla blocks: {@code chest-access allow}
 * lets visitors open storage furniture, {@code ride allow} lets them sit on chairs, as it lets them
 * into boats. Players with
 * WorldGuard's region bypass pass, as everywhere else.</p>
 *
 * <p>For explosions and other changes no player makes, the cause is judged by where it is - by
 * WorldGuard's region overlap, as for a creeper - so a blast inside a region may damage that
 * region and one from outside may not, and the explosion's own flag ({@code tnt},
 * {@code creeper-explosion}...) must allow it as well.</p>
 */
public final class WorldGuardProtection implements ProtectionProvider {

    @Override
    public String name() {
        return "WorldGuard";
    }

    @Override
    public boolean allows(Player player, Block block, Interaction interaction) {
        LocalPlayer local = WorldGuardPlugin.inst().wrapPlayer(player);
        if (WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(local, BukkitAdapter.adapt(block.getWorld()))) {
            return true;
        }
        com.sk89q.worldedit.util.Location at = BukkitAdapter.adapt(block.getLocation());
        RegionQuery query = query();
        return switch (interaction) {
            case PLACE -> query.testBuild(at, local, Flags.BLOCK_PLACE);
            case BREAK -> query.testBuild(at, local, Flags.BLOCK_BREAK);
            case BUILD -> query.testBuild(at, local);
            // What WorldGuard itself tests when anything is mounted - so a refused sitter gets no seat at all.
            case SIT -> query.testBuild(at, local, Flags.RIDE, Flags.INTERACT);
            case CONTAINER -> query.testBuild(at, local, Flags.INTERACT, Flags.CHEST_ACCESS);
        };
    }

    @Override
    public boolean allowsExplosion(Block block, Location origin, @Nullable Entity source) {
        RegionQuery query = query();
        com.sk89q.worldedit.util.Location at = BukkitAdapter.adapt(block.getLocation());
        // TNT lit by a player is that player's doing, as WorldGuard sees it.
        RegionAssociable cause = source instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player
                ? WorldGuardPlugin.inst().wrapPlayer(player)
                : new DelayedRegionOverlapAssociation(query, BukkitAdapter.adapt(origin));
        if (source instanceof TNTPrimed || source instanceof ExplosiveMinecart) {
            return query.testBuild(at, cause, Flags.BLOCK_BREAK, Flags.TNT);
        }
        if (!query.testBuild(at, cause, Flags.BLOCK_BREAK)) {
            return false;
        }
        StateFlag flag = explosionFlag(source);
        return query.testState(at, (RegionAssociable) null, flag);
    }

    @Override
    public boolean allowsChange(Block block, Location from) {
        RegionQuery query = query();
        return query.testBuild(BukkitAdapter.adapt(block.getLocation()),
                new DelayedRegionOverlapAssociation(query, BukkitAdapter.adapt(from)), Flags.BLOCK_BREAK);
    }

    @Override
    public boolean allowsLiquidFlow(Block block) {
        StateFlag flag = WorldGuardFlags.liquidFlow();
        return flag == null || query().testState(BukkitAdapter.adapt(block.getLocation()), (RegionAssociable) null, flag);
    }

    @Override
    public boolean allowsLiquidContact(Player player, Block block) {
        StateFlag flag = WorldGuardFlags.liquidDamage();
        return flag == null || query().testState(BukkitAdapter.adapt(block.getLocation()),
                WorldGuardPlugin.inst().wrapPlayer(player), flag);
    }

    /** The region flag that switches off this kind of explosion. */
    private static StateFlag explosionFlag(@Nullable Entity source) {
        if (source instanceof Creeper) {
            return Flags.CREEPER_EXPLOSION;
        }
        if (source instanceof Fireball fireball && fireball.getShooter() instanceof Ghast) {
            return Flags.GHAST_FIREBALL;
        }
        if (source instanceof Wither || source instanceof WitherSkull) {
            return Flags.WITHER_DAMAGE;
        }
        if (source instanceof EnderDragon) {
            return Flags.ENDERDRAGON_BLOCK_DAMAGE;
        }
        if (source instanceof AbstractWindCharge) {
            return Flags.WIND_CHARGE_BURST;
        }
        return Flags.OTHER_EXPLOSION;
    }

    private static RegionQuery query() {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
    }
}
