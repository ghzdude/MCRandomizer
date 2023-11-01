package com.ghzdude.randomizer;

import com.ghzdude.randomizer.reflection.ReflectionUtils;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/* Recipe Randomizer Description
 * on resource re/load, randomize every recipe
 * each world would have a unique set of randomized recipes
 */
public class RecipeRandomizer {

    public void randomizeRecipes(Collection<RecipeHolder<?>> recipes, RegistryAccess access, List<AdvancementHolder> advancementHolders) {
        for (RecipeHolder<?> holder : recipes) {
            Recipe<?> recipe = holder.value();
            ItemStack newResult = ItemRandomizer.getStackFor(recipe.getResultItem(access));

            if (recipe instanceof ShapedRecipe shapedRecipe) {
                ReflectionUtils.setField(ShapedRecipe.class, shapedRecipe, 5, newResult);
            } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
                ReflectionUtils.setField(ShapelessRecipe.class, shapelessRecipe, 2, newResult);
            } else if (recipe instanceof AbstractCookingRecipe abstractCookingRecipe) {
                ReflectionUtils.setField(AbstractCookingRecipe.class, abstractCookingRecipe, 4, newResult);
            } else if (recipe instanceof SingleItemRecipe singleItemRecipe) {
                ReflectionUtils.setField(SingleItemRecipe.class, singleItemRecipe, 1, newResult);
            }

            // if inputs are not to be randomized, move on to the next recipe
            if (!RandomizerConfig.randomizeInputs()) continue;

            advancementHolders.stream()
                    .filter(advancementHolder -> advancementHolder.id().getPath().contains(holder.id().getPath()))
                    .findFirst().ifPresent(advancementHolder -> {
                        Set<String> keys = advancementHolder.value().criteria().keySet();
                        Map<String, Criterion<?>> old = advancementHolder.value().criteria();
                        for (String s: keys) {
                            Criterion<?> c = old.get(s);
                        }
            });

            List<Ingredient> ingredients = recipe.getIngredients();
            for (Ingredient i : ingredients) {
                Ingredient.Value[] values = ReflectionUtils.getField(Ingredient.class, i, 2);
                for (int j = 0; j < values.length; j++) {
                    if (values[j] instanceof Ingredient.ItemValue itemValue) {
                        values[j] = new Ingredient.ItemValue(ItemRandomizer.getStackFor(itemValue.item()));
                    } else if (values[j] instanceof Ingredient.TagValue tagValue) {
                        values[j] = new Ingredient.TagValue(ItemRandomizer.getTagKeyFor(tagValue.tag()));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void start(ServerStartedEvent event) {
        if (RandomizerConfig.recipeRandomizerEnabled()) {
            RandomizerCore.LOGGER.warn("Recipe Randomizer Running!");
            Collection<RecipeHolder<?>> recipeHolders = event.getServer().getRecipeManager().getRecipes();
            List<AdvancementHolder> advancementHolders = event.getServer().getAdvancements().getAllAdvancements().stream()
                            .filter(advancementHolder -> advancementHolder.id().getPath().contains("recipes/"))
                            .toList();
            randomizeRecipes(recipeHolders, event.getServer().registryAccess(), advancementHolders);
        }
    }
}
