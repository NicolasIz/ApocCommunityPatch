package com.arkcronist.content.core.recipe;

import com.arkcronist.content.core.loader.ContentLoader;
import com.arkcronist.content.core.loader.LoadReport;
import com.arkcronist.content.core.recipe.RecipeDefinition.Cooking;
import com.arkcronist.content.core.recipe.RecipeDefinition.Ingredient;
import com.arkcronist.content.core.recipe.RecipeDefinition.ItemRef;
import com.arkcronist.content.core.recipe.RecipeDefinition.Shaped;
import com.arkcronist.content.core.recipe.RecipeDefinition.Shapeless;
import com.arkcronist.content.core.recipe.RecipeDefinition.Smithing;
import com.arkcronist.content.core.recipe.RecipeDefinition.Station;
import com.arkcronist.content.core.recipe.RecipeDefinition.Stonecutting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 4.0: recipes as content files describe them. */
class RecipeReaderTest {

    @TempDir
    Path contents;

    private static ItemRef custom(String id) {
        return new ItemRef(ItemRef.Kind.CUSTOM, id);
    }

    private static ItemRef vanilla(String id) {
        return new ItemRef(ItemRef.Kind.VANILLA, id);
    }

    @Test
    void everyStationReadsWithItsInputsAndDefaults() throws IOException {
        write("demo/recipes.yml", """
                recipes:
                  ruby_sword:
                    type: shaped
                    pattern:
                      - " R "
                      - " R "
                      - " S "
                    ingredients:
                      R: demo:ruby
                      S: STICK
                    result: demo:ruby_sword
                    category: equipment
                    group: ruby_tools
                  ruby_from_block:
                    type: shapeless
                    ingredients: [demo:ruby_block]
                    result: demo:ruby 9
                  mixed:
                    type: shapeless
                    ingredients: ["#planks", [demo:ruby, minecraft:diamond], "demo:ruby 2"]
                    result: DIAMOND
                    amount: 2
                    recipe-book: false
                    permission: ruby.mixed
                  smelt_star_ore:
                    type: blasting
                    input: demo:star_ore
                    result: demo:ruby
                    experience: 0.7
                    cook-seconds: 2.5
                    category: blocks
                  furnace_default:
                    type: furnace
                    input: demo:star_ore
                    result: demo:ruby
                  cut:
                    type: stonecutter
                    input: STONE
                    result: demo:ruby_block 2
                  upgrade:
                    type: smithing
                    template: NETHERITE_UPGRADE_SMITHING_TEMPLATE
                    base: demo:ruby_helmet
                    addition: demo:ruby
                    result: demo:ruby_helmet_plus
                    keep-components: false
                """);
        LoadReport report = new ContentLoader().load(contents);
        assertEquals(List.of(), report.problems());
        Map<String, RecipeDefinition> byId = new java.util.HashMap<>();
        report.recipes().forEach(recipe -> byId.put(recipe.fullId(), recipe));
        assertEquals(7, byId.size());

        RecipeDefinition sword = byId.get("demo:ruby_sword");
        Shaped shaped = assertInstanceOf(Shaped.class, sword.shape());
        assertEquals(List.of(" R ", " R ", " S "), shaped.pattern());
        assertEquals(new Ingredient(List.of(custom("demo:ruby"))), shaped.key().get('R'));
        assertEquals(new Ingredient(List.of(vanilla("minecraft:stick"))), shaped.key().get('S'));
        assertEquals("equipment", shaped.category());
        assertEquals("ruby_tools", sword.group());
        assertEquals(2, shaped.customSlots(), "two rubies, and the stick is vanilla");
        assertTrue(sword.recipeBook());

        RecipeDefinition block = byId.get("demo:ruby_from_block");
        assertEquals(custom("demo:ruby"), block.result());
        assertEquals(9, block.amount());

        RecipeDefinition mixed = byId.get("demo:mixed");
        Shapeless shapeless = assertInstanceOf(Shapeless.class, mixed.shape());
        assertEquals(4, shapeless.ingredients().size(), "\"demo:ruby 2\" counts twice");
        assertEquals(new ItemRef(ItemRef.Kind.TAG, "minecraft:planks"), shapeless.ingredients().getFirst().options().getFirst());
        assertEquals(List.of(custom("demo:ruby"), vanilla("minecraft:diamond")), shapeless.ingredients().get(1).options());
        assertEquals(3, shapeless.customSlots(), "the two rubies and ruby-or-diamond; the planks take none");
        assertEquals(vanilla("minecraft:diamond"), mixed.result());
        assertEquals(2, mixed.amount());
        assertFalse(mixed.recipeBook());
        assertEquals("ruby.mixed", mixed.permission());

        Cooking blasting = assertInstanceOf(Cooking.class, byId.get("demo:smelt_star_ore").shape());
        assertEquals(Station.BLASTING, blasting.station());
        assertEquals(50, blasting.ticks());
        assertEquals(0.7, blasting.experience(), 1e-9);
        assertEquals("blocks", blasting.category());
        Cooking furnace = assertInstanceOf(Cooking.class, byId.get("demo:furnace_default").shape());
        assertEquals(200, furnace.ticks(), "vanilla's 10 seconds");
        assertEquals(0.1, furnace.experience(), 1e-9);

        assertInstanceOf(Stonecutting.class, byId.get("demo:cut").shape());
        assertEquals(2, byId.get("demo:cut").amount());

        Smithing smithing = assertInstanceOf(Smithing.class, byId.get("demo:upgrade").shape());
        assertEquals(vanilla("minecraft:netherite_upgrade_smithing_template"), smithing.template().options().getFirst());
        assertFalse(smithing.keepComponents());
        assertEquals(List.of("demo:ruby_helmet_plus", "demo:ruby_helmet", "demo:ruby"),
                byId.get("demo:upgrade").customItems());
    }

    @Test
    void aBrokenRecipeIsReportedAndTheOthersStillLoad() throws IOException {
        write("demo/recipes.yml", """
                recipes:
                  ragged:
                    type: shaped
                    pattern: ["RR", "R"]
                    ingredients: { R: demo:ruby }
                    result: demo:x
                  unknown_key:
                    type: shaped
                    pattern: ["RX"]
                    ingredients: { R: demo:ruby }
                    result: demo:x
                  no_result:
                    type: shapeless
                    ingredients: [demo:ruby]
                  ten:
                    type: shapeless
                    ingredients: ["demo:ruby 10"]
                    result: demo:x
                  tag_result:
                    type: shapeless
                    ingredients: [demo:ruby]
                    result: "#planks"
                  bad_type:
                    type: anvil
                    result: demo:x
                  too_many:
                    type: shapeless
                    ingredients: [demo:ruby]
                    result: demo:x
                    amount: 100
                  fine:
                    type: shapeless
                    ingredients: [demo:ruby]
                    result: demo:x
                """);
        LoadReport report = new ContentLoader().load(contents);
        assertEquals(List.of("demo:fine"), report.recipes().stream().map(RecipeDefinition::fullId).toList());
        List<String> problems = report.problems();
        assertEquals(7, problems.size(), String.join("\n", problems));
        assertTrue(problems.get(0).contains("demo:ragged") && problems.get(0).contains("same length"));
        assertTrue(problems.get(1).contains("'X'"));
        assertTrue(problems.get(2).contains("missing 'result'"));
        assertTrue(problems.get(3).contains("nine ingredients"));
        assertTrue(problems.get(4).contains("not a tag"));
        assertTrue(problems.get(5).contains("unknown type 'anvil'"));
        assertTrue(problems.get(6).contains("1 to 99"));
    }

    @Test
    void aRecipeOnAnItemNoFileDefinesIsLeftOut() {
        List<String> problems = new ArrayList<>();
        List<RecipeDefinition> read = RecipeReader.read(Map.of(
                "known", Map.of("type", "shapeless", "ingredients", List.of("demo:ruby"), "result", "DIAMOND"),
                "typo", Map.of("type", "shapeless", "ingredients", List.of("demo:rubby"), "result", "DIAMOND")),
                "demo", "demo/recipes.yml", problems);
        assertEquals(List.of(), problems);
        List<RecipeDefinition> kept = RecipeReader.withKnownItems(read, Set.of("demo:ruby"), problems);
        assertEquals(List.of("demo:known"), kept.stream().map(RecipeDefinition::fullId).toList());
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("demo:rubby"));
    }

    @Test
    void theSameRecipeIdInTwoFilesKeepsTheFirst() throws IOException {
        write("demo/a.yml", "recipes:\n  r:\n    type: shapeless\n    ingredients: [STICK]\n    result: demo:a\n");
        write("demo/b.yml", "recipes:\n  r:\n    type: shapeless\n    ingredients: [STICK]\n    result: demo:b\n");
        LoadReport report = new ContentLoader().load(contents);
        assertEquals(1, report.recipes().size());
        assertEquals(custom("demo:a"), report.recipes().getFirst().result());
        assertEquals(1, report.problems().size());
        assertTrue(report.problems().getFirst().contains("already defined in demo/a.yml"));
    }

    @Test
    void namesAreReadAsCustomVanillaOrTag() {
        List<String> problems = new ArrayList<>();
        assertEquals(custom("demo:ruby"), RecipeReader.ref("demo:ruby", "", problems));
        assertEquals(vanilla("minecraft:stick"), RecipeReader.ref("STICK", "", problems));
        assertEquals(vanilla("minecraft:stick"), RecipeReader.ref("minecraft:stick", "", problems));
        assertEquals(new ItemRef(ItemRef.Kind.TAG, "minecraft:logs"), RecipeReader.ref("#logs", "", problems));
        assertEquals(List.of(), problems);
        assertNull(RecipeReader.ref("ruby block", "", problems));
        assertEquals(1, problems.size());
    }

    private void write(String relative, String text) throws IOException {
        Path file = contents.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
