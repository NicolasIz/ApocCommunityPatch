package com.arkcronist.content.bukkit.emote;

import com.arkcronist.content.core.emotes.EmoteClock;
import com.arkcronist.content.core.emotes.EmoteDefinition;
import com.arkcronist.content.core.emotes.EmotePose;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 5.0: one player's emote while it plays - where its rig stands, what each of its displays shows,
 * the ids they have on the clients, and how far the animation has got.
 *
 * <p>Made and ended on the main thread; its clock is stepped only on {@code ArkContent-Emotes}, and
 * the pose last sent is read by whichever thread shows the rig to a player who comes into sight.</p>
 */
public final class Rig {

    final UUID player;
    final String itemId;
    final EmoteDefinition emote;
    /** Where the rig stands: the player's feet, turned the way they looked. */
    final Location at;
    /** Per bone: what its display shows, or null for a bone with no display. */
    final @Nullable ItemStack[] items;
    /** Per bone: the display's id and uuid on the clients, for packets. */
    final int[] entityIds;
    final UUID[] uuids;
    final EmoteClock clock;
    /** Whether the player was invisible before - by a potion, another plugin - and so stays so after. */
    final boolean wasInvisible;
    private volatile List<EmotePose.Part> latest;
    private volatile boolean ended;

    Rig(UUID player, String itemId, EmoteDefinition emote, Location at, @Nullable ItemStack[] items, int[] entityIds,
        UUID[] uuids, boolean wasInvisible, List<EmotePose.Part> pose) {
        this.player = player;
        this.itemId = itemId;
        this.emote = emote;
        this.at = at.clone();
        this.items = items;
        this.entityIds = entityIds;
        this.uuids = uuids;
        this.clock = new EmoteClock(emote);
        this.wasInvisible = wasInvisible;
        this.latest = pose;
    }

    public UUID player() {
        return player;
    }

    public String itemId() {
        return itemId;
    }

    public EmoteDefinition emote() {
        return emote;
    }

    public Location at() {
        return at.clone();
    }

    public @Nullable ItemStack item(int bone) {
        return items[bone];
    }

    public int entityId(int bone) {
        return entityIds[bone];
    }

    public UUID uuid(int bone) {
        return uuids[bone];
    }

    public int bones() {
        return items.length;
    }

    /** The pose last sent: what a player who comes into sight is shown. */
    public List<EmotePose.Part> latest() {
        return latest;
    }

    void latest(List<EmotePose.Part> pose) {
        this.latest = pose;
    }

    boolean ended() {
        return ended;
    }

    /** Marks it over; true the first time. */
    synchronized boolean end() {
        if (ended) {
            return false;
        }
        ended = true;
        return true;
    }
}
