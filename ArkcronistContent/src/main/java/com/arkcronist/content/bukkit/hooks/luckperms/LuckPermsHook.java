package com.arkcronist.content.bukkit.hooks.luckperms;

import com.arkcronist.content.bukkit.emoji.EmojiRegistry;
import com.arkcronist.content.bukkit.hooks.PlaceholderSource;
import com.arkcronist.content.core.text.PrefixGlyphs;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * LuckPerms (3.0): a rank's prefix, suffix or meta with this plugin's emojis drawn in it.
 *
 * <pre>
 *   %arkcontent_luckperms_prefix%        &amp;c:crown: Admin  -&gt;  the crown's image, then "Admin" in red
 *   %arkcontent_luckperms_suffix%
 *   %arkcontent_luckperms_meta_&lt;key&gt;%
 * </pre>
 *
 * <p>Read from LuckPerms' cached data, which it keeps for players online and lets any thread read:
 * TAB, a chat plugin or a scoreboard can ask from wherever they run. The prefix is the admin's own
 * text - an emoji's permission, meant for what players type, is not asked. Offline players, or a
 * player with no prefix, give an empty string.</p>
 */
public final class LuckPermsHook implements PlaceholderSource {

    private static final String PREFIX = "luckperms_";

    private final LuckPerms luckPerms;
    private final EmojiRegistry emojis;

    public LuckPermsHook(EmojiRegistry emojis) {
        this.luckPerms = LuckPermsProvider.get();
        this.emojis = emojis;
    }

    @Override
    public @Nullable String resolve(@Nullable OfflinePlayer player, String params) {
        if (!params.startsWith(PREFIX)) {
            return null;
        }
        String what = params.substring(PREFIX.length());
        if (!what.equals("prefix") && !what.equals("suffix") && !what.startsWith("meta_")) {
            return null;
        }
        if (player == null) {
            return "";
        }
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return "";
        }
        CachedMetaData meta = user.getCachedData().getMetaData();
        String raw = switch (what) {
            case "prefix" -> meta.getPrefix();
            case "suffix" -> meta.getSuffix();
            default -> meta.getMetaValue(what.substring("meta_".length()));
        };
        return raw == null ? "" : withEmojis(raw);
    }

    /** {@code text} with each {@code :name:} of a loaded emoji drawn as its glyph. */
    public String withEmojis(String text) {
        return PrefixGlyphs.replace(text, name -> emojis.get(name).map(EmojiRegistry.Emoji::glyph).orElse(null));
    }
}
