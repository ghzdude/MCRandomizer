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

            if (recipe instanceof ShapedRecipe) {
                ReflectionUtils.setField(ShapedRecipe.class, (ShapedRecipe) recipe, 5, newResult);
            } else if (recipe instanceof ShapelessRecipe) {
                ReflectionUtils.setField(ShapelessRecipe.class, (ShapelessRecipe) recipe, 2, newResult);
            } else if (recipe instanceof AbstractCookingRecipe) {
                ReflectionUtils.setField(AbstractCookingRecipe.class, (AbstractCookingRecipe) recipe, 4, newResult);
            } else if (recipe instanceof SingleItemRecipe) {
                ReflectionUtils.setField(SingleItemRecipe.class, (SingleItemRecipe) recipe, 1, newResult);
            }

            List<Ingredient> ingredients = recipe.getIngredients();
            for (Ingredient i : ingredients) {
//                if (i.)
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

    public static RecipeData get(DimensionDataStorage storage){
        return storage.computeIfAbsent(RecipeData.factory(), RandomizerCore.MODID + "_recipes");
    }

    protected static class RecipeData extends SavedData {

        private final Map<ResourceLocation, ItemStack> changedRecipes = new Object2ObjectArrayMap<>();
        protected static RecipeData INSTANCE = new RecipeData();

        public static SavedData.Factory<RecipeData> factory() {
            return new Factory<>(RecipeData::new, RecipeData::load, DataFixTypes.LEVEL);
        }

        @Override
        public @NotNull CompoundTag save(CompoundTag tag) {
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

        public static RecipeData load(CompoundTag tag) {
            RandomizerCore.LOGGER.warn("Loading changed recipes to world data!");

            ListTag listTag = tag.getList("changed_recipes", Tag.TAG_COMPOUND);
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag kvPair = listTag.getCompound(i);
                INSTANCE.changedRecipes.put(
                        ResourceLocation.tryParse(kvPair.getString("recipe_id")),
                        ItemStack.of(kvPair.getCompound("item"))
                );
            }
            return INSTANCE;
        }

        public boolean hasRecipe(ResourceLocation location) {
            return changedRecipes.containsKey(location);
        }

        public ItemStack getStack(ResourceLocation location) {
            return changedRecipes.get(location);
        }

        public void put(ResourceLocation location, ItemStack stack) {
            changedRecipes.put(location, stack);
        }
    }
}
