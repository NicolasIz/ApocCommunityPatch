package com.arkcronist.content.bukkit.hooks.packetevents;

import com.arkcronist.content.bukkit.emote.EmoteRenderer;
import com.arkcronist.content.bukkit.emote.Rig;
import com.arkcronist.content.core.emotes.EmotePose;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 5.0: emotes as packets (with PacketEvents) - the rig's displays exist only on the clients of the
 * player and of whoever sees them. Each frame is one entity data packet per display and viewer, sent
 * straight from {@code ArkContent-Emotes} the moment it is due: the server thread never touches an
 * emote while it plays, and a slow tick on it never delays one. Entity data indices are those of
 * the display entities since 1.20.2, as {@link PacketCosmetics} uses them.
 */
public final class PacketEmotes implements EmoteRenderer {

    private static final int INTERPOLATION_DELAY = 8;
    private static final int INTERPOLATION_DURATION = 9;
    private static final int TRANSLATION = 11;
    private static final int SCALE = 12;
    private static final int LEFT_ROTATION = 13;
    private static final int ITEM = 23;

    private final Map<UUID, Set<User>> viewers = new ConcurrentHashMap<>();
    private final Map<UUID, com.github.retrooper.packetevents.protocol.item.ItemStack[]> items = new ConcurrentHashMap<>();

    @Override
    public String describe() {
        return "packets through PacketEvents, sent from ArkContent-Emotes";
    }

    @Override
    public void start(Rig rig, Collection<? extends Player> viewers) {
        com.github.retrooper.packetevents.protocol.item.ItemStack[] converted =
                new com.github.retrooper.packetevents.protocol.item.ItemStack[rig.bones()];
        for (int bone = 0; bone < rig.bones(); bone++) {
            ItemStack item = rig.item(bone);
            converted[bone] = item == null ? null : SpigotConversionUtil.fromBukkitItemStack(item);
        }
        items.put(rig.player(), converted);
        this.viewers.put(rig.player(), ConcurrentHashMap.newKeySet());
        for (Player viewer : viewers) {
            show(rig, viewer);
        }
    }

    @Override
    public void show(Rig rig, Player viewer) {
        User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
        Set<User> seeing = viewers.get(rig.player());
        com.github.retrooper.packetevents.protocol.item.ItemStack[] shown = items.get(rig.player());
        if (user == null || seeing == null || shown == null || !seeing.add(user)) {
            return;
        }
        Location at = rig.at();
        List<EmotePose.Part> pose = rig.latest();
        for (int bone = 0; bone < rig.bones(); bone++) {
            EmotePose.Part part = pose.get(bone);
            if (shown[bone] == null || part == null) {
                continue;
            }
            send(user, new WrapperPlayServerSpawnEntity(rig.entityId(bone), Optional.of(rig.uuid(bone)),
                    EntityTypes.ITEM_DISPLAY, new Vector3d(at.getX(), at.getY(), at.getZ()), 0, at.getYaw(),
                    at.getYaw(), 0, Optional.empty()));
            List<EntityData<?>> data = new ArrayList<>(transform(part));
            data.add(new EntityData<>(INTERPOLATION_DURATION, EntityDataTypes.INT, 0));
            data.add(new EntityData<>(ITEM, EntityDataTypes.ITEMSTACK, shown[bone]));
            send(user, new WrapperPlayServerEntityMetadata(rig.entityId(bone), data));
        }
    }

    @Override
    public void hide(Rig rig, Player viewer) {
        User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
        Set<User> seeing = viewers.get(rig.player());
        if (user != null && seeing != null && seeing.remove(user)) {
            send(user, new WrapperPlayServerDestroyEntities(ids(rig)));
        }
    }

    /** Straight from ArkContent-Emotes: one small packet per display and viewer. */
    @Override
    public void pose(Rig rig, List<EmotePose.Part> pose, int duration) {
        Set<User> seeing = viewers.get(rig.player());
        if (seeing == null || seeing.isEmpty()) {
            return;
        }
        for (int bone = 0; bone < rig.bones() && bone < pose.size(); bone++) {
            EmotePose.Part part = pose.get(bone);
            if (part == null || rig.item(bone) == null) {
                continue;
            }
            List<EntityData<?>> data = new ArrayList<>(5);
            data.add(new EntityData<>(INTERPOLATION_DELAY, EntityDataTypes.INT, 0));
            data.add(new EntityData<>(INTERPOLATION_DURATION, EntityDataTypes.INT, duration));
            data.addAll(transform(part));
            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(rig.entityId(bone), data);
            for (User user : seeing) {
                if (!send(user, packet)) {
                    seeing.remove(user);
                }
            }
        }
    }

    @Override
    public void remove(Rig rig) {
        items.remove(rig.player());
        Set<User> seeing = viewers.remove(rig.player());
        if (seeing != null) {
            int[] ids = ids(rig);
            for (User user : seeing) {
                send(user, new WrapperPlayServerDestroyEntities(ids));
            }
        }
    }

    @Override
    public boolean offMainThread() {
        return true;
    }

    @Override
    public void close() {
        viewers.clear();
        items.clear();
    }

    private static int[] ids(Rig rig) {
        List<Integer> ids = new ArrayList<>();
        for (int bone = 0; bone < rig.bones(); bone++) {
            if (rig.item(bone) != null) {
                ids.add(rig.entityId(bone));
            }
        }
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }

    private static List<EntityData<?>> transform(EmotePose.Part part) {
        return List.of(new EntityData<>(TRANSLATION, EntityDataTypes.VECTOR3F, new Vector3f(part.tx(), part.ty(), part.tz())),
                new EntityData<>(SCALE, EntityDataTypes.VECTOR3F, new Vector3f(part.sx(), part.sy(), part.sz())),
                new EntityData<>(LEFT_ROTATION, EntityDataTypes.QUATERNION,
                        new Quaternion4f(part.qx(), part.qy(), part.qz(), part.qw())));
    }

    /** One packet, or false when the player's connection is closing; see {@link PacketCosmetics}. */
    private static boolean send(User user, PacketWrapper<?> packet) {
        try {
            user.sendPacketSilently(packet);
            return true;
        } catch (RuntimeException gone) {
            return false;
        }
    }
}
