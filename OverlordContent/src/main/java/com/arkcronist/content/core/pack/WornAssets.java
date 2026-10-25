package com.arkcronist.content.core.pack;

import com.arkcronist.content.core.definition.Equipment;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * How armour that says nothing about how it is worn is drawn on the player, read from the pack it
 * is drawn with.
 *
 * <p>An armour piece with no {@code equipment:} section is worn as its material - a
 * {@code NETHERITE_CHESTPLATE} as netherite. That is right for a chestplate meant to look like
 * netherite, and wrong for the pieces of a generated ItemsAdder pack, which only has their icons: a
 * set whose 3D look comes from another plugin's pack - MythicArmor's
 * {@code mythicarmor:darksteel_knight_armor_chest_piece} - has nothing that ties it to
 * {@code darksteel:darksteel_knight_chestplate_item} but the name. So each such piece is matched by
 * its name against the equipment assets the built pack has:</p>
 *
 * <ul>
 *   <li>the words that only say which piece it is ({@code chestplate}, {@code chest}, {@code peto})
 *       or that it is armour ({@code item}, {@code piece}, {@code armor}, {@code armadura}) are left
 *       out of both names, and what remains has to be the same: {@code darksteel_knight};</li>
 *   <li>an asset named for one slot is only worn in that slot; one named for none, the whole set,
 *       in any;</li>
 *   <li>several fitting: the one named for the slot, then the one in the item's own namespace -
 *       still several, none, and the piece is left as it was.</li>
 * </ul>
 *
 * <p>A helmet no asset fits, whose model is 3D and says how it sits on a head
 * ({@code display.head}), is worn as that model. Leather is left alone either way: dyed armour
 * takes its look from the stack's colour, which a pack does not show.</p>
 *
 * <p>An item with an {@code equipment:} section of its own is never touched: writing one is how
 * to keep a piece as its material, or wear it as something else.</p>
 */
public final class WornAssets {

    /** The words that name the piece: where it is worn, in the languages packs are written in. */
    private static final Map<String, Equipment.Slot> SLOT_WORDS = slotWords();

    /** The words that only say it is armour, or part of a set. */
    private static final Set<String> ARMOUR_WORDS = Set.of("item", "items", "piece", "pieces", "armor", "armour",
            "armadura", "armaduras", "set", "suit", "gear", "equipment");

    /** How deep a model's parents are followed for its elements and its head display. */
    private static final int PARENTS = 8;

    private WornAssets() {
    }

    /**
     * What was found.
     *
     * @param equipment the equipment of each piece given one, by full id
     * @param linked    each piece given an asset, as {@code item -> asset}
     * @param onHead    each helmet worn as its 3D model
     * @param ambiguous each piece several assets fit, left as it was
     */
    public record Result(Map<String, Equipment> equipment, List<String> linked, List<String> onHead,
                         List<String> ambiguous) {

        public static final Result NONE = new Result(Map.of(), List.of(), List.of(), List.of());

        public Result {
            equipment = Map.copyOf(equipment);
            linked = List.copyOf(linked);
            onHead = List.copyOf(onHead);
            ambiguous = List.copyOf(ambiguous);
        }
    }

    /**
     * @param items  the items loaded
     * @param assets the equipment assets the built pack has: {@code assets/<ns>/equipment/<name>.json}
     * @param pack   a file of the built pack by its path in it, null when there is none
     */
    public static Result link(Collection<ItemDefinition> items, Collection<ResourceLocation> assets,
                              Function<String, byte[]> pack) {
        Map<String, List<Candidate>> byKey = new LinkedHashMap<>();
        for (ResourceLocation asset : assets) {
            Candidate candidate = candidate(asset);
            if (candidate != null) {
                byKey.computeIfAbsent(candidate.key(), key -> new ArrayList<>()).add(candidate);
            }
        }
        Map<String, Equipment> equipment = new LinkedHashMap<>();
        List<String> linked = new ArrayList<>();
        List<String> onHead = new ArrayList<>();
        List<String> ambiguous = new ArrayList<>();
        for (ItemDefinition item : items) {
            Equipment.Slot slot = slotOf(item.material());
            if (slot == null || item.equipment() != null || item.placement() != null
                    || item.material().toUpperCase(Locale.ROOT).startsWith("LEATHER_")) {
                continue;
            }
            Path root = item.model() != null ? item.model().sourceRoot() : item.source();
            String key = key(item.id(), null);
            List<Candidate> fitting = key == null ? List.of() : byKey.getOrDefault(key, List.of()).stream()
                    .filter(candidate -> candidate.slot() == null || candidate.slot() == slot)
                    .toList();
            List<Candidate> chosen = narrow(fitting, item.namespace());
            if (chosen.size() == 1) {
                ResourceLocation asset = chosen.getFirst().asset();
                equipment.put(item.fullId(), new Equipment(slot, asset, List.of(), null, root));
                linked.add(item.fullId() + " -> " + asset);
                continue;
            }
            if (chosen.size() > 1) {
                ambiguous.add(item.fullId() + " (" + chosen.stream().map(candidate -> candidate.asset().toString())
                        .collect(Collectors.joining(", ")) + ")");
                continue;
            }
            if (slot == Equipment.Slot.HEAD && item.model() != null && wornOnHead(item.itemModel(), pack)) {
                equipment.put(item.fullId(), new Equipment(slot, null, List.of(), null, root));
                onHead.add(item.fullId());
            }
        }
        return new Result(equipment, linked, onHead, ambiguous);
    }

    /** An asset by the slot its name says and the rest of its name; null for one it cannot be worn by. */
    private record Candidate(ResourceLocation asset, @Nullable Equipment.Slot slot, String key) {
    }

    private static @Nullable Candidate candidate(ResourceLocation asset) {
        String name = asset.path().substring(asset.path().lastIndexOf('/') + 1);
        EnumSet<Equipment.Slot> slots = EnumSet.noneOf(Equipment.Slot.class);
        String key = key(name, slots);
        if (key == null || slots.size() > 1) {
            // Named for two slots at once: no telling which piece it draws.
            return null;
        }
        return new Candidate(asset, slots.isEmpty() ? null : slots.iterator().next(), key);
    }

    /** Of several assets that fit: the ones named for a slot, then the ones in the item's namespace. */
    private static List<Candidate> narrow(List<Candidate> fitting, String namespace) {
        List<Candidate> chosen = fitting;
        List<Candidate> named = chosen.stream().filter(candidate -> candidate.slot() != null).toList();
        if (!named.isEmpty()) {
            chosen = named;
        }
        if (chosen.size() > 1) {
            List<Candidate> own = chosen.stream().filter(candidate -> candidate.asset().namespace().equals(namespace))
                    .toList();
            if (!own.isEmpty()) {
                chosen = own;
            }
        }
        return chosen;
    }

    /**
     * A name's words with the ones that say which piece it is and that it is armour left out:
     * {@code darksteel_knight_chestplate_item} and {@code darksteel_knight_armor_chest_piece} are
     * both {@code darksteel_knight}. Null when nothing is left.
     *
     * @param slots gets the slots the name's words named, when not null
     */
    static @Nullable String key(String name, @Nullable Set<Equipment.Slot> slots) {
        List<String> kept = new ArrayList<>();
        for (String word : name.toLowerCase(Locale.ROOT).split("[_.\\-\\s]+")) {
            if (word.isEmpty() || ARMOUR_WORDS.contains(word)) {
                continue;
            }
            Equipment.Slot slot = SLOT_WORDS.get(word);
            if (slot != null) {
                if (slots != null) {
                    slots.add(slot);
                }
                continue;
            }
            kept.add(word);
        }
        return kept.isEmpty() ? null : String.join("_", kept);
    }

    /** The slot a piece made of this vanilla material is worn in, or null for one that is no armour. */
    static @Nullable Equipment.Slot slotOf(String material) {
        String name = material.toUpperCase(Locale.ROOT);
        if (name.endsWith("_HELMET")) {
            return Equipment.Slot.HEAD;
        }
        if (name.endsWith("_CHESTPLATE")) {
            return Equipment.Slot.CHEST;
        }
        if (name.endsWith("_LEGGINGS")) {
            return Equipment.Slot.LEGS;
        }
        return name.endsWith("_BOOTS") ? Equipment.Slot.FEET : null;
    }

    /**
     * Whether the model an item definition draws is a 3D one made to sit on a head: it has
     * elements, and a {@code display.head} - in it or in one of its parents.
     */
    static boolean wornOnHead(ResourceLocation itemModel, Function<String, byte[]> pack) {
        JsonObject definition = json(pack.apply(itemModel.assetPath("items", ".json")));
        String drawn = definition == null ? null : ItemModels.firstModel(definition.get("model"));
        if (drawn == null) {
            return false;
        }
        boolean elements = false;
        boolean head = false;
        ResourceLocation model;
        try {
            model = ResourceLocation.parse(drawn, ResourceLocation.MINECRAFT);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        for (int depth = 0; depth < PARENTS && model != null && !(elements && head); depth++) {
            JsonObject json = json(pack.apply(model.assetPath("models", ".json")));
            if (json == null) {
                break;
            }
            elements |= json.get("elements") instanceof JsonArray array && !array.isEmpty();
            head |= json.get("display") instanceof JsonObject display && display.has("head");
            model = null;
            if (json.get("parent") instanceof JsonElement parent && parent.isJsonPrimitive()) {
                try {
                    model = ResourceLocation.parse(parent.getAsString(), ResourceLocation.MINECRAFT);
                } catch (IllegalArgumentException invalid) {
                    break;
                }
            }
        }
        return elements && head;
    }

    private static @Nullable JsonObject json(@Nullable byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)) instanceof JsonObject object
                    ? object : null;
        } catch (RuntimeException broken) {
            return null;
        }
    }

    private static Map<String, Equipment.Slot> slotWords() {
        Map<String, Equipment.Slot> words = new LinkedHashMap<>();
        put(words, Equipment.Slot.HEAD, "helmet", "helm", "head", "hat", "cap", "hood", "mask", "crown", "casco",
                "yelmo", "capucha", "sombrero", "gorro", "corona", "tiara", "mascara");
        put(words, Equipment.Slot.CHEST, "chestplate", "chest", "body", "torso", "tunic", "cuirass", "peto",
                "pechera", "coraza");
        put(words, Equipment.Slot.LEGS, "leggings", "legs", "leg", "pants", "trousers", "greaves", "grebas",
                "pantalones", "pantalon", "perneras");
        put(words, Equipment.Slot.FEET, "boots", "boot", "feet", "foot", "shoes", "sabatons", "botas", "zapatos");
        return Map.copyOf(words);
    }

    private static void put(Map<String, Equipment.Slot> words, Equipment.Slot slot, String... names) {
        Arrays.stream(names).forEach(name -> words.put(name, slot));
    }
}
