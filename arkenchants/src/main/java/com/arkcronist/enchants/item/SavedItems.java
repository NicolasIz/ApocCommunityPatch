package com.arkcronist.enchants.item;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Items a player keeps through death (protection scrolls, KEEP_ON_DEATH, astral items) until they respawn.
 * Written to plugins/ArkEnchants/guardados.yml at once, so a restart while someone is dead loses nothing.
 */
public final class SavedItems {

    private final File file;
    private final Logger log;
    private final YamlConfiguration data;

    public SavedItems(File folder, Logger log) {
        this.file = new File(folder, "guardados.yml");
        this.log = log;
        this.data = file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    public synchronized void add(UUID player, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        List<ItemStack> all = list(player);
        for (ItemStack i : items) {
            all.add(i.clone());
        }
        data.set(player.toString(), all);
        save();
    }

    /** Takes everything stored for the player (and forgets it). */
    public synchronized List<ItemStack> take(UUID player) {
        List<ItemStack> all = list(player);
        if (!all.isEmpty()) {
            data.set(player.toString(), null);
            save();
        }
        return all;
    }

    public synchronized boolean has(UUID player) {
        return data.contains(player.toString());
    }

    /** Hands back whatever the player has stored; what does not fit is dropped at their feet. */
    public void giveBack(Player p) {
        for (ItemStack it : take(p.getUniqueId())) {
            p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItem(p.getLocation(), rest));
        }
    }

    private List<ItemStack> list(UUID player) {
        List<ItemStack> out = new ArrayList<>();
        List<?> raw = data.getList(player.toString());
        if (raw != null) {
            for (Object o : raw) {
                if (o instanceof ItemStack i) {
                    out.add(i);
                }
            }
        }
        return out;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            log.warning("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
