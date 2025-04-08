package com.ghzdude.randomizer;

import com.ghzdude.randomizer.compat.jei.BlockDropRecipe;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
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

    /**
     * Maps an item (result) to a set of recipes that the item can make
     */
    private static final Object2ObjectMap<ResourceLocation, Set<ResourceLocation>> RESULT_MAP = new Object2ObjectOpenHashMap<>();

    /**
     * Maps a recipe to the ingredients used in it
     */
    private static final Object2ObjectOpenHashMap<ResourceLocation, Set<ResourceLocation>> INGREDIENT_MAP = new Object2ObjectOpenHashMap<>();

    // ender eye recipe location
    public static final ResourceLocation ENDER_EYE = ResourceLocation.withDefaultNamespace("ender_eye");

    // the set of recipes that can craft the ender eye
    static Deque<ResourceLocation> recipePath = new ArrayDeque<>();

    static boolean requiresNether = false;
    static boolean isCompletable = false;

    private static Registry<Item> REGISTRY;

    // overworld
    private static final List<Item> OVERWORLD = List.of(
            Items.GRASS_BLOCK,
            Items.DIRT,
            Items.STONE,
            Items.ACACIA_WOOD,
            Items.BIRCH_WOOD,
            Items.CHERRY_WOOD,
            Items.OAK_WOOD,
            Items.SPRUCE_WOOD,
            Items.ANDESITE,
            Items.GRANITE,
            Items.DIORITE
    );

    // nether
    private static final List<Item> NETHER = List.of(
            Items.NETHERRACK,
            Items.SOUL_SAND,
            Items.SOUL_SOIL,
            Items.BLACKSTONE,
            Items.BASALT
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
                .forEach(key -> addIngredient(key, id));

        addResult(REGISTRY.getKey(result.getItem()), id);
    }

    public static void addBlockDrop(BlockDropRecipe recipe, ResourceLocation id) {
        if (!RandomizerConfig.ensureCompletability) return;
        addIngredient(REGISTRY.getKey(recipe.input().getItem()), id);
        addResult(REGISTRY.getKey(recipe.output().getItem()), id);
    }

    private static void addIngredient(ResourceLocation key, ResourceLocation recipe) {
        INGREDIENT_MAP.computeIfAbsent(recipe, k -> new HashSet<>()).add(key);
    }

    private static void addResult(ResourceLocation key, ResourceLocation recipe) {
        RESULT_MAP.computeIfAbsent(key, k -> new HashSet<>()).add(recipe);
    }

    public static void ensureCompletability() {
        for (ResourceLocation recipe : RESULT_MAP.getOrDefault(ENDER_EYE, Collections.emptySet())) {
            recipePath.add(recipe);
            if (!iterateIngredients(INGREDIENT_MAP.get(recipe)))
                recipePath.pollLast();
            else {
                isCompletable = true;
                return;
            }
        }
    }

    private static boolean iterateIngredients(Set<ResourceLocation> ingredients) {
        for (ResourceLocation ingredient : ingredients) {
            Item item = REGISTRY.get(ingredient);
            Item vanilla = RecipeRandomizer.INSTANCE.getOriginalItem(item);
            if (OVERWORLD.contains(vanilla)) {
                return true;
            } else {
                if (!requiresNether && NETHER.contains(vanilla))
                    requiresNether = true;

                Set<ResourceLocation> recipes = RESULT_MAP.get(ingredient);
                for (ResourceLocation recipe : recipes) {
                    if (!INGREDIENT_MAP.containsKey(recipe) || recipePath.contains(recipe)) continue;
                    recipePath.add(recipe);
                    if (!iterateIngredients(INGREDIENT_MAP.get(recipe))) {
                        recipePath.pollLast();
                    } else {
                        return true;
                    }
                }
            }
//            Ingredient.Value[] values = ((IngredientRandomizable) ingredient).randomizer$getValues();
//            for (Ingredient.Value value : values) {
//                if (checkValue(value)) {
//                    // we can stop here
//                    printPath();
//                    isCompletable = true;
//                    return;
//                } else {
//                    // this is a potential ingredient to investigate
//                    var loc = getLocation(value);
////                    List<ResourceLocation> recipes = RecipeRandomizer.getRecipesFor(loc);
//                    Set<ResourceLocation> recipes = RESULT_MAP.get(loc);
//                    for (ResourceLocation recipe : recipes) {
//                        recipePath.add(recipe);
//                        iterateIngredients(RecipeRandomizer.getIngredients(recipe));
//                        if (!isCompletable) recipePath.pollLast();
//                    }
//                }
//            }
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
            Item vanilla = RecipeRandomizer.INSTANCE.getOriginalItem(item.getItem());
            if (OVERWORLD.contains(vanilla)) {
                return true;
            } else if (NETHER.contains(vanilla)) {
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

    private static void printPath() {
        StringBuilder b = new StringBuilder();
        boolean newline = false;
        for (ResourceLocation loc : recipePath) {
            b.append(loc.toString());
            if (!newline) {
                newline = true;
            } else {
                b.append('\n');
            }
        }
        RandomizerCore.LOGGER.warn("Recipe Path: {}", b);
    }
}
