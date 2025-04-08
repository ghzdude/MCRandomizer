package com.ghzdude.randomizer;

import com.ghzdude.randomizer.api.IngredientRandomizable;
import com.ghzdude.randomizer.compat.jei.BlockDropRecipe;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.*;

/* Description
 * This is to ensure that eyes of ender are always obtainable regardless of randomization
 * Iterate through known recipes and create a path to the most common blocks found in the overworld
 * if an item from the nether is selected, then make sure that obsidian is obtainable
 */

public class CompletabilityVerifier {

    // item -> what the item makes
    private static final Object2ObjectMap<ResourceLocation, Set<ResourceLocation>> INGREDIENTS = new Object2ObjectOpenHashMap<>();

    // item -> recipe that makes this item
    private static final Object2ObjectMap<ResourceLocation, Set<ResourceLocation>> RECIPES = new Object2ObjectOpenHashMap<>();

    // ender eye recipe location
    public static final ResourceLocation ENDER_EYE = ResourceLocation.withDefaultNamespace("ender_eye");

    // the set of recipes that can craft the ender eye
    private static final Deque<ResourceLocation> RECIPE_PATH = new ArrayDeque<>(List.of(ENDER_EYE));

    static boolean requiresNether = false;
    static ResourceLocation lastValue;
    static Deque<ResourceLocation> currentPath = new ArrayDeque<>();
    static Deque<Iterator<ResourceLocation>> iteratorStack = new ArrayDeque<>();

    private static Registry<Item> REGISTRY;

    // overworld
    private static final List<Item> OVERWORLD = List.of(
            Items.GRASS_BLOCK,
            Items.DIRT,
            Items.STONE
    );

    // nether
    private static final List<Item> NETHER = List.of(
            Items.NETHERRACK,
            Items.SOUL_SAND,
            Items.SOUL_SOIL
    );

    public static void init(MinecraftServer server) {
        REGISTRY = server.registryAccess().registryOrThrow(Registries.ITEM);
    }

    public static void addRecipe(NonNullList<Ingredient> ingredients, ItemStack result, ResourceLocation id) {
        if (!RandomizerConfig.ensureCompletability) return;
        ingredients.stream()
                .distinct()
                .map(Ingredient::getItems)
                .flatMap(Arrays::stream)
                .map(ItemStack::getItem)
                .distinct()
                .map(REGISTRY::getKey)
                .forEach(key -> addPath(key, REGISTRY.getKey(result.getItem())));
    }

    public static void addBlockDrop(BlockDropRecipe recipe) {
        if (!RandomizerConfig.ensureCompletability) return;
        ResourceLocation loc = REGISTRY.getKey(recipe.input().getItem());
        addPath(loc, REGISTRY.getKey(recipe.output().getItem()));
    }

    private static void addPath(ResourceLocation loc, ResourceLocation end) {
        INGREDIENTS.computeIfAbsent(loc, k -> new ObjectArraySet<>())
                .add(end);
        RECIPES.computeIfAbsent(end, k -> new ObjectArraySet<>())
                .add(loc);
    }

    public static void addRecipe(ResourceLocation loc) {
        if (RECIPE_PATH.contains(loc)) return;
        RECIPE_PATH.push(loc);
    }

    public static void ensureCompletability() {
        currentPath.push(ENDER_EYE);
        iteratorStack.push(RecipeRandomizer.getRecipesForItem(Items.ENDER_EYE).iterator());
        Iterator<ResourceLocation> itr;

        while (!iteratorStack.isEmpty()) {
            itr = iteratorStack.peek();
            if (!itr.hasNext()) {
                iteratorStack.pop();
                currentPath.pop();
                continue;
            }

            ResourceLocation recipe = itr.next();
            List<Ingredient> ingredients = RecipeRandomizer.getIngredients(recipe);
            if (iterateIngredients(ingredients)) {
                currentPath.push(recipe);
                iteratorStack.push(RecipeRandomizer.getRecipesFor(lastValue).iterator());
            }
        }

        StringBuilder b = new StringBuilder();
        boolean newline = false;
        for (ResourceLocation loc : currentPath) {
            b.append(loc.toString());
            if (!newline) {
                newline = true;
            } else {
                b.append('\n');
            }
        }
        RandomizerCore.LOGGER.warn("Recipe Path: {}", b);
    }

    private static boolean iterateIngredients(List<Ingredient> ingredients) {
        for (Ingredient ingredient : ingredients) {
            Ingredient.Value[] values = ((IngredientRandomizable) ingredient).randomizer$getValues();
            for (Ingredient.Value value : values) {
                if (checkValue(value)) {
                    lastValue = getLocation(value);
                    return true;
                } else {

                }
            }
        }
        return false;
    }

    private static ResourceLocation getLocation(Ingredient.Value value) {
        if (value instanceof Ingredient.ItemValue(ItemStack item)) {
            return REGISTRY.getKey(item.getItem());
        } else {
            return ((Ingredient.TagValue) value).tag().location();
        }
    }

    private static boolean checkValue(Ingredient.Value value) {
        for (ItemStack item : value.getItems()) {
            if (OVERWORLD.contains(item.getItem())) {
                return true;
            } else if (NETHER.contains(item.getItem())) {
                requiresNether = true;
                return true;
            }
        }
        return false;
    }

//    private static boolean ensureCompletability(ResourceLocation primary) {
//        ResourceLocation head = primary;
//        while (true) {
//            // for each recipe that makes this item
//            for (ResourceLocation key : RECIPES.getOrDefault(head, Collections.emptySet())) {
//                if (key.equals(primary)) continue;
//
//                // is this item a common item
//                if (NETHER.contains(REGISTRY.get(key))) {
//                    return ensureCompletability(REGISTRY.getKey(Items.OBSIDIAN));
//                    // ensure obsidian is obtainable
//                } else if (OVERWORLD.contains(REGISTRY.get(key))) {
//                    // we can obtain this item in the overworld
//                    return true;
//                } else {
//                    // not a common item, find a new recipe
//                    head = INGREDIENTS.get(key).iterator().next();
//                    break;
//                }
//            }
//            // no recipe works, we need to make a new one
//            // not sure how to actually do this
//        }
//    }

//    private static void printPath() {
//
//    }
}
