package com.arkcronist.content.bukkit.hooks.griefprevention;

import com.arkcronist.content.bukkit.protection.Interaction;
import com.arkcronist.content.bukkit.protection.ProtectionProvider;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * GriefPrevention claims, asked through its own permission check - which also covers trust given
 * with /trust, /containertrust and /accesstrust, admins in /ignoreclaims, and other plugins that
 * listen to GriefPrevention's permission event.
 *
 * <p>Interactions map onto GriefPrevention's levels of trust: changing blocks is build trust,
 * opening storage furniture is container trust, sitting on a chair is access trust. Outside any
 * claim, and in worlds where claims are off, everything is allowed, as GriefPrevention allows it.</p>
 */
public final class GriefPreventionProtection implements ProtectionProvider {

    @Override
    public String name() {
        return "GriefPrevention";
    }

    @Override
    public boolean allows(Player player, Block block, Interaction interaction) {
        Claim claim = claimAt(block.getLocation());
        if (claim == null) {
            return true;
        }
        ClaimPermission permission = switch (interaction) {
            case PLACE, BREAK, BUILD -> ClaimPermission.Build;
            case SIT -> ClaimPermission.Access;
            case CONTAINER -> ClaimPermission.Inventory;
        };
        return claim.checkPermission(player, permission, null) == null;
    }

    /** Claims are blast-proof unless their owner switched explosions on (/claimexplosions). */
    @Override
    public boolean allowsExplosion(Block block, Location origin, @Nullable Entity source) {
        Claim claim = claimAt(block.getLocation());
        return claim == null || claim.areExplosivesAllowed || !GriefPrevention.instance.config_blockClaimExplosions;
    }

    /** A change from outside a claim, or from someone else's claim, does not reach into it. */
    @Override
    public boolean allowsChange(Block block, Location from) {
        Claim target = claimAt(block.getLocation());
        if (target == null) {
            return true;
        }
        Claim source = GriefPrevention.instance.dataStore.getClaimAt(from, false, target);
        return source != null && Objects.equals(source.getOwnerID(), target.getOwnerID());
    }

    private static @Nullable Claim claimAt(Location location) {
        GriefPrevention plugin = GriefPrevention.instance;
        if (plugin == null || location.getWorld() == null || !plugin.claimsEnabledForWorld(location.getWorld())) {
            return null;
        }
        return plugin.dataStore.getClaimAt(location, false, null);
    }
}
