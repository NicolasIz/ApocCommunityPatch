package com.arkcronist.content.bukkit.hooks.towny;

import com.arkcronist.content.bukkit.protection.Interaction;
import com.arkcronist.content.bukkit.protection.ProtectionProvider;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.utils.CombatUtil;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Towny (3.0): a player's build, destroy and switch permissions in a town block, a plot's explosion
 * setting, town borders for what crosses them, and Towny's own combat rules for gun damage.
 *
 * <p>Interactions map onto Towny's action types: placing and changing blocks is {@code BUILD},
 * breaking {@code DESTROY}, storage, seats and levers {@code SWITCH} - what Towny calls using a
 * block in place. The answer is Towny's cached permission for that player, the same one its own
 * listeners use, so residents, allies, outsiders and plot owners get what they get anywhere else.
 * Outside a Towny world, everything is allowed.</p>
 */
public final class TownyProtection implements ProtectionProvider {

    @Override
    public String name() {
        return "Towny";
    }

    @Override
    public boolean allows(Player player, Block block, Interaction interaction) {
        if (!towny().isTownyWorld(block.getWorld())) {
            return true;
        }
        TownyPermission.ActionType action = switch (interaction) {
            case PLACE, BUILD -> TownyPermission.ActionType.BUILD;
            case BREAK -> TownyPermission.ActionType.DESTROY;
            case CONTAINER, USE, SIT -> TownyPermission.ActionType.SWITCH;
        };
        return PlayerCacheUtil.getCachePermission(player, block.getLocation(), block.getType(), action);
    }

    /** In a town, only where the plot has explosions switched on; the wilderness is Towny's world settings' business. */
    @Override
    public boolean allowsExplosion(Block block, Location origin, @Nullable Entity source) {
        TownBlock townBlock = townBlockAt(block.getLocation());
        return townBlock == null || townBlock.getPermissions().explosion;
    }

    /** Into a town only from inside that same town. */
    @Override
    public boolean allowsChange(Block block, Location from) {
        Town target = townAt(block.getLocation());
        if (target == null) {
            return true;
        }
        return Objects.equals(target, townAt(from));
    }

    /** Towny's own combat rules: PvP in the plot, peaceful towns, allies, protected animals. */
    @Override
    public boolean allowsAttack(Player attacker, Entity target) {
        if (!towny().isTownyWorld(target.getWorld())) {
            return true;
        }
        return !CombatUtil.preventDamageCall(attacker, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK);
    }

    private static TownyAPI towny() {
        return TownyAPI.getInstance();
    }

    private static @Nullable TownBlock townBlockAt(Location location) {
        if (location.getWorld() == null || !towny().isTownyWorld(location.getWorld())) {
            return null;
        }
        return towny().getTownBlock(location);
    }

    private static @Nullable Town townAt(Location location) {
        TownBlock townBlock = townBlockAt(location);
        return townBlock == null ? null : townBlock.getTownOrNull();
    }
}
