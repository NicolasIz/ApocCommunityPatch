package com.arkcronist.content.bukkit.hooks.packetevents;

import com.arkcronist.content.bukkit.bedrock.BlockChangeSender;
import com.arkcronist.content.bukkit.bedrock.CarrierRoute;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 3.3.5: a Bedrock player's furniture carriers, written to their connection through PacketEvents
 * where no plugin rewrites them on the way out - see {@link CarrierRoute}. One packet per chunk
 * section, as the server itself would send them. Where that place cannot be found, or a state has
 * no number PacketEvents knows, the changes go through Bukkit as before.
 */
public final class DirectBlockChanges implements BlockChangeSender {

    private final ClientVersion version = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    /** The server's number for each state sent so far. Main thread. */
    private final Map<BlockData, Integer> ids = new HashMap<>();
    private final Consumer<String> warnOnce;
    private boolean warned;

    /** @param warnOnce told, once, when carriers have to go through Bukkit after all */
    public DirectBlockChanges(Consumer<String> warnOnce) {
        this.warnOnce = warnOnce;
    }

    @Override
    public void send(Player player, Map<Location, BlockData> changes) {
        Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(player);
        String start = channel == null ? null
                : CarrierRoute.startAt(ChannelHelper.pipelineHandlerNames(channel), PacketEvents.ENCODER_NAME);
        if (start == null) {
            if (channel != null && !warned
                    && ChannelHelper.pipelineHandlerNames(channel).contains(CarrierRoute.CRAFTENGINE_ENCODER)) {
                warned = true;
                warnOnce.accept("[bedrock] CraftEngine's handler sits where furniture blocks for Bedrock players cannot"
                        + " be sent past it: they go out rewritten, and Bedrock players may see other pieces in their"
                        + " place. Pipeline: " + ChannelHelper.pipelineHandlerNames(channel));
            }
            BlockChangeSender.BUKKIT.send(player, changes);
            return;
        }
        Map<Long, List<WrapperPlayServerMultiBlockChange.EncodedBlock>> sections = new LinkedHashMap<>();
        for (Map.Entry<Location, BlockData> change : changes.entrySet()) {
            int id = id(change.getValue());
            if (id <= 0) {
                BlockChangeSender.BUKKIT.send(player, changes);
                return;
            }
            Location at = change.getKey();
            int x = at.getBlockX();
            int y = at.getBlockY();
            int z = at.getBlockZ();
            sections.computeIfAbsent(section(x >> 4, y >> 4, z >> 4), key -> new ArrayList<>())
                    .add(new WrapperPlayServerMultiBlockChange.EncodedBlock(id, x, y, z));
        }
        for (List<WrapperPlayServerMultiBlockChange.EncodedBlock> blocks : sections.values()) {
            WrapperPlayServerMultiBlockChange.EncodedBlock first = blocks.get(0);
            WrapperPlayServerMultiBlockChange packet = new WrapperPlayServerMultiBlockChange(
                    new Vector3i(first.getX() >> 4, first.getY() >> 4, first.getZ() >> 4), null,
                    blocks.toArray(WrapperPlayServerMultiBlockChange.EncodedBlock[]::new));
            if (start.equals(PacketEvents.ENCODER_NAME)) {
                PacketEvents.getAPI().getProtocolManager().sendPacketSilently(channel, packet);
                continue;
            }
            Object[] buffers;
            try {
                buffers = PacketEvents.getAPI().getProtocolManager().transformWrappers(packet, channel, true);
            } catch (LinkageError older) {
                BlockChangeSender.BUKKIT.send(player, changes);
                return;
            }
            for (Object buffer : buffers) {
                ChannelHelper.writeAndFlushInContext(channel, start, buffer);
            }
        }
    }

    /** The server's number for the state; 0 (air) when PacketEvents does not know it. */
    private int id(BlockData data) {
        Integer known = ids.get(data);
        if (known == null) {
            known = WrappedBlockState.getByString(version, data.getAsString(false)).getGlobalId();
            ids.put(data, known);
        }
        return known;
    }

    private static long section(int x, int y, int z) {
        return ((long) x & 0x3FFFFF) << 42 | ((long) z & 0x3FFFFF) << 20 | (y & 0xFFFFFL);
    }
}
