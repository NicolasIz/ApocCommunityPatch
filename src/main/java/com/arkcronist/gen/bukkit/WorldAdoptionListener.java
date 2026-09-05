package com.arkcronist.gen.bukkit;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Makes sure a world this generator made is known to the plugin as soon as it is loaded.
 *
 * <p>Worlds were being registered only as a side effect of generating a chunk, and for a brand new
 * world that is fine - the first chunk registers it. For an existing one it is not: restart a server
 * whose Arkcronist world is already generated around spawn and no chunk needs generating, so nothing
 * registers the world, and everything that starts from "is this one of ours" quietly answers no. The
 * hostile mob swap is exactly that: until a player walked far enough out to make the generator run,
 * the world kept spawning vanilla mobs and there was nothing in the log to say why. Then it started
 * working on its own, which is worse.</p>
 *
 * <p>Registering on load costs one map entry per world and removes the whole class of "it works
 * after a while" from this plugin.</p>
 */
public final class WorldAdoptionListener implements Listener {

    private final ArkcronistPlugin plugin;

    public WorldAdoptionListener(ArkcronistPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        adopt(plugin, event.getWorld());
    }

    /**
     * Registers one world if this generator is the one that makes it.
     *
     * <p>The preset is taken from the generator object the server is already holding, not guessed
     * from the config: a server with a BASE world and an INSANE world must not have either of them
     * adopted as the other.</p>
     */
    public static void adopt(ArkcronistPlugin plugin, World world) {
        if (!(world.getGenerator() instanceof ArkChunkGenerator generator)) {
            return;
        }
        plugin.worlds().get(world, generator.preset());
        // Next tick, not now. This can run from inside WorldLoadEvent, and creating a world from
        // inside the load of another world is asking the server to re-enter something it is in the
        // middle of. A tick later it is finished and the creation is an ordinary one.
        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.dimensions().ensureCompanions(world));
    }
}
