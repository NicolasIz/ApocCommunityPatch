package com.arkcronist.content.bukkit;

import com.arkcronist.content.bukkit.block.BlockRegistry;
import com.arkcronist.content.bukkit.block.CustomBlock;
import com.arkcronist.content.bukkit.emoji.EmojiRegistry;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.bukkit.liquid.LiquidRegistry;
import com.arkcronist.content.bukkit.pack.PackDelivery;
import com.arkcronist.content.core.advancement.AdvancementCompiler;
import com.arkcronist.content.core.allocation.StableAllocator;
import com.arkcronist.content.core.block.BlockShape;
import com.arkcronist.content.core.block.CopperBaseAllocator;
import com.arkcronist.content.core.block.NoteBlockAllocator;
import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.definition.EmojiDefinition;
import com.arkcronist.content.core.definition.FontImageDefinition;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.furniture.DisplayFile;
import com.arkcronist.content.core.furniture.DisplayTransform;
import com.arkcronist.content.core.furniture.Hitboxes;
import com.arkcronist.content.core.geyser.BedrockConverter;
import com.arkcronist.content.core.geyser.FurnitureLayout;
import com.arkcronist.content.core.geyser.JavaModel;
import com.arkcronist.content.core.hud.GlyphMetrics;
import com.arkcronist.content.core.hud.HudDefinition;
import com.arkcronist.content.core.hud.HudGlyph;
import com.arkcronist.content.core.hud.HudLayout;
import com.arkcronist.content.core.hud.HudRenderer;
import com.arkcronist.content.core.importer.ImportReport;
import com.arkcronist.content.core.importer.ItemsAdderImporter;
import com.arkcronist.content.core.liquid.TripwireState;
import com.arkcronist.content.core.loader.ContentLoader;
import com.arkcronist.content.core.loader.ExampleUpdates;
import com.arkcronist.content.core.loader.LoadReport;
import com.arkcronist.content.core.recipe.RecipeDefinition;
import com.arkcronist.content.core.recipe.RecipeReader;
import com.arkcronist.content.core.menu.GuiPicture;
import com.arkcronist.content.core.minigame.MinigameRenderer;
import com.arkcronist.content.core.pack.ExternalPack;
import com.arkcronist.content.core.pack.FontImages;
import com.arkcronist.content.core.pack.ModelDataDispatch;
import com.arkcronist.content.core.pack.PackArtifact;
import com.arkcronist.content.core.pack.PackCompiler;
import com.arkcronist.content.core.pack.PackSource;
import com.arkcronist.content.core.pack.PackZipper;
import com.arkcronist.content.core.pack.WornAssets;
import com.arkcronist.content.core.quest.QuestDefinition;
import com.arkcronist.content.core.upload.PackUploader;
import com.arkcronist.content.core.upload.UploadSettings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Rebuilds everything from the contents folder: items, pack folder, zip, hash - then puts the
 * result live.
 *
 * <p>Thread discipline is the point of this class. Every step that touches the disk runs on one
 * dedicated worker thread; only the last step, which swaps the item registry and sends the pack to
 * online players, is handed back to the server thread, and it does no I/O at all. A rebuild of any
 * size therefore costs the server's tick nothing but that final swap.</p>
 *
 * <pre>
 *   worker  : load contents/*.yml -> assign note block states and emoji characters
 *             -> compile pack/ -> zip resource_pack.zip -> SHA-1
 *             -> find its link: the built-in server, http.external-url, or an upload (upload:)
 *   main    : swap item, block and emoji registries and live pack, resend to online players
 * </pre>
 *
 * <p>An upload's network waits do not hold the worker either: the request goes out through the
 * HTTP client's own threads, and the stages after it are queued back onto the worker.</p>
 *
 * <p>The ItemsAdder import writes into contents/, so it runs on the same worker and in the same
 * queue: never while a rebuild is reading the files it writes.</p>
 *
 * <p>Rebuilds never overlap. Each one starts only after the previous has fully finished, including
 * its main-thread step, so two quick reloads cannot interleave their writes into the same folder.
 * A rebuild that fails leaves the previous items and pack live and untouched.</p>
 */
public final class ContentPipeline {

    /**
     * The demo pack's first files, and those a later version only used from a file that was already
     * there (the 1.2 crate, in blocks.yml): copied on a first start only.
     */
    private static final List<String> FIRST_EXAMPLES = List.of(
            "contents/demo/items.yml",
            "contents/demo/models/item/ruby.json",
            "contents/demo/textures/item/ruby.png",
            "contents/demo/textures/item/ruby_sword.png",
            "contents/demo/blocks.yml",
            "contents/demo/textures/block/ruby_block.png",
            "contents/demo/models/furniture/ruby_pedestal.json",
            "contents/demo/textures/furniture/pedestal_stone.png",
            "contents/demo/models/furniture/ruby_crate.json",
            "contents/demo/textures/furniture/ruby_crate.png");

    /**
     * The medieval props of 1.9.5, each a Java model under contents/medieval_props/models/furniture/ and,
     * since 1.9.6, a texture of its own under textures/furniture/.
     */
    private static final List<String> MEDIEVAL_PROPS = List.of(
            "abrevadero", "alambique", "armero", "banco_iglesia", "banco_largo", "banqueta_cuero", "barril_almacen",
            "barril_cerveza", "baul_viaje", "botella_licor", "brasero", "buzon", "caja_madera", "caldero_bruja",
            "caldero_estofado", "caldero_pociones", "campana_plaza", "candelabro_mesa", "carro_heno",
            "cartel_alquimista", "cartel_banco", "cartel_establo", "cartel_herreria", "cartel_mercado",
            "cartel_posada", "cartel_taberna", "cepo_plaza", "copa_vino", "estandarte_azul", "estandarte_rojo",
            "estanteria_botellas", "fardo_heno_asiento", "farol_hierro", "fogata_asador", "forja", "horno_pan",
            "jarra_cerveza", "jarra_hidromiel", "lampara_aceite", "maniqui_armadura", "mapa_mural", "mesa_banquete",
            "mesa_redonda", "mesa_taberna", "piedra_afilar", "pila_lena", "pozo_deseos", "puesto_mercado",
            "saco_grano", "silla_herrero", "silla_taberna_oscura", "silla_taberna_roble", "sillon_cuero",
            "taburete_alto", "taburete_taberna", "tocon_asiento", "tonel_vino", "trono_real", "yunque_herrero");

    /**
     * The examples each later version added. A server that already has a contents/ folder gets
     * these on upgrade (see {@link ExampleUpdates}); a new one gets them with the rest.
     */
    private static final Map<String, List<String>> ADDED_EXAMPLES = Map.ofEntries(
            Map.entry("1.1.0", List.of(
                    "contents/demo/crops.yml",
                    "contents/demo/textures/item/ruby_seeds.png",
                    "contents/demo/textures/crop/ruby_stage_0.png",
                    "contents/demo/textures/crop/ruby_stage_1.png",
                    "contents/demo/textures/crop/ruby_stage_2.png",
                    "contents/demo/textures/crop/ruby_stage_3.png",
                    "contents/demo/emojis.yml",
                    "contents/demo/textures/emoji/ruby.png",
                    "contents/demo/textures/emoji/heart.png")),
            Map.entry("1.3.0", List.of(
                    "contents/demo/furniture.yml",
                    "contents/demo/models/furniture/ruby_chest.bbmodel",
                    "contents/demo/models/furniture/ruby_bed.json",
                    "contents/demo/textures/furniture/ruby_bed.png")),
            Map.entry("1.4.0", List.of(
                    "contents/demo/armor.yml",
                    "contents/demo/textures/entity/equipment/humanoid/ruby_armor.png",
                    "contents/demo/textures/entity/equipment/humanoid_leggings/ruby_armor.png",
                    "contents/demo/textures/item/ruby_helmet.png",
                    "contents/demo/textures/item/ruby_chestplate.png",
                    "contents/demo/textures/item/ruby_leggings.png",
                    "contents/demo/textures/item/ruby_boots.png",
                    "contents/demo/models/item/ruby_helmet_worn.json",
                    "contents/demo/textures/item/ruby_helmet_worn.png")),
            Map.entry("1.5.0", List.of(
                    "contents/demo/advancements.yml",
                    "contents/demo/textures/gui/advancements/ruby.png")),
            Map.entry("1.7.0", List.of(
                    "contents/demo/guns.yml",
                    "contents/demo/textures/item/ruby_pistol.png",
                    "contents/demo/textures/item/ruby_shotgun.png",
                    "contents/demo/textures/item/ruby_bullet.png",
                    "contents/demo/liquids.yml",
                    "contents/demo/textures/block/acid.png",
                    "contents/demo/textures/block/acid.png.mcmeta",
                    "contents/demo/textures/block/frost.png",
                    "contents/demo/textures/block/frost.png.mcmeta",
                    "contents/demo/textures/item/acid_bucket.png",
                    "contents/demo/textures/item/frost_bucket.png",
                    "contents/demo/huds.yml",
                    "contents/demo/textures/hud/thirst_full.png",
                    "contents/demo/textures/hud/thirst_half.png",
                    "contents/demo/textures/hud/thirst_empty.png",
                    "contents/demo/textures/hud/mana_full.png",
                    "contents/demo/textures/hud/mana_half.png",
                    "contents/demo/textures/hud/mana_empty.png")),
            Map.entry("1.9.0", List.of(
                    "contents/demo/models/furniture/ruby_chest_double.bbmodel",
                    "contents/demo/quest_board.yml",
                    "contents/demo/models/furniture/medieval_bulletin_board.json",
                    "contents/demo/textures/furniture/medieval_bulletin_board.png")),
            Map.entry("1.9.5", Stream.of(
                            Stream.of("contents/medieval_props/furniture.yml",
                                    "contents/medieval_props/models/furniture/guillotina_plaza.bbmodel",
                                    "contents/medieval_props/models/furniture/guillotina_plaza_icon.json",
                                    "contents/medieval_props/textures/furniture/guillotina_plaza.png"),
                            MEDIEVAL_PROPS.stream().map(id -> "contents/medieval_props/models/furniture/" + id + ".json"),
                            MEDIEVAL_PROPS.stream().map(id -> "contents/medieval_props/textures/furniture/" + id + ".png"))
                    .flatMap(Function.identity()).toList()),
            // 1.9.6 gave each prop a texture of its own: to a pack 1.9.5 already set up, these are new.
            Map.entry("1.9.6", Stream.concat(Stream.of("contents/medieval_props/textures/furniture/guillotina_plaza.png"),
                    MEDIEVAL_PROPS.stream().map(id -> "contents/medieval_props/textures/furniture/" + id + ".png")).toList()),
            // 2.0 anchors the demo HUDs (huds.yml is brought up to date by its hash) and adds a shield bar.
            Map.entry("2.0.0", List.of(
                    "contents/demo/textures/hud/shield_full.png",
                    "contents/demo/textures/hud/shield_half.png",
                    "contents/demo/textures/hud/shield_empty.png")),
            // 3.0: a rare ore and a fishing rod with the HUD minigame, and a backpack worn on the back.
            Map.entry("3.0.0", List.of(
                    "contents/demo/minigames.yml",
                    "contents/demo/textures/block/star_ore.png",
                    "contents/demo/textures/item/master_rod.png",
                    "contents/medieval_props/cosmetics.yml",
                    "contents/medieval_props/models/cosmetic/mochila_viaje.json",
                    "contents/medieval_props/textures/cosmetic/mochila_viaje.png")),
            // 3.1.1: small icons for the bars drawn as BetterHud's status bars.
            Map.entry("3.1.1", List.of(
                    "contents/demo/textures/hud/thirst_small.png",
                    "contents/demo/textures/hud/mana_small.png",
                    "contents/demo/textures/hud/shield_small.png")),
            // 3.2: the map board's picture, a texture of its own that /arkcontent map draws again.
            Map.entry("3.2.0", List.of(
                    "contents/medieval_props/textures/furniture/mapa_mural_mapa.png")),
            // 3.4: a canteen to drink from - thirst - and fill at water and at the trough.
            Map.entry("3.4.0", List.of(
                    "contents/medieval_props/items.yml",
                    "contents/medieval_props/models/item/cantimplora.json",
                    "contents/medieval_props/textures/item/cantimplora.png")),
            // 3.5: four packs of medieval props - the mine, the smithy, the harbour, the farm - listed in a
            // file of their own, being six hundred.
            Map.entry("3.5.0", exampleList("examples-3.5.0.txt")),
            Map.entry("4.0.0", List.of(
                    "contents/demo/recipes.yml",
                    "contents/demo/vehicles.yml",
                    "contents/demo/models/vehicle/ruby_cart.json",
                    "contents/demo/models/vehicle/ruby_cart_wheel.json",
                    "contents/demo/textures/vehicle/ruby_cart.png",
                    "contents/demo/textures/vehicle/ruby_cart_wheel.png")),
            // 5.0: an ore laid into new chunks.
            Map.entry("5.0.0", List.of(
                    "contents/demo/textures/block/ruby_ore.png",
                    // 5.0: emotes - a squire rig with four animations, and the scroll that plays them.
                    "contents/demo/emotes.yml",
                    "contents/demo/models/emotes/squire.bbmodel",
                    "contents/demo/textures/item/emote_scroll.png")),
            // 5.3: blocks with a shape - a slab, stairs, lattice glass, a door and a trapdoor - and a
            // counter that joins its neighbours, and a goblet clicked where it is.
            Map.entry("5.3.0", List.of(
                    "contents/demo/shapes.yml",
                    "contents/demo/counter.yml",
                    "contents/demo/models/furniture/ruby_counter.json",
                    "contents/demo/models/furniture/ruby_counter_left.json",
                    "contents/demo/models/furniture/ruby_counter_middle.json",
                    "contents/demo/models/furniture/ruby_counter_right.json",
                    "contents/demo/models/furniture/ruby_goblet.json",
                    "contents/demo/textures/furniture/ruby_counter.png",
                    "contents/demo/textures/block/ruby_glass.png",
                    "contents/demo/textures/block/ruby_door_top.png",
                    "contents/demo/textures/block/ruby_door_bottom.png",
                    "contents/demo/textures/block/ruby_trapdoor.png",
                    "contents/demo/textures/item/ruby_door.png")),
            // 5.4: a sapling that grows into a tree of its own wood, and its trees in new chunks.
            Map.entry("5.4.0", List.of(
                    "contents/demo/trees.yml",
                    "contents/demo/textures/crop/ruby_sapling.png",
                    "contents/demo/textures/block/ruby_log.png",
                    "contents/demo/textures/block/ruby_log_top.png",
                    "contents/demo/textures/block/ruby_planks.png",
                    // 5.4: items with events and mechanics of their own.
                    "contents/demo/mechanics.yml",
                    "contents/demo/textures/item/ruby_hammer.png",
                    "contents/demo/textures/item/storm_staff.png",
                    "contents/demo/textures/item/blood_dagger.png",
                    "contents/demo/textures/item/ruby_charm.png",
                    // 5.4: a lantern with states, a seat that keeps things, a chest shaking at rest.
                    "contents/demo/furniture_states.yml",
                    "contents/demo/models/furniture/ruby_lantern.json",
                    "contents/demo/models/furniture/ruby_lantern_on.json",
                    "contents/demo/textures/furniture/ruby_lantern.png")));

    /** The example files a resource lists, one path a line; # starts a comment. */
    private static List<String> exampleList(String resource) {
        try (java.io.InputStream in = ContentPipeline.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is missing from the plugin jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
        } catch (IOException broken) {
            throw new UncheckedIOException(broken);
        }
    }

    /** Copied into contents/ the first time the plugin starts. */
    /** Every example once: later sets list some files again (1.9.6 repainted 1.9.5's textures). */
    private static final List<String> EXAMPLES = Stream.concat(FIRST_EXAMPLES.stream(),
            ADDED_EXAMPLES.values().stream().flatMap(List::stream)).distinct().toList();

    /**
     * What one rebuild did.
     *
     * @param changed  whether the pack's content moved, and players were sent it again
     * @param problems everything that was skipped or looked wrong, already logged
     */
    public record Report(int items, int blocks, int emojis, int advancements, int recipes, int files, String sha1Hex,
                         int bytes, boolean changed,
                         @Nullable String url, String hosting, List<String> problems, long millis) {
    }

    /**
     * Where one build of the pack can be downloaded.
     *
     * @param url          what players are sent; null for nowhere
     * @param note         how it got there, for the log
     * @param keepPrevious the upload failed and nothing else serves this build: the live pack stays
     *                     as it was, since its link still works
     */
    /**
     * @param builtin served by the built-in web server: the link is made when the pack goes live,
     *                from the address as it is then - a port or public IP found meanwhile included
     */
    private record Hosted(@Nullable String url, String note, boolean keepPrevious, boolean builtin) {

        Hosted(@Nullable String url, String note, boolean keepPrevious) {
            this(url, note, keepPrevious, false);
        }
    }

    /**
     * Emoji characters: Unicode's private use area, which no font draws until a pack says so - its
     * first 4096 characters; the rest is for HUD icons and the space characters.
     */
    private static final StableAllocator EMOJI_CHARACTERS = new StableAllocator(0xE000, 0xEFFF, "emoji character",
            "to emojis no longer defined; remove those from emoji_characters.json once no sign or book uses them");

    /** HUD icons: the private use area above the emojis', up to where the space characters begin. */
    private static final StableAllocator HUD_CHARACTERS = new StableAllocator(0xF000, 0xF7FF, "HUD character",
            "to HUDs no longer defined; remove those from hud_characters.json");

    /** Liquids: one pair of tripwire states each, of the sixteen free pairs. */
    /** The minigame's glyphs' keys in hud_characters.json. */
    private static final String MINIGAME_KEY = "arkcontent:minigame/";
    /**
     * The minigame's textures are 24 pixels tall, the bar in their top 13; an ascent of 21 puts
     * the bar's top 86 pixels above the bottom of the screen - just over the HUD bars, with the
     * action bar's own text under it ({@link com.arkcronist.content.core.hud.HudAnchor}).
     */
    private static final int MINIGAME_HEIGHT = 24;
    private static final int MINIGAME_ASCENT = 21;

    private static final StableAllocator LIQUID_SLOTS = new StableAllocator(0, TripwireState.LIQUIDS - 1,
            "pair of tripwire states", "to liquids no longer defined; remove those from liquid_states.json once"
            + " none is left poured in a world");

    /**
     * One rebuild's progress, handed from stage to stage.
     *
     * @param noteBlocks     the state each custom block got, by id
     * @param emojis         every emoji defined, and the character it got once assigned
     * @param liquids        the pair of tripwire states each liquid got, by id
     * @param huds           every HUD, with the characters its icons got
     */
    private record Build(Map<String, CustomItem> items, Map<String, NoteBlockState> noteBlocks,
                         List<EmojiDefinition> emojiDefinitions, Map<EmojiDefinition, Integer> emojis,
                         List<AdvancementCompiler.Compiled> advancements, Map<String, Integer> modelData,
                         List<HudDefinition> hudDefinitions, Map<String, Integer> liquids, List<HudLayout> huds,
                         List<HudGlyph> hudGlyphs, List<FontImageDefinition> fontImageDefinitions,
                         FontImages fontImages, List<String> problems, int files, PackArtifact artifact,
                         Hosted hosted, Extras extras) {

        Build withItems(Map<String, CustomItem> items) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted, extras);
        }

        Build withNoteBlocks(Map<String, NoteBlockState> noteBlocks) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted, extras);
        }

        Build withEmojis(Map<EmojiDefinition, Integer> emojis) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted, extras);
        }

        Build withFiles(int files) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted, extras);
        }

        Build withArtifact(PackArtifact artifact) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted, extras);
        }

        Build withModelData(Map<String, Integer> modelData) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted, extras);
        }

        Build withLiquids(Map<String, Integer> liquids) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted, extras);
        }

        Build withHuds(List<HudLayout> huds, List<HudGlyph> hudGlyphs) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted, extras);
        }

        Build withFontImages(FontImages fontImages) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted, extras);
        }

        Build withExtras(Extras extras) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted, extras);
        }

        Build withHosted(Hosted hosted) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted, extras);
        }
    }

    /**
     * What 3.0 adds to a rebuild, kept together so the stages before it pass it on unchanged.
     *
     * @param minigame    the minigame's glyphs, once given characters; null with no minigame defined
     * @param bedrock     the content made for Bedrock players; null without Geyser
     * @param bedrockPack where its resource pack was written
     * @param brokenShaders 3.0.8: the shaders the pack keeps that the client cannot use, named when a
     *                      player's client fails to apply it
     */
    record Extras(@Nullable MinigameRenderer.Glyphs minigame, @Nullable BedrockConverter.Build bedrock,
                  @Nullable Path bedrockPack, List<String> brokenShaders, List<RecipeDefinition> recipes,
                  Map<String, List<Placement.Hitbox.Box>> hitboxes) {

        static final Extras NONE = new Extras(null, null, null, List.of(), List.of(), Map.of());

        /** 5.3: the boxes each piece of furniture is clicked by, by id; none for one left out. */
        Extras withHitboxes(Map<String, List<Placement.Hitbox.Box>> hitboxes) {
            return new Extras(minigame, bedrock, bedrockPack, brokenShaders, recipes, Map.copyOf(hitboxes));
        }

        Extras withMinigame(@Nullable MinigameRenderer.Glyphs minigame) {
            return new Extras(minigame, bedrock, bedrockPack, brokenShaders, recipes, hitboxes);
        }

        Extras withBedrock(BedrockConverter.Build bedrock, Path bedrockPack) {
            return new Extras(minigame, bedrock, bedrockPack, brokenShaders, recipes, hitboxes);
        }

        Extras withBrokenShaders(List<String> brokenShaders) {
            return new Extras(minigame, bedrock, bedrockPack, List.copyOf(brokenShaders), recipes, hitboxes);
        }

        /** 4.0: the recipes to register, each naming only items this build has. */
        Extras withRecipes(List<RecipeDefinition> recipes) {
            return new Extras(minigame, bedrock, bedrockPack, brokenShaders, List.copyOf(recipes), hitboxes);
        }
    }

    private final ArkContentPlugin plugin;
    private final EngineSettings settings;
    private final ItemRegistry registry;
    private final BlockRegistry blocks;
    private final EmojiRegistry emojis;
    private final PackDelivery delivery;
    private final Logger logger;
    private final PackCompiler compiler;

    private final Path contentsDir;
    private final Path importDir;
    /** Resource packs merged whole into ours, every file as it is: what the importer absorbed, or anything dropped in. */
    private final Path packsDir;
    private final Path packDir;
    private final Path zipFile;
    private final Path noteBlockStateFile;
    /** 5.3: the waxed copper block each shaped custom block is drawn through. */
    private final Path copperBaseFile;
    /** 3.0: the note block states Bedrock players are shown furniture through. */
    private final Path carrierFile;
    /** 5.4: whether a build has been published since the server started. */
    private boolean publishedOnce;
    private final Path bedrockDir;
    /** The first build's Bedrock content, for Geyser, which asks for it as it starts. */
    private final CompletableFuture<BedrockContent> bedrockReady = new CompletableFuture<>();
    private volatile @Nullable BedrockContent bedrockLatest;
    private final boolean bedrockActive;
    /** 3.3: whether Geyser is on this server (Geyser-Spigot), not on a proxy in front of it. */
    private final boolean geyserHere;
    private final Path emojiCharacterFile;
    private final Path modelDataFile;
    private final Path liquidStateFile;
    private final Path hudCharacterFile;
    /** The example sets offered to contents/ so far, one per line. */
    private final Path examplesOfferedFile;
    /** What 1.4.0 wrote instead: the one set it knew, 1.4.0. */
    private final Path legacyExamplesFile;
    /** Content files as they were before the furniture editor last wrote them. */
    private final Path editorBackupsDir;
    /** ItemsAdder's folder, whose configs name font images when they were not imported. */
    private final Path itemsAdderFolder;

    private final ExecutorService worker;
    private final Executor mainThread;
    /** Null unless upload: is on. */
    private final @Nullable PackUploader uploader;

    /** The quest notice templates of the last load, by id: what {@code /arkcontent board post} can copy in. */
    private volatile Map<String, QuestDefinition> questTemplates = Map.of();

    /** Other plugins' packs to merge into ours, by name. Set from the main thread, read by the worker. */
    private final Map<String, ExternalPack> externalPacks = new ConcurrentHashMap<>();

    /** The rebuild in progress, or the last one. Main thread only. */
    private CompletableFuture<?> last = CompletableFuture.completedFuture(null);

    public ContentPipeline(ArkContentPlugin plugin, EngineSettings settings, ItemRegistry registry,
                           BlockRegistry blocks, EmojiRegistry emojis, PackDelivery delivery) {
        this.plugin = plugin;
        this.settings = settings;
        this.registry = registry;
        this.blocks = blocks;
        this.emojis = emojis;
        this.delivery = delivery;
        this.logger = plugin.getLogger();
        // 3.5.3: ItemsAdder's pack silences wood and stone and ItemsAdder plays them; without it, they play again.
        boolean itemsAdder = plugin.getServer().getPluginManager().getPlugin("ItemsAdder") != null;
        this.compiler = new PackCompiler(settings.pack().toPackSettings(), settings.pack().fixSoundNames(),
                settings.pack().shaders(), settings.pack().unsilenceVanillaSounds() && !itemsAdder);
        // Packs named in config.yml: CosmeticsCore's, a model pack bought separately, any other.
        Path server = plugin.getDataFolder().toPath().toAbsolutePath().getParent().getParent();
        for (String merge : settings.pack().merge()) {
            Path root = server.resolve(merge).normalize();
            ExternalPack pack = new ExternalPack(root.getFileName().toString(), root);
            if (Files.exists(root)) {
                externalPacks.put("config:" + merge, pack);
                logger.info("Merging the resource pack at " + merge + " into ours.");
            } else {
                logger.warning("pack.merge: " + merge + " does not exist (looked at " + root + ") - not merged.");
            }
        }

        Path data = plugin.getDataFolder().toPath();
        this.contentsDir = data.resolve("contents");
        this.importDir = data.resolve("import");
        this.packsDir = data.resolve("packs");
        this.packDir = data.resolve("pack");
        this.zipFile = data.resolve("output").resolve("resource_pack.zip");
        this.noteBlockStateFile = data.resolve("data").resolve("note_block_states.json");
        this.copperBaseFile = data.resolve("data").resolve("copper_block_bases.json");
        this.carrierFile = data.resolve("data").resolve("bedrock_carriers.json");
        this.bedrockDir = data.resolve("output").resolve("bedrock");
        this.bedrockActive = bedrockActive(plugin);
        this.geyserHere = bedrockActive && geyserHere(plugin);
        this.emojiCharacterFile = data.resolve("data").resolve("emoji_characters.json");
        this.modelDataFile = data.resolve("data").resolve("custom_model_data.json");
        this.liquidStateFile = data.resolve("data").resolve("liquid_states.json");
        this.hudCharacterFile = data.resolve("data").resolve("hud_characters.json");
        this.examplesOfferedFile = data.resolve("data").resolve("examples_offered.txt");
        this.legacyExamplesFile = data.resolve("data").resolve("examples_version.txt");
        this.editorBackupsDir = data.resolve("data").resolve("editor-backups");
        this.itemsAdderFolder = data.toAbsolutePath().getParent().resolve("ItemsAdder");

        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ArkContent-Worker");
            thread.setDaemon(true);
            return thread;
        });
        // Once the plugin is disabled the scheduler refuses new tasks; a rebuild finishing during
        // shutdown simply never goes live.
        this.mainThread = task -> {
            if (plugin.isEnabled()) {
                plugin.getServer().getScheduler().runTask(plugin, task);
            }
        };
        UploadSettings upload = settings.upload();
        this.uploader = upload == null ? null : new PackUploader(upload, PackUploader.defaultClient(upload),
                data.resolve("data").resolve("upload.json"), worker,
                plugin.getName() + "/" + plugin.getPluginMeta().getVersion());
    }

    /** The thread that does all of this plugin's disk work. */
    public Executor worker() {
        return worker;
    }

    /** Runs a task on the server thread. */
    public Executor mainThread() {
        return mainThread;
    }

    /**
     * Starts a rebuild once any running one has finished. Call from the main thread.
     *
     * @return completes on the main thread once the new items and pack are live
     */
    public CompletableFuture<Report> rebuild() {
        CompletableFuture<Report> next = last
                .handle((ignored, error) -> null)
                .thenComposeAsync(ignored -> stages(), worker);
        next.whenComplete((report, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Rebuild failed - the previous items and pack stay live.", error);
                // Geyser, if it is waiting on the first build, stops waiting.
                bedrockReady.completeExceptionally(error);
            }
        });
        last = next;
        return next;
    }

    /**
     * Converts the ItemsAdder packs in import/ into content files under contents/ - see
     * {@link ItemsAdderImporter}. The reading, converting and copying all happen on the worker, in
     * line with rebuilds: never while one is reading contents/. It does not rebuild; the caller
     * decides whether to. Call from the main thread.
     *
     * @return completes on the worker, with the report already logged
     */
    public CompletableFuture<ImportReport> importFromItemsAdder() {
        return exclusive(() -> {
            ImportReport report = new ItemsAdderImporter().run(importDir, contentsDir, packsDir);
            logImport(report);
            return report;
        });
    }

    /**
     * Writes the furniture editor's values into the content file {@code item} came from - see
     * {@link DisplayFile} - on the worker, in line with rebuilds: never while one is reading
     * contents/. It does not rebuild; the caller does, once this has completed. Main thread.
     *
     * @return completes on the worker with what was written
     */
    public CompletableFuture<DisplayFile.Saved> saveDisplay(ItemDefinition item, DisplayTransform transform) {
        return exclusive(() -> DisplayFile.save(contentsDir, editorBackupsDir, item.source(), item.id(), transform,
                Instant.now()));
    }

    /** The folder content files live in. */
    /** The quest notice templates in contents/, by id, as of the last load. */
    public Map<String, QuestDefinition> questTemplates() {
        return questTemplates;
    }

    public Path contentsDir() {
        return contentsDir;
    }

    /**
     * Runs {@code task} on the worker once any rebuild in progress has finished; the next rebuild
     * waits for it in turn. Main thread.
     */
    private <T> CompletableFuture<T> exclusive(Callable<T> task) {
        CompletableFuture<T> next = last
                .handle((ignored, error) -> null)
                .thenApplyAsync(ignored -> {
                    try {
                        return task.call();
                    } catch (Exception exception) {
                        throw new CompletionException(exception);
                    }
                }, worker);
        last = next;
        return next;
    }

    private void logImport(ImportReport report) {
        if (report.empty()) {
            logger.info("Nothing to import in " + importDir + ". Copy ItemsAdder's contents folder, one"
                    + " pack from it, or its generated resource pack zip in there and run /arkcontent import again.");
            return;
        }
        for (String note : report.notes()) {
            logger.info("[import] " + note);
        }
        for (String problem : report.problems()) {
            logger.warning("[import] " + problem);
        }
        for (String file : report.written()) {
            logger.info("[import] wrote contents/" + file);
        }
        logger.info("ItemsAdder import: " + report.items() + " item(s) from " + report.converted() + " file(s), "
                + report.resources() + " asset file(s) copied byte for byte, "
                + (report.packs() == 0 ? "" : report.packs() + " generated pack(s) of " + report.packFiles()
                + " file(s) taken whole into " + packsDir + ", ")
                + report.skipped() + " item(s) skipped"
                + (report.problems().isEmpty() ? "" : ", " + report.problems().size() + " problem(s) above") + ".");
    }

    private CompletableFuture<Report> stages() {
        long started = System.nanoTime();
        return CompletableFuture.supplyAsync(this::loadItems, worker)
                .thenApplyAsync(this::assignCopperBases, worker)
                .thenApplyAsync(this::assignNoteBlockStates, worker)
                .thenApplyAsync(this::assignEmojiCharacters, worker)
                .thenApplyAsync(this::assignModelData, worker)
                .thenApplyAsync(this::assignLiquidStates, worker)
                .thenApplyAsync(this::assignHudCharacters, worker)
                .thenApplyAsync(this::compilePack, worker)
                .thenApplyAsync(this::fitHitboxes, worker)
                .thenApplyAsync(this::linkWornAssets, worker)
                .thenApplyAsync(this::bedrock, worker)
                .thenComposeAsync(this::zipPack, worker)
                .thenApplyAsync(this::hashPack, worker)
                .thenComposeAsync(this::host, worker)
                .thenApplyAsync(build -> publish(build, started), mainThread);
    }

    /**
     * The items, read straight from contents/ on the main thread and made live at once, ahead of the
     * first rebuild: other plugins that read their own configuration as the server starts - shop
     * plugins, AuraSkills' menus - find this plugin's items there from the first tick, not seconds
     * later when the pack is built. Only the YAML is read here, which takes milliseconds; blocks,
     * emojis and the pack wait for the rebuild, which also reports any problems this skips over.
     *
     * @return how many items were loaded; 0 if the contents could not be read, which the rebuild
     *         will then report
     */
    public int preload() {
        try {
            Build build = loadItems();
            // 5.3: shaped blocks of the copper blocks they had; a new one waits for the rebuild.
            Map<String, BlockShape> shapes = shapedBlocks(build.items());
            Map<String, String> kept = shapes.isEmpty() ? Map.of() : CopperBaseAllocator.allocate(
                    CopperBaseAllocator.read(copperBaseFile), shapes, allCopperBases(), true).active();
            registry.replace(withCopperBases(build.items(), kept).values());
            return build.items().size();
        } catch (RuntimeException | IOException exception) {
            logger.log(Level.FINE, "Could not preload the items; the rebuild will report why.", exception);
            return 0;
        }
    }

    // ---------------------------------------------------------------- worker thread

    /** contents/*.yml into items, each checked against this server's materials. */
    private Build loadItems() {
        try {
            extractExamples();
            LoadReport report = new ContentLoader().load(contentsDir);
            Map<String, QuestDefinition> templates = new LinkedHashMap<>();
            report.quests().forEach(quest -> templates.put(quest.id(), quest));
            questTemplates = Map.copyOf(templates);

            List<String> problems = new ArrayList<>(report.problems());
            Map<String, CustomItem> items = new LinkedHashMap<>();
            for (ItemDefinition definition : report.items()) {
                CustomItem item = CustomItem.resolve(definition, problems);
                if (item != null) {
                    items.put(item.id(), item);
                }
            }
            checkCrops(items, problems);
            checkVehicles(items, problems);
            Map<String, ItemDefinition> definitions = new LinkedHashMap<>();
            items.values().forEach(item -> definitions.put(item.id(), item.definition()));
            AdvancementCompiler.Result advancements = AdvancementCompiler.compile(report.advancements(), definitions,
                    ContentPipeline::textComponent);
            problems.addAll(advancements.problems());
            List<RecipeDefinition> recipes = RecipeReader.withKnownItems(report.recipes(), items.keySet(), problems);
            return new Build(items, Map.of(), report.emojis(), Map.of(), advancements.advancements(), Map.of(),
                    report.huds(), Map.of(), List.of(), List.of(), report.fontImages(), FontImages.EMPTY, problems, 0,
                    null, null, Extras.NONE.withRecipes(recipes));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** MiniMessage to the JSON text component an advancement's title and description are written in. */
    private static JsonElement textComponent(String miniMessage) {
        return GsonComponentSerializer.gson().serializeToTree(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    /** 4.0: a vehicle's parts name items; one that is not loaded is reported, and not drawn. */
    private static void checkVehicles(Map<String, CustomItem> items, List<String> problems) {
        for (CustomItem item : items.values()) {
            com.arkcronist.content.core.vehicle.VehicleDefinition vehicle = item.definition().vehicle();
            if (vehicle == null) {
                continue;
            }
            for (com.arkcronist.content.core.vehicle.VehicleDefinition.Part part : vehicle.parts()) {
                if (part.item() != null && !items.containsKey(part.item())) {
                    problems.add(item.definition().source().getFileName() + " > " + item.id() + ": vehicle: part "
                            + part.item() + " is not a loaded item - it is not drawn");
                }
            }
        }
    }

    /**
     * What a crop drops and what it grows on name materials and items the server has to know. Only
     * reported: a drop that does not resolve is skipped at harvest, a soil that does not is never
     * planted on.
     */
    private static void checkCrops(Map<String, CustomItem> items, List<String> problems) {
        for (CustomItem item : items.values()) {
            if (!(item.placement() instanceof Placement.Crop crop)) {
                continue;
            }
            for (Placement.Drop drop : crop.drops()) {
                boolean custom = items.containsKey(drop.item());
                Material material = custom ? null : Material.matchMaterial(drop.item());
                if (!custom && (material == null || !material.isItem() || material.isAir())) {
                    problems.add(item.id() + ": drop '" + drop.item() + "' is neither a custom item nor a material");
                }
            }
            for (String soil : crop.soils()) {
                Material material = Material.matchMaterial(soil);
                if (material == null || !material.isBlock()) {
                    problems.add(item.id() + ": soil '" + soil + "' is not a block");
                }
            }
        }
    }

    /**
     * A private-use character for every emoji: the one it had before, or the lowest free one. Kept
     * in data/emoji_characters.json, because the character is what a sign or a book saves - hand it
     * to another emoji and every sign showing a ruby shows that instead.
     */
    private Build assignEmojiCharacters(Build build) {
        try {
            List<String> names = build.emojiDefinitions().stream().map(EmojiDefinition::name).toList();
            Map<String, Integer> previous = StableAllocator.read(emojiCharacterFile);
            if (names.isEmpty() && previous.isEmpty()) {
                return build;
            }
            StableAllocator.Allocation allocation = EMOJI_CHARACTERS.allocate(previous, names, fontCharactersInUse(), true);
            if (allocation.changed()) {
                StableAllocator.write(emojiCharacterFile, allocation.assignments());
            }
            build.problems().addAll(allocation.problems());
            Map<EmojiDefinition, Integer> characters = new LinkedHashMap<>();
            for (EmojiDefinition emoji : build.emojiDefinitions()) {
                Integer character = allocation.active().get(emoji.name());
                if (character != null) {
                    characters.put(emoji, character);
                }
            }
            return build.withEmojis(characters);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * 5.3: a waxed copper block for every shaped custom block - slab, stairs, transparent, door,
     * trapdoor: the one it had before, or the first free one of its shape. Kept in
     * data/copper_block_bases.json, because a placed slab is only that copper slab in the world. The
     * item becomes that block, so vanilla places it with its own rules - a slab on a slab doubles, a
     * door takes two blocks and its hinge from where it is clicked; one left without keeps paper,
     * which places nothing.
     */
    private Build assignCopperBases(Build build) {
        try {
            Map<String, BlockShape> shapes = shapedBlocks(build.items());
            Map<String, String> previous = CopperBaseAllocator.read(copperBaseFile);
            if (shapes.isEmpty() && previous.isEmpty()) {
                return build;
            }
            // The copper blocks a merged pack - Nexo's, Oraxen's - draws as its own are left to it.
            Set<String> reserved = new java.util.TreeSet<>();
            for (ExternalPack pack : mergedPacks()) {
                try (PackSource source = PackSource.open(pack)) {
                    reserved.addAll(com.arkcronist.content.core.pack.BlockStates.copperBasesDrawn(source));
                }
            }
            boolean trialChambers = plugin.getConfig().getBoolean("blocks.trial-chamber-copper", false);
            CopperBaseAllocator.Allocation allocation = CopperBaseAllocator.allocate(previous, shapes, reserved,
                    trialChambers);
            if (allocation.changed()) {
                CopperBaseAllocator.write(copperBaseFile, allocation.assignments());
            }
            build.problems().addAll(allocation.problems());
            List<String> chambers = allocation.active().entrySet().stream()
                    .filter(entry -> !shapes.get(entry.getKey()).outsideStructures().contains(entry.getValue()))
                    .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                    .toList();
            if (!chambers.isEmpty()) {
                logger.info("[blocks] drawn through copper that trial chambers are built from - chambers generated"
                        + " from now on show them in their walls: " + String.join(", ", chambers) + ".");
            }
            return build.withItems(withCopperBases(build.items(), allocation.active()));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** 5.3: every shaped custom block - not a cube - with its shape. */
    private static Map<String, BlockShape> shapedBlocks(Map<String, CustomItem> items) {
        Map<String, BlockShape> shapes = new LinkedHashMap<>();
        for (CustomItem item : items.values()) {
            if (item.placement() instanceof Placement.Block block && block.shape().copper()) {
                shapes.put(item.id(), block.shape());
            }
        }
        return shapes;
    }

    /** Every copper block a shaped block can be drawn through: reserved, none is handed out anew. */
    private static Set<String> allCopperBases() {
        Set<String> bases = new java.util.HashSet<>();
        for (BlockShape shape : BlockShape.values()) {
            bases.addAll(shape.bases());
        }
        return bases;
    }

    /**
     * The items, each shaped block made of its copper block. One left without: a see-through block is
     * a note block instead - whole, but no longer see-through - and any other shape is made of paper,
     * which places nothing.
     */
    private static Map<String, CustomItem> withCopperBases(Map<String, CustomItem> items, Map<String, String> bases) {
        Map<String, CustomItem> made = new LinkedHashMap<>();
        for (CustomItem item : items.values()) {
            if (item.placement() instanceof Placement.Block block && block.shape().copper()) {
                String base = bases.get(item.id());
                Material material = base == null ? null : Material.matchMaterial(base);
                if (material == null && block.shape() == BlockShape.TRANSPARENT) {
                    item = item.withPlacement(block.withShape(BlockShape.CUBE), Material.NOTE_BLOCK);
                } else {
                    item = item.withMaterial(material == null ? Material.PAPER : material);
                }
            }
            made.put(item.id(), item);
        }
        return made;
    }

    /**
     * A note block state for every custom block: the one it had before, or the lowest free one.
     * The assignments are kept in data/note_block_states.json, because a placed custom block is only
     * its state - hand the state to another block and every placed one changes with it.
     */
    private Build assignNoteBlockStates(Build build) {
        try {
            List<String> blockIds = build.items().values().stream()
                    .filter(item -> item.placement() instanceof Placement.Block block && block.noteBlock())
                    .map(CustomItem::id)
                    .toList();
            Map<String, Integer> previous = NoteBlockAllocator.read(noteBlockStateFile);
            if (blockIds.isEmpty() && previous.isEmpty()) {
                return build;
            }
            // The states a merged pack - an ItemsAdder pack - draws its own blocks with are left to it.
            Set<Integer> reserved = new java.util.HashSet<>();
            for (ExternalPack pack : mergedPacks()) {
                try (PackSource source = PackSource.open(pack)) {
                    reserved.addAll(com.arkcronist.content.core.pack.BlockStates.customNoteBlockStates(source));
                }
            }
            // 3.0: states shown to Bedrock players as furniture are not a block's - while there are others.
            Set<Integer> carriers = new java.util.HashSet<>(StableAllocator.read(carrierFile).values());
            carriers.removeAll(reserved);
            Set<Integer> withCarriers = new java.util.HashSet<>(reserved);
            withCarriers.addAll(carriers);
            NoteBlockAllocator.Allocation allocation = NoteBlockAllocator.allocate(previous, blockIds, withCarriers);
            if (allocation.active().size() < blockIds.size() && !carriers.isEmpty()) {
                // 5.4: none left but carriers: a block of the Java world comes before a piece of furniture
                // drawn for Bedrock players, which the Bedrock pack then draws with what is left - or
                // smaller, or not at all, and says so.
                NoteBlockAllocator.Allocation taking = NoteBlockAllocator.allocate(previous, blockIds, reserved);
                long taken = taking.active().values().stream().map(NoteBlockState::index).filter(carriers::contains).count();
                logger.info(taken + " note block state(s) Bedrock players were shown furniture with go to new custom"
                        + " blocks: there were no others left. On a rebuild while the server runs, Bedrock players"
                        + " see those blocks as that furniture until it restarts.");
                allocation = taking;
            }
            if (allocation.changed()) {
                NoteBlockAllocator.write(noteBlockStateFile, allocation.assignments());
            }
            build.problems().addAll(allocation.problems());
            return build.withNoteBlocks(allocation.active());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * A {@code custom_model_data} number for every item drawn from a plain vanilla material: the one
     * it had before, or the lowest free one from pack.custom-model-data.first. Kept in
     * data/custom_model_data.json, because a number is what other plugins' configs are written with.
     */
    private Build assignModelData(Build build) {
        EngineSettings.ModelData config = settings.pack().modelData();
        // Geyser 2.8 - the one for 1.21.8 - tells custom items apart by custom_model_data alone, so
        // with Geyser installed every item gets a number, whatever its material.
        boolean bedrock = bedrockActive();
        if (!config.enabled() && !bedrock) {
            return build;
        }
        try {
            List<String> ids = build.items().values().stream()
                    .filter(item -> item.hasModel()
                            && (bedrock || ModelDataDispatch.dispatchable(item.definition().material())))
                    .map(CustomItem::id)
                    .toList();
            Map<String, Integer> previous = StableAllocator.read(modelDataFile);
            if (ids.isEmpty() && previous.isEmpty()) {
                return build;
            }
            StableAllocator numbers = new StableAllocator(config.first(), config.first() + 999_999,
                    "custom_model_data number", "delete its line from " + modelDataFile.getFileName());
            // A merged pack's own numbers - an ItemsAdder pack's, say - are never handed out again.
            Set<Integer> reserved = new java.util.HashSet<>();
            for (ExternalPack pack : mergedPacks()) {
                try (PackSource source = PackSource.open(pack)) {
                    reserved.addAll(ModelDataDispatch.numbersUsedBy(source));
                }
            }
            StableAllocator.Allocation allocation = numbers.allocate(previous, ids, reserved, true);
            if (allocation.changed()) {
                StableAllocator.write(modelDataFile, allocation.assignments());
            }
            build.problems().addAll(allocation.problems());
            return build.withModelData(allocation.active());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * A pair of tripwire states for every liquid: the pair it had before, or the lowest free one.
     * Kept in data/liquid_states.json, because a poured liquid is only its state in the world.
     */
    private Build assignLiquidStates(Build build) {
        try {
            List<String> ids = build.items().values().stream()
                    .filter(item -> item.placement() instanceof Placement.Liquid)
                    .map(CustomItem::id)
                    .toList();
            Map<String, Integer> previous = StableAllocator.read(liquidStateFile);
            if (ids.isEmpty() && previous.isEmpty()) {
                return build;
            }
            StableAllocator.Allocation allocation = LIQUID_SLOTS.allocate(previous, ids);
            if (allocation.changed()) {
                StableAllocator.write(liquidStateFile, allocation.assignments());
            }
            build.problems().addAll(allocation.problems());
            return build.withLiquids(allocation.active());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * A character for every HUD icon, kept in data/hud_characters.json like the emojis', and each
     * icon's width as the client will draw it - read from its image, since the bar steps back over
     * itself by exactly that much.
     */
    private Build assignHudCharacters(Build build) {
        try {
            // Font images first: those a merged pack draws already take its character; the rest
            // get one here, from the same stable allocation as HUD icons.
            List<FontImages.Image> provided = FontImages.provided(mergedFonts());
            List<FontImages.Image> definedImages = new ArrayList<>();
            List<FontImageDefinition> own = new ArrayList<>();
            List<FontImageDefinition> definitions = new ArrayList<>(build.fontImageDefinitions());
            definitions.addAll(builtinFontImages());
            for (FontImageDefinition definition : definitions) {
                FontImages.match(definition, provided).ifPresentOrElse(definedImages::add, () -> own.add(definition));
            }
            definedImages.addAll(itemsAdderFontImages(build, provided));
            Set<Integer> reserved = new java.util.HashSet<>(fontCharactersInUse());
            List<String> keys = new ArrayList<>();
            for (HudDefinition hud : build.hudDefinitions()) {
                hud.icons().forEach(icon -> keys.add(hud.iconKey(icon.name())));
            }
            // 3.0: the minigame's bar, zone pieces and needle - only once something plays it.
            boolean minigames = build.items().values().stream()
                    .anyMatch(item -> item.definition().minigame() != null);
            Map<String, Path> minigameTextures = minigames ? builtinMinigameTextures() : Map.of();
            minigameTextures.keySet().forEach(name -> keys.add(MINIGAME_KEY + name));
            Map<Integer, FontImageDefinition> onSymbol = new LinkedHashMap<>();
            for (FontImageDefinition definition : own) {
                Integer symbol = definition.symbol();
                if (symbol != null && !reserved.contains(symbol) && !onSymbol.containsKey(symbol)) {
                    onSymbol.put(symbol, definition);
                } else {
                    if (symbol != null) {
                        build.problems().add("font image " + definition.fullId() + ": symbol U+"
                                + Integer.toHexString(symbol).toUpperCase(Locale.ROOT) + " is drawn by something else"
                                + " already - it is given another character");
                    }
                    keys.add(fontImageKey(definition));
                }
            }
            reserved.addAll(onSymbol.keySet());
            Map<String, Integer> previous = StableAllocator.read(hudCharacterFile);
            if (keys.isEmpty() && previous.isEmpty() && onSymbol.isEmpty()) {
                return build.withFontImages(fontImageIndex(build, definedImages, provided));
            }
            StableAllocator.Allocation allocation = HUD_CHARACTERS.allocate(previous, keys, reserved, true);
            if (allocation.changed()) {
                StableAllocator.write(hudCharacterFile, allocation.assignments());
            }
            build.problems().addAll(allocation.problems());

            List<HudLayout> layouts = new ArrayList<>();
            List<HudGlyph> glyphs = new ArrayList<>();
            for (HudDefinition hud : build.hudDefinitions()) {
                Map<String, int[]> icons = new HashMap<>();
                for (HudDefinition.Icon icon : hud.icons()) {
                    Integer character = allocation.active().get(hud.iconKey(icon.name()));
                    if (character == null) {
                        continue;
                    }
                    String origin = "hud " + hud.fullId() + " " + icon.name();
                    icons.put(icon.name(), new int[]{character, iconAdvance(hud, icon, origin, build.problems())});
                    glyphs.add(new HudGlyph(origin, hud.namespace(), icon.texture(), hud.sourceRoot(), hud.height(),
                            hud.ascent(), character));
                }
                int[] full = icons.get("full");
                if (full == null) {
                    continue;
                }
                int[] half = icons.getOrDefault("half", new int[]{0, 0});
                int[] empty = icons.getOrDefault("empty", new int[]{0, 0});
                layouts.add(new HudLayout(hud, new HudRenderer.Glyphs((char) full[0], full[1], (char) half[0], half[1],
                        (char) empty[0], empty[1])));
            }
            for (FontImageDefinition definition : own) {
                Integer character = definition.symbol() != null && onSymbol.get(definition.symbol()) == definition
                        ? definition.symbol() : allocation.active().get(fontImageKey(definition));
                if (character == null) {
                    continue;
                }
                Integer height = definition.height() != null ? definition.height()
                        : FontImages.pngHeight(definition.sourceRoot().resolve("textures")
                        .resolve(definition.texture().path() + ".png"));
                int drawnHeight = height != null ? Math.min(height, 4096) : 8;
                int ascent = definition.ascent() != null ? Math.min(definition.ascent(), drawnHeight)
                        : Math.min(8, drawnHeight);
                glyphs.add(new HudGlyph("font image " + definition.fullId(), definition.namespace(),
                        definition.texture(), definition.sourceRoot(), drawnHeight, ascent, character));
                definedImages.add(new FontImages.Image(definition.namespace(), definition.name(),
                        FontImages.DEFAULT_FONT, new String(Character.toChars(character)), ascent, drawnHeight,
                        definition.texture() + ".png"));
            }
            MinigameRenderer.Glyphs minigameGlyphs = minigameGlyphs(minigameTextures, allocation.active(), glyphs,
                    build.problems());
            return build.withHuds(layouts, glyphs).withFontImages(fontImageIndex(build, definedImages, provided))
                    .withExtras(build.extras().withMinigame(minigameGlyphs));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** How wide the client draws an icon; for a texture this plugin cannot read, its height plus one. */
    private static int iconAdvance(HudDefinition hud, HudDefinition.Icon icon, String origin, List<String> problems) {
        if (icon.texture().namespace().equals(hud.namespace())) {
            Path file = hud.sourceRoot().resolve("textures").resolve(icon.texture().path() + ".png");
            try {
                return GlyphMetrics.advance(file, hud.height());
            } catch (IOException exception) {
                problems.add(origin + ": could not read " + file.getFileName() + " to measure it - "
                        + exception.getMessage() + "; the bar may be placed a few pixels off");
            }
        } else {
            problems.add(origin + ": " + icon.texture() + " is another pack's texture, so its width is not known;"
                    + " taken as " + (hud.height() + 1) + " pixels");
        }
        return hud.height() + 1;
    }

    /** The vanilla folder layout under pack/. Only items that survived loading get assets. */
    private Build compilePack(Build build) {
        try {
            List<ItemDefinition> definitions = build.items().values().stream()
                    .map(CustomItem::definition)
                    .toList();
            PackCompiler.Result result = compiler.compile(packDir, new PackCompiler.Input(definitions, build.noteBlocks(),
                    build.emojis(), build.advancements().stream().map(AdvancementCompiler.Compiled::definition).toList(),
                    // Numbers given only for Bedrock change nothing in the Java pack.
                    settings.pack().modelData().enabled() ? build.modelData() : Map.of(), build.liquids(),
                    build.hudGlyphs(), settings.pack().negativeSpaces(),
                    mergedPacks(), contentsDir, builtinIcons()));
            build.problems().addAll(result.problems());
            result.notes().forEach(note -> logger.info("[pack] " + note));
            return build.withExtras(build.extras().withBrokenShaders(result.brokenShaders()));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * 5.3: the boxes each piece of furniture is clicked and punched by - its {@code hitbox:} boxes,
     * or boxes fitted to its model as the built pack draws it: what {@code hitbox: model} asks for,
     * and what a piece on a light block gets unless it says otherwise ({@code furniture.fitted-hitboxes}
     * in config.yml). A piece ModelEngine draws, or whose model cannot be read, is clicked by its blocks.
     */
    private Build fitHitboxes(Build build) {
        boolean onLight = plugin.getConfig().getBoolean("furniture.fitted-hitboxes", true);
        Map<String, List<Placement.Hitbox.Box>> fitted = new HashMap<>();
        Map<ResourceLocation, JsonObject> read = new HashMap<>();
        Function<ResourceLocation, JsonObject> models = location -> read.computeIfAbsent(location,
                key -> {
                    byte[] bytes = builtFile("assets/" + key.namespace() + "/models/" + key.path() + ".json");
                    try {
                        return bytes == null ? null : JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                                .getAsJsonObject();
                    } catch (RuntimeException broken) {
                        return null;
                    }
                });
        for (CustomItem item : build.items().values()) {
            if (!(item.placement() instanceof Placement.Furniture piece)) {
                continue;
            }
            Placement.Hitbox hitbox = piece.hitbox();
            boolean wanted = hitbox != null ? hitbox.model() || !hitbox.boxes().isEmpty()
                    : onLight && piece.support() == Placement.Support.LIGHT;
            if (!wanted) {
                continue;
            }
            if (hitbox != null && !hitbox.model()) {
                fitted.put(item.id(), hitbox.boxes());
                continue;
            }
            ModelSource source = item.definition().model();
            if (piece.modelEngineId() != null || source == null || source instanceof ModelSource.Definition) {
                continue;
            }
            if (!"NONE".equals(piece.display().transform())) {
                // Drawn through the model's own display settings, which the fitting does not follow.
                if (hitbox != null) {
                    build.problems().add(item.id() + ": hitbox: model fits a model drawn with transform: NONE only;"
                            + " give its boxes by hand - it is clicked by its blocks");
                }
                continue;
            }
            JavaModel model = JavaModel.resolve(source.location(), models);
            double[][] bounds = model == null ? null : FurnitureLayout.bounds(model,
                    piece.footprint(), piece.display(), piece.animated() != null);
            if (bounds == null) {
                if (hitbox != null) {
                    build.problems().add(item.id() + ": hitbox: model - its model " + source.location()
                            + " could not be read; it is clicked by its blocks");
                }
                continue;
            }
            fitted.put(item.id(), Hitboxes.cover(bounds[0], bounds[1]));
        }
        return build.withExtras(build.extras().withHitboxes(fitted));
    }

    /**
     * Armour that says nothing about how it is worn, worn as the equipment asset of the same name
     * the built pack has - the 3D pieces MythicArmor draws a set with, for the set an ItemsAdder
     * pack only had the icons of - and helmets with a 3D model for the head, as that model. Read
     * from the built pack, so whatever drew a piece wins as it does there; see {@link WornAssets}.
     */
    private Build linkWornAssets(Build build) {
        try {
            List<ItemDefinition> definitions = build.items().values().stream().map(CustomItem::definition).toList();
            WornAssets.Result result = WornAssets.link(definitions, builtEquipmentAssets(), this::builtFile);
            if (!result.linked().isEmpty()) {
                logger.info("[armor] " + result.linked().size() + " armour piece(s) with no equipment section, worn"
                        + " as the pack's equipment asset of the same name: " + sample(result.linked()) + ".");
            }
            if (!result.onHead().isEmpty()) {
                logger.info("[armor] " + result.onHead().size() + " helmet(s) worn as their 3D model: "
                        + sample(result.onHead()) + ".");
            }
            if (!result.ambiguous().isEmpty()) {
                logger.info("[armor] " + result.ambiguous().size() + " armour piece(s) fit several equipment assets"
                        + " and are worn as their material; give each an equipment section to choose: "
                        + sample(result.ambiguous()) + ".");
            }
            if (result.equipment().isEmpty()) {
                return build;
            }
            Map<String, CustomItem> items = new LinkedHashMap<>(build.items());
            result.equipment().forEach((id, equipment) -> items.computeIfPresent(id, (key, item) ->
                    new CustomItem(item.definition().withEquipment(equipment), item.material(), item.displayName(),
                            item.lore())));
            return build.withItems(items);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Every {@code assets/<ns>/equipment/<name>.json} of the built pack, as {@code <ns>:<name>}. */
    private List<ResourceLocation> builtEquipmentAssets() throws IOException {
        Path assets = packDir.resolve("assets");
        List<ResourceLocation> found = new ArrayList<>();
        if (!Files.isDirectory(assets)) {
            return found;
        }
        List<Path> namespaces;
        try (Stream<Path> list = Files.list(assets)) {
            namespaces = list.filter(Files::isDirectory).sorted().toList();
        }
        for (Path namespace : namespaces) {
            Path equipment = namespace.resolve("equipment");
            if (!Files.isDirectory(equipment) || !ResourceLocation.isValidNamespace(namespace.getFileName().toString())) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(equipment)) {
                for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                    String relative = equipment.relativize(file).toString().replace('\\', '/');
                    if (relative.endsWith(".json")) {
                        String path = relative.substring(0, relative.length() - ".json".length());
                        if (ResourceLocation.isValidPath(path)) {
                            found.add(new ResourceLocation(namespace.getFileName().toString(), path));
                        }
                    }
                }
            }
        }
        return found;
    }

    /** A file of the built pack by its path in it; null when there is none. */
    private @Nullable byte[] builtFile(String path) {
        Path file = packDir.resolve(path).normalize();
        if (!file.startsWith(packDir) || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException unreadable) {
            return null;
        }
    }

    /** The first few of a list, for one log line. */
    private static String sample(List<String> lines) {
        return lines.size() <= 8 ? String.join(", ", lines)
                : String.join(", ", lines.subList(0, 8)) + " and " + (lines.size() - 8) + " more";
    }

    // ---------------------------------------------------------------- 3.0: Bedrock

    /**
     * Whether the content is made for Bedrock players too: config {@code bedrock.enabled}, where
     * auto means when Geyser-Spigot is installed - or (3.3) Floodgate, which a server behind a proxy
     * with Geyser has. Decided once, as the plugin starts - Geyser takes the content only as it
     * starts too.
     */
    public boolean bedrockActive() {
        return bedrockActive;
    }

    /**
     * 3.3: whether Geyser runs on this server and is handed the content through its API; if not, it
     * runs on a proxy (or standalone), and gets it through the extension and the files in
     * output/bedrock/geyser-extension/.
     */
    public boolean geyserHere() {
        return geyserHere;
    }

    private static boolean bedrockActive(ArkContentPlugin plugin) {
        Object mode = plugin.getConfig().get("bedrock.enabled", "auto");
        return switch (String.valueOf(mode).trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "proxy" -> true;
            case "false", "off", "no" -> false;
            default -> plugin.getServer().getPluginManager().getPlugin("Geyser-Spigot") != null
                    || plugin.getServer().getPluginManager().getPlugin("floodgate") != null;
        };
    }

    private static boolean geyserHere(ArkContentPlugin plugin) {
        String mode = String.valueOf(plugin.getConfig().get("bedrock.enabled", "auto")).trim().toLowerCase(Locale.ROOT);
        return !mode.equals("proxy") && plugin.getServer().getPluginManager().getPlugin("Geyser-Spigot") != null;
    }

    /** What Bedrock players are shown, and the .mcpack it is drawn with. */
    public record BedrockContent(BedrockConverter.Build build, Path pack) {
    }

    /**
     * The first build's Bedrock content: Geyser waits on this as it starts. Completed on the worker,
     * never on the main thread - which is where Geyser waits.
     */
    public CompletableFuture<BedrockContent> bedrockReady() {
        return bedrockReady;
    }

    /** The last build's Bedrock content; null before the first, or with Bedrock off. */
    public @Nullable BedrockContent bedrockLatest() {
        return bedrockLatest;
    }

    /**
     * The content for Bedrock players, from the Java pack just compiled: items with icons, blocks,
     * furniture as carriers, and the .mcpack - written as output/bedrock/arkcontent-&lt;hash&gt;.mcpack,
     * a name of its own for each build, since Geyser holds on to the one it started with.
     */
    private Build bedrock(Build build) {
        if (!bedrockActive()) {
            return build;
        }
        try {
            BedrockConverter.Input input = bedrockInput(build);
            long started = System.nanoTime();
            BedrockConverter.Build bedrock = BedrockConverter.convert(input);
            if (!bedrock.carrierAssignments().equals(input.previousCarriers())) {
                StableAllocator.write(carrierFile, bedrock.carrierAssignments());
            }
            Files.createDirectories(bedrockDir);
            Path pack = bedrockDir.resolve("arkcontent-" + bedrock.packId() + ".mcpack");
            if (!Files.isRegularFile(pack)) {
                Files.write(pack, bedrock.pack());
            }
            removeOldPacks(pack);
            build.problems().addAll(bedrock.problems());
            if (!geyserHere) {
                exportForProxy(bedrock);
            }
            Path missing = bedrockDir.resolve("items-without-picture.txt");
            if (bedrock.unpictured().isEmpty()) {
                Files.deleteIfExists(missing);
            } else {
                Files.write(missing, bedrock.unpictured());
            }
            logger.info(String.format(Locale.ROOT, "[bedrock] %d item(s), %d block(s) (%d furniture carrier state(s)),"
                            + " %d armour piece(s) drawn worn, pack %s (%d KB) in %d ms.", bedrock.items().size(),
                    bedrock.blocks().size(), bedrock.carriers().size(), bedrock.wornPieces(), pack.getFileName(),
                    bedrock.pack().length / 1024,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));
            BedrockContent content = new BedrockContent(bedrock, pack);
            bedrockLatest = content;
            bedrockReady.complete(content);
            return build.withExtras(build.extras().withBedrock(bedrock, pack));
        } catch (IOException | RuntimeException failure) {
            bedrockReady.completeExceptionally(failure);
            build.problems().add("bedrock: the content could not be made for Bedrock players - " + failure);
            return build;
        }
    }

    /**
     * 3.3.1: the .mcpacks of builds before this one, one more each time the content changed - all
     * but this build's and the one Geyser took as it started, which it keeps reading until it stops.
     */
    private void removeOldPacks(Path current) {
        Path held = bedrockReady.isDone() && !bedrockReady.isCompletedExceptionally() ? bedrockReady.join().pack() : null;
        try (var files = Files.list(bedrockDir)) {
            for (Path file : files.toList()) {
                if (OLD_PACK.matcher(file.getFileName().toString()).matches() && !file.equals(current) && !file.equals(held)) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException inUse) {
                        // Still open somewhere: the next build tries again.
                    }
                }
            }
        } catch (IOException unreadable) {
            logger.fine("[bedrock] Could not look for old packs in " + bedrockDir + ": " + unreadable);
        }
    }

    private static final java.util.regex.Pattern OLD_PACK = java.util.regex.Pattern.compile("arkcontent-[0-9a-f]{12}\\.mcpack");

    /**
     * 3.3: for a Geyser on a proxy, the items and blocks it is to be told and the .mcpack, as the
     * extension reads them: output/bedrock/geyser-extension/, and the folder {@code
     * bedrock.geyser-folder} names, if it does - Geyser's extensions/arkcontent/ on a proxy that
     * shares this machine's disk. Each file is written only when it changed.
     */
    private void exportForProxy(BedrockConverter.Build bedrock) throws IOException {
        byte[] content = com.arkcronist.content.core.geyser.GeyserExport.write(bedrock.items(), bedrock.blocks(),
                bedrock.carriers().size()).getBytes(StandardCharsets.UTF_8);
        List<Path> folders = new ArrayList<>();
        folders.add(bedrockDir.resolve("geyser-extension"));
        String elsewhere = plugin.getConfig().getString("bedrock.geyser-folder", "");
        if (elsewhere != null && !elsewhere.isBlank()) {
            folders.add(Path.of(elsewhere.trim()));
        }
        boolean changed = false;
        for (Path folder : folders) {
            try {
                Files.createDirectories(folder);
                changed |= writeIfChanged(folder.resolve(com.arkcronist.content.core.geyser.GeyserExport.FILE), content);
                changed |= writeIfChanged(folder.resolve(com.arkcronist.content.core.geyser.GeyserExport.PACK),
                        bedrock.pack());
            } catch (IOException failure) {
                logger.warning("[bedrock] Could not write the files for Geyser's extension into " + folder + ": " + failure);
            }
        }
        if (changed) {
            logger.info("[bedrock] Geyser is not on this server: the content for Bedrock players is in"
                    + " output/bedrock/geyser-extension/. Put this plugin's jar in the proxy's Geyser extensions/"
                    + " folder and those two files in extensions/arkcontent/, then restart the proxy."
                    + (folders.size() > 1 ? " Also written to " + folders.get(1) + "." : ""));
        }
    }

    private static boolean writeIfChanged(Path file, byte[] bytes) throws IOException {
        if (Files.isRegularFile(file) && java.util.Arrays.equals(Files.readAllBytes(file), bytes)) {
            return false;
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temporary, bytes);
        Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        return true;
    }

    /** 3.4: how Bedrock clients are to use an item: drunk, one to a slot, worn. */
    private static BedrockConverter.Uses uses(ItemDefinition definition) {
        com.arkcronist.content.core.definition.DrinkDefinition drink = definition.drink();
        float seconds = drink == null ? 0 : (float) drink.seconds();
        int stack = drink != null || definition.backpack() != null ? 1 : 0;
        int damage = drink == null ? 0 : drink.sips();
        String slot = definition.equipment() == null ? null
                : definition.equipment().slot().name().toLowerCase(Locale.ROOT);
        return new BedrockConverter.Uses(seconds, stack, damage, slot);
    }

    /**
     * 3.4: how a piece of armour is drawn worn, for its Bedrock attachable: its equipment asset, its
     * 3D model on the head, or - worn as its material - vanilla's armour of that material.
     */
    private static @Nullable BedrockConverter.Worn worn(ItemDefinition definition, String material) {
        com.arkcronist.content.core.definition.Equipment equipment = definition.equipment();
        if (equipment == null) {
            return BedrockConverter.Worn.ofMaterial(material);
        }
        String slot = equipment.slot().name().toLowerCase(Locale.ROOT);
        if (equipment.asset() != null) {
            return new BedrockConverter.Worn(slot, equipment.asset(), null, null);
        }
        if (equipment.slot() == com.arkcronist.content.core.definition.Equipment.Slot.HEAD) {
            ResourceLocation onHead = equipment.worn() != null ? equipment.worn().location()
                    : definition.model() != null ? definition.model().location() : definition.itemModel();
            return new BedrockConverter.Worn(slot, null, onHead, null);
        }
        return null;
    }

    private BedrockConverter.Input bedrockInput(Build build) throws IOException {
        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer plain =
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        List<BedrockConverter.ItemSpec> items = new ArrayList<>();
        List<BedrockConverter.BlockSpec> blocks = new ArrayList<>();
        List<BedrockConverter.LiquidSpec> liquids = new ArrayList<>();
        List<BedrockConverter.FurnitureSpec> furniture = new ArrayList<>();
        List<BedrockConverter.ShapedSpec> shaped = new ArrayList<>();
        for (CustomItem item : build.items().values()) {
            ItemDefinition definition = item.definition();
            String name = item.displayName() == null ? item.id() : plain.serialize(item.displayName());
            ResourceLocation model = definition.model() == null ? null : definition.model().location();
            if (item.hasModel()) {
                String material = item.material().name();
                boolean handheld = definition.gun() != null || material.endsWith("_SWORD") || material.endsWith("_AXE")
                        || material.endsWith("_PICKAXE") || material.endsWith("_SHOVEL") || material.endsWith("_HOE")
                        || material.equals("FISHING_ROD") || material.equals("STICK");
                items.add(new BedrockConverter.ItemSpec(item.id(), material, definition.itemModel(), model,
                        build.modelData().get(item.id()), name, handheld, uses(definition), worn(definition, material)));
            }
            NoteBlockState state = build.noteBlocks().get(item.id());
            // 5.4: a block of its own hardness takes its own time on Bedrock too.
            com.arkcronist.content.core.block.Mining mining = item.placement() instanceof Placement.Block placed
                    ? placed.mining() : null;
            if (state != null && model != null) {
                blocks.add(mining == null ? new BedrockConverter.BlockSpec(item.id(), state, model, name)
                        : new BedrockConverter.BlockSpec(item.id(), state, model, name, mining.bedrockSeconds()));
            }
            Integer slot = build.liquids().get(item.id());
            if (slot != null && item.placement() instanceof Placement.Liquid liquid) {
                liquids.add(new BedrockConverter.LiquidSpec(item.id(), TripwireState.source(slot).blockData(),
                        liquid.source().location(), TripwireState.flowing(slot).blockData(), liquid.flowing().location(),
                        name, liquid.glow()));
            }
            String base = item.material().name().toLowerCase(Locale.ROOT);
            if (item.placement() instanceof Placement.Block shapedBlock && shapedBlock.shape().bases().contains(base)
                    && model != null) {
                // 5.3: a slab, stairs, door... drawn for Bedrock players over the copper block it is.
                Map<String, ResourceLocation> shapeModels = new LinkedHashMap<>();
                if (shapedBlock.shape() == BlockShape.TRANSPARENT) {
                    shapeModels.put("", model);
                }
                for (com.arkcronist.content.core.block.ShapedModels.Part part
                        : com.arkcronist.content.core.block.ShapedModels.parts(shapedBlock.shape())) {
                    shapeModels.put(part.suffix(), com.arkcronist.content.core.block.ShapedModels.blockModel(
                            definition.namespace(), definition.id(), part.suffix()));
                }
                shaped.add(mining == null
                        ? new BedrockConverter.ShapedSpec(item.id(), shapedBlock.shape(), base, shapeModels, name)
                        : new BedrockConverter.ShapedSpec(item.id(), shapedBlock.shape(), base, shapeModels, name,
                        mining.bedrockSeconds()));
            }
            if (item.placement() instanceof Placement.Furniture piece && model != null && piece.modelEngineId() == null
                    && (piece.support() == Placement.Support.BARRIER || piece.support() == Placement.Support.LIGHT)) {
                furniture.add(new BedrockConverter.FurnitureSpec(item.id(), model, piece.footprint(), piece.display(),
                        piece.animated() != null, piece.support() == Placement.Support.BARRIER, piece.light(), name));
            }
        }
        List<BedrockConverter.EmojiSpec> emojis = new ArrayList<>();
        build.emojis().forEach((emoji, character) -> emojis.add(new BedrockConverter.EmojiSpec(character, emoji.texture())));
        Set<Integer> blockStates = new java.util.HashSet<>(NoteBlockAllocator.read(noteBlockStateFile).values());
        build.noteBlocks().values().forEach(state -> blockStates.add(state.index()));
        // 3.3: furniture the admin leaves out for Bedrock players, to give its carriers to the rest.
        Set<String> skip = new java.util.HashSet<>();
        for (String id : plugin.getConfig().getStringList("bedrock.skip-furniture")) {
            skip.add(id.trim().toLowerCase(Locale.ROOT));
        }
        return new BedrockConverter.Input(this::builtFile, items, blocks, liquids, furniture, emojis,
                StableAllocator.read(carrierFile), blockStates, skip, shaped);
    }

    private CompletableFuture<Build> zipPack(Build build) {
        return PackZipper.zipAsync(packDir, zipFile, worker).thenApply(build::withFiles);
    }

    private Build hashPack(Build build) {
        try {
            return build.withArtifact(PackArtifact.load(zipFile, build.files()));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Decides the link players are sent for this build - uploading the zip first, when upload: is on. */
    private CompletableFuture<Build> host(Build build) {
        String sha1 = build.artifact().sha1Hex();
        return switch (settings.hosting()) {
            case BUILTIN -> CompletableFuture.completedFuture(build.withHosted(settings.http().enabled()
                    ? new Hosted(null, "served by the built-in web server", false, true)
                    : new Hosted(null, "the built-in web server is off and no other hosting is set; host "
                    + zipFile + " yourself", false)));
            case EXTERNAL -> CompletableFuture.completedFuture(build.withHosted(new Hosted(
                    settings.http().externalUrl(sha1), "hosted at http.external-url - upload " + zipFile
                    + " there after each rebuild", false)));
            case UPLOAD -> upload(build);
        };
    }

    private CompletableFuture<Build> upload(Build build) {
        PackArtifact pack = build.artifact();
        long started = System.nanoTime();
        // An unchanged pack keeps its link (data/upload.json): checked, not uploaded again.
        return uploader.publish(pack.bytes(), pack.sha1Hex()).handleAsync((result, error) -> {
            if (error == null) {
                String how = result.reused() ? "already uploaded" : "uploaded in "
                        + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) + " ms"
                        + (result.attempts() > 1 ? " (" + result.attempts() + " attempts)" : "");
                return build.withHosted(new Hosted(result.url(), how + (result.verified() ? ", download checked" : ""),
                        false));
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            String reason = "Upload failed: " + cause.getMessage();
            if (plugin.httpRunning()) {
                build.problems().add(reason + ". Players are sent the built-in web server's link instead.");
                return build.withHosted(new Hosted(null,
                        "served by the built-in web server, the upload having failed", false, true));
            }
            build.problems().add(reason + ". Players keep the pack they have; the new items have no textures until"
                    + " an upload succeeds (/arkcontent reload tries again).");
            return build.withHosted(new Hosted(null, "not uploaded", true));
        }, worker);
    }

    /**
     * The whole demo pack on the first start; on an upgrade, the example sets not offered yet (see
     * {@link ExampleUpdates}) - never over a file that is there, never into a deleted pack folder.
     */
    private void extractExamples() throws IOException {
        if (!Files.exists(contentsDir)) {
            if (settings.extractExamples()) {
                for (String example : EXAMPLES) {
                    plugin.saveResource(example, false);
                }
            }
            Files.createDirectories(contentsDir);
            rememberOffered();
            return;
        }
        List<String> lines = new ArrayList<>();
        for (Path file : List.of(examplesOfferedFile, legacyExamplesFile)) {
            if (Files.isRegularFile(file)) {
                lines.addAll(Files.readAllLines(file));
            }
        }
        Set<String> offered = ExampleUpdates.parse(lines);
        if (!offered.containsAll(ADDED_EXAMPLES.keySet())) {
            if (settings.extractExamples()) {
                List<String> copy = ExampleUpdates.toCopy(FIRST_EXAMPLES, ADDED_EXAMPLES, offered,
                        plugin.getDataFolder().toPath());
                for (String example : copy) {
                    plugin.saveResource(example, false);
                }
                if (!copy.isEmpty()) {
                    List<String> files = copy.stream().filter(path -> path.endsWith(".yml")).toList();
                    logger.info("Added " + copy.size() + " example file(s) from newer versions to contents/"
                            + (files.isEmpty() ? "" : ": " + String.join(", ", files) + " and what they use")
                            + ". Nothing already there was touched; delete what you do not want, it will not come back.");
                }
            }
            rememberOffered();
        }
        updateUntouchedExamples();
    }

    private void rememberOffered() throws IOException {
        Files.createDirectories(examplesOfferedFile.getParent());
        Files.write(examplesOfferedFile, ADDED_EXAMPLES.keySet().stream().sorted().toList());
        Files.deleteIfExists(legacyExamplesFile);
    }

    /**
     * An example still byte for byte as an earlier version shipped it was never edited: it is
     * replaced by this version's (the 1.5 chest and bed, the demo blocks with their job rewards).
     * One the owner changed is theirs and stays.
     */
    private void updateUntouchedExamples() throws IOException {
        if (!settings.extractExamples()) {
            return;
        }
        List<String> lines;
        try (java.io.InputStream history = plugin.getResource("examples-history.txt")) {
            if (history == null) {
                return;
            }
            lines = new String(history.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        }
        List<String> outdated = ExampleUpdates.outdated(ExampleUpdates.parseHistory(lines),
                plugin.getDataFolder().toPath());
        for (String example : outdated) {
            plugin.saveResource(example, true);
        }
        if (!outdated.isEmpty()) {
            logger.info("Updated " + outdated.size() + " example file(s) you had not changed to this version's: "
                    + String.join(", ", outdated) + ".");
        }
    }

    // ---------------------------------------------------------------- main thread

    private Report publish(Build build, long started) {
        plugin.itemFactory().modelData(build.modelData());
        registry.replace(build.items().values());
        // Stacks made before - a piece worn as its material until this build matched it with an asset.
        plugin.stackRepair().repairOnline();
        List<CustomBlock> customBlocks = new ArrayList<>();
        for (Map.Entry<String, NoteBlockState> entry : build.noteBlocks().entrySet()) {
            CustomItem item = build.items().get(entry.getKey());
            customBlocks.add(new CustomBlock(item, entry.getValue(),
                    plugin.getServer().createBlockData(entry.getValue().asBlockData())));
        }
        blocks.replace(customBlocks);
        // 5.3: the shaped blocks, by the copper block each is; vanilla's recipes for those taken out.
        plugin.shapedBlocks().replace(build.items().values());
        plugin.copperRecipes().update(plugin.shapedBlocks().claimed());
        // 5.3: the boxes furniture is clicked by; every loaded piece fitted with its own again.
        plugin.furniture().useHitboxes(build.extras().hitboxes());
        if (plugin.ores() != null) {
            plugin.ores().publish(customBlocks);
        }
        if (plugin.treeGenerator() != null) {
            plugin.treeGenerator().publish(registry, blocks);
        }
        if (plugin.mechanics() != null) {
            plugin.mechanics().publish(registry);
            plugin.mechanicsListener().recipesChanged();
        }
        List<LiquidRegistry.Liquid> liquids = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : build.liquids().entrySet()) {
            CustomItem item = build.items().get(entry.getKey());
            if (item != null && item.placement() instanceof Placement.Liquid liquid) {
                int slot = entry.getValue();
                liquids.add(new LiquidRegistry.Liquid(item, liquid, slot,
                        plugin.getServer().createBlockData(TripwireState.source(slot).blockData()),
                        plugin.getServer().createBlockData(TripwireState.flowing(slot).blockData())));
            }
        }
        plugin.liquidRegistry().replace(liquids);
        plugin.hudRegistry().replace(build.huds());
        if (plugin.betterHudBars() != null) {
            plugin.betterHudBars().publish(build.huds());
        }
        if (plugin.minigames() != null) {
            plugin.minigames().glyphs(build.extras().minigame());
        }
        if (plugin.cosmetics() != null) {
            plugin.cosmetics().contentReloaded();
        }
        if (plugin.vehicles() != null) {
            plugin.vehicles().contentReloaded();
        }
        if (plugin.bedrock() != null && build.extras().bedrock() != null) {
            plugin.bedrock().update(build.extras().bedrock());
        }
        plugin.fontImages().replace(build.fontImages());
        emojis.replace(build.emojis());
        int advancements = plugin.advancements() == null ? 0 : plugin.advancements().publish(build.advancements());
        // After the advancements: a changed one reloads the server's data, which drops added recipes.
        List<String> problems = new ArrayList<>(build.problems());
        int recipes = plugin.recipes() == null ? 0 : plugin.recipes().publish(build.extras().recipes(), problems);
        if (plugin.hooks() != null) {
            plugin.hooks().contentReloaded();
        }
        if (plugin.menus() != null) {
            plugin.menus().refreshOpen();
        }
        Hosted hosted = build.hosted();
        String url = hosted.builtin() ? plugin.webHost().address().packUrl(build.artifact().sha1Hex()) : hosted.url();
        // A failed upload leaves the live pack alone: its link still works, the new one would not.
        boolean changed = !(hosted.keepPrevious() && delivery.live() != null)
                && delivery.publish(build.artifact(), url);
        delivery.suspects(build.extras().brokenShaders());
        if (changed) {
            delivery.sendAll(plugin.getServer().getOnlinePlayers());
        }

        PackArtifact pack = build.artifact();
        Report report = new Report(build.items().size(), customBlocks.size(), build.emojis().size(), advancements,
                recipes, pack.entries(),
                pack.sha1Hex(), pack.size(),
                changed, url, hosted.note(), List.copyOf(problems),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        log(report);
        // 5.4: other plugins are told the content is live - the first time, and after each reload.
        if (plugin.api() != null) {
            plugin.api().markReady();
            boolean first = !publishedOnce;
            publishedOnce = true;
            try {
                plugin.getServer().getPluginManager().callEvent(new com.arkcronist.content.api.event.OverlordContentLoadedEvent(
                        first, report.items(), report.blocks(), changed));
            } catch (RuntimeException listener) {
                logger.log(Level.WARNING, "A plugin listening for the content being loaded failed", listener);
            }
        }
        return report;
    }

    private void log(Report report) {
        for (String problem : report.problems()) {
            logger.warning(problem);
        }
        logger.info(report.items() + " custom item(s) (" + report.blocks() + " block(s)), " + report.emojis()
                + " emoji(s), " + report.advancements() + " advancement(s), " + report.recipes() + " recipe(s), pack of "
                + report.files() + " file(s), "
                + (report.bytes() / 1024) + " KiB, sha1 " + report.sha1Hex()
                + (report.changed() ? "" : " (unchanged)")
                + " - built in " + report.millis() + " ms"
                + (report.problems().isEmpty() ? "" : ", " + report.problems().size() + " problem(s) above")
                + ". Pack " + report.hosting() + (report.url() != null ? ": " + report.url() : "."));
    }

    private static String fontImageKey(FontImageDefinition definition) {
        return "font_image:" + definition.fullId();
    }

    /** The icons of the plugin's own menu buttons, drawn as {@code arkcontent:menu/<name>}. */
    static final List<String> MENU_ICONS = List.of("previous", "next", "up", "home", "filter", "close", "page");

    /**
     * The plugin's own font images - the content browser's book and the quest board's parchment -
     * copied out of the jar into {@code data/builtin/textures/gui/} when missing or changed, so the
     * pack draws them like any other, each with the size and placement {@link GuiPicture} gives it.
     */
    private List<FontImageDefinition> builtinFontImages() throws IOException {
        Path root = builtinRoot();
        List<FontImageDefinition> images = new ArrayList<>();
        for (GuiPicture picture : GuiPicture.BUILTIN) {
            Path file = extract(root, "textures/gui/" + picture.name() + ".png");
            if (file == null) {
                continue;
            }
            images.add(new FontImageDefinition("arkcontent", picture.name(),
                    new ResourceLocation("arkcontent", "gui/" + picture.name()), picture.ascent(), picture.height(), null,
                    root, file));
        }
        return images;
    }

    /** The minigame's textures, out of the jar the same way: name to file. */
    private Map<String, Path> builtinMinigameTextures() throws IOException {
        Path root = builtinRoot();
        Map<String, Path> textures = new LinkedHashMap<>();
        List<String> names = new ArrayList<>(List.of("frame", "needle"));
        for (int width = 1; width <= 64; width *= 2) {
            names.add("hit_" + width);
            names.add("critical_" + width);
        }
        for (String name : names) {
            Path file = extract(root, "textures/minigame/" + name + ".png");
            if (file != null) {
                textures.put(name, file);
            }
        }
        return textures;
    }

    /**
     * The minigame's glyphs, on their characters, each measured as the client will draw it - the
     * frame is stepped over pixel by pixel, so its advance has to be exact. Null when any is
     * missing: no minigame is better than one drawn wrong.
     */
    private @Nullable MinigameRenderer.Glyphs minigameGlyphs(Map<String, Path> textures, Map<String, Integer> characters,
                                                             List<HudGlyph> glyphs, List<String> problems) {
        if (textures.isEmpty()) {
            return null;
        }
        Map<String, int[]> placed = new HashMap<>();
        for (Map.Entry<String, Path> texture : textures.entrySet()) {
            Integer character = characters.get(MINIGAME_KEY + texture.getKey());
            if (character == null) {
                continue;
            }
            int advance;
            try {
                advance = GlyphMetrics.advance(texture.getValue(), MINIGAME_HEIGHT);
            } catch (IOException exception) {
                problems.add("minigame: could not read " + texture.getValue().getFileName() + " - " + exception.getMessage());
                return null;
            }
            glyphs.add(new HudGlyph("minigame " + texture.getKey(), "arkcontent",
                    new ResourceLocation("arkcontent", "minigame/" + texture.getKey()), builtinRoot(), MINIGAME_HEIGHT,
                    MINIGAME_ASCENT, character));
            placed.put(texture.getKey(), new int[]{character, advance});
        }
        Map<Integer, MinigameRenderer.Piece> hit = new HashMap<>();
        Map<Integer, MinigameRenderer.Piece> critical = new HashMap<>();
        for (int width = 1; width <= 64; width *= 2) {
            int[] h = placed.get("hit_" + width);
            int[] c = placed.get("critical_" + width);
            if (h == null || c == null) {
                problems.add("minigame: the pieces of the bar did not all get a character; the minigame is off");
                return null;
            }
            hit.put(width, new MinigameRenderer.Piece((char) h[0], h[1]));
            critical.put(width, new MinigameRenderer.Piece((char) c[0], c[1]));
        }
        int[] frame = placed.get("frame");
        int[] needle = placed.get("needle");
        if (frame == null || needle == null) {
            problems.add("minigame: the bar or the needle did not get a character; the minigame is off");
            return null;
        }
        return new MinigameRenderer.Glyphs((char) frame[0], frame[1], frame[1] - 1, 2, (char) needle[0], needle[1],
                needle[1] - 1, hit, critical);
    }

    /** The menu buttons' icons, out of the jar the same way, for the pack's {@code arkcontent:menu/<name>}. */
    private List<PackCompiler.Icon> builtinIcons() throws IOException {
        Path root = builtinRoot();
        List<PackCompiler.Icon> icons = new ArrayList<>();
        for (String name : MENU_ICONS) {
            Path file = extract(root, "textures/menu/" + name + ".png");
            if (file != null) {
                icons.add(new PackCompiler.Icon(new ResourceLocation("arkcontent", "menu/" + name), file));
            }
        }
        return icons;
    }

    private Path builtinRoot() {
        return hudCharacterFile.getParent().resolve("builtin");
    }

    /** Copies {@code builtin/<relative>} out of the jar under {@code root} when missing or changed. */
    private @Nullable Path extract(Path root, String relative) throws IOException {
        byte[] bytes;
        try (java.io.InputStream in = plugin.getResource("builtin/" + relative)) {
            if (in == null) {
                return null;
            }
            bytes = in.readAllBytes();
        }
        Path file = root.resolve(relative);
        if (!Files.isRegularFile(file) || !java.util.Arrays.equals(Files.readAllBytes(file), bytes)) {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        }
        return file;
    }

    /**
     * Font images named in ItemsAdder's own configs, when its folder is still on the server and they
     * were not imported: those a merged pack draws - ItemsAdder's generated one - by the names menus
     * written for it use. A content file's own {@code font_images:} entry of the same id wins.
     */
    private List<FontImages.Image> itemsAdderFontImages(Build build, List<FontImages.Image> provided)
            throws IOException {
        if (!Files.isDirectory(itemsAdderFolder)) {
            return List.of();
        }
        Set<String> defined = new java.util.HashSet<>();
        build.fontImageDefinitions().forEach(definition -> defined.add(definition.fullId()));
        List<FontImages.Image> images = new ArrayList<>();
        int undrawn = 0;
        for (FontImageDefinition definition : new ContentLoader().itemsAdderFontImages(itemsAdderFolder)) {
            if (defined.contains(definition.fullId())) {
                continue;
            }
            java.util.Optional<FontImages.Image> image = FontImages.match(definition, provided);
            if (image.isPresent()) {
                images.add(image.get());
            } else {
                undrawn++;
            }
        }
        if (!images.isEmpty() || undrawn > 0) {
            logger.info(images.size() + " font image name(s) read from the configs in plugins/ItemsAdder"
                    + (undrawn > 0 ? " (" + undrawn + " more no merged pack draws, left out)" : "")
                    + ". To keep them once that folder is gone, import those configs: /arkcontent import.");
        }
        return images;
    }

    /** The font images by name, for titles and placeholders; how many there are goes in the log. */
    private FontImages fontImageIndex(Build build, List<FontImages.Image> defined, List<FontImages.Image> provided) {
        FontImages index = FontImages.of(defined, provided);
        if (index.size() > 0) {
            logger.info(index.size() + " font image(s) by name (" + index.defined() + " from font_images:, the rest"
                    + " named after their picture in a merged pack): :<name>:, :offset_<n>: and %img_<name>% work in"
                    + " menu titles and placeholders."
                    + (index.ambiguous().isEmpty() ? "" : " Left out - one picture, several characters: "
                    + String.join(", ", index.ambiguous().stream().limit(8).toList())
                    + (index.ambiguous().size() > 8 ? " and " + (index.ambiguous().size() - 8) + " more" : "") + "."));
        }
        return index;
    }

    /** Every font file of the merged packs, for font images. */
    private List<FontImages.Font> mergedFonts() throws IOException {
        List<FontImages.Font> fonts = new ArrayList<>();
        for (ExternalPack pack : mergedPacks()) {
            try (PackSource source = PackSource.open(pack)) {
                fonts.addAll(FontImages.fontsOf(source));
            }
        }
        return fonts;
    }

    /**
     * The characters the merged packs' default fonts draw already - an ItemsAdder pack's emojis and
     * icons - which no emoji or HUD icon of ours is put on.
     */
    private Set<Integer> fontCharactersInUse() throws IOException {
        Set<Integer> characters = new java.util.HashSet<>();
        for (ExternalPack pack : mergedPacks()) {
            try (PackSource source = PackSource.open(pack)) {
                characters.addAll(com.arkcronist.content.core.pack.FontCharacters.usedBy(source));
            }
        }
        return characters;
    }

    /**
     * Every pack merged into ours: those config.yml names, other plugins' and each folder or zip
     * in packs/ - where /arkcontent import puts the ItemsAdder packs it absorbs. Read on every
     * rebuild, so a pack dropped in or deleted there counts from the next one.
     */
    private List<ExternalPack> mergedPacks() throws IOException {
        List<ExternalPack> packs = new ArrayList<>(externalPacks.values());
        if (Files.isDirectory(packsDir)) {
            try (Stream<Path> list = Files.list(packsDir)) {
                list.filter(path -> !path.getFileName().toString().startsWith("."))
                        .filter(path -> Files.isDirectory(path) || path.getFileName().toString().endsWith(".zip"))
                        .sorted()
                        .forEach(path -> packs.add(new ExternalPack("packs/" + path.getFileName(), path)));
            }
        }
        return packs;
    }

    /**
     * Merges another plugin's pack into ours from now on, and rebuilds so players get it. A second
     * call with the same name replaces the first. Main thread.
     */
    public void mergeExternalPack(ExternalPack pack) {
        externalPacks.put(pack.name(), pack);
        logger.info("Merging the " + pack.name() + " resource pack from " + pack.root() + " into ours.");
        rebuild();
    }

    /** Stops the worker. Called from onDisable. */
    public void shutdown() {
        worker.shutdownNow();
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("The content worker did not stop within 5 seconds.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
