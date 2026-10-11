package com.arkcronist.content.bukkit.cosmetic;

import com.arkcronist.content.core.cosmetic.BackPose;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cosmetics as real display entities, for a server without PacketEvents: the physics still runs on
 * its own thread, but only the main thread may move an entity, so each tick it copies the latest
 * pose onto the display and moves the display to its wearer, the client smoothing both over two
 * ticks.
 *
 * <p>The display does not ride its wearer - a player carrying a passenger cannot be teleported by
 * other plugins - so it follows them a moment behind. It is never saved with the world.</p>
 */
public final class EntityCosmetics implements CosmeticRenderer {

    private final Plugin plugin;
    private final Map<UUID, ItemDisplay> displays = new ConcurrentHashMap<>();
    /** The pose each display was last given; a new display is given the current one. Main thread. */
    private final Map<UUID, BackPose.Transform> applied = new java.util.HashMap<>();
    private final Map<UUID, Worn> worn = new ConcurrentHashMap<>();

    public EntityCosmetics(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String describe() {
        return "display entities moved each tick (install PacketEvents to send them as packets from their own thread)";
    }

    @Override
    public void wear(Worn worn, Player wearer) {
        takeOff(worn);
        this.worn.put(worn.player(), worn);
        ItemDisplay display = wearer.getWorld().spawn(anchor(wearer), ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setItemStack(worn.item());
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setInterpolationDuration(2);
            entity.setTeleportDuration(2);
        });
        displays.put(worn.player(), display);
        applied.remove(worn.player());
    }

    @Override
    public void show(Worn worn, Player viewer) {
    }

    @Override
    public void hide(Worn worn, Player viewer) {
    }

    @Override
    public void update(Worn worn, BackPose.Transform transform) {
        // Read by tick(), on the main thread.
    }

    @Override
    public void tick() {
        for (Map.Entry<UUID, ItemDisplay> entry : displays.entrySet()) {
            Player wearer = plugin.getServer().getPlayer(entry.getKey());
            ItemDisplay display = entry.getValue();
            Worn on = worn.get(entry.getKey());
            if (wearer == null || on == null) {
                continue;
            }
            if (!display.isValid() || !display.getWorld().equals(wearer.getWorld())) {
                wear(on, wearer);
                continue;
            }
            display.teleport(anchor(wearer));
            BackPose.Transform pose = on.latest();
            // Only a pose the physics has not drawn yet: the interpolation delay is sent every time
            // it is set, changed or not - 20 packets a second for a pack at rest.
            if (pose != null && applied.get(entry.getKey()) != pose) {
                applied.put(entry.getKey(), pose);
                display.setTransformation(new Transformation(pose.translation(), pose.rotation(), pose.scale(),
                        new Quaternionf()));
                display.setInterpolationDelay(0);
            }
        }
    }

    @Override
    public void takeOff(Worn worn) {
        this.worn.remove(worn.player());
        ItemDisplay display = displays.remove(worn.player());
        applied.remove(worn.player());
        if (display != null) {
            display.remove();
        }
    }

    @Override
    public void close() {
        displays.values().forEach(ItemDisplay::remove);
        displays.clear();
        applied.clear();
        worn.clear();
    }

    /** Where a rider would sit: the top of the wearer, facing nowhere in particular. */
    private static Location anchor(Player wearer) {
        Location at = wearer.getLocation().add(0, wearer.getHeight(), 0);
        at.setYaw(0);
        at.setPitch(0);
        return at;
    }
}
