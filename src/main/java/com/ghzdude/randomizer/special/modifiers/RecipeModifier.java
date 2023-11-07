package com.ghzdude.randomizer.special.modifiers;

import com.ghzdude.randomizer.ItemRandomizer;
import com.ghzdude.randomizer.RandomizerConfig;
import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.RecipeRandomizer;
import com.ghzdude.randomizer.reflection.ReflectionUtils;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class RecipeModifier implements ResourceManagerReloadListener {

    private final RegistryAccess access;
    private final RecipeManager manager;
    public RecipeModifier(RegistryAccess access, RecipeManager manager) {
        this.access = access;
        this.manager = manager;
    }

    @Override
    public void onResourceManagerReload(ResourceManager p_10758_) {
        randomizeRecipes();
    }

    private void randomizeRecipes() {
        RandomizerCore.LOGGER.warn("Randomizing Recipes!");
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack newResult = ItemRandomizer.getStackFor(recipe.getResultItem(this.access));

            modifyRecipeOutputs(recipe, newResult);

            // if inputs are not to be randomized, move on to the next recipe
            if (RandomizerConfig.randomizeInputs()) {
                modifyRecipeInputs(
                        recipe.getIngredients().stream()
                                .distinct().filter(ingredient -> !ingredient.isEmpty()).toList(), holder.id()
                );
            }
        }
    }

    private void modifyRecipeOutputs(Recipe<?> recipe, ItemStack newResult) {
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            ReflectionUtils.setField(ShapedRecipe.class, shapedRecipe, 5, newResult);
        } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            ReflectionUtils.setField(ShapelessRecipe.class, shapelessRecipe, 2, newResult);
        } else if (recipe instanceof AbstractCookingRecipe abstractCookingRecipe) {
            ReflectionUtils.setField(AbstractCookingRecipe.class, abstractCookingRecipe, 4, newResult);
        } else if (recipe instanceof SingleItemRecipe singleItemRecipe) {
            ReflectionUtils.setField(SingleItemRecipe.class, singleItemRecipe, 1, newResult);
        }
    }

    private void modifyRecipeInputs(List<Ingredient> ingredients, ResourceLocation recipe) {
        IForgeRegistry<Item> registry = ForgeRegistries.ITEMS;

        for (int k = 0; k < ingredients.size(); k++) {
            Ingredient.Value[] values = ReflectionUtils.getField(Ingredient.class, ingredients.get(k), 2);
            if (values.length == 0 || Arrays.stream(values).allMatch(Objects::isNull)) continue;
            ResourceLocation ingredient = null;

            for (int j = 0; j < values.length; j++) {
                if (values[j] instanceof Ingredient.ItemValue itemValue) {
                    ItemStack stack = ItemRandomizer.getStackFor(itemValue.item());
                    ingredient = registry.getKey(stack.getItem());
                    values[j] = new Ingredient.ItemValue(stack);
                } else if (values[j] instanceof Ingredient.TagValue tagValue) {
                    TagKey<Item> key = ItemRandomizer.getTagKeyFor(tagValue.tag());
                    ingredient = key.location();
                    values[j] = new Ingredient.TagValue(key);
                }
                if (ingredient == null) return;

            }
            RecipeRandomizer.addToMap(recipe, ingredient);
        }
    }
}
