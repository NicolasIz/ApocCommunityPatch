package com.arkcronist.content.bukkit.crop;

import com.arkcronist.content.core.crop.CropGrowth;
import com.arkcronist.content.core.crop.CropStore;
import com.arkcronist.content.core.crop.PlantedCrop;
import com.arkcronist.content.core.definition.Placement;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The growth scheduler: every few seconds, off the main thread, adds the time that passed to every
 * crop in a loaded chunk.
 *
 * <p>All of the arithmetic happens here, on one of Bukkit's asynchronous scheduler threads: walking
 * every crop, working out how far each has grown, noticing which have finished their stage. The
 * server thread is handed only the short list of crops ready for their next stage, in one task,
 * and only when there is one - a field of ten thousand crops growing quietly costs the tick
 * nothing at all.</p>
 *
 * <p>Time is measured, not assumed: a run that comes late, because the scheduler was busy, adds
 * the time that really passed. Crops in unloaded chunks do not grow, as vanilla crops do not.</p>
 */
public final class CropTicker {

    private final Plugin plugin;
    private final CropService crops;
    private final CropStore store;
    private final int periodSeconds;

    private BukkitTask task;
    /** Touched only by the scheduler thread running {@link #tick}; runs never overlap. */
    private long lastRun;
    private long carriedNanos;

    public CropTicker(Plugin plugin, CropService crops, int periodSeconds) {
        this.plugin = plugin;
        this.crops = crops;
        this.store = crops.store();
        this.periodSeconds = periodSeconds;
    }

    public void start() {
        lastRun = System.nanoTime();
        long period = periodSeconds * 20L;
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::tickSafely, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickSafely() {
        try {
            tick();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Crop growth failed this round; it carries on next time", exception);
        }
    }

    private void tick() {
        long now = System.nanoTime();
        long nanos = now - lastRun + carriedNanos;
        lastRun = now;
        long elapsed = nanos / 1_000_000_000L;
        carriedNanos = nanos % 1_000_000_000L;
        if (elapsed <= 0) {
            return;
        }

        List<PlantedCrop> due = new ArrayList<>();
        for (UUID world : List.copyOf(store.worlds())) {
            for (PlantedCrop crop : store.crops(world)) {
                if (!crops.isLoaded(crop)) {
                    continue;
                }
                Optional<Placement.Crop> definition = crops.definition(crop);
                if (definition.isEmpty()) {
                    continue;
                }
                int stageSeconds = definition.get().stageSeconds();
                int lastStage = definition.get().lastStage();
                PlantedCrop grown = CropGrowth.grow(crop, elapsed, stageSeconds, lastStage);
                if (grown != crop && !store.update(crop, grown, false)) {
                    // Harvested or bone-mealed on the main thread since it was read: its turn is next time.
                    continue;
                }
                if (CropGrowth.due(grown, stageSeconds, lastStage)) {
                    due.add(grown);
                }
            }
        }
        if (!due.isEmpty() && plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> crops.advance(due));
        }
    }
}
