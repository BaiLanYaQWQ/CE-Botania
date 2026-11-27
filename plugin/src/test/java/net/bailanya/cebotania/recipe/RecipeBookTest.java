package net.bailanya.cebotania.recipe;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class RecipeBookTest {

    private static final RecipeBook BOOK = RecipeBook.load(RecipeBookTest.class.getClassLoader());

    @Test
    void apothecaryRequiresEachRepeatedPetalAndAcceptsTagAlternatives() {
        var recipe = BOOK.matchApothecary(List.of("botania:red_petal", "botania:brown_mushroom",
                "minecraft:melon_seeds", "botania:light_gray_petal", "botania:brown_petal"));

        assertNotNull(recipe);
        assertEquals("botania:petal_apothecary/endoflame", recipe.id());
        assertEquals("botania:endoflame", recipe.output());
        assertNull(BOOK.matchApothecary(List.of("botania:red_petal", "minecraft:melon_seeds",
                "botania:light_gray_petal", "botania:brown_petal")));
        assertNull(BOOK.matchApothecary(List.of("botania:red_petal", "botania:brown_mushroom",
                "minecraft:wheat", "botania:light_gray_petal", "botania:brown_petal")));
    }

    @Test
    void runicMatchingAcceptsTagsAndIgnoresInputOrder() {
        var recipe = BOOK.matchRunic(List.of("botania:rune_earth", "botania:mana_diamond",
                "botania:rune_winter", "botania:mana_diamond"));

        assertNotNull(recipe);
        assertEquals("botania:runic_altar/wrath", recipe.id());
        assertEquals("botania:rune_wrath", recipe.output());
        assertEquals(1, recipe.count());
        assertEquals(12000L, recipe.mana());
        assertNull(BOOK.matchRunic(List.of("botania:rune_earth", "minecraft:diamond",
                "botania:rune_winter", "botania:mana_diamond")));
    }

    @Test
    void infusionReturnsFirstMatchingRecipeInResourceOrder() {
        var recipe = BOOK.matchInfusion("minecraft:dead_bush");

        assertNotNull(recipe);
        assertEquals("botania:mana_infusion/dead_bush_to_grass", recipe.id());
        assertEquals("botania:alchemy_catalyst", recipe.catalystBlock());
        assertEquals(500L, recipe.mana());
        assertEquals(1, recipe.count());
    }

    @Test
    void elvenTradeKeepsExactItemTagAndSingleInputBehavior() {
        assertEquals(new RecipeBook.Elven("minecraft:diamond", null, "minecraft:diamond", 1),
                BOOK.matchElven(List.of("minecraft:diamond")));
        assertEquals(new RecipeBook.Elven(null, "botania:mana_diamond_gems", "botania:dragonstone", 1),
                BOOK.matchElven(List.of("botania:mana_diamond")));
        assertEquals(new RecipeBook.Elven(null, "botania:manasteel_ingots", "botania:elementium_ingot", 1),
                BOOK.matchElven(List.of("botania:manasteel_ingot")));
        assertNull(BOOK.matchElven(List.of("botania:manasteel_ingot", "botania:manasteel_ingot")));
        assertNull(BOOK.matchElven(List.of("minecraft:coal")));
    }

    @Test
    void missingResourcesLeaveAnEmptyBook() {
        var empty = RecipeBook.load(new ClassLoader(null) {});

        assertEquals(Map.of("apothecary", 0, "infusion", 0, "runic", 0), empty.describe());
        assertNull(empty.matchElven(List.of("minecraft:diamond")));
    }
}
