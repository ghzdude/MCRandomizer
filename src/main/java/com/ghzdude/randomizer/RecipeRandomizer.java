package com.ghzdude.randomizer;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.common.crafting.conditions.IConditionBuilder;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/* Recipe Randomizer Description
 * on resource re/load, randomize every recipe
 */
public class RecipeRandomizer {

    public void randomizeRecipe(Recipe<?> recipe) {
        ItemRandomizer randomizer = new ItemRandomizer();


        SpecialRecipeBuilder specialBuilder;
        ShapelessRecipeBuilder shapelessBuilder;

        if (recipe instanceof ShapedRecipe){
            ShapedRecipeBuilder shapedBuilder = ShapedRecipeBuilder.shaped(randomizer.getRandomItem().item, recipe.getResultItem().getCount());

        }

        if (recipe instanceof ShapelessRecipe) {
            shapelessBuilder = new ShapelessRecipeBuilder(randomizer.getRandomItem().item, recipe.getResultItem().getCount());
        }
    }

    public static class RandomRecipeProvider extends RecipeProvider implements IConditionBuilder {

        public RandomRecipeProvider(DataGenerator output) {
            super(output);
        }

        @Override
        protected void buildCraftingRecipes(Consumer<FinishedRecipe> consumer) {
            super.buildCraftingRecipes(vanilla -> {
                FinishedRecipe modified = enhance(vanilla);
                if (modified != null)
                    consumer.accept(modified);
            });
        }

        private FinishedRecipe enhance(FinishedRecipe vanilla)
        {
            if (vanilla instanceof ShapelessRecipeBuilder.Result shapeless)
                return enhance(shapeless);
            if (vanilla instanceof ShapedRecipeBuilder.Result shaped)
                return enhance(shaped);
            return null;
        }

        @Nullable
        private FinishedRecipe enhance(ShapelessRecipeBuilder.Result vanilla) {
            List<Ingredient> ingredients = getField(ShapelessRecipeBuilder.Result.class, vanilla, 4);

            boolean modified = false;
            for (int x = 0; x < ingredients.size(); x++) {
                Ingredient ing = enhance(ingredients.get(x));
                ingredients.set(x, ing);
                modified = true;
            }
            return modified ? vanilla : null;
        }

        private FinishedRecipe enhance(ShapedRecipeBuilder.Result vanilla) {
            Map<Character, Ingredient> ingredients = getField(ShapedRecipeBuilder.Result.class, vanilla, 5);
            boolean modified = false;
            for (Character x : ingredients.keySet()) {
                Ingredient ing = enhance(ingredients.get(x));
                ingredients.put(x, ing);
                modified = true;
            }
            return modified ? vanilla : null;
        }


        private Ingredient enhance(Ingredient vanilla) {
            Ingredient.Value[] vanillaItems = getField(Ingredient.class, vanilla, 2);
            for (Ingredient.Value value : vanillaItems) {
                if (value instanceof Ingredient.ItemValue) {

                }
            }

            List<Ingredient.Value> items = new ArrayList<>(Arrays.asList(vanillaItems));
            return  Ingredient.fromValues(items.stream());
        }

        @SuppressWarnings("unchecked")
        private <T, R> R getField(Class<T> clz, T inst, int index)
        {
            Field fld = clz.getDeclaredFields()[index];
            fld.setAccessible(true);
            try
            {
                return (R) fld.get(inst);
            } catch (IllegalArgumentException | IllegalAccessException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
