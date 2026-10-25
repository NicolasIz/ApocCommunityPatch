package com.arkcronist.content.bukkit.command;

import com.arkcronist.content.bukkit.furniture.FurnitureService;
import com.arkcronist.content.bukkit.furniture.StorageService;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.core.definition.Placement;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.Consumer;

/**
 * Sets out a pack's furniture in rows in front of a player, each piece facing them, as
 * {@code /arkcontent showcase} does. Each piece is placed the way a player would place it - its
 * supports, its displays, its rows, in one database transaction - except that there is no item to
 * use up and no protection to ask: the command is for admins.
 *
 * <p>A few pieces a tick, so that setting out a whole pack never holds one tick up the way placing
 * all of it at once would: spawning a piece's displays is the part only the main thread can do.</p>
 *
 * <p>Main thread.</p>
 */
final class Showcase {

    /** How wide a row is, in blocks, before the next starts behind it. */
    static final int ROW_WIDTH = 25;
    /** How far the first row is in front of the player, and each row from the next. */
    static final int FIRST_ROW = 3;
    static final int ROW_STEP = 3;
    /** Pieces placed in one tick. */
    static final int PER_TICK = 4;

    /**
     * @param slowestTickMillis the longest any one tick spent placing
     */
    record Result(int placed, int skipped, int rows, int ticks, double slowestTickMillis) {
    }

    private final FurnitureService furniture;
    private final StorageService storage;
    private final Player player;
    private final List<CustomItem> pieces;
    private final int forwardX;
    private final int forwardZ;
    private final Block origin;
    /** Where the player looked when they asked: every piece faces them as they stood then. */
    private final float lookingYaw;
    private int next;
    private int row;
    private int cursor = -ROW_WIDTH / 2;
    private int placed;
    private int skipped;
    private int ticks;
    private double slowest;

    private Showcase(FurnitureService furniture, StorageService storage, Player player, List<CustomItem> pieces) {
        this.furniture = furniture;
        this.storage = storage;
        this.player = player;
        this.pieces = pieces;
        BlockFace facing = player.getFacing();
        this.forwardX = facing.getModX();
        this.forwardZ = facing.getModZ();
        this.origin = player.getLocation().getBlock();
        this.lookingYaw = player.getLocation().getYaw();
    }

    /** Starts setting out {@code pieces}; {@code done} hears how it went once the last is placed. */
    static void start(Plugin plugin, FurnitureService furniture, StorageService storage, Player player,
                      List<CustomItem> pieces, Consumer<Result> done) {
        Showcase showcase = new Showcase(furniture, storage, player, pieces);
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            long started = System.nanoTime();
            for (int i = 0; i < PER_TICK && showcase.next < pieces.size(); i++) {
                showcase.place(pieces.get(showcase.next++));
            }
            showcase.ticks++;
            showcase.slowest = Math.max(showcase.slowest, (System.nanoTime() - started) / 1e6);
            if (showcase.next >= pieces.size()) {
                task.cancel();
                done.accept(new Result(showcase.placed, showcase.skipped, showcase.row + 1, showcase.ticks,
                        showcase.slowest));
            }
        }, 0L, 1L);
    }

    private void place(CustomItem item) {
        // The player's right hand, looking along where they face.
        int rightX = -forwardZ;
        int rightZ = forwardX;
        Placement.Furniture spec = (Placement.Furniture) item.placement();
        int width = spec.footprint().width();
        if (cursor + width > ROW_WIDTH / 2 + 1) {
            row++;
            cursor = -ROW_WIDTH / 2;
        }
        // The block it is placed on is (width - 1) / 2 from its left end, as seen from the front.
        int across = cursor + (width - 1) / 2;
        int ahead = FIRST_ROW + row * ROW_STEP;
        Block anchor = origin.getRelative(forwardX * ahead + rightX * across, 0, forwardZ * ahead + rightZ * across);
        cursor += width + 1;
        float yaw = FurnitureService.placementYaw(lookingYaw, spec);
        List<Block> blocks = FurnitureService.footprint(anchor, spec, yaw);
        boolean room = blocks.stream().allMatch(block -> (block.isEmpty() || block.isReplaceable())
                && !block.isLiquid() && !furniture.claims(block));
        if (!room || !player.isValid()) {
            skipped++;
            return;
        }
        anchor.setType(spec.support() == Placement.Support.LIGHT ? Material.LIGHT : Material.BARRIER, false);
        if (furniture.place(anchor, yaw, item, spec)) {
            placed++;
            if (spec.storage() != null) {
                storage.placed(anchor);
            }
        } else {
            anchor.setType(Material.AIR, false);
            skipped++;
        }
    }
}
