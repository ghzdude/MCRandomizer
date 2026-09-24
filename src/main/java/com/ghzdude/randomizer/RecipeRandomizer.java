package com.ghzdude.randomizer;

import com.ghzdude.randomizer.api.AdvancementModify;
import com.ghzdude.randomizer.api.Randomizable;
import com.ghzdude.randomizer.util.KeyFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Predicate;

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

    /// item ingredients -> recipes
    private static final Map<Either<TagKey<Item>, ResourceKey<Item>>, List<ResourceKey<Recipe<?>>>> INGREDIENTS_TO_RECIPES = new Object2ObjectOpenHashMap<>();

    /// recipe id -> recipe inputs
    private static final Map<ResourceKey<Recipe<?>>, Set<Either<TagKey<Item>, ResourceKey<Item>>>> INPUT_MAP = new Object2ObjectOpenHashMap<>();

    /// recipe id -> result item
    private static final Map<ResourceKey<Recipe<?>>, ResourceKey<Item>> RESULT_MAP = new Object2ObjectOpenHashMap<>();

    /// item output -> recipe
    private static final Map<Identifier, List<ResourceKey<Recipe<?>>>> OUTPUT_MAP = new Object2ObjectOpenHashMap<>();

    public static final ResourceManagerReloadListener LISTENER = _ -> reload();

    private static final Logger LOGGER = LogUtils.getLogger();

    private static RandomizationMapData INSTANCE = null;
    private static Registry<Item> ITEM_REGISTRY;
    private static Runnable onReload = () -> {};
    private static boolean init = false;

    static void init(MinecraftServer server) {
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

    private static void reload() {
        if (init) onReload.run();
    }

    public static void dispose() {
        INGREDIENTS_TO_RECIPES.clear();
        INPUT_MAP.clear();
        OUTPUT_MAP.clear();
        RESULT_MAP.clear();
        init = false;
    }

    public static RandomizationMapData getMapData() {
        if (RandomizerConfig.randomizeRecipes && INSTANCE != null) {
            return INSTANCE;
        }
        return RandomizationMapData.VANILLA;
    }

    public static Map<Either<TagKey<Item>, ResourceKey<Item>>, List<ResourceKey<Recipe<?>>>> getIngredientsToRecipes() {
        return Collections.unmodifiableMap(INGREDIENTS_TO_RECIPES);
    }

    public static Collection<ResourceKey<Recipe<?>>> getRecipesForItem(Item item) {
        return getRecipesFor(ITEM_REGISTRY.getKey(item));
    }

    public static Collection<ResourceKey<Recipe<?>>> getRecipesForTag(TagKey<Item> tagKey) {
        return getRecipesFor(tagKey.location());
    }

    public static Collection<ResourceKey<Recipe<?>>> getRecipesFor(Identifier location) {
        return OUTPUT_MAP.getOrDefault(location, Collections.emptyList());
    }

    public static Collection<Either<TagKey<Item>, ResourceKey<Item>>> getIngredients(ResourceKey<Recipe<?>> recipe) {
        return INPUT_MAP.getOrDefault(recipe, Collections.emptySet());
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
                Optional<RecipeHolder<Recipe<?>>> randomizedRecipe = randomizeRecipe(recipeHolder.id(), recipeHolder.value(), ops);
                //noinspection unchecked
                randomized.add(randomizedRecipe.orElse((RecipeHolder<Recipe<?>>) recipeHolder));
            }
            activeRecipe = null;
            return RecipeMap.create(randomized);
        }).orElse(original);
    }

    private static ResourceKey<Recipe<?>> activeRecipe;

    private static Optional<RecipeHolder<Recipe<?>>> randomizeRecipe(ResourceKey<Recipe<?>> recipeId, Recipe<?> recipe, DynamicOps<JsonElement> ops) {
        activeRecipe = recipeId;
        if (RandomizerConfig.enableDebug) {
            LOGGER.info("Randomizing \"{}\"!", activeRecipe);
        }
        return Recipe.CODEC.encodeStart(ops, recipe)
                .map(JsonElement::getAsJsonObject)
                .ifError(e -> error(e.message()))
                // handling recipes in this way means tagkeys are expanded into items
                .map(r -> handleRecipe(r, ops))
                // back to recipe object
                .flatMap(object -> Recipe.CODEC.decode(ops, object).ifError(e -> error(e.message())))
                .map(Pair::getFirst)
                .map(r -> new RecipeHolder<Recipe<?>>(activeRecipe, r))
                .result();

    }

    private static void error(String message) {
        LOGGER.error("failed to randomize: {}", activeRecipe);
        LOGGER.error(message);
    }

    private static JsonObject handleRecipe(JsonObject recipe, DynamicOps<JsonElement> ops) {
        if (RandomizerConfig.randomizeRecipeInputs) {
            switch (RecipeType.fromRecipe(recipe)) {
                case CRAFTING_SHAPED -> {
                    JsonObject inputs = recipe.getAsJsonObject("key");
                    Set.copyOf(inputs.keySet()).forEach(key -> inputs.add(key, randomizeInput(inputs.get(key))));
                }
                case CRAFTING_SHAPELESS -> {
                    JsonArray inputs = recipe.getAsJsonArray("ingredients");

                    recipe.add("ingredients", inputs.asList().stream()
                            .map(RecipeRandomizer::randomizeInput)
                            .collect(JsonArray::new, JsonArray::add, JsonArray::addAll));
                }
                case SINGLE -> modifyOutput(recipe, "ingredient");
                case CRAFTING_TRANSMUTE -> modifyOutput(recipe, "input");
                case SMITHING_TRIM, SMITHING_TRANSFORM -> {
                    modifyOutput(recipe, "template");
                    modifyOutput(recipe, "base");
                    modifyOutput(recipe, "addition");
                }
                default -> {
                    if (RandomizerConfig.enableDebug) {
                        LOGGER.info("unhandled recipe object: {}", recipe);
                    }
                }
            }
        }

        if (recipe.has("result")) {
            JsonObject result = recipe.getAsJsonObject("result");
            ItemStack.CODEC.decode(ops, result)
                    .ifError(e -> LOGGER.info("failed to decode \"{}\": {}", result, e.message()))
                    .result()
                    .map(Pair::getFirst)
                    .map(vanilla -> {
                        if (vanilla.is(Items.ENDER_EYE) && RandomizerConfig.ensureCompletability)
                            return vanilla;

                        ItemStack stack = getMapData().getStackFor(vanilla);
                        ITEM_REGISTRY.getResourceKey(stack.getItem())
                                .ifPresent(resourceKey -> RESULT_MAP.put(activeRecipe, resourceKey));
                        return stack;
                    })
                    .flatMap(stack -> ItemStack.CODEC.encodeStart(ops, stack)
                            .ifError(e -> LOGGER.info("failed to encode \"{}\"\n{}", stack, e.message()))
                            .result())
                    .ifPresent(element -> recipe.add("result", element));
        }

        return recipe;
    }

    private static void modifyOutput(JsonObject recipe, String key) {
        recipe.add(key, randomizeInput(recipe.get(key)));
    }

    private static JsonElement randomizeInput(JsonElement output) {
        if (output.isJsonArray()) {
            // list of outputs
            JsonArray inner = new JsonArray();
            for (JsonElement jsonElement : output.getAsJsonArray()) {
                inner.add(randomizeInput(jsonElement));
            }
            return inner;
        } else {
            // single output
            String vanilla = output.getAsString();
            ItemType type = ItemType.getType(vanilla);
            Identifier location = type.parse(vanilla);
            Set<Either<TagKey<Item>, ResourceKey<Item>>> inputs = INPUT_MAP.computeIfAbsent(activeRecipe, k -> new ObjectOpenHashSet<>(9));
            if (type.isItem()) {
                Either<TagKey<Item>, ResourceKey<Item>> item = Either.right(KeyFactory.item(location));
                inputs.add(item);
                addToMap(activeRecipe, item);
            } else {
                Either<TagKey<Item>, ResourceKey<Item>> tag = Either.left(KeyFactory.itemTag(location));
                inputs.add(tag);
                addToMap(activeRecipe, tag);
            }
            return type.toJson(getMapData(), location);
        }
    }

    public static void addToMap(ResourceKey<Recipe<?>> recipe, Either<TagKey<Item>, ResourceKey<Item>> ingredient) {
        INGREDIENTS_TO_RECIPES.computeIfAbsent(ingredient, key -> new ArrayList<>())
                .add(recipe);
    }

    public static Set<ResourceKey<Recipe<?>>> getKnownRecipes() {
        return RESULT_MAP.keySet();
    }

    public static ResourceKey<Item> getResultFor(ResourceKey<Recipe<?>> recipe) {
        return RESULT_MAP.get(recipe);
    }

    public enum ItemType {
        ITEM, TAG;

        public static ItemType getType(String output) {
            return output.startsWith("#") ? TAG : ITEM;
        }

        public Identifier parse(String id) {
            return Identifier.parse(isItem() ? id : id.substring(1));
        }

        public JsonElement toJson(RandomizationMapData mapData, Identifier location) {
            Identifier random = isItem() ? mapData.getItemFor(location) : mapData.getTagKeyFor(location);
            return new JsonPrimitive(format(random));
        }

        public String format(Identifier id) {
            return isItem() ? id.toString() : String.format("#%s", id);
        }

        private boolean isItem() {
            return this == ITEM;
        }
    }

    public enum RecipeType implements Predicate<Identifier> {
        UNKNOWN(RandomizerCore.MODID, "unknown"),
        CRAFTING_TRANSMUTE("crafting_transmute"),
        SMITHING_TRIM("smithing_trim"),
        SMITHING_TRANSFORM("smithing_transform"),
        SINGLE("ingredient"),
        CRAFTING_SHAPED("crafting_shaped"),
        CRAFTING_SHAPELESS("crafting_shapeless");

        private static final RecipeType[] VALUES = RecipeType.values();
        private final Identifier name;

        RecipeType(String name) {
            this.name = Identifier.withDefaultNamespace(name);
        }

        RecipeType(String namespace, String path) {
            this.name = Identifier.fromNamespaceAndPath(namespace, path);
        }

        public static RecipeType fromRecipe(JsonObject recipe) {
            if (recipe.has("ingredient")) return SINGLE;
            Identifier id = Identifier.tryParse(recipe.get("type").getAsString());
            if (id != null) for (RecipeType type : VALUES) {
                if (type.test(id)) return type;
            }
            return UNKNOWN;
        }

        @Override
        public boolean test(Identifier identifier) {
            return identifier.equals(this.name);
        }
    }
}
