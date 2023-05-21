package com.ghzdude.randomizer;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Map;

/* Recipe Randomizer Description
 * on resource re/load, randomize every recipe
 * each world would have a unique set of randomized recipes
 */
public class RecipeRandomizer {
    private RecipeData data;

    public void randomizeRecipe(Recipe<?> recipe) {

        ItemStack newResult;
        if (data.changedRecipes.get(recipe.getId()) != null) {
            newResult = data.changedRecipes.get(recipe.getId());
        } else {
            newResult = new ItemStack(ItemRandomizer.getRandomItem().item);
            newResult.setCount(Math.min(recipe.getResultItem().getCount(), newResult.getMaxStackSize()));
        }

        if (recipe instanceof ShapedRecipe) {
            setField(ShapedRecipe.class, (ShapedRecipe) recipe, 5, newResult);
        }
        if (recipe instanceof ShapelessRecipe) {
            setField(ShapelessRecipe.class, (ShapelessRecipe) recipe, 2, newResult);
        }
        if (recipe instanceof AbstractCookingRecipe) {
            setField(AbstractCookingRecipe.class, (AbstractCookingRecipe) recipe, 4, newResult);
        }
        if (recipe instanceof SingleItemRecipe) {
            setField(SingleItemRecipe.class, (SingleItemRecipe) recipe, 1, newResult);
        }
    }

    private <T> void setField(Class<T> clazz, T instance, int index, ItemStack newResult) {
        Field fld = clazz.getDeclaredFields()[index];
        fld.setAccessible(true);
        try {
            fld.set(instance, newResult);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        data.changedRecipes.put(((Recipe<?>) instance).getId(), newResult);
    }

    @SubscribeEvent
    public void start(ServerStartedEvent event) {
        if (!RandomizerConfig.recipeRandomizerEnabled()) return;

        data = get(event.getServer().overworld().getDataStorage());
        // load state here
        RandomizerCore.LOGGER.warn("Randomizing Recipes!");
        for (Recipe<?> recipe : event.getServer().getRecipeManager().getRecipes()) {
            randomizeRecipe(recipe);
        }
        data.setDirty();
    }

    public static RecipeData get(DimensionDataStorage storage){
        return storage.computeIfAbsent(RecipeData::load, RecipeData::create, RandomizerCore.MODID + "_recipes");
    }

    protected static class RecipeData extends SavedData {

        public final Map<ResourceLocation, ItemStack> changedRecipes = new Object2ObjectArrayMap<>();

        @Override
        public @NotNull CompoundTag save(CompoundTag tag) {
            if (!RandomizerConfig.recipeRandomizerEnabled()) return tag;

            RandomizerCore.LOGGER.warn("Saving changed recipes to world data!");
            ListTag changedRecipesTag = new ListTag();

            changedRecipes.forEach((key, value) -> {
                CompoundTag kvPair = new CompoundTag();
                kvPair.putString("recipe_id", key.toString());
                kvPair.put("item", value.serializeNBT());
                changedRecipesTag.add(kvPair);
            });

            tag.put("changed_recipes", changedRecipesTag);
            return tag;
        }

        public static RecipeData create() {
            return new RecipeData();
        }

        public static RecipeData load(CompoundTag tag) {
            RecipeData data = create();
            if (!RandomizerConfig.recipeRandomizerEnabled()) return data;

            RandomizerCore.LOGGER.warn("Loading changed recipes to world data!");
            ListTag listTag = tag.getList("changed_recipes", Tag.TAG_COMPOUND);
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag kvPair = listTag.getCompound(i);
                data.changedRecipes.put(
                        ResourceLocation.tryParse(kvPair.getString("recipe_id")),
                        ItemStack.of(kvPair.getCompound("item"))
                );
            }
            return data;
        }
    }
}
