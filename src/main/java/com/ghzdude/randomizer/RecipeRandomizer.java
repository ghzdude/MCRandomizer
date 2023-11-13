package com.ghzdude.randomizer;

import com.ghzdude.randomizer.reflection.ReflectionUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementList;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.tags.ITag;
import net.minecraftforge.registries.tags.ITagManager;

import javax.annotation.Nonnull;
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
    private static final Map<ResourceLocation, List<ResourceLocation>> MODIFIED = new Object2ObjectOpenHashMap<>();

    private static RandomizationMapData INSTANCE;

    @SubscribeEvent
    public void start(ServerStartedEvent event) {
        if (RandomizerConfig.recipeRandomizerEnabled()) {
            INSTANCE = RandomizationMapData.get(event.getServer().overworld().getDataStorage(), "recipes");

            RandomizerCore.LOGGER.warn("Recipe Randomizer Running!");
            randomizeRecipes(
                    event.getServer().getRecipeManager()
            );

            setAdvancements(event.getServer().getAdvancements());
        }
    }

    @SubscribeEvent
    public void stop(ServerStoppingEvent event) {
        MODIFIED.clear();
    }

    public static void setAdvancements(ServerAdvancementManager manager) {
        AdvancementList list = ReflectionUtils.getField(ServerAdvancementManager.class, manager, 2);
        Map<ResourceLocation, Advancement> holders = ReflectionUtils.getField(AdvancementList.class, list, 1);
        Map<ResourceLocation, Advancement> toKeep = new Object2ObjectOpenHashMap<>();
        holders.forEach(((resourceLocation, holder) -> {
            if (!holder.getId().getPath().contains("recipes/")) toKeep.put(resourceLocation, holder);
        }));

        buildAdvancements(toKeep);
        ReflectionUtils.setField(ServerAdvancementManager.class, manager, 2, toKeep);
    }

    public static void randomizeRecipes(RecipeManager manager) {
        for (var recipe : manager.getRecipes()) {
            ItemStack newResult = INSTANCE.getStackFor(recipe.getResultItem());

            modifyRecipeOutputs(recipe, newResult);

            // if inputs are not to be randomized, move on to the next recipe
            if (RandomizerConfig.randomizeInputs()) {
                modifyRecipeInputs(
                        recipe.getIngredients().stream()
                        .distinct().filter(ingredient -> !ingredient.isEmpty()).toList(), recipe.getId()
                );
            }
        }
    }

    private static void modifyRecipeOutputs(Recipe<?> recipe, ItemStack newResult) {
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

    private static void modifyRecipeInputs(List<Ingredient> ingredients, ResourceLocation recipe) {
        IForgeRegistry<Item> registry = ForgeRegistries.ITEMS;

        for (int k = 0; k < ingredients.size(); k++) {
            Ingredient.Value[] values = ReflectionUtils.getField(Ingredient.class, ingredients.get(k), 2);
            if (values.length == 0 || Arrays.stream(values).allMatch(Objects::isNull)) continue;
            ResourceLocation ingredient = null;

            for (int j = 0; j < values.length; j++) {
                if (values[j] instanceof Ingredient.ItemValue itemValue) {
                    ItemStack vanilla = ReflectionUtils.getField(Ingredient.ItemValue.class, itemValue, 0);
                    ItemStack stack = INSTANCE.getStackFor(vanilla);
                    ingredient = registry.getKey(stack.getItem());
                    values[j] = new Ingredient.ItemValue(stack);
                } else if (values[j] instanceof Ingredient.TagValue tagValue) {
                    TagKey<Item> vanilla = ReflectionUtils.getField(Ingredient.TagValue.class, tagValue, 0);
                    TagKey<Item> key = INSTANCE.getTagKeyFor(vanilla);
                    ingredient = key.location();
                    values[j] = new Ingredient.TagValue(key);
                }
                if (ingredient == null) return;

            }
            addToMap(recipe, ingredient);
        }
    }

    public static void addToMap(@Nonnull ResourceLocation recipe, @Nonnull ResourceLocation ingredient) {
        if (RecipeRandomizer.MODIFIED.containsKey(ingredient)) {
            RecipeRandomizer.MODIFIED.get(ingredient).add(recipe);
        } else {
            List<ResourceLocation> list = new ArrayList<>();
            list.add(recipe);
            RecipeRandomizer.MODIFIED.put(ingredient, list);
        }
    }

    private static void buildAdvancements(Map<ResourceLocation, Advancement> map) {
        ITagManager<Item> tagManager = ForgeRegistries.ITEMS.tags();
        if (tagManager == null) return;

        MODIFIED.forEach((ing, recipes) -> {
            Item[] changedItems;
            Item item = ForgeRegistries.ITEMS.getValue(ing);
            Optional<ITag<Item>> tag = tagManager.getTagNames()
                    .filter(key -> key.location().equals(ing))
                    .map(tagManager::getTag)
                    .findFirst();

            if (item != Items.AIR) {
                changedItems = new Item[]{item};
            } else if (tag.isPresent()) {
                changedItems = tag.get().stream().toArray(Item[]::new);
            } else {
                RandomizerCore.LOGGER.warn("{} is not a valid item or tag!", ing);
                return;
            }

            Advancement.Builder builder = Advancement.Builder.advancement();
            for (ResourceLocation recipe : recipes) {
                builder.rewards(AdvancementRewards.Builder.recipe(recipe));
            }
            builder.addCriterion("has_item", InventoryChangeTrigger.TriggerInstance.hasItems(changedItems));
            Advancement toAdd = builder.build(new ResourceLocation(RandomizerCore.MODID, ing.getNamespace() + "-" + ing.getPath() + "_gives_recipes"));
            map.put(toAdd.getId(), toAdd);
        });
    }
}
