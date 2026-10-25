package com.arkcronist.content.core.definition;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * One content entry exactly as a content file describes it, before anything has been checked
 * against the server.
 *
 * <p>Nothing here touches Bukkit: the material is still the text that was written and the name is
 * still MiniMessage source. That keeps the loader and the pack compiler testable without a running
 * server; the plugin layer turns a definition into a {@code CustomItem} once it has confirmed the
 * material exists.</p>
 *
 * <p>Custom blocks and furniture are definitions too. They are items until they are placed, and
 * {@link #placement()} says what placing them does.</p>
 *
 * @param namespace   the namespace the item and its assets live in
 * @param id          unique within the namespace, across every type
 * @param material    the base material as written, e.g. {@code DIAMOND_SWORD}; implied by the
 *                    type for blocks ({@code NOTE_BLOCK}) and furniture (the support block)
 * @param displayName MiniMessage source, or null to keep the material's own name
 * @param lore        MiniMessage source, one entry per line
 * @param model       how it is drawn, or null to keep the material's own look
 * @param placement   what placing it does, or null for a plain item
 * @param source      the content file it came from, for messages
 * @param price       what one costs in the content shop ({@code /arkcontent shop}, with Vault);
 *                    0 when it is not for sale
 * @param equipment   how it is worn - armour - or null for an item that is not
 * @param gun         how it shoots, or null for an item that does not
 * @param drink       3.4: how it is drunk from - a canteen - or null for an item that is not
 * @param backpack    3.4: what it holds - a backpack - or null for an item that holds nothing
 * @param vehicle     4.0: how it is driven - the item that puts a vehicle down - or null
 * @param emote       5.0: the emote it plays when used, or null
 */
public record ItemDefinition(String namespace, String id, String material, String displayName,
                             List<String> lore, ModelSource model, ItemBehaviour behaviour,
                             Placement placement, Path source, double price, @Nullable Equipment equipment,
                             @Nullable GunDefinition gun,
                             @Nullable com.arkcronist.content.core.minigame.MinigameDefinition minigame,
                             @Nullable com.arkcronist.content.core.cosmetic.CosmeticDefinition cosmetic,
                             @Nullable DrinkDefinition drink, @Nullable BackpackDefinition backpack,
                             @Nullable com.arkcronist.content.core.vehicle.VehicleDefinition vehicle,
                             @Nullable com.arkcronist.content.core.emotes.EmoteDefinition emote) {

    public ItemDefinition {
        lore = List.copyOf(lore);
    }

    /** An item that plays no emote. */
    public ItemDefinition(String namespace, String id, String material, String displayName, List<String> lore,
                          ModelSource model, ItemBehaviour behaviour, Placement placement, Path source, double price,
                          @Nullable Equipment equipment, @Nullable GunDefinition gun,
                          @Nullable com.arkcronist.content.core.minigame.MinigameDefinition minigame,
                          @Nullable com.arkcronist.content.core.cosmetic.CosmeticDefinition cosmetic,
                          @Nullable DrinkDefinition drink, @Nullable BackpackDefinition backpack,
                          @Nullable com.arkcronist.content.core.vehicle.VehicleDefinition vehicle) {
        this(namespace, id, material, displayName, lore, model, behaviour, placement, source, price, equipment, gun,
                minigame, cosmetic, drink, backpack, vehicle, null);
    }

    /** An item that is not a vehicle. */
    public ItemDefinition(String namespace, String id, String material, String displayName, List<String> lore,
                          ModelSource model, ItemBehaviour behaviour, Placement placement, Path source, double price,
                          @Nullable Equipment equipment, @Nullable GunDefinition gun,
                          @Nullable com.arkcronist.content.core.minigame.MinigameDefinition minigame,
                          @Nullable com.arkcronist.content.core.cosmetic.CosmeticDefinition cosmetic,
                          @Nullable DrinkDefinition drink, @Nullable BackpackDefinition backpack) {
        this(namespace, id, material, displayName, lore, model, behaviour, placement, source, price, equipment, gun,
                minigame, cosmetic, drink, backpack, null);
    }

    /** An item neither drunk from nor holding anything. */
    public ItemDefinition(String namespace, String id, String material, String displayName, List<String> lore,
                          ModelSource model, ItemBehaviour behaviour, Placement placement, Path source, double price,
                          @Nullable Equipment equipment, @Nullable GunDefinition gun,
                          @Nullable com.arkcronist.content.core.minigame.MinigameDefinition minigame,
                          @Nullable com.arkcronist.content.core.cosmetic.CosmeticDefinition cosmetic) {
        this(namespace, id, material, displayName, lore, model, behaviour, placement, source, price, equipment, gun,
                minigame, cosmetic, null, null);
    }

    /** An item with no minigame and not worn as a cosmetic. */
    public ItemDefinition(String namespace, String id, String material, String displayName, List<String> lore,
                          ModelSource model, ItemBehaviour behaviour, Placement placement, Path source, double price,
                          @Nullable Equipment equipment, @Nullable GunDefinition gun) {
        this(namespace, id, material, displayName, lore, model, behaviour, placement, source, price, equipment, gun,
                null, null);
    }

    /** An item that is not for sale. */
    public ItemDefinition(String namespace, String id, String material, String displayName, List<String> lore,
                          ModelSource model, ItemBehaviour behaviour, Placement placement, Path source) {
        this(namespace, id, material, displayName, lore, model, behaviour, placement, source, 0, null, null);
    }

    /** An item that is not worn. */
    public ItemDefinition(String namespace, String id, String material, String displayName, List<String> lore,
                          ModelSource model, ItemBehaviour behaviour, Placement placement, Path source, double price) {
        this(namespace, id, material, displayName, lore, model, behaviour, placement, source, price, null, null);
    }

    /** An item that does not shoot. */
    public ItemDefinition(String namespace, String id, String material, String displayName, List<String> lore,
                          ModelSource model, ItemBehaviour behaviour, Placement placement, Path source, double price,
                          @Nullable Equipment equipment) {
        this(namespace, id, material, displayName, lore, model, behaviour, placement, source, price, equipment, null);
    }

    /** The same item with a price: what one costs in the content shop, through Vault. 0 is not for sale. */
    public ItemDefinition withPrice(double price) {
        return new ItemDefinition(namespace, id, material, displayName, lore, model, behaviour, placement, source, price,
                equipment, gun, minigame, cosmetic, drink, backpack, vehicle, emote);
    }

    /** The same item, worn as {@code equipment}. */
    public ItemDefinition withEquipment(@Nullable Equipment equipment) {
        return new ItemDefinition(namespace, id, material, displayName, lore, model, behaviour, placement, source, price,
                equipment, gun, minigame, cosmetic, drink, backpack, vehicle, emote);
    }

    /** The same item, played as {@code minigame} (3.0): a rare ore's, or a fishing rod's. */
    public ItemDefinition withMinigame(@Nullable com.arkcronist.content.core.minigame.MinigameDefinition minigame) {
        return new ItemDefinition(namespace, id, material, displayName, lore, model, behaviour, placement, source, price,
                equipment, gun, minigame, cosmetic, drink, backpack, vehicle, emote);
    }

    /** The same item, worn as {@code cosmetic} (3.0). */
    public ItemDefinition withCosmetic(@Nullable com.arkcronist.content.core.cosmetic.CosmeticDefinition cosmetic) {
        return new ItemDefinition(namespace, id, material, displayName, lore, model, behaviour, placement, source, price,
                equipment, gun, minigame, cosmetic, drink, backpack, vehicle, emote);
    }

    /** The same item, shooting as {@code gun}. */
    public ItemDefinition withGun(@Nullable GunDefinition gun) {
        return new ItemDefinition(namespace, id, material, displayName, lore, model, behaviour, placement, source, price,
                equipment, gun, minigame, cosmetic, drink, backpack, vehicle, emote);
    }

    /** The same item, drunk from as {@code drink} (3.4). */
    public ItemDefinition withDrink(@Nullable DrinkDefinition drink) {
        return new ItemDefinition(namespace, id, material, displayName, lore, model, behaviour, placement, source, price,
                equipment, gun, minigame, cosmetic, drink, backpack, vehicle, emote);
    }

    /** The same item, holding others as {@code backpack} (3.4). */
    public ItemDefinition withBackpack(@Nullable BackpackDefinition backpack) {
        return new ItemDefinition(namespace, id, material, displayName, lore, model, behaviour, placement, source, price,
                equipment, gun, minigame, cosmetic, drink, backpack, vehicle, emote);
    }

    /** The same item, driven as {@code vehicle} (4.0). */
    public ItemDefinition withVehicle(@Nullable com.arkcronist.content.core.vehicle.VehicleDefinition vehicle) {
        return new ItemDefinition(namespace, id, material, displayName, lore, model, behaviour, placement, source, price,
                equipment, gun, minigame, cosmetic, drink, backpack, vehicle, emote);
    }

    /** The same item, playing {@code emote} (5.0) when used, or with /emote. */
    public ItemDefinition withEmote(@Nullable com.arkcronist.content.core.emotes.EmoteDefinition emote) {
        return new ItemDefinition(namespace, id, material, displayName, lore, model, behaviour, placement, source, price,
                equipment, gun, minigame, cosmetic, drink, backpack, vehicle, emote);
    }

    /** The same item, made of another material - 5.3: a shaped block, of the copper block it was given. */
    public ItemDefinition withMaterial(String material) {
        return new ItemDefinition(namespace, id, material, displayName, lore, model, behaviour, placement, source, price,
                equipment, gun, minigame, cosmetic, drink, backpack, vehicle, emote);
    }

    /** The same item, placing something else - 5.3: a see-through block left without copper, as a note block. */
    public ItemDefinition withPlacement(Placement placement) {
        return new ItemDefinition(namespace, id, material, displayName, lore, model, behaviour, placement, source, price,
                equipment, gun, minigame, cosmetic, drink, backpack, vehicle, emote);
    }

    /** {@code namespace:id}, the key the item is registered, given and recognised by. */
    public String fullId() {
        return namespace + ":" + id;
    }

    public ContentType type() {
        return placement == null ? ContentType.ITEM : placement.type();
    }

    /**
     * The item definition the client is pointed at: {@code assets/<namespace>/items/<id>.json}.
     *
     * <p>Since 1.21.4 an item stack's {@code item_model} component names one of these files, not a
     * model. The indirection is what lets a stack already sitting in a chest pick up a new model: the
     * stack only ever stores this key, and the pack decides what it draws.</p>
     *
     * <p>An item whose look is an item definition file ({@code resource.item-model}) is pointed at
     * that file instead.</p>
     */
    public ResourceLocation itemModel() {
        if (model instanceof ModelSource.Definition definition) {
            return definition.location();
        }
        return new ResourceLocation(namespace, id);
    }
}
