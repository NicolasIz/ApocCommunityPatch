package com.arkcronist.enchants.gui;

import com.arkcronist.enchants.ArkEnchants;
import com.arkcronist.enchants.text.Colors;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** After pressing "Buscar" in a menu, the next chat line is the search (and is not sent to chat). */
public final class SearchChat implements Listener {

    private final ArkEnchants plugin;

    public SearchChat(ArkEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!plugin.menus().isSearching(p)) {
            return;
        }
        e.setCancelled(true);
        String text = Colors.plain(e.message());
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.menus().onSearchText(p, text));
    }
}
