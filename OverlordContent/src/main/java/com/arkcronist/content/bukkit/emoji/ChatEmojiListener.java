package com.arkcronist.content.bukkit.emoji;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * {@code :ruby:} in chat becomes the ruby emoji.
 *
 * <p>Paper hands chat to plugins off the main thread, and this stays there: the registry is read
 * without locks and the replacement is pure component work, so a busy chat costs the server tick
 * nothing. It works on the message component before it is rendered, so chat plugins that format
 * the line - EssentialsChat, and anything using Paper's or Spigot's chat events - format a message
 * that already holds the emoji.</p>
 */
public final class ChatEmojiListener implements Listener {

    private final EmojiRegistry emojis;

    public ChatEmojiListener(EmojiRegistry emojis) {
        this.emojis = emojis;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.message(emojis.replace(event.message(), event.getPlayer()));
    }
}
