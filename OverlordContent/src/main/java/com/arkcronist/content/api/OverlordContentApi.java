package com.arkcronist.content.api;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * 5.4: what other plugins use OverlordContent through - its items, blocks, furniture and crops, by
 * their ids ({@code namespace:id}, as in the content YAML).
 *
 * <pre>
 * OverlordContentApi content = OverlordContentApi.get();
 * content.createItem("demo:ruby", 3).ifPresent(stack -&gt; player.getInventory().addItem(stack));
 * content.blockAt(block).ifPresent(id -&gt; player.sendMessage("That is " + id));
 * </pre>
 *
 * <p>Add the plugin's jar as a {@code compileOnly} dependency and {@code OverlordContent} to the
 * plugin's {@code depend} or {@code softdepend}. The events of {@code com.arkcronist.content.api.event}
 * say what players do with the content; {@link com.arkcronist.content.api.event.OverlordContentLoadedEvent}
 * when it has been (re)loaded.</p>
 *
 * <p>Everything here is for the main thread, unless it says otherwise. Ids are only known once the
 * content has loaded: from the first tick of the server for items, a few seconds later for blocks and
 * furniture - {@link #ready()} says when.</p>
 */
public interface OverlordContentApi {

    /** The API, from Bukkit's services. */
    static OverlordContentApi get() {
        return find().orElseThrow(() -> new IllegalStateException("OverlordContent is not enabled"));
    }

    /** The API, or empty when OverlordContent is not enabled. */
    static Optional<OverlordContentApi> find() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(OverlordContentApi.class));
    }

    /** The plugin's version, {@code 5.4.0}. */
    String version();

    /** Whether the content has been built once: blocks, furniture and the resource pack are live. */
    boolean ready();

    // ---------------------------------------------------------------- items

    /** Every custom item's id; blocks, furniture and crops are items too. */
    Set<String> itemIds();

    /** A new stack of the item, or empty when there is none of that id. */
    Optional<ItemStack> createItem(String id, int amount);

    /** The id of the custom item {@code stack} is, or empty for any other stack. Any thread. */
    Optional<String> itemId(@Nullable ItemStack stack);

    // ---------------------------------------------------------------- blocks

    /** Every custom block's id, shaped ones (slabs, stairs, doors...) included. */
    Set<String> blockIds();

    /** The custom block at {@code block}, by id. */
    Optional<String> blockAt(Block block);

    /** Puts the custom block there, as if a player had placed it. False when there is no block of that id. */
    boolean placeBlock(Block block, String id);

    /** Takes the custom block there away, dropping nothing. False when there was none. */
    boolean removeBlock(Block block);

    // ---------------------------------------------------------------- furniture

    /** Every piece of furniture's id. */
    Set<String> furnitureIds();

    /** The piece of furniture standing on {@code block} - any of its blocks - by id. */
    Optional<String> furnitureAt(Block block);

    /** The piece of furniture an entity is part of - its model, a hitbox - by id. */
    Optional<String> furnitureOf(Entity entity);

    /**
     * Puts the furniture down at {@code block}, turned to {@code yaw}, where its blocks are free.
     * Recorded as a player's would be. False when it cannot stand there or there is none of that id.
     */
    boolean placeFurniture(Block block, String id, float yaw);

    /** Takes the furniture standing on {@code block} away, its item dropped when {@code drop}. */
    boolean removeFurniture(Block block, boolean drop);

    /** Plays one of the animations of the animated furniture there (Blockbench clips). */
    boolean playAnimation(Block block, String clip);

    // ---------------------------------------------------------------- crops

    /** The crop or sapling planted at {@code block}, by its item's id. */
    Optional<String> cropAt(Block block);

    /** Its stage, from 0; empty when there is no crop there. */
    Optional<Integer> cropStage(Block block);

    // ---------------------------------------------------------------- the whole

    /**
     * Reads the content again and rebuilds the resource pack, in the background; as
     * {@code /arkcontent reload}. {@link com.arkcronist.content.api.event.OverlordContentLoadedEvent}
     * says when it is done.
     */
    void reload();
}
