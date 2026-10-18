package com.arkcronist.content.bukkit.bedrock;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * 3.3.5: how a player is sent blocks only their client sees - a Bedrock player's furniture
 * carriers. Bukkit's {@link Player#sendMultiBlockChange} by default; with PacketEvents, a route
 * that plugins rewriting block states on the way out (CraftEngine's block state mappings) do not
 * touch, since a carrier has to reach Geyser as the state it was registered with.
 */
@FunctionalInterface
public interface BlockChangeSender {

    BlockChangeSender BUKKIT = Player::sendMultiBlockChange;

    /** Main thread. */
    void send(Player player, Map<Location, BlockData> changes);
}
