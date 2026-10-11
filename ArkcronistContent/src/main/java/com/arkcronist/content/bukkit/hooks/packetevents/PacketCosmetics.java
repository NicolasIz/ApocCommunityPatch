package com.arkcronist.content.bukkit.hooks.packetevents;

import com.arkcronist.content.bukkit.cosmetic.CosmeticRenderer;
import com.arkcronist.content.bukkit.cosmetic.Worn;
import com.arkcronist.content.core.cosmetic.BackPose;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cosmetics as packets (3.0, with PacketEvents): an item display that exists only on the clients,
 * spawned for each player who sees the wearer and set riding the wearer there - so the client
 * itself carries it along, smoothly, at any speed, and the server, which knows nothing of it, still
 * teleports the wearer freely.
 *
 * <p>Its pose is sent from {@code ArkContent-Physics} as soon as it is worked out: one entity data
 * packet per viewer, a few dozen bytes, interpolated by the client over two ticks. The main thread
 * only spawns and removes it, as players come into and out of sight.</p>
 *
 * <p>The server sends a wearer's riders again whenever they change - a player getting on them - and
 * that list would leave the display out; it is put back in on its way out. Entity data indices are
 * those of 1.21.8's display entities: 8 interpolation delay, 9 its duration, 11 translation, 12
 * scale, 13 left rotation, 23 the item.</p>
 */
public final class PacketCosmetics implements CosmeticRenderer {

    private static final int INTERPOLATION_DELAY = 8;
    private static final int INTERPOLATION_DURATION = 9;
    private static final int TRANSLATION = 11;
    private static final int SCALE = 12;
    private static final int LEFT_ROTATION = 13;
    private static final int ITEM = 23;

    private final Map<Integer, Worn> byWearer = new ConcurrentHashMap<>();
    private final Map<UUID, Set<User>> viewers = new ConcurrentHashMap<>();
    private final Map<UUID, com.github.retrooper.packetevents.protocol.item.ItemStack> items = new ConcurrentHashMap<>();
    private final PacketListenerCommon listener;

    public PacketCosmetics() {
        this.listener = PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(
                PacketListenerPriority.NORMAL) {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
                    keepRiding(event);
                }
            }
        });
    }

    @Override
    public String describe() {
        return "packets through PacketEvents, sent from ArkContent-Physics";
    }

    @Override
    public void wear(Worn worn, Player wearer) {
        byWearer.put(worn.wearerEntityId(), worn);
        items.put(worn.player(), SpigotConversionUtil.fromBukkitItemStack(worn.item()));
        show(worn, wearer);
        for (Player viewer : wearer.getTrackedBy()) {
            show(worn, viewer);
        }
    }

    @Override
    public void show(Worn worn, Player viewer) {
        User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
        Player wearer = viewer.getServer().getPlayer(worn.player());
        if (user == null || wearer == null) {
            return;
        }
        viewers.computeIfAbsent(worn.player(), ignored -> ConcurrentHashMap.newKeySet()).add(user);
        Location at = wearer.getLocation();
        user.sendPacketSilently(new WrapperPlayServerSpawnEntity(worn.displayId(), Optional.of(worn.displayUuid()),
                EntityTypes.ITEM_DISPLAY, new Vector3d(at.getX(), at.getY() + wearer.getHeight(), at.getZ()), 0, 0, 0, 0,
                Optional.empty()));
        List<EntityData<?>> data = new ArrayList<>();
        data.add(new EntityData<>(INTERPOLATION_DURATION, EntityDataTypes.INT, 2));
        data.add(new EntityData<>(ITEM, EntityDataTypes.ITEMSTACK, items.get(worn.player())));
        BackPose.Transform pose = worn.latest();
        if (pose != null) {
            data.addAll(pose(pose));
        }
        user.sendPacketSilently(new WrapperPlayServerEntityMetadata(worn.displayId(), data));
        int[] riders = wearer.getPassengers().stream().mapToInt(Entity::getEntityId).toArray();
        user.sendPacketSilently(new WrapperPlayServerSetPassengers(wearer.getEntityId(), withDisplay(riders, worn)));
    }

    @Override
    public void hide(Worn worn, Player viewer) {
        User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
        Set<User> seeing = viewers.get(worn.player());
        if (user == null || seeing == null || !seeing.remove(user)) {
            return;
        }
        user.sendPacketSilently(new WrapperPlayServerDestroyEntities(worn.displayId()));
    }

    /** Straight from the physics thread: one small packet per viewer. */
    @Override
    public void update(Worn worn, BackPose.Transform transform) {
        Set<User> seeing = viewers.get(worn.player());
        if (seeing == null || seeing.isEmpty()) {
            return;
        }
        List<EntityData<?>> data = new ArrayList<>(pose(transform));
        data.add(0, new EntityData<>(INTERPOLATION_DELAY, EntityDataTypes.INT, 0));
        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(worn.displayId(), data);
        for (User user : seeing) {
            user.sendPacketSilently(packet);
        }
    }

    @Override
    public void tick() {
    }

    @Override
    public void takeOff(Worn worn) {
        byWearer.remove(worn.wearerEntityId(), worn);
        items.remove(worn.player());
        Set<User> seeing = viewers.remove(worn.player());
        if (seeing != null) {
            for (User user : seeing) {
                user.sendPacketSilently(new WrapperPlayServerDestroyEntities(worn.displayId()));
            }
        }
    }

    @Override
    public void close() {
        PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        for (Worn worn : List.copyOf(byWearer.values())) {
            takeOff(worn);
        }
    }

    /** The server's list of a wearer's riders, sent to someone who sees the display: the display is put back in. */
    private void keepRiding(PacketSendEvent event) {
        WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
        Worn worn = byWearer.get(packet.getEntityId());
        if (worn == null) {
            return;
        }
        Set<User> seeing = viewers.get(worn.player());
        if (seeing == null || !seeing.contains(event.getUser())) {
            return;
        }
        int[] riders = packet.getPassengers();
        if (Arrays.stream(riders).noneMatch(id -> id == worn.displayId())) {
            packet.setPassengers(withDisplay(riders, worn));
            event.markForReEncode(true);
        }
    }

    private static int[] withDisplay(int[] riders, Worn worn) {
        int[] all = Arrays.copyOf(riders, riders.length + 1);
        all[riders.length] = worn.displayId();
        return all;
    }

    private static List<EntityData<?>> pose(BackPose.Transform pose) {
        org.joml.Vector3f t = pose.translation();
        org.joml.Vector3f s = pose.scale();
        org.joml.Quaternionf q = pose.rotation();
        return List.of(new EntityData<>(TRANSLATION, EntityDataTypes.VECTOR3F, new Vector3f(t.x, t.y, t.z)),
                new EntityData<>(SCALE, EntityDataTypes.VECTOR3F, new Vector3f(s.x, s.y, s.z)),
                new EntityData<>(LEFT_ROTATION, EntityDataTypes.QUATERNION, new Quaternion4f(q.x, q.y, q.z, q.w)));
    }
}
