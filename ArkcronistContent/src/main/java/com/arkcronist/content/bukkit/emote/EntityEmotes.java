package com.arkcronist.content.bukkit.emote;

import com.arkcronist.content.core.emotes.EmotePose;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 5.0: emotes without PacketEvents - the rig made of real item displays, never saved with the
 * world, which the server tracks and sends to whoever is near like any entity. Moving them is
 * entity metadata, so it happens on the main thread.
 */
public final class EntityEmotes implements EmoteRenderer {

    private final Map<UUID, List<ItemDisplay>> displays = new ConcurrentHashMap<>();

    @Override
    public String describe() {
        return "item displays the server moves (install PacketEvents to send them as packets instead)";
    }

    @Override
    public void start(Rig rig, Collection<? extends Player> viewers) {
        List<EmotePose.Part> pose = rig.latest();
        List<ItemDisplay> spawned = new ArrayList<>();
        for (int bone = 0; bone < rig.bones(); bone++) {
            ItemStack item = rig.item(bone);
            EmotePose.Part part = pose.get(bone);
            if (item == null || part == null) {
                spawned.add(null);
                continue;
            }
            spawned.add(rig.at().getWorld().spawn(rig.at(), ItemDisplay.class, display -> {
                display.setPersistent(false);
                display.setInvulnerable(true);
                display.setGravity(false);
                display.setItemStack(item);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                display.setTransformation(transformation(part));
            }));
        }
        displays.put(rig.player(), spawned);
    }

    @Override
    public void show(Rig rig, Player viewer) {
    }

    @Override
    public void hide(Rig rig, Player viewer) {
    }

    @Override
    public void pose(Rig rig, List<EmotePose.Part> pose, int duration) {
        List<ItemDisplay> bones = displays.get(rig.player());
        if (bones == null) {
            return;
        }
        for (int bone = 0; bone < bones.size() && bone < pose.size(); bone++) {
            ItemDisplay display = bones.get(bone);
            EmotePose.Part part = pose.get(bone);
            if (display == null || part == null || !display.isValid()) {
                continue;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(duration);
            display.setTransformation(transformation(part));
        }
    }

    @Override
    public void remove(Rig rig) {
        List<ItemDisplay> bones = displays.remove(rig.player());
        if (bones != null) {
            bones.stream().filter(display -> display != null && display.isValid()).forEach(ItemDisplay::remove);
        }
    }

    @Override
    public boolean offMainThread() {
        return false;
    }

    @Override
    public void close() {
        displays.values().forEach(bones -> bones.stream().filter(display -> display != null && display.isValid())
                .forEach(ItemDisplay::remove));
        displays.clear();
    }

    private static Transformation transformation(EmotePose.Part part) {
        return new Transformation(part.translation(), part.rotation(), part.scale(), new Quaternionf());
    }
}
