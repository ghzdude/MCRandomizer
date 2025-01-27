package com.ghzdude.randomizer;

import com.ghzdude.randomizer.api.AdvancementModify;
import com.ghzdude.randomizer.api.IngredientRandomizable;
import com.ghzdude.randomizer.api.OutputSetter;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
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
    private static Registry<Item> ITEM_REGISTRY;

    // todo look into RecipesUpdatedEvent
    public static void init(MinecraftServer server) {
        if (RandomizerConfig.randomizeRecipes) {
            ITEM_REGISTRY = server.registryAccess().registryOrThrow(Registries.ITEM);
            INSTANCE = RandomizationMapData.get(server, "recipes");

            RandomizerCore.LOGGER.warn("Recipe Randomizer Running!");
            randomizeRecipes(server.getRecipeManager(), server.registryAccess());

            setAdvancements(server.getAdvancements());
        }
    }

    public static void dispose() {
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

            if (!recipe.getResultItem(access).is(Items.ENDER_EYE) || !RandomizerConfig.ensureCompletability)
                modifyRecipeOutputs(recipe, newResult);

            // if inputs are not to be randomized, move on to the next recipe
            if (RandomizerConfig.randomizeRecipeInputs) {
                modifyRecipeInputs(recipe.getIngredients(), holder.id());
            }
        }
    }

    private static void modifyRecipeOutputs(Recipe<?> recipe, ItemStack newResult) {
        if (recipe instanceof OutputSetter setter) {
            setter.randomizer$setResult(newResult);
        }
    }

    private static void modifyRecipeInputs(List<Ingredient> ingredients, ResourceLocation recipe) {
        List<Ingredient> checked = new ArrayList<>();
        for (var ing : ingredients) {
            if (checked.contains(ing)) continue;
            if (ing instanceof IngredientRandomizable randomizable) {
                checked.add(ing);
                randomizable.randomizer$randomizeInputs(value -> {
                    ResourceLocation ingredient;
                    Ingredient.Value random;
                    if (value instanceof Ingredient.ItemValue itemValue) {
                        ItemStack stack = INSTANCE.getStackFor(itemValue.item());
                        ingredient = ITEM_REGISTRY.getKey(stack.getItem());
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
        MODIFIED.computeIfAbsent(ingredient, key -> new ArrayList<>())
                .add(recipe);
    }

    public static void buildAdvancements(ImmutableMap.Builder<ResourceLocation, AdvancementHolder> map) {
        for (var ing : MODIFIED.keySet()) {
            Item[] changedItems;
            Optional<Item> item = ITEM_REGISTRY.getOptional(ing);
            var tag = ITEM_REGISTRY.getTagNames()
                    .filter(key -> key.location().equals(ing))
                    .findFirst();

            if (item.isPresent()) {
                changedItems = new Item[]{ item.get() };
            } else if (tag.isPresent()) {
                changedItems = ITEM_REGISTRY.getTag(tag.get()).orElseThrow()
                        .stream().map(Holder::get).toArray(Item[]::new);
            } else {
                RandomizerCore.LOGGER.warn("{} is not a valid item or tag!", ing);
                continue;
            }

            Advancement.Builder builder = new Advancement.Builder();
            for (ResourceLocation recipe : MODIFIED.get(ing)) {
                builder.rewards(AdvancementRewards.Builder.recipe(recipe));
            }
            builder.addCriterion("has_item", InventoryChangeTrigger.TriggerInstance.hasItems(changedItems));
            String path = "%s-%s_gives_recipes".formatted(ing.getNamespace(), ing.getPath());
            AdvancementHolder toAdd = builder.build(RandomizerUtil.location(path));
            map.put(toAdd.id(), toAdd);
        }
    }
}
