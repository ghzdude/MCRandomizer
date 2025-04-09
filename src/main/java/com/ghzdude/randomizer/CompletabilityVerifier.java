package com.ghzdude.randomizer;

import com.ghzdude.randomizer.compat.jei.BlockDropRecipe;
import com.ghzdude.randomizer.loot.LootRandomizer;
import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
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

import static net.minecraft.world.item.Items.*;

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
    private static final Object2ObjectMap<ResourceLocation, Set<ResourceLocation>> INGREDIENT_MAP = new Object2ObjectOpenHashMap<>();

    /**
     * maps a recipe to its associated map data for getting the original vanilla item
     */
    private static final Object2ObjectMap<ResourceLocation, RandomizationMapData> DATA_MAP = new Object2ObjectOpenHashMap<>();

    public static ResourceLocation ENDER_EYE;
    public static ResourceLocation OBSIDIAN;

    // the set of recipes that can craft the ender eye
    static Deque<ResourceLocation> recipePath = new ArrayDeque<>();

    static boolean requiresNether = false;
    static boolean isCompletable = false;

    private static Registry<Item> REGISTRY;

    // overworld
    private static final List<Item> OVERWORLD = List.of(
            // surface
            GRASS_BLOCK,
            DIRT,

            // underground
            STONE,
            ANDESITE,
            GRANITE,
            DIORITE,
            AMETHYST_BLOCK,
            AMETHYST_SHARD,

            // woode
            ACACIA_WOOD,
            BIRCH_WOOD,
            CHERRY_WOOD,
            OAK_WOOD,
            DARK_OAK_WOOD,
            SPRUCE_WOOD,

            // raw ores
            RAW_IRON,
            RAW_GOLD,
            RAW_COPPER,
            COAL,
            DIAMOND,

            // flowers
            CORNFLOWER,
            SUNFLOWER,
            DANDELION,
            ORANGE_TULIP,
            PINK_TULIP,
            RED_TULIP,
            WHITE_TULIP,
            ROSE_BUSH
    );

    // nether
    private static final List<Item> NETHER = List.of(
            NETHERRACK,
            SOUL_SAND,
            SOUL_SOIL,
            BLACKSTONE,
            BASALT,
            QUARTZ,
            GLOWSTONE_DUST
    );

    public static void init(MinecraftServer server) {
        REGISTRY = server.registryAccess().registryOrThrow(Registries.ITEM);
        ENDER_EYE = REGISTRY.getKey(Items.ENDER_EYE);
        OBSIDIAN = REGISTRY.getKey(Items.OBSIDIAN);
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
        DATA_MAP.put(id, RecipeRandomizer.INSTANCE);
    }

    public static void addBlockDrop(BlockDropRecipe recipe, ResourceLocation id) {
        if (!RandomizerConfig.ensureCompletability) return;
        addIngredient(REGISTRY.getKey(recipe.input().getItem()), id);
        addResult(REGISTRY.getKey(recipe.output().getItem()), id);
        DATA_MAP.put(id, LootRandomizer.INSTANCE);
    }

    private static void addIngredient(ResourceLocation key, ResourceLocation recipe) {
        INGREDIENT_MAP.computeIfAbsent(recipe, k -> new HashSet<>()).add(key);
    }

    private static void addResult(ResourceLocation key, ResourceLocation recipe) {
        RESULT_MAP.computeIfAbsent(key, k -> new HashSet<>()).add(recipe);
    }

    private static RandomizationMapData getDataFor(ResourceLocation recipe) {
        return DATA_MAP.getOrDefault(recipe, RecipeRandomizer.INSTANCE);
    }

    public static void ensureCompletability() {
//        isCompletable = ensureCompletability(ENDER_EYE);

        Object2BooleanMap<ResourceLocation> map = new Object2BooleanArrayMap<>();
        Object2ObjectMap<ResourceLocation, String> strings = new Object2ObjectOpenHashMap<>();
        for (ResourceLocation ing : INGREDIENT_MAP.get(ENDER_EYE)) {
            recipePath.clear();
            map.put(ing, canCraftIngredient(ing, ENDER_EYE));
            strings.put(ing, printPath());
        }

        if (requiresNether) {
            isCompletable = ensureCompletability(OBSIDIAN);
        }
    }

    /**
     * @param ingredient the registry location of the item ingredient
     * @return true if this ingredient is obtainable from common blocks in the overworld or nether
     */
    private static boolean ensureCompletability(ResourceLocation ingredient) {
        if (!RESULT_MAP.containsKey(ingredient)) return false;
        for (ResourceLocation recipe : RESULT_MAP.get(ingredient)) {
            if (!INGREDIENT_MAP.containsKey(recipe) || recipePath.contains(recipe)) continue;
            recipePath.add(recipe);
            Set<ResourceLocation> ingredients = INGREDIENT_MAP.get(recipe);
            long i = ingredients.stream()
                    .filter(ing -> canCraftIngredient(ing, recipe))
                    .count();
            if (i == ingredients.size()) {
                return true;
            } else {
                recipePath.pollLast();
            }
        }
        return false;
    }

    private static boolean canCraftIngredient(ResourceLocation ingredient, ResourceLocation recipe) {
        Item item = REGISTRY.get(ingredient);
        Item vanilla = getDataFor(recipe).getOriginalItem(item);
        if (OVERWORLD.contains(vanilla)) {
            return true;
        } else {
            if (!requiresNether && NETHER.contains(vanilla)) {
                requiresNether = true;
            }

            return ensureCompletability(ingredient);
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
        }
        return false;
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
//                    List<ResourceLocation> recipes = RecipeRandomizer.getRecipesFor(loc);
//                    Set<ResourceLocation> recipes = RESULT_MAP.get(loc);
//                    for (ResourceLocation recipe : recipes) {
//                        recipePath.add(recipe);
//                        iterateIngredients(RecipeRandomizer.getIngredients(recipe));
//                        if (!isCompletable) recipePath.pollLast();
//                    }
//                }
//            }
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

    private static String printPath() {
        StringBuilder b = new StringBuilder();
        if (requiresNether) b.append("Requires nether access!\n");
        b.append("Recipe Path:\n");
        boolean newline = false;
        for (ResourceLocation loc : recipePath) {
            b.append(loc.toString());
            if (newline) {
                b.append('\n').append(" -> ");
            } else {
                newline = true;
            }
        }
        return b.toString();
    }
}
