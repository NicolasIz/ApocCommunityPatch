package com.arkcronist.content.bukkit.hooks.packetevents;

import com.arkcronist.content.bukkit.bedrock.WideGlyphText;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.chat.ChatType;
import com.github.retrooper.packetevents.protocol.chat.message.ChatMessage_v1_19_3;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBossBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisguisedChat;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerListHeaderAndFooter;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerScoreboardObjective;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleSubtitle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleText;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 3.3.3: a glyph too wide for Bedrock's one-cell glyphs - a rank's banner in a scoreboard - is
 * written, in what a Bedrock player is sent, as the pieces the converter cut it into, which the
 * .mcpack draws side by side ({@code BedrockConverter.Build#glyphText}). Java players are sent the
 * text untouched.
 *
 * <p>On the way out, on Netty's threads: only the packets that carry such text - chat from the
 * server and players' names in chat, the action bar, titles, the tab list, the sidebar and teams,
 * boss bars - and only for Bedrock players. A packet without any of those characters is left as it
 * was.</p>
 */
public final class BedrockGlyphText extends PacketListenerAbstract {

    private static final Set<PacketTypeCommon> CARRYING_TEXT = Set.of(PacketType.Play.Server.SYSTEM_CHAT_MESSAGE,
            PacketType.Play.Server.DISGUISED_CHAT, PacketType.Play.Server.ACTION_BAR,
            PacketType.Play.Server.SET_TITLE_TEXT, PacketType.Play.Server.SET_TITLE_SUBTITLE,
            PacketType.Play.Server.PLAYER_LIST_HEADER_AND_FOOTER, PacketType.Play.Server.PLAYER_INFO_UPDATE,
            PacketType.Play.Server.SCOREBOARD_OBJECTIVE, PacketType.Play.Server.UPDATE_SCORE,
            PacketType.Play.Server.TEAMS, PacketType.Play.Server.BOSS_BAR, PacketType.Play.Server.CHAT_MESSAGE);

    private final Predicate<UUID> bedrock;
    private final Supplier<Map<Integer, String>> pieces;
    private volatile @Nullable Map<Integer, String> madeFor;
    private volatile @Nullable TextReplacementConfig replacement;

    /**
     * @param bedrock whether a player plays on Bedrock - from any thread
     * @param pieces  the build Geyser has: wide character to the characters of its pieces
     */
    public BedrockGlyphText(Predicate<UUID> bedrock, Supplier<Map<Integer, String>> pieces) {
        super(PacketListenerPriority.HIGH);
        this.bedrock = bedrock;
        this.pieces = pieces;
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        PacketTypeCommon type = event.getPacketType();
        if (!CARRYING_TEXT.contains(type)) {
            return;
        }
        UUID player = event.getUser().getUUID();
        if (player == null || !bedrock.test(player)) {
            return;
        }
        TextReplacementConfig config = replacement();
        if (config == null) {
            return;
        }
        if (rewrite(event, type, config)) {
            event.markForReEncode(true);
        }
    }

    private boolean rewrite(PacketSendEvent event, PacketTypeCommon type, TextReplacementConfig config) {
        boolean changed = false;
        if (type == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
            WrapperPlayServerSystemChatMessage packet = new WrapperPlayServerSystemChatMessage(event);
            Component text = written(packet.getMessage(), config);
            if (text != null) {
                packet.setMessage(text);
                changed = true;
            }
        } else if (type == PacketType.Play.Server.DISGUISED_CHAT) {
            WrapperPlayServerDisguisedChat packet = new WrapperPlayServerDisguisedChat(event);
            Component text = written(packet.getMessage(), config);
            if (text != null) {
                packet.setMessage(text);
                changed = true;
            }
        } else if (type == PacketType.Play.Server.ACTION_BAR) {
            WrapperPlayServerActionBar packet = new WrapperPlayServerActionBar(event);
            Component text = written(packet.getActionBarText(), config);
            if (text != null) {
                packet.setActionBarText(text);
                changed = true;
            }
        } else if (type == PacketType.Play.Server.SET_TITLE_TEXT) {
            WrapperPlayServerSetTitleText packet = new WrapperPlayServerSetTitleText(event);
            Component text = written(packet.getTitle(), config);
            if (text != null) {
                packet.setTitle(text);
                changed = true;
            }
        } else if (type == PacketType.Play.Server.SET_TITLE_SUBTITLE) {
            WrapperPlayServerSetTitleSubtitle packet = new WrapperPlayServerSetTitleSubtitle(event);
            Component text = written(packet.getSubtitle(), config);
            if (text != null) {
                packet.setSubtitle(text);
                changed = true;
            }
        } else if (type == PacketType.Play.Server.PLAYER_LIST_HEADER_AND_FOOTER) {
            WrapperPlayServerPlayerListHeaderAndFooter packet = new WrapperPlayServerPlayerListHeaderAndFooter(event);
            Component header = written(packet.getHeader(), config);
            Component footer = written(packet.getFooter(), config);
            if (header != null) {
                packet.setHeader(header);
            }
            if (footer != null) {
                packet.setFooter(footer);
            }
            changed = header != null || footer != null;
        } else if (type == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(event);
            if (packet.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME)) {
                for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry : packet.getEntries()) {
                    Component name = written(entry.getDisplayName(), config);
                    if (name != null) {
                        entry.setDisplayName(name);
                        changed = true;
                    }
                }
            }
        } else if (type == PacketType.Play.Server.SCOREBOARD_OBJECTIVE) {
            WrapperPlayServerScoreboardObjective packet = new WrapperPlayServerScoreboardObjective(event);
            Component name = written(packet.getDisplayName(), config);
            if (name != null) {
                packet.setDisplayName(name);
                changed = true;
            }
        } else if (type == PacketType.Play.Server.UPDATE_SCORE) {
            WrapperPlayServerUpdateScore packet = new WrapperPlayServerUpdateScore(event);
            Component name = written(packet.getEntityDisplayName(), config);
            if (name != null) {
                packet.setEntityDisplayName(name);
                changed = true;
            }
        } else if (type == PacketType.Play.Server.TEAMS) {
            WrapperPlayServerTeams packet = new WrapperPlayServerTeams(event);
            WrapperPlayServerTeams.ScoreBoardTeamInfo info = packet.getTeamInfo().orElse(null);
            if (info != null) {
                Component prefix = written(info.getPrefix(), config);
                Component suffix = written(info.getSuffix(), config);
                Component name = written(info.getDisplayName(), config);
                if (prefix != null) {
                    info.setPrefix(prefix);
                }
                if (suffix != null) {
                    info.setSuffix(suffix);
                }
                if (name != null) {
                    info.setDisplayName(name);
                }
                changed = prefix != null || suffix != null || name != null;
            }
        } else if (type == PacketType.Play.Server.CHAT_MESSAGE) {
            // A player's chat: the rank is in the sender's name, or in the unsigned content a chat
            // plugin decorated - never in the signed words, which are left as they were signed.
            WrapperPlayServerChatMessage packet = new WrapperPlayServerChatMessage(event);
            if (packet.getMessage() instanceof ChatMessage_v1_19_3 message) {
                Component unsigned = written(message.getUnsignedChatContent().orElse(null), config);
                if (unsigned != null) {
                    message.setUnsignedChatContent(unsigned);
                    changed = true;
                }
                ChatType.Bound format = message.getChatFormatting();
                if (format != null) {
                    Component name = written(format.getName(), config);
                    Component target = written(format.getTargetName(), config);
                    if (name != null) {
                        format.setName(name);
                    }
                    if (target != null) {
                        format.setTargetName(target);
                    }
                    changed |= name != null || target != null;
                }
            }
        } else if (type == PacketType.Play.Server.BOSS_BAR) {
            WrapperPlayServerBossBar packet = new WrapperPlayServerBossBar(event);
            Component title = written(packet.getTitle(), config);
            if (title != null) {
                packet.setTitle(title);
                changed = true;
            }
        }
        return changed;
    }

    private static @Nullable Component written(@Nullable Component text, TextReplacementConfig config) {
        return WideGlyphText.written(text, config);
    }

    /** The replacement for the build Geyser has now; null while there is nothing to replace. */
    private @Nullable TextReplacementConfig replacement() {
        Map<Integer, String> now = pieces.get();
        if (now == null || now.isEmpty()) {
            return null;
        }
        if (now != madeFor) {
            replacement = WideGlyphText.replacementFor(now);
            madeFor = now;
        }
        return replacement;
    }
}
