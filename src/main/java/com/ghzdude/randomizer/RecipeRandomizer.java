package com.ghzdude.randomizer;

import net.minecraft.data.recipes.*;
import net.minecraft.world.item.crafting.*;

/* Recipe Randomizer Description
 * on resource re/load, randomize every recipe
 */
public class RecipeRandomizer {
    public void randomizeRecipe(Recipe<?> recipe) {
        ItemRandomizer randomizer = new ItemRandomizer();

        SpecialRecipeBuilder specialBuilder;
        ShapedRecipeBuilder shapedBuilder;
        ShapelessRecipeBuilder shapelessBuilder;

        if (recipe instanceof ShapedRecipe){
            shapedBuilder = new ShapedRecipeBuilder(randomizer.getRandomItem().item, recipe.getResultItem().getCount());
            shapedBuilder.pattern(recipe.getGroup());
        }

        if (recipe instanceof ShapelessRecipe) {
            shapelessBuilder = new ShapelessRecipeBuilder(randomizer.getRandomItem().item, recipe.getResultItem().getCount());
        }
    }
}
