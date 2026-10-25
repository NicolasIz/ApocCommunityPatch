package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.animation.AnimatedModel;
import com.arkcronist.content.core.animation.BbModelReader;
import com.arkcronist.content.core.ballistics.DamageFalloff;
import com.arkcronist.content.core.block.BlockShape;
import com.arkcronist.content.core.block.ShapedModels;
import com.arkcronist.content.core.cosmetic.CosmeticDefinition;
import com.arkcronist.content.core.definition.AdvancementDefinition;
import com.arkcronist.content.core.definition.BackpackDefinition;
import com.arkcronist.content.core.definition.ContentType;
import com.arkcronist.content.core.definition.DrinkDefinition;
import com.arkcronist.content.core.definition.EmojiDefinition;
import com.arkcronist.content.core.definition.Equipment;
import com.arkcronist.content.core.definition.FontImageDefinition;
import com.arkcronist.content.core.definition.GunDefinition;
import com.arkcronist.content.core.definition.ItemBehaviour;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.JobReward;
import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.furniture.Footprint;
import com.arkcronist.content.core.emotes.EmoteDefinition;
import com.arkcronist.content.core.generation.OreGeneration;
import com.arkcronist.content.core.hud.HudAnchor;
import com.arkcronist.content.core.hud.HudDefinition;
import com.arkcronist.content.core.liquid.LiquidModels;
import com.arkcronist.content.core.minigame.MinigameDefinition;
import com.arkcronist.content.core.quest.QuestDefinition;
import com.arkcronist.content.core.quest.QuestReader;
import com.arkcronist.content.core.recipe.RecipeDefinition;
import com.arkcronist.content.core.recipe.RecipeReader;
import com.arkcronist.content.core.vehicle.VehicleDefinition;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans the contents folder and reads every item it describes.
 *
 * <p>Layout: each folder directly under {@code contents/} is one content pack. Any {@code .yml}
 * inside it, at any depth, may declare items; the pack's {@code models/} and {@code textures/}
 * folders hold the files those items point at.</p>
 *
 * <pre>
 * contents/
 *   demo/
 *     items.yml                      namespace: demo, items: { ruby: ..., ruby_sword: ... }
 *     models/item/ruby.json          resource.model: item/ruby
 *     textures/item/ruby_sword.png   resource.texture: item/ruby_sword
 * </pre>
 *
 * <p>Every entry under {@code items:} is an item. {@code type: custom_block} and
 * {@code type: custom_furniture} make it something that is placed into the world as well, with
 * its own {@code block:} or {@code furniture:} section; see {@link ContentType}.</p>
 *
 * <p>A file's namespace is its {@code namespace:} key, or failing that the name of the content
 * pack folder it sits in. Files are read in path order, so when two define the same id the one
 * that wins is the same on every restart - and the other is reported, never silently dropped.</p>
 *
 * <p>Runs off the main thread. It is plain file and YAML work with no server state, and SnakeYAML
 * is used through {@link SafeConstructor}: a content file can describe data, never ask for a Java
 * class to be instantiated.</p>
 */
public final class ContentLoader {

    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern TEXTURE_VARIABLE = Pattern.compile("[a-z0-9_]+");
    /** Typed between two colons in chat, so no colon and nothing a sentence would run into. */
    private static final Pattern EMOJI_NAME = Pattern.compile("[a-z0-9_]+");
    private static final Pattern FONT_IMAGE_NAME = Pattern.compile("[a-z0-9_.\\-]+");
    private static final Pattern AMOUNT_RANGE = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)");

    /** Vanilla's own glyph size: a capital letter is 8 pixels high, 7 of them above the baseline. */
    private static final int EMOJI_HEIGHT = 8;
    private static final int EMOJI_ASCENT = 7;

    /**
     * Reads every {@code .yml} and {@code .yaml} file under {@code contentsDir}.
     *
     * @throws IOException only when the folder itself cannot be walked; a single unreadable file
     *                     is reported in the result instead
     */
    public LoadReport load(Path contentsDir) throws IOException {
        if (!Files.isDirectory(contentsDir)) {
            return new LoadReport(List.of(), List.of());
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(contentsDir)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(ContentLoader::isYaml)
                    .sorted(Comparator.comparing(file -> unix(contentsDir.relativize(file))))
                    .toList();
        }

        Map<String, ItemDefinition> items = new LinkedHashMap<>();
        Map<String, EmojiDefinition> emojis = new LinkedHashMap<>();
        Map<String, AdvancementDefinition> advancements = new LinkedHashMap<>();
        Map<String, HudDefinition> huds = new LinkedHashMap<>();
        Map<String, FontImageDefinition> fontImages = new LinkedHashMap<>();
        Map<String, QuestDefinition> quests = new LinkedHashMap<>();
        Map<String, RecipeDefinition> recipes = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            readFile(contentsDir, file, items, emojis, advancements, huds, fontImages, quests, recipes, problems);
        }
        return new LoadReport(new ArrayList<>(items.values()), new ArrayList<>(emojis.values()),
                new ArrayList<>(advancements.values()), new ArrayList<>(huds.values()),
                new ArrayList<>(fontImages.values()), new ArrayList<>(quests.values()),
                new ArrayList<>(recipes.values()), problems);
    }

    /**
     * The {@code font_images:} ItemsAdder's own configs define, read where ItemsAdder keeps them -
     * {@code contents/<pack>/configs/} (ItemsAdder 4) or {@code data/items_packs/} (3.x): the names
     * menus written for it use ({@code %img_xp_bar_white_lore%}), for a server that still has the
     * folder but did not import those configs. A file's namespace is its {@code info: namespace:}.
     *
     * <p>These are ItemsAdder's files, not this plugin's content: one that cannot be read, or an
     * entry this plugin would report, is skipped quietly, and a repeated key is read the way
     * ItemsAdder reads it - the last one wins.</p>
     *
     * @param itemsAdderFolder {@code plugins/ItemsAdder}
     */
    public List<FontImageDefinition> itemsAdderFontImages(Path itemsAdderFolder) throws IOException {
        Map<String, FontImageDefinition> fontImages = new LinkedHashMap<>();
        for (Path base : List.of(itemsAdderFolder.resolve("contents"), itemsAdderFolder.resolve("data")
                .resolve("items_packs"))) {
            if (!Files.isDirectory(base)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(base)) {
                files = walk.filter(Files::isRegularFile)
                        .filter(ContentLoader::isYaml)
                        .sorted(Comparator.comparing(file -> unix(base.relativize(file))))
                        .toList();
            }
            for (Path file : files) {
                readItemsAdderFontImages(itemsAdderFolder, file, fontImages);
            }
        }
        return new ArrayList<>(fontImages.values());
    }

    private static void readItemsAdderFontImages(Path itemsAdderFolder, Path file,
                                                 Map<String, FontImageDefinition> fontImages) {
        Object document;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!text.contains("font_images")) {
                return;
            }
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(true);
            document = new Yaml(new SafeConstructor(options)).load(text);
        } catch (IOException | YAMLException | IllegalArgumentException unreadable) {
            return;
        }
        if (!(document instanceof Map<?, ?> root) || !(root.get("info") instanceof Map<?, ?> info)
                || info.get("namespace") == null) {
            return;
        }
        String namespace = String.valueOf(info.get("namespace")).trim().toLowerCase(Locale.ROOT);
        if (!ITEM_ID.matcher(namespace).matches()) {
            return;
        }
        Map<String, FontImageDefinition> found = new LinkedHashMap<>();
        readFontImages(root.get("font_images"), namespace, file.getParent(), file,
                unix(itemsAdderFolder.relativize(file)), itemsAdderFolder, found, new ArrayList<>());
        found.forEach(fontImages::putIfAbsent);
    }

    private void readFile(Path contentsDir, Path file, Map<String, ItemDefinition> items,
                          Map<String, EmojiDefinition> emojis, Map<String, AdvancementDefinition> advancements,
                          Map<String, HudDefinition> huds, Map<String, FontImageDefinition> fontImages,
                          Map<String, QuestDefinition> quests, Map<String, RecipeDefinition> recipes,
                          List<String> problems) {
        String where = unix(contentsDir.relativize(file));

        Object document;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            document = yaml().load(reader);
        } catch (MarkedYAMLException exception) {
            // The mark is the useful half: SnakeYAML's first line says what, only the mark says where.
            Mark mark = exception.getProblemMark();
            problems.add(where + (mark != null ? " line " + (mark.getLine() + 1) : "")
                    + ": not valid YAML - " + firstLine(exception.getProblem()));
            return;
        } catch (IOException | YAMLException exception) {
            problems.add(where + ": not readable as YAML - " + firstLine(exception.getMessage()));
            return;
        }
        if (document == null) {
            return;
        }
        if (!(document instanceof Map<?, ?> root)) {
            problems.add(where + ": expected 'namespace' and 'items' at the top level");
            return;
        }

        Path sourceRoot = sourceRoot(contentsDir, file);
        String namespace = namespace(root, sourceRoot, contentsDir, where, problems);
        if (namespace == null) {
            return;
        }

        readEmojis(root.get("emojis"), namespace, sourceRoot, file, where, contentsDir, emojis, problems);
        readAdvancements(root.get("advancements"), namespace, sourceRoot, file, where, contentsDir, advancements,
                problems);
        readHuds(root.get("huds"), namespace, sourceRoot, file, where, contentsDir, huds, problems);
        readFontImages(root.get("font_images"), namespace, sourceRoot, file, where, contentsDir, fontImages, problems);
        for (QuestDefinition quest : QuestReader.read(root.get("quests"), where, problems)) {
            if (quests.putIfAbsent(quest.id(), quest) != null) {
                problems.add(where + " > quest " + quest.id() + ": already defined in another file - this one is ignored");
            }
        }
        for (RecipeDefinition recipe : RecipeReader.read(root.get("recipes"), namespace, where, problems)) {
            RecipeDefinition earlier = recipes.putIfAbsent(recipe.fullId(), recipe);
            if (earlier != null) {
                problems.add(where + " > recipe " + recipe.fullId() + ": already defined in " + earlier.where()
                        + " - this one is ignored");
            }
        }

        // A file without items is not an error: it may hold only emojis, or another kind of
        // content this version does not read yet.
        Object itemsNode = root.get("items");
        if (itemsNode == null) {
            return;
        }
        if (!(itemsNode instanceof Map<?, ?> itemSections)) {
            problems.add(where + ": 'items' should be a section of item ids");
            return;
        }

        for (Map.Entry<?, ?> entry : itemSections.entrySet()) {
            String id = String.valueOf(entry.getKey());
            String prefix = where + " > " + namespace + ":" + id + ": ";
            if (!(entry.getValue() instanceof Map<?, ?> section)) {
                problems.add(prefix + "should be a section with at least 'material'");
                continue;
            }
            ItemDefinition item = readItem(namespace, id, section, sourceRoot, file, prefix, problems);
            if (item == null) {
                continue;
            }
            ItemDefinition earlier = items.putIfAbsent(item.fullId(), item);
            if (earlier != null) {
                problems.add(prefix + "already defined in "
                        + unix(contentsDir.relativize(earlier.source())) + " - this one is ignored");
            }
        }
    }

    private static String namespace(Map<?, ?> root, Path sourceRoot, Path contentsDir, String where,
                                    List<String> problems) {
        Object declared = root.get("namespace");
        String namespace;
        if (declared != null) {
            namespace = String.valueOf(declared).trim();
        } else if (!sourceRoot.equals(contentsDir)) {
            namespace = sourceRoot.getFileName().toString().toLowerCase(Locale.ROOT);
        } else {
            problems.add(where + ": files placed directly in contents/ must declare a 'namespace'");
            return null;
        }

        if (!ResourceLocation.isValidNamespace(namespace)) {
            problems.add(where + ": invalid namespace '" + namespace + "' (allowed: a-z 0-9 _ . -)");
            return null;
        }
        // Writing into minecraft: would replace the vanilla item of the same name for every player,
        // and one typo away from "ruby" is "diamond".
        if (namespace.equals(ResourceLocation.MINECRAFT)) {
            problems.add(where + ": the minecraft namespace is reserved for vanilla content");
            return null;
        }
        return namespace;
    }

    private static ItemDefinition readItem(String namespace, String id, Map<?, ?> section,
                                           Path sourceRoot, Path file, String prefix,
                                           List<String> problems) {
        if (!ITEM_ID.matcher(id).matches()) {
            problems.add(prefix + "invalid id (allowed: a-z 0-9 _ . -)");
            return null;
        }
        ContentType type = type(section, prefix, problems);
        if (type == null) {
            return null;
        }
        unusedSection(section, "block", ContentType.CUSTOM_BLOCK, type, prefix, problems);
        unusedSection(section, "furniture", ContentType.CUSTOM_FURNITURE, type, prefix, problems);
        unusedSection(section, "crop", ContentType.CUSTOM_CROP, type, prefix, problems);
        unusedSection(section, "liquid", ContentType.CUSTOM_LIQUID, type, prefix, problems);

        String displayName = text(section, "display-name", prefix, problems);
        List<String> lore = lines(section, "lore", prefix, problems);
        ModelSource model = model(namespace, id, type, section(section, "resource", prefix, problems),
                sourceRoot, prefix, problems);
        ItemBehaviour behaviour = behaviour(section(section, "behaviour", prefix, problems),
                prefix, problems);

        double price = price(section, prefix, problems);
        Map<?, ?> equipment = section(section, "equipment", prefix, problems);
        ItemDefinition item = readTyped(namespace, id, type, section, displayName, lore, model, behaviour, sourceRoot,
                file, prefix, problems);
        if (item == null) {
            return null;
        }
        if (price != 0) {
            item = item.withPrice(price);
        }
        if (equipment != null) {
            if (type != ContentType.ITEM) {
                problems.add(prefix + "'equipment' is only read on items worn as armour (type: item)");
            } else {
                item = item.withEquipment(equipment(namespace, equipment, model, sourceRoot, prefix, problems));
            }
        }
        Map<?, ?> gun = section(section, "gun", prefix, problems);
        if (gun != null) {
            if (type != ContentType.ITEM) {
                problems.add(prefix + "'gun' is only read on items held in the hand (type: item)");
            } else {
                item = item.withGun(gun(gun, prefix, problems));
            }
        }
        Map<?, ?> minigame = section(section, "minigame", prefix, problems);
        if (minigame != null) {
            boolean rod = type == ContentType.ITEM && "FISHING_ROD".equalsIgnoreCase(item.material());
            if (type != ContentType.CUSTOM_BLOCK && !rod) {
                problems.add(prefix + "'minigame' is played on a custom_block (a right click on it) or on an item"
                        + " made of FISHING_ROD (when something bites); ignored here");
            } else {
                item = item.withMinigame(minigame(minigame, rod, prefix, problems));
            }
        }
        Map<?, ?> cosmetic = section(section, "cosmetic", prefix, problems);
        if (cosmetic != null) {
            if (type != ContentType.ITEM) {
                problems.add(prefix + "'cosmetic' is only read on items (type: item)");
            } else if (model == null) {
                problems.add(prefix + "'cosmetic' needs a 'resource' - the model drawn on the wearer");
            } else {
                item = item.withCosmetic(cosmetic(cosmetic, prefix, problems));
            }
        }
        Map<?, ?> drink = section(section, "drink", prefix, problems);
        if (drink != null) {
            if (type != ContentType.ITEM) {
                problems.add(prefix + "'drink' is only read on items (type: item)");
            } else {
                item = item.withDrink(drink(drink, prefix, problems));
            }
        }
        Map<?, ?> backpack = section(section, "backpack", prefix, problems);
        if (backpack != null) {
            if (type != ContentType.ITEM) {
                problems.add(prefix + "'backpack' is only read on items (type: item)");
            } else {
                int slots = (int) number(backpack, "slots", 27, 9, 54, prefix + "backpack: ", problems);
                if (slots % 9 != 0) {
                    problems.add(prefix + "backpack: 'slots' is in whole rows of 9; using " + (slots / 9 * 9));
                    slots = slots / 9 * 9;
                }
                item = item.withBackpack(new BackpackDefinition(slots, text(backpack, "title", prefix, problems)));
            }
        }
        Map<?, ?> vehicle = section(section, "vehicle", prefix, problems);
        if (vehicle != null) {
            if (type != ContentType.ITEM) {
                problems.add(prefix + "'vehicle' is only read on items (type: item) - the item placed to put it down");
            } else {
                item = item.withVehicle(vehicle(vehicle, prefix + "vehicle: ", problems));
            }
        }
        Map<?, ?> emote = section(section, "emote", prefix, problems);
        if (emote != null) {
            if (type != ContentType.ITEM) {
                problems.add(prefix + "'emote' is only read on items (type: item) - the item used to play it");
            } else {
                item = item.withEmote(emote(item.itemModel(), emote, sourceRoot, prefix + "emote: ", problems));
            }
        }
        return item;
    }

    /**
     * {@code emote:} (5.0) - a Blockbench rig under {@code models/} and the animation of it played.
     * Its frames are worked out here, on the worker, once per build. Null when it cannot be played.
     */
    private static EmoteDefinition emote(ResourceLocation itemModel, Map<?, ?> section, Path sourceRoot, String where,
                                         List<String> problems) {
        String raw = text(section, "model", where, problems);
        if (raw == null || raw.isBlank()) {
            problems.add(where + "needs 'model': a .bbmodel under models/ whose bones are the body (head, body,"
                    + " right_arm, left_arm, right_leg, left_leg); not played");
            return null;
        }
        String path = raw.trim().replaceFirst("\\.bbmodel$", "");
        if (path.contains(":")) {
            path = path.substring(path.indexOf(':') + 1);
        }
        Path file = sourceRoot.resolve("models").resolve(path + ".bbmodel").normalize();
        if (!ResourceLocation.isValidPath(path) || !file.startsWith(sourceRoot.normalize()) || !Files.isRegularFile(file)) {
            problems.add(where + "'model' " + path + ".bbmodel is not in " + sourceRoot.getFileName() + "/models/;"
                    + " not played");
            return null;
        }
        AnimatedModel rig = BbModelReader.read(file, sourceRoot, itemModel, where, problems);
        if (rig == null) {
            return null;
        }
        String animation = text(section, "animation", where, problems);
        if (animation == null || !rig.clips().containsKey(animation.trim())) {
            problems.add(where + path + ".bbmodel has no animation '" + animation + "' (it has: "
                    + (rig.clips().isEmpty() ? "none" : String.join(", ", rig.clips().keySet())) + "); not played");
            return null;
        }
        boolean playerHead = flag(section, "player-head", true, where, problems);
        boolean hasHead = rig.bones().stream().anyMatch(bone -> bone.name().equals(EmoteDefinition.HEAD));
        if (playerHead && !hasHead && section.containsKey("player-head")) {
            problems.add(where + "'player-head' needs a bone named 'head' in " + path + ".bbmodel");
        }
        return EmoteDefinition.of(rig, animation.trim(), playerHead && hasHead,
                (float) number(section, "scale", EmoteDefinition.PLAYER_SCALE, 0.1, 4, where, problems),
                flag(section, "stop-on-move", true, where, problems),
                (int) Math.round(number(section, "max-seconds", 30, 1, 600, where, problems) * 20),
                (int) Math.round(number(section, "cooldown-seconds", 1, 0, 3600, where, problems) * 20));
    }

    /**
     * {@code vehicle:} (4.0) - speeds, steering, size, seats and the displays it is drawn with. With
     * no {@code parts}, it is drawn with its own item's model, its middle half its height up.
     */
    private static VehicleDefinition vehicle(Map<?, ?> section, String where, List<String> problems) {
        double maxSpeed = number(section, "max-speed", 8, 0.5, 40, where, problems);
        double reverse = number(section, "reverse-speed", 3, 0, 40, where, problems);
        double acceleration = number(section, "acceleration", 6, 0.1, 100, where, problems);
        double braking = number(section, "braking", 16, 0.1, 200, where, problems);
        double coasting = number(section, "coasting", 3, 0, 100, where, problems);
        double turnRate = number(section, "turn-rate", 90, 0, 720, where, problems);
        double step = number(section, "step-height", 0.6, 0, 1.5, where, problems);
        double waterSpeed = number(section, "water-speed", 0.35, 0, 1, where, problems);
        Placement.Vec3 size = vector(section, "size", new Placement.Vec3(1.2f, 1f, 2f), where, problems);
        double width = Math.max(0.2, Math.min(8, size.x()));
        double height = Math.max(0.2, Math.min(8, size.y()));
        double length = Math.max(0.2, Math.min(8, size.z()));
        if (width != size.x() || height != size.y() || length != size.z()) {
            problems.add(where + "'size' is width, height, length, each from 0.2 to 8 blocks; kept within that");
        }

        List<VehicleDefinition.Offset> seats = new ArrayList<>();
        Object seatNode = section.get("seats");
        if (seatNode != null) {
            if (!(seatNode instanceof List<?> list) || list.isEmpty() || list.size() > 8) {
                problems.add(where + "'seats' should be a list of one to eight [right, up, forward] offsets;"
                        + " using one seat in the middle");
            } else {
                for (Object entry : list) {
                    VehicleDefinition.Offset seat = offset(entry, where + "seat " + (seats.size() + 1) + ": ", problems);
                    if (seat != null) {
                        seats.add(seat);
                    }
                }
            }
        }
        if (seats.isEmpty()) {
            seats.add(new VehicleDefinition.Offset(0, Math.min(height, 0.6), 0));
        }

        List<VehicleDefinition.Part> parts = new ArrayList<>();
        Object partNode = section.get("parts");
        if (partNode != null) {
            if (!(partNode instanceof List<?> list) || list.size() > 16) {
                problems.add(where + "'parts' should be a list of up to sixteen displays; drawing its own model");
            } else {
                for (Object entry : list) {
                    String at = where + "part " + (parts.size() + 1) + ": ";
                    if (!(entry instanceof Map<?, ?> part)) {
                        problems.add(at + "should be a section with 'item' and 'offset'");
                        continue;
                    }
                    Object itemId = part.get("item");
                    String id = itemId == null ? null : String.valueOf(itemId).trim().toLowerCase(Locale.ROOT);
                    if (id != null && !id.matches("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+")) {
                        problems.add(at + "'item' should be an item id like demo:ruby_cart_wheel");
                        continue;
                    }
                    VehicleDefinition.Offset offset = part.get("offset") == null ? VehicleDefinition.Offset.ZERO
                            : offset(part.get("offset"), at, problems);
                    if (offset == null) {
                        continue;
                    }
                    boolean wheel = flag(part, "wheel", false, at, problems);
                    double radius = number(part, "radius", 0.3, 0.05, 4, at, problems);
                    double scale = number(part, "scale", 1, 0.05, 16, at, problems);
                    parts.add(new VehicleDefinition.Part(id, offset, scale, wheel, radius));
                }
            }
        }
        if (parts.isEmpty()) {
            parts.add(new VehicleDefinition.Part(null, new VehicleDefinition.Offset(0, height / 2, 0), 1, false, 0.3));
        }
        return new VehicleDefinition(maxSpeed, reverse, acceleration, braking, coasting, turnRate, step, width,
                length, height, waterSpeed, seats, parts);
    }

    /** {@code [right, up, forward]}, in blocks, each from -8 to 8. */
    private static VehicleDefinition.Offset offset(Object node, String where, List<String> problems) {
        if (node instanceof List<?> list && list.size() == 3 && list.stream().allMatch(value -> value instanceof Number
                && Math.abs(((Number) value).doubleValue()) <= 8)) {
            return new VehicleDefinition.Offset(((Number) list.get(0)).doubleValue(),
                    ((Number) list.get(1)).doubleValue(), ((Number) list.get(2)).doubleValue());
        }
        problems.add(where + "an offset is [right, up, forward] in blocks, each from -8 to 8, not " + node);
        return null;
    }

    /**
     * {@code drink:} (3.4) - a canteen: {@code sips}, {@code seconds}, {@code hud: {thirst: 6}},
     * {@code potions}, {@code refill: [water, cauldron, <furniture id>...]}, {@code sound}, and the
     * messages {@code filled} and {@code empty}.
     */
    private static DrinkDefinition drink(Map<?, ?> section, String prefix, List<String> problems) {
        String where = prefix + "drink: ";
        int sips = (int) number(section, "sips", 5, 1, 100, where, problems);
        double seconds = number(section, "seconds", 1.6, 0.05, 30, where, problems);
        Map<String, Double> hud = InteractionReader.hudGains(section.get("hud"), where, problems);
        List<Placement.Effect> potions = InteractionReader.potions(section.get("potions"), where, problems);
        List<String> refill = new ArrayList<>();
        Object raw = section.get("refill");
        if (raw == null) {
            refill.addAll(DrinkDefinition.SOURCES);
        } else {
            for (Object entry : raw instanceof List<?> list ? list : List.of(raw)) {
                String source = String.valueOf(entry).trim().toLowerCase(Locale.ROOT);
                if (DrinkDefinition.SOURCES.contains(source) || source.matches("[a-z0-9_.\\-]+:[a-z0-9_/.\\-]+")) {
                    refill.add(source);
                } else {
                    problems.add(where + "'refill' takes water, cauldron or a furniture id such as"
                            + " medieval_props:abrevadero, not '" + entry + "'");
                }
            }
        }
        if (hud.isEmpty() && potions.isEmpty()) {
            problems.add(where + "a drink with neither 'hud' nor 'potions' does nothing");
        }
        String sound = text(section, "sound", where, problems);
        return new DrinkDefinition(sips, seconds, hud, potions, refill, sound == null || sound.isBlank() ? null : sound.trim(),
                text(section, "filled", where, problems), text(section, "empty", where, problems));
    }

    /**
     * {@code minigame:} (3.0) - the precision bar. Every key has a default; a value out of range is
     * reported and the default used.
     */
    private static MinigameDefinition minigame(Map<?, ?> section, boolean rod, String prefix, List<String> problems) {
        String where = prefix + "minigame: ";
        double speed = number(section, "speed", 1.2, 0.1, 10, where, problems);
        int zone = (int) number(section, "zone", 20, 1, MinigameDefinition.BAR, where, problems);
        int critical = (int) number(section, "critical", Math.min(4, zone), 0, zone, where, problems);
        int sweeps = (int) number(section, "sweeps", 4, 1, 50, where, problems);
        boolean randomZone = flag(section, "random-zone", true, where, problems);
        List<MinigameDefinition.Drop> drops = drops(section, "drops", where, problems);
        List<MinigameDefinition.Drop> criticalDrops = drops(section, "critical-drops", where, problems);
        MinigameDefinition.OnMiss fallback = rod ? MinigameDefinition.OnMiss.LOSE : MinigameDefinition.OnMiss.KEEP;
        MinigameDefinition.OnMiss onMiss = fallback;
        String rawMiss = text(section, "on-miss", where, problems);
        if (rawMiss != null) {
            try {
                onMiss = MinigameDefinition.OnMiss.parse(rawMiss);
            } catch (IllegalArgumentException unknown) {
                problems.add(where + "'on-miss' should be keep, break or lose; using " + fallback.name().toLowerCase(Locale.ROOT));
            }
            if (rod && onMiss != MinigameDefinition.OnMiss.LOSE && onMiss != fallback) {
                problems.add(where + "a fishing rod's catch can only be lost on a miss; 'on-miss' is ignored");
                onMiss = MinigameDefinition.OnMiss.LOSE;
            } else if (!rod && onMiss == MinigameDefinition.OnMiss.LOSE) {
                problems.add(where + "a block cannot be lost - use keep or break; using keep");
                onMiss = MinigameDefinition.OnMiss.KEEP;
            }
        }
        int cooldown = (int) Math.round(number(section, "cooldown-seconds", 1, 0, 3600, where, problems) * 20);
        return new MinigameDefinition(speed, zone, critical, sweeps, randomZone, drops, criticalDrops, onMiss, cooldown);
    }

    /** A list of {@code {item, amount, chance}}; the item is a custom item's id or a material, checked once all files are read. */
    private static List<MinigameDefinition.Drop> drops(Map<?, ?> section, String key, String prefix,
                                                       List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            problems.add(prefix + "'" + key + "' should be a list of {item, amount, chance}");
            return List.of();
        }
        List<MinigameDefinition.Drop> drops = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof String item && !item.isBlank()) {
                drops.add(new MinigameDefinition.Drop(item.trim(), 1, 1));
                continue;
            }
            if (!(entry instanceof Map<?, ?> drop)) {
                problems.add(prefix + "each of '" + key + "' should be an item id, or {item, amount, chance}");
                continue;
            }
            String item = text(drop, "item", prefix + key + " ", problems);
            if (item == null || item.isBlank()) {
                problems.add(prefix + "a drop in '" + key + "' has no 'item'");
                continue;
            }
            int amount = (int) number(drop, "amount", 1, 1, 6400, prefix + key + " ", problems);
            double chance = number(drop, "chance", 1, 0, 1, prefix + key + " ", problems);
            drops.add(new MinigameDefinition.Drop(item.trim(), amount, chance));
        }
        return drops;
    }

    /** {@code cosmetic:} (3.0) - worn on the back, swinging as its wearer moves. */
    private static CosmeticDefinition cosmetic(Map<?, ?> section, String prefix, List<String> problems) {
        String where = prefix + "cosmetic: ";
        CosmeticDefinition.Slot slot = CosmeticDefinition.Slot.BACK;
        String rawSlot = text(section, "slot", where, problems);
        if (rawSlot != null) {
            try {
                slot = CosmeticDefinition.Slot.parse(rawSlot);
            } catch (IllegalArgumentException unknown) {
                problems.add(where + "'slot' can only be back for now; using back");
            }
        }
        Placement.Vec3 offset = vector(section, "offset", CosmeticDefinition.BACK_OFFSET, where, problems);
        float scale = (float) number(section, "scale", 1, 0.05, 4, where, problems);
        CosmeticDefinition.Physics physics = CosmeticDefinition.Physics.DEFAULT;
        Object rawPhysics = section.get("physics");
        if (rawPhysics instanceof Boolean on && !on || "none".equals(rawPhysics)) {
            physics = CosmeticDefinition.Physics.STILL;
        } else if (rawPhysics instanceof Map<?, ?> map) {
            String at = where + "physics ";
            CosmeticDefinition.Physics base = CosmeticDefinition.Physics.DEFAULT;
            physics = new CosmeticDefinition.Physics(
                    number(map, "stiffness", base.stiffness(), 1, 400, at, problems),
                    number(map, "damping", base.damping(), 0, 60, at, problems),
                    number(map, "sway", base.sway(), 0, 4, at, problems),
                    number(map, "max-angle", base.maxAngle(), 0, 80, at, problems),
                    number(map, "bounce", base.bounce(), 0, 1, at, problems));
        } else if (rawPhysics != null && !(rawPhysics instanceof Boolean)) {
            problems.add(where + "'physics' should be a section, or none");
        }
        return new CosmeticDefinition(slot, offset, scale, physics);
    }

    /**
     * {@code gun:} - how the item shoots. Every key has a default, so {@code gun: {}} is already a
     * pistol; a value out of its range is reported and replaced by the default, never clamped
     * silently.
     */
    private static GunDefinition gun(Map<?, ?> section, String prefix, List<String> problems) {
        String where = prefix + "gun: ";
        double damage = number(section, "damage", 5, 0, 1000, where, problems);
        double headshot = number(section, "headshot-multiplier", 1.5, 0, 100, where, problems);
        double range = number(section, "range", 64, 1, 256, where, problems);
        double falloffStart = range;
        double minFactor = 1;
        Map<?, ?> falloff = section(section, "falloff", where, problems);
        if (falloff != null) {
            falloffStart = number(falloff, "start", Math.min(24, range), 0, range, where + "falloff ", problems);
            minFactor = number(falloff, "min-factor", 0.5, 0, 1, where + "falloff ", problems);
        }
        int pellets = (int) number(section, "pellets", 1, 1, 64, where, problems);
        double spread = number(section, "spread", pellets > 1 ? 6 : 0.5, 0, 45, where, problems);
        double sneakSpread = number(section, "sneak-spread", spread / 2, 0, 45, where, problems);
        int pierce = (int) number(section, "pierce", 0, 0, 64, where, problems);
        int magazine = (int) number(section, "magazine", 12, 1, 10000, where, problems);
        double reloadSeconds = number(section, "reload-seconds", 1.5, 0, 60, where, problems);
        double fireRate = number(section, "fire-rate", 4, 0.05, 20, where, problems);

        String ammo = null;
        String rawAmmo = text(section, "ammo", where, problems);
        if (rawAmmo != null && !rawAmmo.isBlank() && !rawAmmo.trim().equalsIgnoreCase("none")) {
            // Whether it is a custom item or a material is only known once every file is read.
            ammo = rawAmmo.trim();
        }

        GunDefinition.Recoil recoil = recoil(section.get("recoil"), where, problems);

        String damageType = text(section, "damage-type", where, problems);
        damageType = damageType == null || damageType.isBlank() ? "minecraft:arrow"
                : damageType.trim().toLowerCase(Locale.ROOT);
        if (damageType.indexOf(':') < 0) {
            damageType = "minecraft:" + damageType;
        }

        Set<GunDefinition.TargetKind> targets = EnumSet.allOf(GunDefinition.TargetKind.class);
        if (section.containsKey("targets")) {
            targets = EnumSet.noneOf(GunDefinition.TargetKind.class);
            for (String raw : lines(section, "targets", where, problems)) {
                Optional<GunDefinition.TargetKind> kind = GunDefinition.TargetKind.parse(raw);
                if (kind.isPresent()) {
                    targets.add(kind.get());
                } else {
                    problems.add(where + "unknown target '" + raw.trim() + "' (players, mythic_mobs, mobs)");
                }
            }
            if (targets.isEmpty()) {
                problems.add(where + "'targets' leaves nothing to hit; using players, mythic_mobs and mobs");
                targets = EnumSet.allOf(GunDefinition.TargetKind.class);
            }
        }

        String particle = "minecraft:crit";
        if (section.containsKey("particle")) {
            String raw = text(section, "particle", where, problems);
            particle = raw == null || raw.isBlank() || raw.trim().equalsIgnoreCase("none") ? null
                    : raw.trim().toLowerCase(Locale.ROOT);
        }

        return new GunDefinition(damage, headshot, new DamageFalloff(falloffStart, range, minFactor), pellets, spread,
                sneakSpread, pierce, magazine, ammo, (int) Math.round(reloadSeconds * 20),
                Math.max(1, (int) Math.round(20 / fireRate)), recoil, damageType, targets, particle,
                sounds(section(section, "sounds", where, problems), where, problems));
    }

    /** {@code recoil:} - degrees up per shot, or {@code {pitch, yaw}}. */
    private static GunDefinition.Recoil recoil(Object node, String prefix, List<String> problems) {
        if (node == null) {
            return new GunDefinition.Recoil(1.5, 0.5);
        }
        if (node instanceof Number number) {
            return new GunDefinition.Recoil(Math.max(0, Math.min(45, number.doubleValue())), 0);
        }
        if (node instanceof Map<?, ?> section) {
            return new GunDefinition.Recoil(number(section, "pitch", 1.5, 0, 45, prefix + "recoil ", problems),
                    number(section, "yaw", 0.5, 0, 45, prefix + "recoil ", problems));
        }
        problems.add(prefix + "'recoil' should be a number of degrees or {pitch, yaw}; using pitch 1.5, yaw 0.5");
        return new GunDefinition.Recoil(1.5, 0.5);
    }

    /**
     * {@code sounds:} - each a sound key, {@code none}, or {@code {sound, volume, pitch}}; the ones
     * left out keep their defaults.
     */
    private static GunDefinition.Sounds sounds(Map<?, ?> section, String prefix, List<String> problems) {
        GunDefinition.Sounds defaults = GunDefinition.Sounds.DEFAULT;
        if (section == null) {
            return defaults;
        }
        GunDefinition.Sound[] sounds = {defaults.shoot(), defaults.empty(), defaults.reload(), defaults.reloaded(),
                defaults.hit(), defaults.headshot()};
        for (Object key : section.keySet()) {
            int index = GunDefinition.Sounds.KEYS.indexOf(String.valueOf(key));
            if (index < 0) {
                problems.add(prefix + "unknown sound '" + key + "' " + GunDefinition.Sounds.KEYS);
                continue;
            }
            sounds[index] = sound(section.get(key), sounds[index], prefix + "sounds." + key + " ", problems);
        }
        return new GunDefinition.Sounds(sounds[0], sounds[1], sounds[2], sounds[3], sounds[4], sounds[5]);
    }

    private static GunDefinition.Sound sound(Object node, GunDefinition.Sound fallback, String prefix,
                                             List<String> problems) {
        Map<?, ?> section = node instanceof Map<?, ?> map ? map : null;
        Object rawKey = section != null ? section.get("sound") : node;
        if (rawKey == null || rawKey instanceof Map<?, ?> || rawKey instanceof List<?>) {
            problems.add(prefix + "should be a sound key, none, or {sound, volume, pitch}");
            return fallback;
        }
        String key = String.valueOf(rawKey).trim().toLowerCase(Locale.ROOT);
        if (key.equals("none") || key.isEmpty()) {
            return null;
        }
        if (!key.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+")) {
            problems.add(prefix + "'" + key + "' is not a sound key (like entity.generic.explode)");
            return fallback;
        }
        if (key.indexOf(':') < 0) {
            key = "minecraft:" + key;
        }
        float volume = section == null ? 1f : (float) number(section, "volume", 1, 0, 16, prefix, problems);
        float pitch = section == null ? 1f : (float) number(section, "pitch", 1, 0.5, 2, prefix, problems);
        return new GunDefinition.Sound(key, volume, pitch);
    }

    /**
     * {@code equipment:} - how the item is worn. Each problem is reported against what the player
     * would see: a piece with no texture for its slot would be invisible, a worn model next to an
     * asset would never be drawn.
     */
    private static Equipment equipment(String namespace, Map<?, ?> section, ModelSource model, Path sourceRoot,
                                       String prefix, List<String> problems) {
        String rawSlot = text(section, "slot", prefix, problems);
        Equipment.Slot slot = rawSlot == null ? null : Equipment.Slot.parse(rawSlot);
        if (slot == null) {
            problems.add(prefix + "'equipment.slot' must be HEAD, CHEST, LEGS or FEET"
                    + (rawSlot == null ? "" : ", not '" + rawSlot.trim() + "'") + "; the item is not worn");
            return null;
        }
        ResourceLocation asset = location(section, "asset", namespace, prefix, problems);
        List<String> layers = new ArrayList<>();
        if (asset != null && asset.namespace().equals(namespace)) {
            for (String layer : Equipment.LAYERS) {
                if (Files.isRegularFile(texturePath(sourceRoot, Equipment.layerTexture(asset, layer)))) {
                    layers.add(layer);
                }
            }
            // With none of its textures here, the asset may come whole from a merged pack (an
            // ItemsAdder pack's equipment/): the pack compiler, which sees those, checks it.
            if (!layers.isEmpty() && !layers.contains(slot.layer)) {
                problems.add(prefix + "worn on " + slot + ", " + asset + " is drawn from textures/"
                        + Equipment.layerTexture(asset, slot.layer).path() + ".png, which is not in "
                        + sourceRoot.getFileName() + " - the piece would be invisible");
            }
        }

        ResourceLocation wornModel = location(section, "model", namespace, prefix, problems);
        ModelSource worn = null;
        if (wornModel != null) {
            if (slot != Equipment.Slot.HEAD) {
                problems.add(prefix + "'equipment.model' is drawn on the head only; ignored on " + slot);
            } else if (asset != null) {
                problems.add(prefix + "'equipment.model' and 'equipment.asset' do not go together on the head: the"
                        + " client draws the armour layer and never the model. Using the asset");
            } else if (model == null) {
                problems.add(prefix + "'equipment.model' needs a 'resource' too: the look the item keeps everywhere"
                        + " but on the head");
            } else if (wornModel.namespace().equals(namespace)
                    && !Files.isRegularFile(sourceRoot.resolve("models").resolve(wornModel.path() + ".json"))) {
                problems.add(prefix + "'equipment.model' " + wornModel.path() + ".json is not in "
                        + sourceRoot.getFileName() + "/models/");
            } else {
                worn = new ModelSource.Provided(sourceRoot, wornModel);
            }
        }
        return new Equipment(slot, asset, layers, worn, sourceRoot);
    }

    private static Path texturePath(Path sourceRoot, ResourceLocation texture) {
        return sourceRoot.resolve("textures").resolve(texture.path() + ".png");
    }

    private static ItemDefinition readTyped(String namespace, String id, ContentType type, Map<?, ?> section,
                                            String displayName, List<String> lore, ModelSource model,
                                            ItemBehaviour behaviour, Path sourceRoot, Path file, String prefix,
                                            List<String> problems) {
        if (type == ContentType.ITEM) {
            String material = text(section, "material", prefix, problems);
            if (material == null || material.isBlank()) {
                problems.add(prefix + "'material' is required, e.g. material: PAPER");
                return null;
            }
            return new ItemDefinition(namespace, id, material.trim(), displayName, lore, model,
                    behaviour, null, file);
        }

        if (type == ContentType.CUSTOM_CROP) {
            return crop(namespace, id, section, model, displayName, lore, sourceRoot, file, prefix, problems);
        }
        if (type == ContentType.CUSTOM_LIQUID) {
            return liquid(namespace, id, section, model, displayName, lore, sourceRoot, file, prefix, problems);
        }

        // Blocks and furniture: the material is what the placement needs, the look is mandatory, and
        // the item has to stay placeable.
        Placement placement = type == ContentType.CUSTOM_BLOCK
                ? block(section(section, "block", prefix, problems), prefix, problems)
                : furniture(namespace, id, section(section, "furniture", prefix, problems), sourceRoot, prefix, problems);
        if (model == null && placement instanceof Placement.Furniture furniture && furniture.animated() != null) {
            // The item in an inventory: the whole Blockbench model at rest, or its first bone.
            model = icon(namespace, id, furniture.animated(), sourceRoot);
        }
        if (model == null) {
            problems.add(prefix + "a " + type.yamlName() + " needs resource.model, resource.texture"
                    + " or resource.textures" + (type == ContentType.CUSTOM_FURNITURE ? ", or furniture.animated-model" : "")
                    + " - without one it would look like its support block");
            return null;
        }
        BlockShape shape = placement instanceof Placement.Block block ? block.shape() : BlockShape.CUBE;
        if (shape.copper()) {
            // 5.3: drawn through a copper block of its shape, from vanilla's models for that shape.
            model = ShapedModels.itemModel(namespace, id, shape, model, prefix, problems);
            if (model == null) {
                return null;
            }
        }
        String material = placement instanceof Placement.Furniture furniture
                ? switch (furniture.support()) {
                    case BED -> furniture.bed().material();
                    default -> furniture.support().name();
                }
                // A shaped block's copper block is decided by the build; until then, the first of its shape.
                : shape.copper() ? shape.bases().get(0).toUpperCase(Locale.ROOT) : "NOTE_BLOCK";
        if (section.containsKey("material")) {
            problems.add(prefix + "'material' is ignored - a " + type.yamlName() + " is always "
                    + (shape.copper() ? "a waxed copper " + shape.yamlName() : material) + " underneath");
        }
        if (behaviour.cancelVanillaUse()) {
            problems.add(prefix + "'cancel-vanilla-use' is ignored - it would stop the "
                    + type.yamlName() + " being placed");
        }
        return new ItemDefinition(namespace, id, material, displayName, lore, model,
                new ItemBehaviour(false, true), placement, file);
    }

    private static ContentType type(Map<?, ?> section, String prefix, List<String> problems) {
        String raw = text(section, "type", prefix, problems);
        if (raw == null || raw.isBlank()) {
            return ContentType.ITEM;
        }
        Optional<ContentType> type = ContentType.parse(raw);
        if (type.isEmpty()) {
            problems.add(prefix + "unknown type '" + raw.trim()
                    + "' (item, custom_block, custom_furniture, custom_crop, custom_liquid)");
            return null;
        }
        return type.get();
    }

    /**
     * How the entry is drawn.
     *
     * <ul>
     *   <li>{@code model} - a model file from the content pack, used as it is;</li>
     *   <li>{@code texture} - one texture: a flat item ({@code item/generated}, {@code layer0}) or,
     *       for a block, a cube with that texture on every face ({@code block/cube_all},
     *       {@code all});</li>
     *   <li>{@code textures} + {@code parent} - any vanilla-style parent with its own texture
     *       variables, e.g. {@code block/cube_column} with {@code end} and {@code side}.</li>
     *   <li>{@code item-model} - an item definition file, {@code items/<path>.json}, used as it is:
     *       from the content pack, or from a resource pack merged in. For items and furniture; a
     *       block, a crop and a liquid are drawn from models.</li>
     * </ul>
     */
    private static ModelSource model(String namespace, String id, ContentType type, Map<?, ?> resource,
                                     Path sourceRoot, String prefix, List<String> problems) {
        if (resource == null) {
            return null;
        }
        ResourceLocation definition = location(resource, "item-model", namespace, prefix, problems);
        if (definition != null) {
            if (type != ContentType.ITEM && type != ContentType.CUSTOM_FURNITURE) {
                problems.add(prefix + "'item-model' draws items and furniture only; a " + type.yamlName()
                        + " needs resource.model, resource.texture or resource.textures");
                return null;
            }
            if (resource.size() > 1) {
                problems.add(prefix + "only 'item-model' is used - the item definition file decides everything"
                        + " the item draws");
            }
            return new ModelSource.Definition(sourceRoot, definition);
        }
        ResourceLocation model = location(resource, "model", namespace, prefix, problems);
        ResourceLocation texture = location(resource, "texture", namespace, prefix, problems);
        Map<String, ResourceLocation> textures = textureVariables(resource, namespace, prefix, problems);
        // Parents are nearly always vanilla (item/generated, block/cube_all), so a bare parent reads
        // the way it would inside a model file.
        ResourceLocation parent = location(resource, "parent", ResourceLocation.MINECRAFT, prefix, problems);

        if (model != null) {
            if (texture != null || !textures.isEmpty() || parent != null) {
                problems.add(prefix + "'texture', 'textures' and 'parent' are ignored because 'model' is set"
                        + " - the textures the model uses are copied with it");
            }
            return new ModelSource.Provided(sourceRoot, model);
        }

        boolean block = type == ContentType.CUSTOM_BLOCK;
        Map<String, ResourceLocation> variables = new LinkedHashMap<>(textures);
        if (texture != null) {
            variables.putIfAbsent(block ? "all" : "layer0", texture);
        }
        if (variables.isEmpty()) {
            if (parent != null) {
                problems.add(prefix + "'parent' is ignored without 'texture' or 'textures'");
            }
            return null;
        }
        ResourceLocation location = new ResourceLocation(namespace, (block ? "block/" : "item/") + id);
        ResourceLocation defaultParent = block ? ModelSource.CUBE_ALL : ModelSource.ITEM_GENERATED;
        return new ModelSource.Generated(sourceRoot, location, parent != null ? parent : defaultParent, variables);
    }

    /** {@code textures:} - variable name to texture. Blockbench numbers its variables, so 0 is a valid name. */
    private static Map<String, ResourceLocation> textureVariables(Map<?, ?> resource, String namespace,
                                                                  String prefix, List<String> problems) {
        Map<?, ?> section = section(resource, "textures", prefix, problems);
        if (section == null) {
            return Map.of();
        }
        Map<String, ResourceLocation> textures = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String variable = String.valueOf(entry.getKey());
            if (!TEXTURE_VARIABLE.matcher(variable).matches()) {
                problems.add(prefix + "texture variable '" + variable + "' (allowed: a-z 0-9 _)");
                continue;
            }
            ResourceLocation texture = location(entry.getValue(), "textures." + variable, namespace, prefix, problems);
            if (texture != null) {
                textures.put(variable, texture);
            }
        }
        return textures;
    }

    private static Placement.Block block(Map<?, ?> section, String prefix, List<String> problems) {
        if (section == null) {
            return new Placement.Block(true);
        }
        BlockShape shape = shape(section, prefix, problems);
        List<OreGeneration> generation = generation(section.get("generation"), prefix + "generation: ", problems);
        if (shape.copper() && !generation.isEmpty()) {
            problems.add(prefix + "generation: only a cube is laid into chunks as an ore, not a "
                    + shape.yamlName() + " - left out");
            generation = List.of();
        }
        return new Placement.Block(flag(section, "drop-self", true, prefix, problems),
                skillXp(section, prefix, problems), jobs(section, prefix, problems), generation, shape);
    }

    /** {@code shape:} (5.3) - cube when left out. */
    private static BlockShape shape(Map<?, ?> section, String prefix, List<String> problems) {
        String raw = text(section, "shape", prefix, problems);
        if (raw == null || raw.isBlank()) {
            return BlockShape.CUBE;
        }
        Optional<BlockShape> shape = BlockShape.parse(raw);
        if (shape.isEmpty()) {
            problems.add(prefix + "unknown block shape '" + raw.trim() + "' (cube, slab, stairs, transparent, door,"
                    + " trapdoor) - made a cube");
            return BlockShape.CUBE;
        }
        return shape.get();
    }

    /**
     * {@code generation:} (5.0) - one section, or a list of them: where the block is laid into new
     * chunks as an ore. An entry that cannot be read is left out, and said so; the others still are.
     */
    private static List<OreGeneration> generation(Object value, String where, List<String> problems) {
        if (value == null) {
            return List.of();
        }
        List<?> entries = value instanceof List<?> list ? list : List.of(value);
        List<OreGeneration> generation = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            String at = entries.size() > 1 ? where + "#" + (i + 1) + ": " : where;
            if (!(entries.get(i) instanceof Map<?, ?> section)) {
                problems.add(at + "should be a section (min-y, max-y, veins-per-chunk, ...); left out");
                continue;
            }
            Set<String> environments = new LinkedHashSet<>();
            for (String environment : lines(section, "environments", at, problems)) {
                String name = environment.trim().toLowerCase(Locale.ROOT);
                if (OreGeneration.ENVIRONMENTS.contains(name)) {
                    environments.add(name);
                } else {
                    problems.add(at + "unknown environment '" + environment + "' (normal, nether, the_end, custom)");
                }
            }
            Set<String> worlds = new LinkedHashSet<>();
            lines(section, "worlds", at, problems).forEach(world -> worlds.add(world.trim().toLowerCase(Locale.ROOT)));
            Set<String> biomes = new LinkedHashSet<>();
            lines(section, "biomes", at, problems).forEach(biome -> biomes.add(OreGeneration.key(biome)));
            Set<String> replace = new LinkedHashSet<>();
            lines(section, "replace", at, problems).forEach(block -> replace.add(OreGeneration.key(block)));
            int minY = (int) number(section, "min-y", -64, -2048, 2048, at, problems);
            int maxY = (int) number(section, "max-y", 64, -2048, 2048, at, problems);
            if (minY > maxY) {
                problems.add(at + "'min-y' " + minY + " is above 'max-y' " + maxY + "; swapped");
                int swap = minY;
                minY = maxY;
                maxY = swap;
            }
            generation.add(new OreGeneration(worlds, environments, biomes, minY, maxY,
                    (int) number(section, "veins-per-chunk", 4, 0, 64, at, problems),
                    (int) number(section, "vein-size", 5, 1, 64, at, problems),
                    number(section, "chance", 1, 0, 1, at, problems),
                    replace.isEmpty() ? OreGeneration.STONES : replace));
        }
        return generation;
    }

    /** {@code price:} at the item's top level: what one costs in the content shop. */
    private static double price(Map<?, ?> section, String prefix, List<String> problems) {
        Object value = section.get("price");
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number && number.doubleValue() > 0 && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        problems.add(prefix + "'price' should be a positive number; the item is not for sale");
        return 0;
    }

    /** {@code skill-xp:} skill experience given through whichever skill plugin is installed. */
    private static double skillXp(Map<?, ?> section, String prefix, List<String> problems) {
        Object value = section.get("skill-xp");
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number && number.doubleValue() >= 0 && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        problems.add(prefix + "'skill-xp' should be a number, 0 or more; using 0");
        return 0;
    }

    /**
     * {@code jobs:} - job id to {@code {money, xp}}, paid through Jobs Reborn or ExcellentJobs to a
     * player who has that job. A job with a bad amount is left out; the others still pay.
     */
    private static Map<String, JobReward> jobs(Map<?, ?> section, String prefix, List<String> problems) {
        Map<?, ?> jobs = section(section, "jobs", prefix, problems);
        if (jobs == null) {
            return Map.of();
        }
        Map<String, JobReward> rewards = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : jobs.entrySet()) {
            String job = String.valueOf(entry.getKey()).trim();
            if (!(entry.getValue() instanceof Map<?, ?> reward)) {
                problems.add(prefix + "'jobs." + job + "' should be a section like {money: 2.5, xp: 4}");
                continue;
            }
            double money = amount(reward, "money");
            double xp = amount(reward, "xp");
            if (money < 0 || xp < 0) {
                problems.add(prefix + "'jobs." + job + "': 'money' and 'xp' should be numbers, 0 or more - not paid");
                continue;
            }
            if (money == 0 && xp == 0) {
                problems.add(prefix + "'jobs." + job + "' pays neither 'money' nor 'xp'");
                continue;
            }
            rewards.put(job, new JobReward(money, xp));
        }
        return rewards;
    }

    /** A reward amount: 0 when absent, -1 when it is not a number of 0 or more. */
    private static double amount(Map<?, ?> section, String key) {
        Object value = section.get(key);
        if (value == null) {
            return 0;
        }
        return value instanceof Number number && number.doubleValue() >= 0 && Double.isFinite(number.doubleValue())
                ? number.doubleValue() : -1;
    }

    /** The icon of a Blockbench-drawn piece of furniture: its merged model, or its first bone's. */
    private static ModelSource icon(String namespace, String id, Placement.Animated animated, Path sourceRoot) {
        String json = animated.model().icon();
        if (json == null) {
            json = animated.model().bones().stream().map(AnimatedModel.Bone::model).filter(Objects::nonNull)
                    .findFirst().orElse(null);
        }
        return json == null ? null : new ModelSource.Inline(sourceRoot, new ResourceLocation(namespace, id + "/icon"), json);
    }

    private static Placement.Furniture furniture(String namespace, String id, Map<?, ?> section, Path sourceRoot,
                                                 String prefix, List<String> problems) {
        if (section == null) {
            return new Placement.Furniture(Placement.Support.BARRIER, 0, true, Placement.Display.DEFAULT, null, null, null);
        }

        Placement.Support support = Placement.Support.BARRIER;
        String rawSupport = text(section, "support", prefix, problems);
        if (rawSupport != null) {
            try {
                support = Placement.Support.valueOf(rawSupport.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                problems.add(prefix + "'support' must be BARRIER, LIGHT, CHEST or BED, not '" + rawSupport.trim()
                        + "'; using BARRIER");
            }
        }

        int light = integer(section, "light", 0, prefix, problems);
        if (light < 0 || light > 15) {
            int clamped = Math.max(0, Math.min(15, light));
            problems.add(prefix + "'light' must be 0-15; using " + clamped);
            light = clamped;
        }
        // 3.1: a barrier support gives its light from the block above the piece, when that is free.
        if (support != Placement.Support.LIGHT && support != Placement.Support.BARRIER && section.containsKey("light")) {
            problems.add(prefix + "'light' only applies to a LIGHT or BARRIER support");
            light = 0;
        }

        String interactable = interactable(section, support, prefix, problems);
        // A chest is for keeping things; a bed is for sleeping, which vanilla does.
        if (support == Placement.Support.CHEST && !interactable.equals("storage")) {
            problems.add(prefix + "a CHEST support is always storage, not a " + interactable);
            interactable = "storage";
        }
        if (support == Placement.Support.BED && !interactable.isEmpty()) {
            problems.add(prefix + "a BED support sleeps, as a vanilla bed does; 'interactable: " + interactable
                    + "' is ignored");
            interactable = "";
        }
        if ((support == Placement.Support.CHEST || support == Placement.Support.BED) && section.containsKey("face-player")) {
            problems.add(prefix + "'face-player' is ignored: a " + support + " faces the way vanilla placed it");
        }

        Placement.Animated animated = animated(namespace, new ResourceLocation(namespace, id), section, sourceRoot,
                "open", "close", interactable.equals("storage"), prefix, problems);
        String modelEngineId = modelEngineId(section, prefix, problems);
        if (animated != null && modelEngineId != null) {
            problems.add(prefix + "both 'animated-model' and 'modelengine-id' are set; the animated model is used");
            modelEngineId = null;
        }
        Placement.Storage storage = interactable.equals("storage") ? storage(section, prefix, problems) : null;
        Placement.DoubleChest doubleChest = null;
        if (section.containsKey("double")) {
            if (support != Placement.Support.CHEST) {
                problems.add(prefix + "'double' joins two chests: it needs support: CHEST");
            } else {
                doubleChest = doubleChest(namespace, id, section(section, "double", prefix, problems), sourceRoot,
                        prefix + "double: ", problems);
                if (doubleChest != null && storage != null && storage.slots() > Placement.DoubleChest.HALF_SLOTS) {
                    problems.add(prefix + "a chest that joins into a double chest holds "
                            + Placement.DoubleChest.HALF_SLOTS + " slots, not " + storage.slots() + "; using "
                            + Placement.DoubleChest.HALF_SLOTS);
                    storage = new Placement.Storage(Placement.DoubleChest.HALF_SLOTS, storage.title());
                }
            }
        }
        Footprint footprint = footprint(section, support, prefix, problems);
        Placement.Ambience ambience = interactable.equals("effect")
                ? InteractionReader.ambience(section(section, "effect", prefix, problems), prefix, problems) : null;
        Placement.Commands commands = interactable.equals("command")
                ? InteractionReader.commands(section(section, "command", prefix, problems), prefix, problems) : null;
        Placement.Guillotine guillotine = interactable.equals("guillotine")
                ? InteractionReader.guillotine(section(section, "guillotine", prefix, problems), animated, footprint,
                prefix, problems) : null;
        return new Placement.Furniture(support, light,
                flag(section, "face-player", true, prefix, problems),
                display(section(section, "display", prefix, problems), prefix, problems),
                modelEngineId,
                interactable.equals("seat") ? seat(section, prefix, problems) : null,
                storage,
                animated,
                support == Placement.Support.BED ? bed(section, prefix, problems) : null,
                doubleChest,
                footprint,
                interactable.equals("board") ? board(section, prefix, problems) : null,
                ambience, commands, guillotine);
    }

    /**
     * {@code double:} what two chests of this furniture become side by side - a Blockbench model two
     * chests wide with its {@code open_double} and {@code close_double} animations, or a plain
     * {@code model}, either centred between the two.
     */
    private static Placement.DoubleChest doubleChest(String namespace, String id, Map<?, ?> section, Path sourceRoot,
                                                     String prefix, List<String> problems) {
        if (section == null) {
            return null;
        }
        ResourceLocation key = Placement.DoubleChest.itemModel(new ResourceLocation(namespace, id));
        Placement.Animated animated = animated(namespace, key, section, sourceRoot, "open_double", "close_double",
                true, prefix, problems);
        ResourceLocation model = location(section, "model", namespace, prefix, problems);
        if (animated != null && model != null) {
            problems.add(prefix + "both 'animated-model' and 'model' are set; the animated model is used");
            model = null;
        }
        if (animated == null && model == null) {
            problems.add(prefix + "needs 'animated-model' (a .bbmodel two chests wide) or 'model' - the chests stay"
                    + " single");
            return null;
        }
        String title = text(section, "storage-title", prefix, problems);
        return new Placement.DoubleChest(animated, model == null ? null : new ModelSource.Provided(sourceRoot, model),
                title == null || title.isBlank() ? null : title);
    }

    /**
     * {@code size:} the blocks a piece stands on - {@code {width: 3, height: 2}}, or {@code 3x2} -
     * one invisible support each; since 3.1 also {@code depth}, or {@code 6x2x6}. Only barrier and
     * light supports can be more than one block.
     */
    private static Footprint footprint(Map<?, ?> section, Placement.Support support, String prefix,
                                       List<String> problems) {
        Object raw = section.get("size");
        if (raw == null) {
            return Footprint.SINGLE;
        }
        int width;
        int height;
        int depth;
        if (raw instanceof Map<?, ?> size) {
            width = size.get("width") instanceof Number number ? number.intValue() : 1;
            height = size.get("height") instanceof Number number ? number.intValue() : 1;
            depth = size.get("depth") instanceof Number number ? number.intValue() : 1;
        } else {
            String[] parts = String.valueOf(raw).trim().toLowerCase(Locale.ROOT).split("x");
            try {
                width = Integer.parseInt(parts[0].trim());
                height = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
                depth = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 1;
            } catch (NumberFormatException exception) {
                problems.add(prefix + "'size' should be {width: <n>, height: <n>, depth: <n>} or <width>x<height>[x<depth>],"
                        + " not '" + raw + "'");
                return Footprint.SINGLE;
            }
        }
        if (width < 1 || height < 1 || depth < 1 || width > Footprint.MAX || height > Footprint.MAX || depth > Footprint.MAX) {
            problems.add(prefix + "'size' is 1 to " + Footprint.MAX + " blocks each way, not " + width + "x" + height
                    + "x" + depth + "; using 1x1");
            return Footprint.SINGLE;
        }
        if ((width > 1 || height > 1 || depth > 1) && support != Placement.Support.BARRIER
                && support != Placement.Support.LIGHT) {
            problems.add(prefix + "only a BARRIER or LIGHT support can be more than one block; 'size' is ignored");
            return Footprint.SINGLE;
        }
        return new Footprint(width, height, depth);
    }

    /** {@code interactable: medieval_bulletin_board}: the title over its notices, {@code board-title}. */
    private static Placement.QuestBoard board(Map<?, ?> section, String prefix, List<String> problems) {
        String title = text(section, "board-title", prefix, problems);
        return title == null || title.isBlank() ? Placement.QuestBoard.DEFAULT : new Placement.QuestBoard(title);
    }

    /** {@code bed-color:} the vanilla bed underneath, white unless said otherwise. */
    private static Placement.Bed bed(Map<?, ?> section, String prefix, List<String> problems) {
        String raw = text(section, "bed-color", prefix, problems);
        if (raw == null) {
            return Placement.Bed.DEFAULT;
        }
        String color = raw.trim().toLowerCase(Locale.ROOT);
        if (!Placement.Bed.COLORS.contains(color)) {
            problems.add(prefix + "'bed-color' must be a dye colour (" + String.join(", ", Placement.Bed.COLORS)
                    + "); using white");
            return Placement.Bed.DEFAULT;
        }
        return new Placement.Bed(color);
    }

    /**
     * {@code animated-model:} a Blockbench project under {@code models/}, drawn bone by bone, and
     * {@code animations:} which of its animations play as its inventory opens and closes.
     */
    /**
     * {@code animated-model}, and which of its animations open and close it. Only storage opens and
     * closes, so only storage is told when one of the two is missing.
     */
    private static Placement.Animated animated(String namespace, ResourceLocation key, Map<?, ?> section,
                                               Path sourceRoot, String defaultOpen, String defaultClose,
                                               boolean storage, String prefix, List<String> problems) {
        Map<?, ?> names = section(section, "animations", prefix, problems);
        String raw = text(section, "animated-model", prefix, problems);
        if (raw == null || raw.isBlank()) {
            if (names != null) {
                problems.add(prefix + "'animations' is only read with an 'animated-model'");
            }
            return null;
        }
        String path = raw.trim().replaceFirst("\\.bbmodel$", "");
        if (path.contains(":")) {
            path = path.substring(path.indexOf(':') + 1);
        }
        if (!ResourceLocation.isValidPath(path)) {
            problems.add(prefix + "'animated-model' '" + raw.trim() + "' is not a valid path (a-z 0-9 _ - . /)");
            return null;
        }
        Path file = sourceRoot.resolve("models").resolve(path + ".bbmodel").normalize();
        if (!file.startsWith(sourceRoot.normalize()) || !Files.isRegularFile(file)) {
            problems.add(prefix + "'animated-model' " + path + ".bbmodel is not in " + sourceRoot.getFileName() + "/models/");
            return null;
        }
        AnimatedModel model = BbModelReader.read(file, sourceRoot, key, prefix, problems);
        if (model == null) {
            return null;
        }
        String open = names == null ? defaultOpen
                : Objects.requireNonNullElse(text(names, "open", prefix, problems), defaultOpen);
        String close = names == null ? defaultClose
                : Objects.requireNonNullElse(text(names, "close", prefix, problems), defaultClose);
        for (String animation : List.of(open, close)) {
            if (!model.clips().containsKey(animation) && (names != null || (storage && !model.clips().isEmpty()))) {
                problems.add(prefix + path + ".bbmodel has no animation '" + animation + "' (it has: "
                        + (model.clips().isEmpty() ? "none" : String.join(", ", model.clips().keySet())) + ")");
            }
        }
        return new Placement.Animated(model, open, close, file);
    }

    /**
     * What a right click does: {@code seat}, {@code storage}, or {@code ""} for nothing. The settings
     * of the kind not chosen are reported if present, rather than silently ignored.
     */
    private static String interactable(Map<?, ?> section, Placement.Support support, String prefix,
                                       List<String> problems) {
        String raw = text(section, "interactable", prefix, problems);
        String kind = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (kind.isEmpty() && support == Placement.Support.CHEST) {
            kind = "storage";
        }
        if (kind.equals("medieval_bulletin_board") || kind.equals("quest_board")) {
            kind = "board";
        }
        if (kind.equals("effects") || kind.equals("commands")) {
            kind = kind.substring(0, kind.length() - 1);
        }
        if (!kind.isEmpty() && !List.of("seat", "storage", "board", "effect", "command", "guillotine").contains(kind)) {
            problems.add(prefix + "'interactable' can only be 'seat', 'storage', 'medieval_bulletin_board', 'effect',"
                    + " 'command' or 'guillotine', not '" + raw.trim() + "'");
            kind = "";
        }
        for (String key : List.of("effect", "command", "guillotine")) {
            if (!kind.equals(key) && section.containsKey(key)) {
                problems.add(prefix + "'" + key + ":' is only read with interactable: " + key);
            }
        }
        if (!kind.equals("board") && section.containsKey("board-title")) {
            problems.add(prefix + "'board-title' is only read with interactable: medieval_bulletin_board");
        }
        if (!kind.equals("seat") && section.containsKey("seat-height")) {
            problems.add(prefix + "'seat-height' is only read with interactable: seat");
        }
        for (String key : List.of("slots", "storage-title", "show")) {
            if (!kind.equals("storage") && section.containsKey(key)) {
                problems.add(prefix + "'" + key + "' is only read with interactable: storage");
            }
        }
        return kind;
    }

    /** {@code interactable: storage}: how many {@code slots}, and the inventory's {@code storage-title}. */
    private static Placement.Storage storage(Map<?, ?> section, String prefix, List<String> problems) {
        int slots = Placement.Storage.DEFAULT_SLOTS;
        Object raw = section.get("slots");
        if (raw != null) {
            if (raw instanceof Integer number && Placement.Storage.validSlots(number)) {
                slots = number;
            } else {
                problems.add(prefix + "'slots' must be whole rows of nine - 9, 18, 27, 36, 45 or 54 - not '" + raw
                        + "'; using " + Placement.Storage.DEFAULT_SLOTS);
            }
        }
        String title = text(section, "storage-title", prefix, problems);
        Map<?, ?> show = section(section, "show", prefix, problems);
        return new Placement.Storage(slots, title == null || title.isBlank() ? null : title,
                show == null ? null : show(show, prefix + "show: ", problems));
    }

    /**
     * {@code show:} (3.4) - {@code armor: true} with {@code offset} and {@code yaw}, or {@code items:}
     * a list of {@code {offset, rotation, scale}}.
     */
    private static Placement.Show show(Map<?, ?> section, String prefix, List<String> problems) {
        boolean armor = flag(section, "armor", false, prefix, problems);
        Placement.Vec3 offset = vector(section, "offset", Placement.Vec3.ZERO, prefix, problems);
        float yaw = (float) number(section, "yaw", 0, -360, 360, prefix, problems);
        List<Placement.ShownItem> items = new ArrayList<>();
        Object raw = section.get("items");
        if (raw instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> item)) {
                    problems.add(prefix + "each of 'items' should be {offset: [x, y, z], rotation: [x, y, z], scale: 1}");
                    continue;
                }
                items.add(new Placement.ShownItem(vector(item, "offset", Placement.Vec3.ZERO, prefix, problems),
                        vector(item, "rotation", Placement.Vec3.ZERO, prefix, problems),
                        (float) number(item, "scale", 1, 0.05, 4, prefix, problems)));
            }
        } else if (raw != null) {
            problems.add(prefix + "'items' should be a list of {offset, rotation, scale}");
        }
        if (!armor && items.isEmpty()) {
            problems.add(prefix + "shows nothing: give it armor: true, or items");
        }
        return new Placement.Show(armor, offset, yaw, items);
    }

    /**
     * {@code interactable: seat}, and where the seat is: {@code seat-height}, blocks above the floor,
     * and (3.2) {@code seat-at: [across, forward]} for one seat in the middle of a piece several
     * blocks big, moved that far.
     */
    private static Placement.Seat seat(Map<?, ?> section, String prefix, List<String> problems) {
        Object height = section.get("seat-height");
        float seatHeight = Placement.Seat.DEFAULT.height();
        if (height instanceof Number number && number.doubleValue() >= 0 && number.doubleValue() <= 3) {
            seatHeight = number.floatValue();
        } else if (height != null) {
            problems.add(prefix + "'seat-height' should be a number from 0 to 3; using " + Placement.Seat.DEFAULT.height());
        }
        Object at = section.get("seat-at");
        if (at == null) {
            return new Placement.Seat(seatHeight);
        }
        if (at instanceof List<?> pair && pair.size() == 2 && pair.get(0) instanceof Number across
                && pair.get(1) instanceof Number forward && Math.abs(across.doubleValue()) <= 3
                && Math.abs(forward.doubleValue()) <= 3) {
            return new Placement.Seat(seatHeight, true, across.floatValue(), forward.floatValue());
        }
        problems.add(prefix + "'seat-at' should be [across, forward] in blocks from the piece's middle, each -3 to 3;"
                + " a seat on each block instead");
        return new Placement.Seat(seatHeight);
    }

    /**
     * A crop. The item itself is the seed: it is always paper underneath, planted by this plugin
     * rather than placed by vanilla, and without its own {@code resource} it is drawn as its fully
     * grown stage.
     */
    private static ItemDefinition crop(String namespace, String id, Map<?, ?> section, ModelSource model,
                                       String displayName, List<String> lore, Path sourceRoot, Path file,
                                       String prefix, List<String> problems) {
        Map<?, ?> crop = section(section, "crop", prefix, problems);
        if (crop == null) {
            problems.add(prefix + "a custom_crop needs a 'crop' section with its 'stages'");
            return null;
        }
        Object stagesNode = crop.get("stages");
        if (!(stagesNode instanceof List<?> stageList) || stageList.size() < 2) {
            problems.add(prefix + "'crop.stages' should list at least two stages, first to fully grown");
            return null;
        }
        List<ModelSource> stages = new ArrayList<>();
        for (int stage = 0; stage < stageList.size(); stage++) {
            String stagePrefix = prefix + "stage " + stage + ": ";
            Object entry = stageList.get(stage);
            // A bare string is the one texture of a cross, like a sapling or a flower.
            Map<?, ?> stageSection = entry instanceof String texture ? Map.of("texture", texture) : entry instanceof Map<?, ?> map ? map : null;
            ModelSource stageModel = stageSection == null ? null
                    : stageModel(namespace, id, stage, stageSection, sourceRoot, stagePrefix, problems);
            if (stageModel == null) {
                problems.add(stagePrefix + "needs 'model', 'texture' or 'textures' - the crop is skipped");
                return null;
            }
            stages.add(stageModel);
        }

        int stageSeconds = integer(crop, "stage-seconds", 120, prefix, problems);
        if (stageSeconds < 1) {
            problems.add(prefix + "'stage-seconds' must be at least 1; using 120");
            stageSeconds = 120;
        }
        int minLight = integer(crop, "min-light", 9, prefix, problems);
        if (minLight < 0 || minLight > 15) {
            problems.add(prefix + "'min-light' must be 0-15; using 9");
            minLight = 9;
        }
        List<String> soils = lines(crop, "soil", prefix, problems).stream()
                .map(soil -> soil.trim().toUpperCase(Locale.ROOT))
                .filter(soil -> !soil.isEmpty())
                .toList();
        List<Placement.Drop> drops = drops(crop.get("drops"), prefix, problems);

        if (section.containsKey("material")) {
            problems.add(prefix + "'material' is ignored - a custom_crop's item is always PAPER underneath");
        }
        ModelSource look = model != null ? model : stages.get(stages.size() - 1);
        Placement.Crop placement = new Placement.Crop(stages, stageSeconds, minLight,
                soils.isEmpty() ? List.of("FARMLAND") : soils, flag(crop, "bone-meal", true, prefix, problems), drops,
                skillXp(crop, prefix, problems), jobs(crop, prefix, problems));
        // Placeable, like blocks and furniture: planting places the crop's block, through a real
        // BlockPlaceEvent that the guard against placing custom items must let through.
        return new ItemDefinition(namespace, id, "PAPER", displayName, lore, look, new ItemBehaviour(false, true),
                placement, file);
    }

    /**
     * A liquid: the entry's item is its bucket, and {@code liquid:} says how it looks, how it flows
     * and what it does to a player in it.
     *
     * <pre>
     * acid:
     *   type: custom_liquid
     *   display-name: "&lt;green&gt;Bucket of acid"
     *   resource: { texture: item/acid_bucket }
     *   liquid:
     *     texture: block/acid          # or model: / flowing-model: for models of your own
     *     flow-distance: 4
     *     tick-rate: 10
     *     contact: { element: poison, damage: 1 }
     * </pre>
     */
    private static ItemDefinition liquid(String namespace, String id, Map<?, ?> section, ModelSource model,
                                         String displayName, List<String> lore, Path sourceRoot, Path file,
                                         String prefix, List<String> problems) {
        Map<?, ?> liquid = section(section, "liquid", prefix, problems);
        if (liquid == null) {
            problems.add(prefix + "a custom_liquid needs a 'liquid' section with at least its 'texture'");
            return null;
        }
        String where = prefix + "liquid: ";
        ResourceLocation stillModel = location(liquid, "model", namespace, where, problems);
        ResourceLocation flowingModel = location(liquid, "flowing-model", namespace, where, problems);
        ResourceLocation still = location(liquid, "texture", namespace, where, problems);
        ResourceLocation flowingTexture = location(liquid, "flowing-texture", namespace, where, problems);
        if (stillModel == null && still == null) {
            problems.add(where + "needs 'texture' (or 'model') - without one it would be invisible");
            return null;
        }
        int glow = (int) number(liquid, "glow", 0, 0, 15, where, problems);
        if (glow > 0 && stillModel != null && flowingModel != null) {
            problems.add(where + "'glow' applies to a liquid drawn from its 'texture'; a model of your own sets"
                    + " \"light_emission\" on its elements itself");
        }
        ModelSource source = stillModel != null ? new ModelSource.Provided(sourceRoot, stillModel)
                : liquidModel(namespace, id, "source", still, glow, sourceRoot);
        ModelSource flowing;
        if (flowingModel != null) {
            flowing = new ModelSource.Provided(sourceRoot, flowingModel);
        } else if (still != null || flowingTexture != null) {
            flowing = liquidModel(namespace, id, "flowing", flowingTexture != null ? flowingTexture : still, glow,
                    sourceRoot);
        } else {
            flowing = source;
        }

        int flowDistance = (int) number(liquid, "flow-distance", 4, 1, 8, where, problems);
        int tickRate = (int) number(liquid, "tick-rate", 5, 1, 200, where, problems);
        int maxFall = (int) number(liquid, "max-fall", 32, 0, 256, where, problems);
        Map<?, ?> contactSection = section(liquid, "contact", where, problems);
        Placement.Contact contact = contactSection == null ? Placement.Contact.HARMLESS
                : contact(contactSection, where + "contact ", problems);

        if (section.containsKey("material")) {
            problems.add(prefix + "'material' is ignored - a custom_liquid's bucket is always PAPER underneath");
        }
        // Without a look of its own, the bucket looks like a water bucket rather than paper.
        ModelSource bucket = model != null ? model
                : new ModelSource.Provided(sourceRoot, new ResourceLocation(ResourceLocation.MINECRAFT, "item/water_bucket"));
        return new ItemDefinition(namespace, id, "PAPER", displayName, lore, bucket, new ItemBehaviour(true, false),
                new Placement.Liquid(flowDistance, tickRate, maxFall, source, flowing, contact, glow), file);
    }

    /**
     * The model a liquid is drawn with: its texture on a box a little lower than a block, under one
     * of the two parents the pack compiler writes ({@link LiquidModels}).
     */
    private static ModelSource liquidModel(String namespace, String id, String kind, ResourceLocation texture,
                                           int glow, Path sourceRoot) {
        ResourceLocation parent = LiquidModels.parentFor(kind.equals("source"), glow);
        return new ModelSource.Generated(sourceRoot, new ResourceLocation(namespace, "block/" + id + "_" + kind), parent,
                Map.of(LiquidModels.TEXTURE_VARIABLE, texture, "particle", texture));
    }

    /**
     * {@code contact:} - {@code element} sets what a kind of liquid usually does, and any key
     * written next to it changes that part:
     * fire burns, frost freezes and slows, poison and wither give their effect, acid only hurts.
     */
    private static Placement.Contact contact(Map<?, ?> section, String prefix, List<String> problems) {
        double damage = 0;
        String damageType = "minecraft:generic";
        int fireTicks = 0;
        int freezeTicks = 0;
        List<Placement.Effect> effects = new ArrayList<>();
        String element = text(section, "element", prefix, problems);
        if (element != null) {
            switch (element.trim().toLowerCase(Locale.ROOT)) {
                case "fire" -> {
                    damage = 1;
                    damageType = "minecraft:in_fire";
                    fireTicks = 80;
                }
                case "frost", "ice" -> {
                    damage = 1;
                    damageType = "minecraft:freeze";
                    freezeTicks = 200;
                    effects.add(new Placement.Effect("minecraft:slowness", 40, 1));
                }
                case "poison" -> {
                    damageType = "minecraft:magic";
                    effects.add(new Placement.Effect("minecraft:poison", 60, 0));
                }
                case "wither" -> {
                    damageType = "minecraft:wither";
                    effects.add(new Placement.Effect("minecraft:wither", 60, 0));
                }
                case "acid" -> {
                    damage = 2;
                    damageType = "minecraft:magic";
                }
                default -> problems.add(prefix + "unknown element '" + element.trim()
                        + "' (fire, frost, poison, wither, acid)");
            }
        }
        int interval = (int) number(section, "interval-ticks", 10, 1, 1200, prefix, problems);
        damage = number(section, "damage", damage, 0, 1000, prefix, problems);
        String rawType = text(section, "damage-type", prefix, problems);
        if (rawType != null && !rawType.isBlank()) {
            damageType = rawType.trim().toLowerCase(Locale.ROOT);
            if (damageType.indexOf(':') < 0) {
                damageType = "minecraft:" + damageType;
            }
        }
        fireTicks = (int) number(section, "fire-ticks", fireTicks, 0, 72000, prefix, problems);
        freezeTicks = (int) number(section, "freeze-ticks", freezeTicks, 0, 72000, prefix, problems);
        if (section.containsKey("effects")) {
            effects.clear();
            Object node = section.get("effects");
            if (!(node instanceof List<?> list)) {
                problems.add(prefix + "'effects' should be a list of {type, duration, amplifier}");
            } else {
                for (Object entry : list) {
                    Map<?, ?> effect = entry instanceof Map<?, ?> map ? map
                            : entry == null ? null : Map.of("type", String.valueOf(entry));
                    String type = effect == null ? null : text(effect, "type", prefix, problems);
                    if (type == null || type.isBlank()) {
                        problems.add(prefix + "each effect needs a 'type', e.g. poison");
                        continue;
                    }
                    String key = type.trim().toLowerCase(Locale.ROOT);
                    effects.add(new Placement.Effect(key.indexOf(':') < 0 ? "minecraft:" + key : key,
                            (int) number(effect, "duration", 60, 1, 72000, prefix, problems),
                            (int) number(effect, "amplifier", 0, 0, 255, prefix, problems)));
                }
            }
        }
        boolean mobs = flag(section, "mobs", false, prefix, problems);
        return new Placement.Contact(interval, damage, damageType, fireTicks, freezeTicks, effects, mobs);
    }

    /**
     * One stage's look. The same keys as {@code resource}, but a lone {@code texture} makes a
     * {@code block/cross} - two crossed planes, like every vanilla sapling and flower - since a
     * crop drawn as a cube or a flat item would look like neither.
     */
    private static ModelSource stageModel(String namespace, String id, int stage, Map<?, ?> section, Path sourceRoot,
                                          String prefix, List<String> problems) {
        ResourceLocation model = location(section, "model", namespace, prefix, problems);
        if (model != null) {
            return new ModelSource.Provided(sourceRoot, model);
        }
        ResourceLocation texture = location(section, "texture", namespace, prefix, problems);
        Map<String, ResourceLocation> variables = new LinkedHashMap<>(textureVariables(section, namespace, prefix, problems));
        ResourceLocation parent = location(section, "parent", ResourceLocation.MINECRAFT, prefix, problems);
        if (texture != null) {
            variables.putIfAbsent(parent == null ? "cross" : "crop", texture);
        }
        if (variables.isEmpty()) {
            return null;
        }
        if (parent == null) {
            parent = new ResourceLocation(ResourceLocation.MINECRAFT, variables.containsKey("crop") ? "block/crop" : "block/cross");
        }
        return new ModelSource.Generated(sourceRoot, new ResourceLocation(namespace, "block/" + id + "_stage_" + stage),
                parent, variables);
    }

    /** {@code drops:} - each an item (custom {@code ns:id} or vanilla), an amount and a chance. */
    private static List<Placement.Drop> drops(Object node, String prefix, List<String> problems) {
        if (node == null) {
            return List.of();
        }
        if (!(node instanceof List<?> list)) {
            problems.add(prefix + "'crop.drops' should be a list");
            return List.of();
        }
        List<Placement.Drop> drops = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> drop) || drop.get("item") == null) {
                problems.add(prefix + "each of 'crop.drops' needs at least 'item'");
                continue;
            }
            String item = String.valueOf(drop.get("item")).trim();
            int min = 1;
            int max = 1;
            Object amount = drop.get("amount");
            if (amount instanceof Integer || amount instanceof Long) {
                min = max = ((Number) amount).intValue();
            } else if (amount != null) {
                Matcher range = AMOUNT_RANGE.matcher(String.valueOf(amount).trim());
                if (range.matches()) {
                    min = Integer.parseInt(range.group(1));
                    max = Integer.parseInt(range.group(2));
                } else {
                    problems.add(prefix + "drop " + item + ": 'amount' should be a number or a range like 1-3; using 1");
                }
            }
            if (min < 1 || max < min) {
                problems.add(prefix + "drop " + item + ": amount " + min + "-" + max + " makes no sense; using 1");
                min = max = 1;
            }
            double chance = 1;
            if (drop.get("chance") instanceof Number number) {
                chance = number.doubleValue();
            } else if (drop.get("chance") != null) {
                problems.add(prefix + "drop " + item + ": 'chance' should be a number from 0 to 1; using 1");
            }
            if (chance <= 0 || chance > 1) {
                problems.add(prefix + "drop " + item + ": 'chance' must be above 0 and at most 1; using 1");
                chance = 1;
            }
            drops.add(new Placement.Drop(item, min, max, chance));
        }
        return drops;
    }

    /**
     * {@code emojis:} - name to texture, or name to a section with {@code texture}, {@code height},
     * {@code ascent} and {@code permission}. Names are shared by every namespace, since chat has no
     * room for one: the first file, in path order, keeps a name.
     */
    private static void readEmojis(Object node, String namespace, Path sourceRoot, Path file, String where,
                                   Path contentsDir, Map<String, EmojiDefinition> emojis, List<String> problems) {
        if (node == null) {
            return;
        }
        if (!(node instanceof Map<?, ?> section)) {
            problems.add(where + ": 'emojis' should be a section of emoji names");
            return;
        }
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String name = String.valueOf(entry.getKey());
            String prefix = where + " > emoji " + name + ": ";
            if (!EMOJI_NAME.matcher(name).matches()) {
                problems.add(prefix + "invalid name (allowed: a-z 0-9 _)");
                continue;
            }
            Map<?, ?> emoji = entry.getValue() instanceof Map<?, ?> map ? map : Map.of("texture", String.valueOf(entry.getValue()));
            ResourceLocation texture = location(emoji, "texture", namespace, prefix, problems);
            if (texture == null) {
                problems.add(prefix + "needs 'texture', e.g. emoji/" + name);
                continue;
            }
            int height = integer(emoji, "height", EMOJI_HEIGHT, prefix, problems);
            int ascent = integer(emoji, "ascent", Math.min(EMOJI_ASCENT, height), prefix, problems);
            if (height < 1 || height > 256 || ascent > height) {
                problems.add(prefix + "'height' must be 1-256 and 'ascent' no more than it; using "
                        + EMOJI_HEIGHT + " and " + EMOJI_ASCENT);
                height = EMOJI_HEIGHT;
                ascent = EMOJI_ASCENT;
            }
            String permission = text(emoji, "permission", prefix, problems);
            EmojiDefinition definition = new EmojiDefinition(namespace, name, texture, height, ascent,
                    permission == null || permission.isBlank() ? null : permission.trim(), sourceRoot, file);
            EmojiDefinition earlier = emojis.putIfAbsent(name, definition);
            if (earlier != null) {
                problems.add(prefix + "already defined in " + unix(contentsDir.relativize(earlier.source()))
                        + " - this one is ignored");
            }
        }
    }

    /**
     * {@code font_images:} - pictures drawn as characters, by name, in ItemsAdder's own format (see
     * {@link FontImageDefinition}): {@code path}, {@code y_position}, {@code scale_ratio} and
     * {@code symbol}, or this plugin's words for the first three, {@code texture}, {@code ascent} and
     * {@code height}.
     */
    private static void readFontImages(Object node, String namespace, Path sourceRoot, Path file, String where,
                                       Path contentsDir, Map<String, FontImageDefinition> fontImages,
                                       List<String> problems) {
        if (node == null) {
            return;
        }
        if (!(node instanceof Map<?, ?> section)) {
            problems.add(where + ": 'font_images' should be a section of image names");
            return;
        }
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String name = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
            String prefix = where + " > font image " + namespace + ":" + name + ": ";
            if (!FONT_IMAGE_NAME.matcher(name).matches()) {
                problems.add(prefix + "invalid name (allowed: a-z 0-9 _ - .)");
                continue;
            }
            Map<?, ?> image = entry.getValue() instanceof Map<?, ?> map ? map
                    : Map.of("path", String.valueOf(entry.getValue()));
            String key = image.containsKey("texture") ? "texture" : "path";
            ResourceLocation texture = location(image, key, namespace, prefix, problems);
            if (texture == null) {
                problems.add(prefix + "needs 'path' (or 'texture'), the picture under textures/");
                continue;
            }
            Integer ascent = optionalInteger(image, image.containsKey("ascent") ? "ascent" : "y_position", prefix,
                    problems);
            Integer height = optionalInteger(image, image.containsKey("height") ? "height" : "scale_ratio", prefix,
                    problems);
            if (height != null && (height < 1 || height > 4096)) {
                problems.add(prefix + "a height of " + height + " cannot be drawn - the picture's own is used");
                height = null;
            }
            if (ascent != null && height != null && ascent > height) {
                problems.add(prefix + "y_position " + ascent + " is more than the height " + height
                        + ", which the client refuses - using " + height);
                ascent = height;
            }
            Integer symbol = null;
            Object rawSymbol = image.get("symbol");
            if (rawSymbol != null) {
                String text = String.valueOf(rawSymbol);
                if (text.codePointCount(0, text.length()) == 1) {
                    symbol = text.codePointAt(0);
                } else {
                    problems.add(prefix + "'symbol' should be one character - any free one is used");
                }
            }
            FontImageDefinition definition = new FontImageDefinition(namespace, name, texture, ascent, height, symbol,
                    sourceRoot, file);
            FontImageDefinition earlier = fontImages.putIfAbsent(definition.fullId(), definition);
            if (earlier != null) {
                problems.add(prefix + "already defined in " + unix(contentsDir.relativize(earlier.source()))
                        + " - this one is ignored");
            }
        }
    }

    private static Integer optionalInteger(Map<?, ?> section, String key, String prefix, List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            problems.add(prefix + "'" + key + "' should be a whole number - left out");
            return null;
        }
    }

    /**
     * {@code huds:} - bars drawn on the screen, by id.
     *
     * <pre>
     * huds:
     *   thirst:
     *     icons: { full: hud/drop_full, half: hud/drop_half, empty: hud/drop_empty }
     *     height: 9
     *     ascent: -32
     *     segments: 10
     *     offset: 10
     *     value: { start: 20, max: 20, per-second: -0.05, empty-damage: 1, consume: { POTION: 6 } }
     *   mana:
     *     icons: { full: hud/mana_full, empty: hud/mana_empty }
     *     value: { placeholder: "%mmocore_mana%", max: "%mmocore_max_mana%" }
     *   shield:                                   # 2.0
     *     icons: { full: hud/shield_full, empty: hud/shield_empty }
     *     anchor: above-health                    # or { at: above-food, row: 1, nudge: [0, 1] }
     *     value: { stat: absorption, max: 20 }    # or { auraskills: mana }
     * </pre>
     */
    private static void readHuds(Object node, String namespace, Path sourceRoot, Path file, String where,
                                 Path contentsDir, Map<String, HudDefinition> huds, List<String> problems) {
        if (node == null) {
            return;
        }
        if (!(node instanceof Map<?, ?> section)) {
            problems.add(where + ": 'huds' should be a section of HUD ids");
            return;
        }
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String id = String.valueOf(entry.getKey());
            String prefix = where + " > hud " + namespace + ":" + id + ": ";
            if (!EMOJI_NAME.matcher(id).matches()) {
                problems.add(prefix + "invalid id (allowed: a-z 0-9 _)");
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> hud)) {
                problems.add(prefix + "should be a section with at least 'icons' and 'value'");
                continue;
            }
            HudDefinition definition = hud(namespace, id, hud, sourceRoot, file, prefix, problems);
            if (definition == null) {
                continue;
            }
            HudDefinition earlier = huds.putIfAbsent(definition.fullId(), definition);
            if (earlier != null) {
                problems.add(prefix + "already defined in " + unix(contentsDir.relativize(earlier.file()))
                        + " - this one is ignored");
            }
        }
    }

    private static HudDefinition hud(String namespace, String id, Map<?, ?> section, Path sourceRoot, Path file,
                                     String prefix, List<String> problems) {
        Map<?, ?> icons = section(section, "icons", prefix, problems);
        ResourceLocation full = icons == null ? null : location(icons, "full", namespace, prefix + "icons ", problems);
        if (full == null) {
            problems.add(prefix + "needs 'icons' with at least 'full', e.g. icons: { full: hud/mana_full }");
            return null;
        }
        ResourceLocation half = location(icons, "half", namespace, prefix + "icons ", problems);
        ResourceLocation empty = location(icons, "empty", namespace, prefix + "icons ", problems);
        for (ResourceLocation icon : Arrays.asList(full, half, empty)) {
            if (icon != null && icon.namespace().equals(namespace)
                    && !Files.isRegularFile(texturePath(sourceRoot, icon))) {
                problems.add(prefix + "icon textures/" + icon.path() + ".png is not in " + sourceRoot.getFileName());
                return null;
            }
        }

        int height = (int) number(section, "height", 9, 1, 256, prefix, problems);
        HudAnchor anchor = hudAnchor(section.get("anchor"), height, prefix, problems);
        if (anchor != null && (section.containsKey("ascent") || section.containsKey("offset"))) {
            problems.add(prefix + "'anchor' places the bar itself - 'ascent' and 'offset' are ignored; move it with"
                    + " anchor: { at: " + anchor.kind().key() + ", nudge: [x, y] }");
        }
        int ascent = anchor != null ? anchor.ascent() : (int) number(section, "ascent", 7, -512, 256, prefix, problems);
        if (ascent > height) {
            problems.add(prefix + "'ascent' can be at most 'height' (" + height + "); using " + height);
            ascent = height;
        }
        int segments = (int) number(section, "segments", 10, 1, 100, prefix, problems);
        int spacing = (int) number(section, "spacing", 0, -64, 64, prefix, problems);
        int offset = anchor != null ? 0 : (int) number(section, "offset", 0, -2048, 2048, prefix, problems);

        Map<?, ?> value = section(section, "value", prefix, problems);
        HudDefinition.Source source;
        if (value != null && (value.containsKey("stat") || value.containsKey("auraskills"))) {
            String stat = value.containsKey("auraskills")
                    ? "auraskills:" + String.valueOf(value.get("auraskills")).trim().toLowerCase(Locale.ROOT)
                    : String.valueOf(value.get("stat")).trim().toLowerCase(Locale.ROOT);
            if (!HudDefinition.Source.Stat.NAMES.contains(stat)) {
                problems.add(prefix + "unknown stat '" + stat + "' ("
                        + String.join(", ", HudDefinition.Source.Stat.NAMES) + ")");
                return null;
            }
            source = new HudDefinition.Source.Stat(stat, number(value, "max", 0, 0, 1_000_000, prefix, problems));
        } else if (value != null && value.containsKey("placeholder")) {
            String placeholder = text(value, "placeholder", prefix, problems);
            Object max = value.get("max");
            if (placeholder == null || placeholder.isBlank() || max == null || max instanceof Map<?, ?>
                    || max instanceof List<?>) {
                problems.add(prefix + "a placeholder value needs 'placeholder' and 'max' (a number or a placeholder)");
                return null;
            }
            source = new HudDefinition.Source.Placeholder(placeholder.trim(), String.valueOf(max).trim());
        } else {
            Map<?, ?> stored = value == null ? Map.of() : value;
            double max = number(stored, "max", 20, 0.001, 1_000_000, prefix, problems);
            double start = number(stored, "start", max, 0, max, prefix, problems);
            Map<String, Double> consume = new LinkedHashMap<>();
            Map<?, ?> consumeSection = section(stored, "consume", prefix, problems);
            if (consumeSection != null) {
                for (Map.Entry<?, ?> item : consumeSection.entrySet()) {
                    if (item.getValue() instanceof Number amount && Double.isFinite(amount.doubleValue())) {
                        consume.put(String.valueOf(item.getKey()).trim(), amount.doubleValue());
                    } else {
                        problems.add(prefix + "'consume." + item.getKey() + "' should be a number to add");
                    }
                }
            }
            source = new HudDefinition.Source.Stored(start, max,
                    number(stored, "per-second", 0, -1_000_000, 1_000_000, prefix, problems),
                    number(stored, "empty-damage", 0, 0, 1000, prefix, problems), consume);
        }
        // A bar placed against the vanilla HUD is meant for the action bar, unless it says otherwise.
        return new HudDefinition(namespace, id, full, half, empty, height, ascent, segments, spacing, offset, source,
                flag(section, "action-bar", anchor != null, prefix, problems), sourceRoot, file, anchor,
                betterHud(section.get("betterhud"), namespace, sourceRoot, prefix, problems),
                bedrockBar(section.get("bedrock"), prefix, problems));
    }

    /**
     * 3.4: {@code bedrock:} - {@code true}, {@code false}, or a section: {@code enabled},
     * {@code title} and {@code color}. Null when it is not there: the default.
     */
    private static HudDefinition.Bedrock bedrockBar(Object node, String prefix, List<String> problems) {
        if (node == null) {
            return null;
        }
        if (node instanceof Boolean shown) {
            return new HudDefinition.Bedrock(shown, null, "blue");
        }
        if (!(node instanceof Map<?, ?> section)) {
            problems.add(prefix + "'bedrock' should be true, false or a section such as { title: 'Sed', color: blue };"
                    + " using the default");
            return null;
        }
        String color = String.valueOf(section.containsKey("color") ? section.get("color") : "blue").trim()
                .toLowerCase(Locale.ROOT);
        if (!HudDefinition.Bedrock.COLORS.contains(color)) {
            problems.add(prefix + "'bedrock.color' should be one of " + String.join(", ", HudDefinition.Bedrock.COLORS)
                    + "; using blue");
            color = "blue";
        }
        Object title = section.get("title");
        return new HudDefinition.Bedrock(flag(section, "enabled", true, prefix, problems),
                title == null ? null : String.valueOf(title), color);
    }

    /**
     * 3.1: {@code betterhud:} - {@code true}, {@code false}, or a section: {@code enabled},
     * {@code gui: [x, y]} and {@code pixel: [x, y]} for a place of its own on BetterHud's screen, and
     * (3.1.1) {@code icon}, {@code color}, {@code hide-empty} and {@code warn-below} for a bar drawn as
     * BetterHud's status bars. Null when it is not there: config.yml decides.
     */
    private static HudDefinition.BetterHud betterHud(Object node, String namespace, Path sourceRoot, String prefix,
                                                     List<String> problems) {
        if (node == null) {
            return null;
        }
        if (node instanceof Boolean drawn) {
            return drawn ? HudDefinition.BetterHud.ON : HudDefinition.BetterHud.OFF;
        }
        if (!(node instanceof Map<?, ?> section)) {
            problems.add(prefix + "'betterhud' should be true, false or a section such as { gui: [x, y], pixel: [x, y] };"
                    + " left to config.yml");
            return null;
        }
        Boolean drawn = section.get("enabled") instanceof Boolean enabled ? enabled : null;
        ResourceLocation icon = location(section, "icon", namespace, prefix + "betterhud ", problems);
        if (icon != null && icon.namespace().equals(namespace) && !Files.isRegularFile(texturePath(sourceRoot, icon))) {
            problems.add(prefix + "betterhud icon textures/" + icon.path() + ".png is not in " + sourceRoot.getFileName()
                    + "; using the full icon, made smaller");
            icon = null;
        }
        Integer color = null;
        Object rawColor = section.get("color");
        if (rawColor != null) {
            String text = String.valueOf(rawColor).trim();
            if (text.matches("#?[0-9a-fA-F]{6}")) {
                color = Integer.parseInt(text.replace("#", ""), 16);
            } else {
                problems.add(prefix + "'betterhud.color' should be like \"#1c9ec0\"; using its icon's colour");
            }
        }
        return new HudDefinition.BetterHud(drawn, spot(section, "gui", -100, 200, prefix, problems),
                spot(section, "pixel", -4096, 4096, prefix, problems), icon, color,
                flag(section, "hide-empty", false, prefix, problems),
                (int) number(section, "warn-below", 0, 0, 100, prefix, problems));
    }

    private static HudDefinition.Spot spot(Map<?, ?> section, String key, int min, int max, String prefix,
                                           List<String> problems) {
        Object node = section.get(key);
        if (node == null) {
            return null;
        }
        if (node instanceof List<?> pair && pair.size() == 2 && pair.get(0) instanceof Number x
                && pair.get(1) instanceof Number y) {
            return new HudDefinition.Spot(Math.max(min, Math.min(max, x.intValue())),
                    Math.max(min, Math.min(max, y.intValue())));
        }
        problems.add(prefix + "'betterhud." + key + "' should be [x, y]; left where the bar was");
        return null;
    }

    /**
     * {@code anchor:} - {@code above-food}, or {@code { at: above-food, row: 1, nudge: [x, y] }}.
     * A row too high for icons this tall - a glyph's ascent may not exceed its height - is brought
     * down to the highest that fits, with a problem saying so.
     */
    private static HudAnchor hudAnchor(Object node, int height, String prefix, List<String> problems) {
        if (node == null) {
            return null;
        }
        String at;
        int row = 0;
        int nudgeX = 0;
        int nudgeY = 0;
        if (node instanceof Map<?, ?> anchor) {
            at = anchor.get("at") == null ? null : String.valueOf(anchor.get("at"));
            row = (int) number(anchor, "row", 0, 0, 20, prefix + "anchor ", problems);
            Object nudge = anchor.get("nudge");
            if (nudge instanceof List<?> pair && pair.size() == 2 && pair.get(0) instanceof Number x
                    && pair.get(1) instanceof Number y) {
                nudgeX = Math.max(-200, Math.min(200, x.intValue()));
                nudgeY = Math.max(-100, Math.min(100, y.intValue()));
            } else if (nudge != null) {
                problems.add(prefix + "anchor 'nudge' should be [x, y] in pixels, right and up");
            }
        } else {
            at = String.valueOf(node);
        }
        HudAnchor.Kind kind;
        try {
            kind = at == null ? null : HudAnchor.Kind.parse(at);
        } catch (IllegalArgumentException exception) {
            kind = null;
        }
        if (kind == null) {
            problems.add(prefix + "anchor '" + at + "' is not health, food, above-health or above-food - left where"
                    + " 'ascent' and 'offset' put it");
            return null;
        }
        int max = HudAnchor.maxRow(kind, height, nudgeY);
        if (row > max) {
            problems.add(prefix + "anchor row " + row + " is too high for icons " + height + " pixels tall (a glyph"
                    + " reaches at most its own height above its line); using row " + Math.max(0, max)
                    + " - taller icons, with empty space below, go higher");
            row = Math.max(0, max);
        }
        return new HudAnchor(kind, row, nudgeX, nudgeY);
    }

    /**
     * {@code advancements:} - id to section. What can be checked here is: frames, triggers and the
     * shape of each entry. Whether a parent, an icon or a worn item exists is only known once every
     * file is read, so {@link com.arkcronist.content.core.advancement.AdvancementCompiler} checks that.
     */
    private static void readAdvancements(Object node, String namespace, Path sourceRoot, Path file, String where,
                                         Path contentsDir, Map<String, AdvancementDefinition> advancements,
                                         List<String> problems) {
        if (node == null) {
            return;
        }
        if (!(node instanceof Map<?, ?> section)) {
            problems.add(where + ": 'advancements' should be a section of advancement ids");
            return;
        }
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String id = String.valueOf(entry.getKey());
            String prefix = where + " > advancement " + id + ": ";
            if (!ResourceLocation.isValidPath(id)) {
                problems.add(prefix + "invalid id (allowed: a-z 0-9 _ . - and / between segments)");
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> advancement)) {
                problems.add(prefix + "should be a section with at least 'title' and 'icon'");
                continue;
            }
            AdvancementDefinition definition = advancement(namespace, id, advancement, sourceRoot, file, prefix,
                    problems);
            if (definition == null) {
                continue;
            }
            AdvancementDefinition earlier = advancements.putIfAbsent(definition.fullId(), definition);
            if (earlier != null) {
                problems.add(prefix + "already defined in " + unix(contentsDir.relativize(earlier.source()))
                        + " - this one is ignored");
            }
        }
    }

    private static AdvancementDefinition advancement(String namespace, String id, Map<?, ?> section, Path sourceRoot,
                                                     Path file, String prefix, List<String> problems) {
        String title = text(section, "title", prefix, problems);
        ResourceLocation icon = location(section, "icon", namespace, prefix, problems);
        if (title == null || title.isBlank() || icon == null) {
            problems.add(prefix + "needs 'title' and 'icon' (an item id, e.g. ruby or minecraft:diamond)");
            return null;
        }
        String description = text(section, "description", prefix, problems);

        AdvancementDefinition.Frame frame = AdvancementDefinition.Frame.TASK;
        String rawFrame = text(section, "frame", prefix, problems);
        if (rawFrame != null) {
            AdvancementDefinition.Frame parsed = AdvancementDefinition.Frame.parse(rawFrame);
            if (parsed == null) {
                problems.add(prefix + "'frame' must be task, goal or challenge; using task");
            } else {
                frame = parsed;
            }
        }

        ResourceLocation parent = location(section, "parent", namespace, prefix, problems);
        ResourceLocation background = location(section, "background", namespace, prefix, problems);
        if (parent != null && background != null) {
            problems.add(prefix + "'background' is drawn for the root of a tab only; ignored under a parent");
            background = null;
        }

        AdvancementDefinition.Trigger trigger = trigger(section.get("trigger"), namespace, parent == null, prefix,
                problems);
        if (trigger == null) {
            return null;
        }
        boolean joins = trigger instanceof AdvancementDefinition.Trigger.Join;

        String announce = text(section, "announce", prefix, problems);
        int experience = 0;
        Map<?, ?> reward = section(section, "reward", prefix, problems);
        if (reward != null) {
            experience = integer(reward, "experience", 0, prefix, problems);
            if (experience < 0) {
                problems.add(prefix + "'reward.experience' should be 0 or more; using 0");
                experience = 0;
            }
        }
        return new AdvancementDefinition(namespace, id, title, description == null ? "" : description, icon, frame,
                parent, background,
                flag(section, "toast", !joins, prefix, problems),
                flag(section, "hidden", false, prefix, problems),
                announce == null || announce.isBlank() ? null : announce,
                flag(section, "celebrate", !joins, prefix, problems),
                experience, trigger, sourceRoot, file);
    }

    /**
     * {@code trigger:} - {@code join}, {@code manual}, {@code {wear: [items]}} or
     * {@code {obtain: item}}. Left out: a root is granted on joining (so everyone has its tab), a
     * child only by {@code /advancement grant}.
     */
    private static AdvancementDefinition.Trigger trigger(Object node, String namespace, boolean root, String prefix,
                                                         List<String> problems) {
        if (node == null) {
            return root ? new AdvancementDefinition.Trigger.Join() : new AdvancementDefinition.Trigger.Manual();
        }
        if (node instanceof String word) {
            switch (word.trim().toLowerCase(Locale.ROOT)) {
                case "join" -> {
                    return new AdvancementDefinition.Trigger.Join();
                }
                case "manual" -> {
                    return new AdvancementDefinition.Trigger.Manual();
                }
                default -> {
                    problems.add(prefix + "'trigger' must be join, manual, {wear: [items]} or {obtain: item}");
                    return null;
                }
            }
        }
        if (node instanceof Map<?, ?> section && section.size() == 1) {
            if (section.get("wear") instanceof List<?> list && !list.isEmpty()) {
                List<ResourceLocation> items = new ArrayList<>();
                for (Object item : list) {
                    ResourceLocation location = location(item, "trigger.wear", namespace, prefix, problems);
                    if (location == null) {
                        return null;
                    }
                    if (items.contains(location)) {
                        problems.add(prefix + "'trigger.wear' names " + location + " twice");
                        return null;
                    }
                    items.add(location);
                }
                return new AdvancementDefinition.Trigger.Wear(items);
            }
            if (section.containsKey("obtain")) {
                ResourceLocation item = location(section, "obtain", namespace, prefix, problems);
                return item == null ? null : new AdvancementDefinition.Trigger.Obtain(item);
            }
        }
        problems.add(prefix + "'trigger' must be join, manual, {wear: [items]} or {obtain: item}");
        return null;
    }

    /**
     * A ModelEngine blueprint id. Accepted as {@code modelengine-id} or, for configs written for
     * ModelEngine's own naming, {@code modelengine_id}.
     */
    private static String modelEngineId(Map<?, ?> section, String prefix, List<String> problems) {
        String key = section.containsKey("modelengine-id") ? "modelengine-id" : "modelengine_id";
        String id = text(section, key, prefix, problems);
        return id == null || id.isBlank() ? null : id.trim();
    }

    private static Placement.Display display(Map<?, ?> section, String prefix, List<String> problems) {
        if (section == null) {
            return Placement.Display.DEFAULT;
        }
        String transform = Placement.Display.DEFAULT.transform();
        String rawTransform = text(section, "transform", prefix, problems);
        if (rawTransform != null) {
            String upper = rawTransform.trim().toUpperCase(Locale.ROOT);
            if (Placement.Display.TRANSFORMS.contains(upper)) {
                transform = upper;
            } else {
                problems.add(prefix + "'display.transform' must be one of " + Placement.Display.TRANSFORMS
                        + "; using " + transform);
            }
        }
        return new Placement.Display(transform,
                vector(section, "translation", Placement.Vec3.ZERO, prefix, problems),
                vector(section, "scale", Placement.Vec3.ONE, prefix, problems),
                vector(section, "rotation", Placement.Vec3.ZERO, prefix, problems));
    }

    private static ItemBehaviour behaviour(Map<?, ?> section, String prefix, List<String> problems) {
        if (section == null) {
            return ItemBehaviour.DEFAULT;
        }
        return new ItemBehaviour(
                flag(section, "cancel-vanilla-use", ItemBehaviour.DEFAULT.cancelVanillaUse(), prefix, problems),
                flag(section, "placeable", ItemBehaviour.DEFAULT.placeable(), prefix, problems));
    }

    private static void unusedSection(Map<?, ?> section, String key, ContentType owner, ContentType type,
                                      String prefix, List<String> problems) {
        if (type != owner && section.containsKey(key)) {
            problems.add(prefix + "'" + key + "' is only read when type is " + owner.yamlName());
        }
    }

    // ---------------------------------------------------------------- typed reads

    private static ResourceLocation location(Map<?, ?> section, String key, String defaultNamespace,
                                             String prefix, List<String> problems) {
        return location(section.get(key), key, defaultNamespace, prefix, problems);
    }

    private static ResourceLocation location(Object node, String key, String defaultNamespace,
                                             String prefix, List<String> problems) {
        if (node instanceof Map<?, ?> || node instanceof List<?>) {
            problems.add(prefix + "'" + key + "' should be a single value");
            return null;
        }
        String raw = node == null ? null : String.valueOf(node);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        // Tolerate the extension people naturally type; the client never wants it.
        if (value.endsWith(".json") || value.endsWith(".png")) {
            value = value.substring(0, value.lastIndexOf('.'));
        }
        try {
            return ResourceLocation.parse(value, defaultNamespace);
        } catch (IllegalArgumentException exception) {
            problems.add(prefix + "'" + key + "': " + exception.getMessage());
            return null;
        }
    }

    private static String text(Map<?, ?> section, String key, String prefix, List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            problems.add(prefix + "'" + key + "' should be a single value");
            return null;
        }
        return String.valueOf(value);
    }

    private static List<String> lines(Map<?, ?> section, String key, String prefix, List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> lines = new ArrayList<>(list.size());
            for (Object line : list) {
                lines.add(line == null ? "" : String.valueOf(line));
            }
            return lines;
        }
        if (value instanceof Map<?, ?>) {
            problems.add(prefix + "'" + key + "' should be a list of lines");
            return List.of();
        }
        return List.of(String.valueOf(value));
    }

    /** A number from {@code min} to {@code max}; anything else is reported and the fallback used. */
    private static double number(Map<?, ?> section, String key, double fallback, double min, double max,
                                 String prefix, List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number && Double.isFinite(number.doubleValue())
                && number.doubleValue() >= min && number.doubleValue() <= max) {
            return number.doubleValue();
        }
        problems.add(prefix + "'" + key + "' should be a number from " + plain(min) + " to " + plain(max)
                + "; using " + plain(fallback));
        return fallback;
    }

    /** 2 rather than 2.0, for messages. */
    private static String plain(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15 ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static int integer(Map<?, ?> section, String key, int fallback, String prefix, List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Integer || value instanceof Long) {
            return ((Number) value).intValue();
        }
        problems.add(prefix + "'" + key + "' should be a whole number; using " + fallback);
        return fallback;
    }

    /** A number for all three axes, or a list of exactly three: {@code 0.5} or {@code [0, 0.5, 0]}. */
    private static Placement.Vec3 vector(Map<?, ?> section, String key, Placement.Vec3 fallback,
                                         String prefix, List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            float n = number.floatValue();
            return new Placement.Vec3(n, n, n);
        }
        if (value instanceof List<?> list && list.size() == 3
                && list.stream().allMatch(element -> element instanceof Number)) {
            return new Placement.Vec3(((Number) list.get(0)).floatValue(), ((Number) list.get(1)).floatValue(),
                    ((Number) list.get(2)).floatValue());
        }
        problems.add(prefix + "'" + key + "' should be a number or a list of three, e.g. [0, 0.5, 0]; using "
                + "[" + fallback.x() + ", " + fallback.y() + ", " + fallback.z() + "]");
        return fallback;
    }

    private static boolean flag(Map<?, ?> section, String key, boolean fallback, String prefix,
                                List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        problems.add(prefix + "'" + key + "' should be true or false; using " + fallback);
        return fallback;
    }

    private static Map<?, ?> section(Map<?, ?> parent, String key, String prefix, List<String> problems) {
        Object value = parent.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        problems.add(prefix + "'" + key + "' should be a section");
        return null;
    }

    // ---------------------------------------------------------------- helpers

    /** The content pack a file belongs to: its top folder under contents/, or contents/ itself. */
    static Path sourceRoot(Path contentsDir, Path file) {
        Path relative = contentsDir.relativize(file);
        return relative.getNameCount() > 1 ? contentsDir.resolve(relative.getName(0)) : contentsDir;
    }

    private static Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        // A repeated key is almost always a copy-paste slip that would otherwise silently keep the
        // second value.
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options));
    }

    private static boolean isYaml(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static String unix(Path relative) {
        return relative.toString().replace(relative.getFileSystem().getSeparator(), "/");
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "no detail given";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
