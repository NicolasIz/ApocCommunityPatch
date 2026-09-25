package com.arkcronist.gen.bukkit.mobs;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Keeps the mobs this plugin puts in the world as disposable as the vanilla ones they stand in for.
 *
 * <h2>The flood this exists to stop</h2>
 *
 * <p>Many packs write {@code Despawn: false} on their mobs - every tree ent in the Tree Ents pack
 * does - because they are meant to be placed by hand, as a boss or a guard. Used as the stand-in for
 * a natural spawn, that setting is a leak with a multiplier. A mob that must never despawn is exactly
 * the kind the game leaves out of its count of how many monsters are about, so every replacement
 * frees the slot it filled, the game spawns another zombie into it, that one is replaced too, and by
 * morning a hillside has hundreds of tree ents on it that will never leave.</p>
 *
 * <p>So a mob made here for a natural spawn or the daylight spawner is released the moment it
 * exists: it may despawn when nobody is near, like the husk it replaced, and it counts against the
 * cap again. It is also marked, which lets this put the release back when its chunk loads - a mob
 * plugin that re-applies its own settings on load would otherwise quietly undo it - and lets
 * {@code /ag mobs purge} find them.</p>
 *
 * <p>Mobs placed in structures are not touched: a castle's guards are meant to stay.</p>
 */
public final class SpawnedMobs implements Listener {

    /** Marks a mob that stood in for a natural spawn. */
    public static final String SWAP_MARK = "arkcronist_swapped";

    private final Plugin plugin;
    private final NamespacedKey swap;
    private final NamespacedKey ambient;

    public SpawnedMobs(Plugin plugin) {
        this.plugin = plugin;
        this.swap = new NamespacedKey(plugin, SWAP_MARK);
        this.ambient = new NamespacedKey(plugin, AmbientSpawnTask.MARK);
    }

    /** Marks a replacement for a natural spawn and, when asked to, lets it despawn. */
    public static void released(Plugin plugin, Entity entity, String mark, boolean despawn) {
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, mark),
                PersistentDataType.BYTE, (byte) 1);
        if (despawn) {
            release(entity);
        }
    }

    /** Lets a mob despawn when nobody is near, and count against the monster cap again. */
    public static void release(Entity entity) {
        try {
            entity.setPersistent(false);
            if (entity instanceof LivingEntity living) {
                living.setRemoveWhenFarAway(true);
            }
        } catch (RuntimeException ignored) {
            // An entity that will not be changed is left as it is.
        }
    }

    /** Whether this plugin made the entity for a natural spawn or the daylight spawner. */
    public boolean ours(Entity entity) {
        return entity.getPersistentDataContainer().has(swap, PersistentDataType.BYTE)
                || entity.getPersistentDataContainer().has(ambient, PersistentDataType.BYTE);
    }

    /** Puts the release back when a chunk's mobs load, in case something re-applied its own. */
    @EventHandler
    public void onLoad(EntitiesLoadEvent event) {
        if (!despawns()) {
            return;
        }
        for (Entity entity : event.getEntities()) {
            if (ours(entity)) {
                release(entity);
            }
        }
    }

    private boolean despawns() {
        return plugin instanceof com.arkcronist.gen.bukkit.ArkcronistPlugin ark
                && ark.arkConfig().spawnedMobsDespawn();
    }

    /**
     * Whether a mob should go in a purge: one this plugin marked, or - for mobs placed before the
     * mark existed - one whose MythicMobs name is in the pools this plugin spawns from.
     *
     * @param marked      whether it carries this plugin's mark
     * @param mythicName  its MythicMobs name, or null when it is not a MythicMobs mob
     * @param spawnable   every name the plugin spawns by itself, lower case
     */
    public static boolean purgeable(boolean marked, String mythicName, Set<String> spawnable) {
        if (marked) {
            return true;
        }
        return mythicName != null && spawnable.contains(mythicName.toLowerCase(Locale.ROOT));
    }

    /** Removes every purgeable mob in loaded chunks and says how many went. */
    public int purge(Collection<? extends org.bukkit.World> worlds, Set<String> spawnable) {
        int removed = 0;
        for (org.bukkit.World world : worlds) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof org.bukkit.entity.Player) {
                    continue;
                }
                if (purgeable(ours(entity), ours(entity) ? null : MythicBridge.nameOf(entity),
                        spawnable)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }
}
