package com.ghzdude.randomizer;

import com.ghzdude.randomizer.api.AdvancementModify;
import com.ghzdude.randomizer.api.IngredientRandomizable;
import com.ghzdude.randomizer.api.OutputSetter;
import com.ghzdude.randomizer.api.Randomizable;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
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
import net.minecraft.world.item.crafting.*;
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
    private static boolean init = false;

    public static void init(MinecraftServer server) {
        if (RandomizerConfig.randomizeRecipes) {
            if (init) return;

            ITEM_REGISTRY = server.registryAccess().lookupOrThrow(Registries.ITEM);
            INSTANCE = RandomizationMapData.get(server, "recipes");

            if (server.getRecipeManager() instanceof Randomizable randomizable) {
                LOGGER.warn("Recipe Randomizer Running!");
                randomizable.randomizer$randomize();
            }

            setAdvancements(server.getAdvancements());
            init = true;
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
        if (holder != null && holder.value() instanceof OutputSetter setter) {
            return setter.randomizer$getIngredients();
        }
        return Collections.emptyList();
    }

    public static void setAdvancements(ServerAdvancementManager manager) {
        if (manager instanceof AdvancementModify modify) {
            modify.randomizer$randomizeRecipeAdvancements();
        }
    }

    public static void randomizeRecipes(RecipeManager manager, HolderLookup.Provider access) {
        if (false)
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            CACHED_RECIPES.put(holder.id().location(), holder);
            Recipe<?> recipe = holder.value();
            if (recipe.isSpecial()) continue;
            DataResult<JsonElement> encoded = Recipe.CODEC.encodeStart(JsonOps.INSTANCE, recipe);
            encoded.map(JsonElement::getAsJsonObject).ifSuccess(RecipeRandomizer::handleRecipe);
        }
    }

    public static RecipeMap randomizeRecipeMap(RecipeMap original) {
        if (!RandomizerConfig.randomizeRecipes) return original;
        List<RecipeHolder<?>> randomized = new ArrayList<>(original.values().size());
        for (RecipeHolder<?> recipeHolder : original.values()) {
            randomized.add(randomizeRecipe(recipeHolder));
        }
        return RecipeMap.create(randomized);
    }

    private static RecipeHolder<?> randomizeRecipe(RecipeHolder<?> recipeHolder) {
        DataResult<JsonElement> encoded = Recipe.CODEC.encodeStart(JsonOps.INSTANCE, recipeHolder.value());
        Optional<RecipeHolder<?>> optional = encoded.map(JsonElement::getAsJsonObject)
                .map(RecipeRandomizer::handleRecipe)
                .map(object -> Recipe.CODEC.decode(JsonOps.INSTANCE, object))
                .result()
                .filter(DataResult::isSuccess)
                .map(DataResult::result)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Pair::getFirst)
                .map(r -> new RecipeHolder<>(recipeHolder.id(), r));
        return optional.isPresent() ? optional.get() : recipeHolder;

    }

    private static JsonObject handleRecipe(JsonObject recipe) {
        if (RandomizerConfig.randomizeRecipeInputs) {
            if (isType(recipe, "minecraft:crafting_shaped")) {
                JsonObject inputs = recipe.getAsJsonObject("key");
                for (String key : inputs.keySet()) {
                    ResourceLocation input = ResourceLocation.parse(inputs.get(key).getAsString());
                    Optional<Holder.Reference<Item>> itemReference = ITEM_REGISTRY.get(input);
                    if (itemReference.isEmpty()) {
                        continue;
                    }
                    Item stackFor = INSTANCE.getItemFor(itemReference.get().get());
                    inputs.addProperty(key, Objects.requireNonNull(ITEM_REGISTRY.getKey(stackFor)).toString());
                }
            } else if (isType(recipe, "minecraft:crafting_shapeless")) {
                JsonArray inputs = recipe.getAsJsonArray("ingredients");
                JsonArray randomized = new JsonArray();

                inputs.asList().stream()
                        .map(element -> ResourceLocation.parse(element.getAsString()))
                        .map(ITEM_REGISTRY::get)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(Holder::get)
                        .map(INSTANCE::getItemFor)
                        .map(ITEM_REGISTRY::getKey)
                        .filter(Objects::nonNull)
                        .map(ResourceLocation::toString)
                        .forEach(randomized::add);

                recipe.add("ingredients", randomized);
            } else {
                LOGGER.debug("unhandled object: {}", recipe);
            }
        }

        JsonObject result = recipe.getAsJsonObject("result");
        ItemStack.CODEC.decode(JsonOps.INSTANCE, result)
                .map(Pair::getFirst)
                .result()
                .map(INSTANCE::getStackFor)
                .map(stack -> ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, stack))
                .map(DataResult::result)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .ifPresent(element -> recipe.add("result", element));

        return recipe;
    }

    private static boolean isType(JsonObject recipe, String type) {
        return recipe.get("type").getAsString().equals(type);
    }

    private static void modifyRecipeInputs(List<Ingredient> ingredients, ResourceLocation recipe) {
        List<Ingredient> checked = new ArrayList<>();
        for (var ing : ingredients) {
            if (checked.contains(ing)) continue;
            if (ing instanceof IngredientRandomizable randomizable) {
                checked.add(ing);
                randomizable.randomizer$randomizeInputs(holders -> {
                    Optional<TagKey<Item>> tagKey = holders.unwrapKey();
                    if (tagKey.isPresent()) {
                        // we are a tag key
                        TagKey<Item> key = tagKey.get();
                        key = INSTANCE.getTagKeyFor(key);
                        addToMap(recipe, key.location());
                        return ITEM_REGISTRY.get(key).orElseThrow();
                    }
                    // this is either one item or a set of items
                    Optional<ImmutableList<Holder<Item>>> right = holders.unwrap().mapRight(list -> {
                        ImmutableList.Builder<Holder<Item>> builder = new ImmutableList.Builder<>();
                        list.forEach(itemHolder -> {
                            Item item = INSTANCE.getItemFor(itemHolder.get());
                            addToMap(recipe, Objects.requireNonNull(ITEM_REGISTRY.getKey(item)));
                            builder.add(ITEM_REGISTRY.wrapAsHolder(item));
                        });
                        return builder.build();
                    }).right();

                    if (right.isPresent()) {
                        return HolderSet.direct(right.get());
                    } else {
                        return holders; // do not randomize
                    }
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
            AdvancementRewards.Builder rewards = new AdvancementRewards.Builder();
            for (ResourceLocation recipe : MODIFIED.get(ing)) {
                rewards.addRecipe(ResourceKey.create(Registries.RECIPE, recipe));
            }
            builder.rewards(rewards);
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
