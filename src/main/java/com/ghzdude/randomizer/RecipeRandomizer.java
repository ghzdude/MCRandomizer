package com.ghzdude.randomizer;

import com.ghzdude.randomizer.reflection.ReflectionUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.*;

/* Recipe Randomizer Description.
 * on resource re/load, randomize every recipe.
 * each world would have a unique set of randomized recipes.
 *
 * for advancements, picking up item A gives you recipes whose input is also item A.
 * to be more precise, picking up item A gives you a recipe by resource location
 * the inputs and output can change, but the resource location does not
 * if the recipe inputs are modified, the advancement rewards needs to change.
 * if recipe A with item A as input becomes item B, then picking item B should unlock recipe A.
 * an item can unlock multiple recipes.
 * for a given advancement for an item, i need to find all recipes whose inputs match.
 * then update the rewards for the advancement
 *
 */
public class RecipeRandomizer {

    private static final Map<ResourceLocation, ResourceLocation[]> VANILLA = new Object2ObjectOpenHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation[]> MODIFIED = new Object2ObjectOpenHashMap<>();

    @SubscribeEvent
    public void start(ServerStartedEvent event) {
        if (RandomizerConfig.recipeRandomizerEnabled()) {
            RandomizerCore.LOGGER.warn("Recipe Randomizer Running!");
            Collection<AdvancementHolder> advancementHolders = event.getServer().getAdvancements().getAllAdvancements()
                    .stream().filter(advancementHolder -> advancementHolder.id().getPath().contains("recipes/"))
                    .toList();
            randomizeRecipes(
                    event.getServer().getRecipeManager(),
                    event.getServer().registryAccess()
            );
            tryModifyAdvancements(advancementHolders);
        }
    }

    @SubscribeEvent
    public void stop(ServerStoppingEvent event) {
        MODIFIED.clear();
    }
    public void randomizeRecipes(RecipeManager manager, RegistryAccess access) {
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack newResult = ItemRandomizer.getStackFor(recipe.getResultItem(access));

            modifyRecipeOutputs(recipe, newResult);

            // if inputs are not to be randomized, move on to the next recipe
            if (RandomizerConfig.randomizeInputs()) modifyRecipeInputs(
                    recipe.getIngredients().stream().distinct().filter(ingredient -> !ingredient.isEmpty()).toList(), holder.id());
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
            ResourceLocation[] vanilla = new ResourceLocation[values.length];
            ResourceLocation[] modified = new ResourceLocation[values.length];

            for (int j = 0; j < values.length; j++) {
                if (values[j] instanceof Ingredient.ItemValue itemValue) {
                    vanilla[j] = registry.getKey(itemValue.item().getItem());
                    ItemStack stack = ItemRandomizer.getStackFor(itemValue.item());
                    values[j] = new Ingredient.ItemValue(stack);
                    modified[j] = registry.getKey(stack.getItem());
                } else if (values[j] instanceof Ingredient.TagValue tagValue) {
                    TagKey<Item> key = ItemRandomizer.getTagKeyFor(tagValue.tag());
                    vanilla[j] = tagValue.tag().location();
                    values[j] = new Ingredient.TagValue(key);
                    modified[j] = key.location();
                }
            }
            addToMap(VANILLA, vanilla, recipe);
            addToMap(MODIFIED, modified, recipe);
        }
    }

    private void addToMap(Map<ResourceLocation, ResourceLocation[]> map, ResourceLocation[] toAdd, ResourceLocation recipe) {
        if (map.containsKey(recipe)) {
            if (toAdd.length == 0) {
                RandomizerCore.LOGGER.warn("{} was given no inputs!", recipe);
            } else if (map.get(recipe) == null) {
                map.replace(recipe, toAdd);
            }
        } else {
            map.put(recipe, toAdd);
        }
    }

    private void tryModifyAdvancements(Collection<AdvancementHolder> advancementHolders) {
        for (AdvancementHolder holder : advancementHolders) {
            AdvancementRewards rewards = holder.value().rewards();
            if (rewards.getRecipes().length == 0) continue;

            List<ResourceLocation> updated = new ArrayList<>();
            for (ResourceLocation resourceLocation : rewards.getRecipes()) {
                if (!VANILLA.containsKey(resourceLocation)) continue;
                ResourceLocation[] ingredients = VANILLA.get(resourceLocation);
                MODIFIED.entrySet().stream()
                    .filter(entry -> entry.getValue().length != ingredients.length)
                    .filter(entry -> ingredients[0] == entry.getValue()[0])
                    .forEach(entry -> updated.add(entry.getKey()));
            }
            if (updated.size() == 0) updated.add(new ResourceLocation("empty"));
            ReflectionUtils.setField(AdvancementRewards.class, rewards, 3, updated.toArray(ResourceLocation[]::new));
        }
    }
}
