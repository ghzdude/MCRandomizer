package com.ghzdude.randomizer;

import com.ghzdude.randomizer.api.AdvancementModify;
import com.ghzdude.randomizer.api.IngredientRandomizable;
import com.ghzdude.randomizer.api.OutputSetter;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.advancements.*;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.tags.ITag;
import net.minecraftforge.registries.tags.ITagManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private static RandomizationMapData INSTANCE = null;

    @SubscribeEvent
    public void start(ServerStartedEvent event) {
        if (RandomizerConfig.randomizeRecipes) {
            if (INSTANCE == null)
                INSTANCE = RandomizationMapData.get(event.getServer().overworld().getDataStorage(), "recipes");

            RandomizerCore.LOGGER.warn("Recipe Randomizer Running!");
            randomizeRecipes(
                    event.getServer().getRecipeManager(),
                    event.getServer().registryAccess()
            );

            setAdvancements(event.getServer().getAdvancements());
        }
    }

    @SubscribeEvent
    public void stop(ServerStoppingEvent event) {
        MODIFIED.clear();
    }

    public static void setAdvancements(ServerAdvancementManager manager) {
        if (manager instanceof AdvancementModify modify) {
            modify.randomizer$randomizeRecipeAdvancements();
        }
    }

    public static void randomizeRecipes(RecipeManager manager, RegistryAccess access) {
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack newResult = INSTANCE.getStackFor(recipe.getResultItem(access));

            modifyRecipeOutputs(recipe, newResult);

            // if inputs are not to be randomized, move on to the next recipe
            if (RandomizerConfig.randomizeRecipeInputs) {
                modifyRecipeInputs(
                        recipe.getIngredients().stream()
                        .distinct().filter(ingredient -> !ingredient.isEmpty()).toList(), holder.id()
                );
            }
        }
    }

    private static void modifyRecipeOutputs(Recipe<?> recipe, ItemStack newResult) {
        if (recipe instanceof OutputSetter setter) {
            setter.randomizer$setResult(newResult);
        }
    }

    private static void modifyRecipeInputs(List<Ingredient> ingredients, ResourceLocation recipe) {
        IForgeRegistry<Item> registry = ForgeRegistries.ITEMS;

        for (int k = 0; k < ingredients.size(); k++) {
            if (ingredients.get(k) instanceof IngredientRandomizable randomizable) {
                randomizable.randomizer$randomizeInputs(value -> {
                    ResourceLocation ingredient;
                    Ingredient.Value random;
                    if (value instanceof Ingredient.ItemValue itemValue) {
                        ItemStack stack = INSTANCE.getStackFor(itemValue.item());
                        ingredient = registry.getKey(stack.getItem());
                        if (ingredient == null) return value;
                        random = new Ingredient.ItemValue(stack);
                    } else {
                        Ingredient.TagValue tagValue = (Ingredient.TagValue) value;
                        TagKey<Item> key = INSTANCE.getTagKeyFor(tagValue.tag());
                        ingredient = key.location();
                        random = new Ingredient.TagValue(key);
                    }
                    addToMap(recipe, ingredient);
                    return random;
                });
            }
        }
    }

    public static void addToMap(@NotNull ResourceLocation recipe, @NotNull ResourceLocation ingredient) {
        RecipeRandomizer.MODIFIED
                .computeIfAbsent(ingredient, key -> new ArrayList<>())
                .add(recipe);
    }

    public static void buildAdvancements(Map<ResourceLocation, AdvancementHolder> map) {
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

            Advancement.Builder builder = new Advancement.Builder();
            for (ResourceLocation recipe : recipes) {
                builder.rewards(AdvancementRewards.Builder.recipe(recipe));
            }
            builder.addCriterion("has_item", InventoryChangeTrigger.TriggerInstance.hasItems(changedItems));
            AdvancementHolder toAdd = builder.build(ResourceLocation.fromNamespaceAndPath(RandomizerCore.MODID, ing.getNamespace() + "-" + ing.getPath() + "_gives_recipes"));
            map.put(toAdd.id(), toAdd);
        });
    }
}
