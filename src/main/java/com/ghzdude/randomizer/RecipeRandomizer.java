package com.ghzdude.randomizer;

import com.ghzdude.randomizer.saveddata.RandomizerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;

/* Recipe Randomizer Description
 * on resource re/load, randomize every recipe
 * each world would have a unique set of randomized recipes
 */
public class RecipeRandomizer {
    public boolean hasChanged = false;
    private ArrayList<ItemStack> newResults = new ArrayList<>();

    public void randomizeRecipe(Recipe<?> recipe, int index) {

        ItemStack newResult;

        if (!this.hasChanged) {
            newResult = new ItemStack(ItemRandomizer.getRandomItem().item);
            newResult.setCount(recipe.getResultItem().getCount());
            newResults.add(newResult);
        } else {
            newResult = newResults.get(index);
        }

        //access recipe result directly somehow
        // recipe.getResultItem() = new ItemStack(newResult.item);
        if (recipe instanceof ShapedRecipe) {
            setField(ShapedRecipe.class, (ShapedRecipe) recipe, 5, newResult);
        }
        if (recipe instanceof ShapelessRecipe) {
            setField(ShapelessRecipe.class, (ShapelessRecipe) recipe, 2, newResult);
        }
        if (recipe instanceof AbstractCookingRecipe) {
            setField(AbstractCookingRecipe.class, (AbstractCookingRecipe) recipe, 4, newResult);
        }
    }

    private <T, R> void setField(Class<T> clz, T inst, int index, ItemStack replace) {
        Field fld = clz.getDeclaredFields()[index];
        fld.setAccessible(true);
        try {
            fld.set(inst, replace);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SubscribeEvent
    public void start(ServerStartedEvent event) {
        RandomizerData data = RandomizerData.get(event.getServer().overworld().getDataStorage());
        this.hasChanged = data.recipesChanged;
        // load state here
        RecipeManager manager = event.getServer().getRecipeManager();

        RandomizerCore.LOGGER.warn("Randomizing Recipes!");
        int index = 0;
        for (Recipe<?> recipe : manager.getRecipes()) {
            randomizeRecipe(recipe, index);
            index++;
        }

        this.hasChanged = true;
    }

    @SubscribeEvent
    public void stop(ServerStoppingEvent event) {
        RandomizerData data = RandomizerData.get(event.getServer().overworld().getDataStorage());
        data.recipesChanged = this.hasChanged;
        data.setDirty();
        // save state here
    }
}
