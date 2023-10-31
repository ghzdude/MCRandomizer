package com.ghzdude.randomizer;

import com.ghzdude.randomizer.reflection.ReflectionUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/* Recipe Randomizer Description
 * on resource re/load, randomize every recipe
 * each world would have a unique set of randomized recipes
 */
public class RecipeRandomizer {

    public void randomizeRecipes(Collection<RecipeHolder<?>> recipes, RegistryAccess access) {
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
            randomizeRecipes(event.getServer().getRecipeManager().getRecipes(), event.getServer().registryAccess());
        }
    }
}
