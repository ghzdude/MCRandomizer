package com.ghzdude.randomizer;

import com.ghzdude.randomizer.api.AdvancementModify;
import com.ghzdude.randomizer.api.Randomizable;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.Holder;
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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
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
    private static final Map<ResourceLocation, Set<JsonElement>> CACHED_RECIPES = new Object2ObjectOpenHashMap<>();

    // recipe id -> result item
    private static final Map<ResourceLocation, ResourceLocation> RESULT_MAP = new Object2ObjectOpenHashMap<>();

    // item output -> recipe
    public static final Map<ResourceLocation, List<ResourceLocation>> OUTPUT_MAP = new Object2ObjectOpenHashMap<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    private static RandomizationMapData INSTANCE = null;
    private static Registry<Item> ITEM_REGISTRY;
    private static Runnable onReload = () -> {};
    private static boolean init = false;

    public static void init(MinecraftServer server) {
        if (RandomizerConfig.randomizeRecipes) {
            if (init) return;

            ITEM_REGISTRY = server.registryAccess().lookupOrThrow(Registries.ITEM);
            INSTANCE = RandomizationMapData.get(server, "recipes");

            onReload = () -> {
                RecipeManager manager = server.getRecipeManager();
                if (!(manager instanceof Randomizable randomizable))
                    return;

                if (RandomizerConfig.randomizeRecipes) {
                    LOGGER.warn("Recipe Randomizer Running!");
                    randomizable.randomizer$randomize();
                    manager.finalizeRecipeLoading(server.getWorldData().enabledFeatures());

                    if (RandomizerConfig.randomizeRecipeInputs)
                        setAdvancements(server.getAdvancements());
                }
            };
            onReload.run();

            init = true;
        }
    }

    public static void reload() {
        if (init) onReload.run();
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

    public static Set<JsonElement> getIngredients(ResourceLocation recipe) {
        return CACHED_RECIPES.getOrDefault(recipe, Collections.emptySet());
    }

    public static void setAdvancements(ServerAdvancementManager manager) {
        if (manager instanceof AdvancementModify modify) {
            LOGGER.warn("Modifying advancements!");
            modify.randomizer$randomizeRecipeAdvancements();
        }
    }

    public static RecipeMap randomizeRecipeMap(RecipeMap original) {
        if (!RandomizerConfig.randomizeRecipes) return original;
        return RandomizerCore.getOps().map(ops -> {
            List<RecipeHolder<?>> randomized = new ArrayList<>(original.values().size());
            for (RecipeHolder<?> recipeHolder : original.values()) {
                //noinspection unchecked
                randomized.add(randomizeRecipe((RecipeHolder<Recipe<?>>) recipeHolder, ops));
            }
            activeRecipe = null;
            return RecipeMap.create(randomized);
        }).orElse(original);
    }

    private static ResourceLocation activeRecipe;

    private static RecipeHolder<Recipe<?>> randomizeRecipe(RecipeHolder<Recipe<?>> recipeHolder, DynamicOps<JsonElement> ops) {
        DataResult<JsonElement> encoded = Recipe.CODEC.encodeStart(ops, recipeHolder.value());
        activeRecipe = recipeHolder.id().location();
        return encoded.map(JsonElement::getAsJsonObject)
                .ifError(e -> error(recipeHolder, e.message()))
                // handling recipes in this way means tagkeys are expanded into items
                .map(recipe -> handleRecipe(recipe, ops))
                .map(object -> Recipe.CODEC.decode(ops, object)
                        .ifError(e -> error(recipeHolder, e.message())))
                .result()
                .filter(DataResult::isSuccess).map(DataResult::result)
                .filter(Optional::isPresent).map(Optional::get)
                .map(Pair::getFirst)
                .map(r -> new RecipeHolder<Recipe<?>>(recipeHolder.id(), r))
                .orElse(recipeHolder);

    }

    private static void error(RecipeHolder<?> recipeHolder, String message) {
        LOGGER.debug("failed to randomize: {}", recipeHolder.id());
        LOGGER.debug(message);
    }

    private static JsonObject handleRecipe(JsonObject recipe, DynamicOps<JsonElement> ops) {
        if (RandomizerConfig.randomizeRecipeInputs) {
            if (isType(recipe, "minecraft:crafting_shaped")) {
                JsonObject inputs = recipe.getAsJsonObject("key");
                Set.copyOf(inputs.keySet()).forEach(key -> inputs.add(key, randomizeOutput(inputs.get(key))));
            } else if (isType(recipe, "minecraft:crafting_shapeless")) {
                JsonArray inputs = recipe.getAsJsonArray("ingredients");
                JsonArray randomized = new JsonArray();

                inputs.asList().stream()
                        .map(RecipeRandomizer::randomizeOutput)
                        .forEach(randomized::add);

                recipe.add("ingredients", randomized);
            } else if (recipe.has("ingredient")) {
                modifyOutput(recipe, "ingredient");
            } else if (isType(recipe, "minecraft:crafting_transmute")) {
                modifyOutput(recipe, "input");
            } else if (isType(recipe, "minecraft:smithing_trim") || isType(recipe, "minecraft:smithing_transform")) {
                modifyOutput(recipe, "template");
                modifyOutput(recipe, "base");
                modifyOutput(recipe, "addition");
            } else {
                LOGGER.debug("unhandled object: {}", recipe);
            }
        }

        if (recipe.has("result")) {
            JsonObject result = recipe.getAsJsonObject("result");
            ItemStack.CODEC.decode(ops, result)
                    .ifError(e -> LOGGER.debug("failed to decode \"{}\"\n{}", result, e.message()))
                    .result().map(Pair::getFirst)
                    .map(vanilla -> {
                        if (vanilla.is(Items.ENDER_EYE) && RandomizerConfig.ensureCompletability)
                            return vanilla;

                        ItemStack stack = getMapData().getStackFor(vanilla);
                        RESULT_MAP.put(activeRecipe, ITEM_REGISTRY.getKey(stack.getItem()));
                        return stack;
                    })
                    .flatMap(stack -> ItemStack.CODEC.encodeStart(ops, stack)
                            .ifError(e -> LOGGER.debug("failed to encode \"{}\"\n{}", stack, e.message()))
                            .result())
                    .ifPresent(element -> recipe.add("result", element));
        }

        return recipe;
    }

    private static void modifyOutput(JsonObject recipe, String key) {
        recipe.add(key, randomizeOutput(recipe.get(key)));
    }

    private static JsonElement randomizeOutput(JsonElement output) {
        CACHED_RECIPES.computeIfAbsent(activeRecipe, k -> new ObjectOpenHashSet<>(9))
                .add(output);
        if (output.isJsonArray()) {
            JsonArray inner = new JsonArray();
            output.getAsJsonArray().asList().stream()
                    .map(JsonElement::getAsString)
                    .map(ResourceLocation::tryParse)
                    .map(getMapData()::getItemFor)
                    .map(loc -> {
                        addToMap(activeRecipe, loc);
                        return loc.toString();
                    })
                    .forEach(inner::add);
            return inner;
        } else {
            String vanilla = output.getAsString();
            ResourceLocation location;
            if (vanilla.startsWith("#")) {
                location = ResourceLocation.parse(vanilla.substring(1));
                addToMap(activeRecipe, location);
                return new JsonPrimitive("#" + getMapData().getTagKeyFor(location).toString());
            } else {
                location = ResourceLocation.parse(vanilla);
                addToMap(activeRecipe, location);
                return new JsonPrimitive(getMapData().getItemFor(location).toString());
            }
        }
    }

    private static boolean isType(JsonObject recipe, String type) {
        return recipe.get("type").getAsString().equals(type);
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
        return RESULT_MAP.keySet();
    }

    public static ResourceLocation getResultFor(ResourceLocation recipe) {
        return RESULT_MAP.get(recipe);
    }
}
