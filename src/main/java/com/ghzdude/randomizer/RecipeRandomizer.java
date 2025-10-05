package com.ghzdude.randomizer;

import com.ghzdude.randomizer.api.AdvancementModify;
import com.ghzdude.randomizer.api.IngredientRandomizable;
import com.ghzdude.randomizer.api.OutputSetter;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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
import org.slf4j.Logger;

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

    // item ingredients -> recipes
    private static final Map<ResourceLocation, List<ResourceLocation>> MODIFIED = new Object2ObjectOpenHashMap<>();

    // recipe id -> recipe
    private static final Map<ResourceLocation, RecipeHolder<?>> CACHED_RECIPES = new Object2ObjectOpenHashMap<>();

    // recipe id -> result item
    private static final Map<ResourceLocation, ResourceLocation> RESULT_MAP = new Object2ObjectOpenHashMap<>();

    // item output -> recipe
    public static final Map<ResourceLocation, List<ResourceLocation>> OUTPUT_MAP = new Object2ObjectOpenHashMap<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    private static RandomizationMapData INSTANCE = null;
    private static Registry<Item> ITEM_REGISTRY;

    // todo look into RecipesUpdatedEvent
    public static void init(MinecraftServer server) {
        if (RandomizerConfig.randomizeRecipes) {
            ITEM_REGISTRY = server.registryAccess().lookupOrThrow(Registries.ITEM);
            INSTANCE = RandomizationMapData.get(server, "recipes");

            LOGGER.warn("Recipe Randomizer Running!");
            randomizeRecipes(server.getRecipeManager(), server.registryAccess());

            setAdvancements(server.getAdvancements());
        }
    }

    public static void dispose() {
        MODIFIED.clear();
        CACHED_RECIPES.clear();
        OUTPUT_MAP.clear();
        RESULT_MAP.clear();
    }

    public static RandomizationMapData getMapData() {
        if (RandomizerConfig.randomizeRecipes && INSTANCE != null) {
            return INSTANCE;
        }
        return RandomizationMapData.VANILLA;
    }

    public static List<ResourceLocation> getRecipesForItem(Item item) {
        return getRecipesFor(ITEM_REGISTRY.getKey(item));
    }

    public static List<ResourceLocation> getRecipesForTag(TagKey<Item> tagKey) {
        return getRecipesFor(tagKey.location());
    }

    public static List<ResourceLocation> getRecipesFor(ResourceLocation location) {
        return OUTPUT_MAP.getOrDefault(location, Collections.emptyList());
    }

    public static List<Ingredient> getIngredients(ResourceLocation loc) {
        RecipeHolder<?> holder = CACHED_RECIPES.get(loc);
//        if (holder != null) {
//            return holder.value().getIngredients();
//        }
        return Collections.emptyList();
    }

    public static void setAdvancements(ServerAdvancementManager manager) {
        if (manager instanceof AdvancementModify modify) {
            modify.randomizer$randomizeRecipeAdvancements();
        }
    }

    public static void randomizeRecipes(RecipeManager manager, HolderLookup.Provider access) {
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            CACHED_RECIPES.put(holder.id().location(), holder);
            Recipe<?> recipe = holder.value();
            if (recipe.isSpecial()) continue;
            DataResult<JsonElement> encoded = Recipe.CODEC.encodeStart(JsonOps.INSTANCE, recipe);
            encoded.map(JsonElement::getAsJsonObject).ifSuccess(object -> handleRecipe(object, recipe, holder.id()));
            if (true) continue;

            ItemStack result = ItemStack.EMPTY;
            ItemStack newResult = INSTANCE.getStackFor(result);

            if (result.isEmpty() || newResult.isEmpty()) {
                LOGGER.warn("Recipe '{}' result is empty!", holder.id());
                continue;
            }

            // set the new result back to the ender eye
            if (RandomizerConfig.ensureCompletability && result.is(Items.ENDER_EYE)) {
                newResult = result;
            }

//            modifyRecipeOutputs(recipe);
//            RESULT_MAP.put(holder.id().location(), ITEM_REGISTRY.getKey(newResult.getItem()));
//            OUTPUT_MAP.computeIfAbsent(ITEM_REGISTRY.getKey(newResult.getItem()), k -> new ArrayList<>())
//                    .add(holder.id().location());

            // if inputs are not to be randomized, move on to the next recipe
//            if (RandomizerConfig.randomizeRecipeInputs) {
//                modifyRecipeInputs(recipe.getIngredients(), holder.id());
//            }
        }
    }

    private static void handleRecipe(JsonObject object, Recipe<?> recipe, ResourceKey<Recipe<?>> id) {
        if (!(recipe instanceof OutputSetter setter)) {
            LOGGER.debug("Recipe \"{}\" cannot be randomized!", id);
            return;
        }

        if (!RandomizerConfig.ensureCompletability || !setter.randomizer$getResult().is(Items.ENDER_EYE)) {
            setter.randomizer$randomize(getMapData()::getStackFor);
        }
        ItemStack newResult = setter.randomizer$getResult();
        RESULT_MAP.put(id.location(), ITEM_REGISTRY.getKey(newResult.getItem()));
        OUTPUT_MAP.computeIfAbsent(ITEM_REGISTRY.getKey(newResult.getItem()), k -> new ArrayList<>())
                .add(id.location());
        // if inputs are not to be randomized, move on to the next recipe
        if (RandomizerConfig.randomizeRecipeInputs) {
            modifyRecipeInputs(setter.randomizer$getIngredients(), id.location());
        }
    }

    private static void modifyRecipeOutputs(Recipe<?> recipe) {
        if (recipe instanceof OutputSetter setter) {
            setter.randomizer$randomize(getMapData()::getStackFor);
        }
    }

    private static void modifyRecipeInputs(List<Ingredient> ingredients, ResourceLocation recipe) {
        List<Ingredient> checked = new ArrayList<>();
        for (var ing : ingredients) {
            if (checked.contains(ing)) continue;
            if (ing instanceof IngredientRandomizable randomizable) {
                checked.add(ing);
//                randomizable.randomizer$randomizeInputs(value -> {
//                    ResourceLocation ingredient;
//                    Ingredient.Value random;
//                    if (value instanceof Ingredient.ItemValue(ItemStack item)) {
//                        ItemStack stack = INSTANCE.getStackFor(item);
//                        ingredient = ITEM_REGISTRY.getKey(stack.getItem());
//                        if (ingredient == null) return value;
//                        random = new Ingredient.ItemValue(stack);
//                    } else {
//                        Ingredient.TagValue tagValue = (Ingredient.TagValue) value;
//                        TagKey<Item> key = INSTANCE.getTagKeyFor(tagValue.tag());
//                        ingredient = key.location();
//                        random = new Ingredient.TagValue(key);
//                    }
//                    addToMap(recipe, ingredient);
//                    return random;
//                });
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
            var tag = ITEM_REGISTRY.getTags()
                    .map(HolderSet.Named::key)
                    .filter(key -> key.location().equals(ing))
                    .findFirst();

            if (item.isPresent()) {
                changedItems = new Item[]{ item.get() };
            } else if (tag.isPresent()) {
                changedItems = ITEM_REGISTRY.get(tag.get()).orElseThrow()
                        .stream().map(Holder::get).toArray(Item[]::new);
            } else {
                LOGGER.warn("{} is not a valid item or tag!", ing);
                continue;
            }

            Advancement.Builder builder = new Advancement.Builder();
//            for (ResourceLocation recipe : MODIFIED.get(ing)) {
//                builder.rewards(AdvancementRewards.Builder.recipe(recipe));
//            }
            builder.addCriterion("has_item", InventoryChangeTrigger.TriggerInstance.hasItems(changedItems));
            String path = "%s-%s_gives_recipes".formatted(ing.getNamespace(), ing.getPath());
            AdvancementHolder toAdd = builder.build(RandomizerUtil.location(path));
            map.put(toAdd.id(), toAdd);
        }
    }

    public static Set<ResourceLocation> getKnownRecipes() {
        return CACHED_RECIPES.keySet();
    }

    public static ResourceLocation getResultFor(ResourceLocation recipe) {
        return RESULT_MAP.get(recipe);
    }
}
