package com.ssomar.score.api.executableblocks.config.placed;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.Optional;

/** Signature copied from SCore 5.25 - see build.gradle. Never packaged. */
public interface ExecutableBlocksPlacedManagerInterface {

    void removeExecutableBlockPlaced(ExecutableBlockPlacedInterface placed);

    Optional<ExecutableBlockPlacedInterface> getExecutableBlockPlaced(Location location);

    Optional<ExecutableBlockPlacedInterface> getExecutableBlockPlaced(Block block);
}
