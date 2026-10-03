package com.arkcronist.content.bukkit.protection;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asks every installed protection plugin - WorldGuard, GriefPrevention - before this plugin changes
 * a block itself.
 *
 * <p>Most of what players do to custom content reaches protection plugins anyway, as a real
 * {@code BlockBreakEvent} or {@code BlockPlaceEvent} this plugin fires, and they cancel it as they
 * would for any block. This covers what no such event carries: a crop uprooted because the soil
 * under it blew up, an inventory opened by a right click on an invisible block, bone meal on a
 * crop. Each of those is asked here first, and refused if any plugin refuses it.</p>
 *
 * <p>A plugin whose check throws - an API that changed under a newer version - counts as a refusal:
 * a broken protection check must not become an open door. It is logged once.</p>
 *
 * <p>With no protection plugin installed, everything is allowed. Main thread only.</p>
 */
public final class Protection {

    private static final Component REFUSED = Component.text("This area is protected.", NamedTextColor.RED);

    private final Logger logger;
    private final List<ProtectionProvider> providers = new CopyOnWriteArrayList<>();
    private final Set<String> broken = ConcurrentHashMap.newKeySet();

    public Protection(Logger logger) {
        this.logger = logger;
    }

    public void add(ProtectionProvider provider) {
        providers.add(provider);
    }

    /** The protection plugins being asked. */
    public List<String> names() {
        return providers.stream().map(ProtectionProvider::name).toList();
    }

    /** Whether {@code player} may do {@code interaction} to {@code block}, by every protection plugin. */
    public boolean allows(Player player, Block block, Interaction interaction) {
        return all(provider -> provider.allows(player, block, interaction));
    }

    /** {@link #allows}, telling the player on the action bar when the answer is no. */
    public boolean check(Player player, Block block, Interaction interaction) {
        if (allows(player, block, interaction)) {
            return true;
        }
        player.sendActionBar(REFUSED);
        return false;
    }

    /** Whether an explosion at {@code origin}, set off by {@code source}, may destroy {@code block}. */
    public boolean allowsExplosion(Block block, Location origin, @Nullable Entity source) {
        return all(provider -> provider.allowsExplosion(block, origin, source));
    }

    /** Whether a mob, a fluid or anything else that is not a player may change {@code block} from {@code from}. */
    public boolean allowsChange(Block block, Location from) {
        return all(provider -> provider.allowsChange(block, from));
    }

    private boolean all(Predicate<ProtectionProvider> question) {
        for (ProtectionProvider provider : providers) {
            try {
                if (!question.test(provider)) {
                    return false;
                }
            } catch (RuntimeException | LinkageError error) {
                if (broken.add(provider.name())) {
                    logger.log(Level.SEVERE, provider.name() + "'s protection check failed. Until this is fixed,"
                            + " everything it would be asked is refused - a broken check must not let anything"
                            + " through. A newer " + provider.name() + " than this plugin knows may have changed its API.",
                            error);
                }
                return false;
            }
        }
        return true;
    }
}
