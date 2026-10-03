package com.arkcronist.content.bukkit.hooks.decentholograms;

import com.arkcronist.content.bukkit.crop.CropService;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.crop.PlantedCrop;
import com.arkcronist.content.core.definition.Placement;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A look at a crop through DecentHolograms: sneak and right-click a custom crop with an empty hand,
 * and a hologram above it - seen by that player alone - tells its name, its stage and whether it is
 * ripe, for a few seconds.
 *
 * <p>Holograms made here are never saved to DecentHolograms' files: they exist for a few seconds
 * and are gone with a restart. Any hologram of your own can show this plugin's emojis through
 * PlaceholderAPI, {@code %arkcontent_emoji_<name>%}. An {@code #ICON} line cannot show a custom
 * item's model: DecentHolograms keeps only an icon's material and custom model data, not the
 * {@code item_model} these items are drawn with.</p>
 */
public final class DecentHologramsHook implements Listener, AutoCloseable {

    private static final long SHOWN_TICKS = 5 * 20;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Plugin plugin;
    private final Supplier<CropService> crops;
    private final ItemRegistry items;
    /** The hologram each player is looking at, by name. */
    private final Map<UUID, String> shown = new HashMap<>();
    private int next;

    public DecentHologramsHook(Plugin plugin, Supplier<CropService> crops, ItemRegistry items) {
        this.plugin = plugin;
        this.crops = crops;
        this.items = items;
    }

    // Not ignoreCancelled: the crop's own listener cancels clicks it takes for itself.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInspect(PlayerInteractEvent event) {
        Action action = event.getAction();
        Player player = event.getPlayer();
        if (action != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || !player.isSneaking()
                || !player.getInventory().getItemInMainHand().isEmpty()) {
            return;
        }
        CropService service = crops.get();
        Optional<Block> target = service == null ? Optional.empty() : service.target(player);
        if (target.isEmpty()) {
            return;
        }
        Optional<PlantedCrop> crop = service.at(target.get());
        Optional<Placement.Crop> definition = crop.flatMap(service::definition);
        if (crop.isPresent() && definition.isPresent()) {
            show(player, target.get(), crop.get(), definition.get());
        }
    }

    private void show(Player player, Block block, PlantedCrop crop, Placement.Crop definition) {
        hide(player.getUniqueId());
        String name = "arkcontent_crop_" + (next++);
        Location at = block.getLocation().add(0.5, 1.6, 0.5);
        Hologram hologram = DHAPI.createHologram(name, at, false);
        hologram.setDefaultVisibleState(false);
        hologram.setShowPlayer(player);
        for (String line : lines(crop, definition)) {
            DHAPI.addHologramLine(hologram, line);
        }
        shown.put(player.getUniqueId(), name);
        UUID viewer = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (name.equals(shown.get(viewer))) {
                hide(viewer);
            }
        }, SHOWN_TICKS);
    }

    /** The crop's name, its stage with a bar, and whether it can be harvested. */
    private List<String> lines(PlantedCrop crop, Placement.Crop definition) {
        Optional<CustomItem> item = items.get(crop.cropId());
        String title = item.map(CustomItem::displayName).map(LEGACY::serialize).orElse("§a" + crop.cropId());
        int stage = crop.stage() + 1;
        int stages = definition.lastStage() + 1;
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 1; i <= stages; i++) {
            bar.append(i <= stage ? "§a■" : "§7■");
        }
        bar.append("§8]");
        List<String> lines = new ArrayList<>();
        lines.add(title);
        lines.add("§7Stage §f" + stage + "§7/§f" + stages + " " + bar);
        lines.add(crop.stage() >= definition.lastStage() ? "§aReady to harvest" : "§7Growing...");
        return lines;
    }

    private void hide(UUID viewer) {
        String name = shown.remove(viewer);
        if (name != null) {
            DHAPI.removeHologram(name);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hide(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        for (UUID viewer : List.copyOf(shown.keySet())) {
            hide(viewer);
        }
    }
}
