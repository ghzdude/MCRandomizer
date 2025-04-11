package com.ghzdude.randomizer;

import com.ghzdude.randomizer.compat.jei.BlockDropRecipe;
import com.ghzdude.randomizer.loot.LootRandomizer;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

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
    private static final Object2ObjectMap<ResourceLocation, Int2ObjectArrayMap<Set<ResourceLocation>>> INGREDIENT_MAP = new Object2ObjectOpenHashMap<>();

    /**
     * maps a recipe to its associated map data for getting the original vanilla item
     */
    private static final Object2ObjectMap<ResourceLocation, RandomizationMapData> DATA_MAP = new Object2ObjectOpenHashMap<>();

    /**
     * maps a recipe to its result item.
     */
    private static final Object2ObjectMap<ResourceLocation, ResourceLocation> RECIPE_MAP = new Object2ObjectOpenHashMap<>();

    public static ResourceLocation ENDER_EYE;
    public static ResourceLocation OBSIDIAN;

    private static final Deque<ResourceLocation> recipePath = new ArrayDeque<>();

    private static boolean requiresNether = false;
    private static boolean isCompletable = false;

    private static Registry<Item> REGISTRY;

    // overworld
    private static final List<Item> OVERWORLD_ITEMS = List.of(
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

            // wood
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

    private static final List<ResourceLocation> OVERWORLD_LOOT = Stream.of(
            BuiltInLootTables.BURIED_TREASURE,
            BuiltInLootTables.ABANDONED_MINESHAFT,
            BuiltInLootTables.SIMPLE_DUNGEON,
            BuiltInLootTables.DESERT_PYRAMID,
            BuiltInLootTables.ANCIENT_CITY,
            BuiltInLootTables.ANCIENT_CITY_ICE_BOX
    ).map(ResourceKey::location).toList();

    // nether
    private static final List<Item> NETHER_ITEMS = List.of(
            NETHERRACK,
            SOUL_SAND,
            SOUL_SOIL,
            BLACKSTONE,
            BASALT,
            QUARTZ,
            GLOWSTONE_DUST
    );

    private static final List<ResourceLocation> NETHER_LOOT = Stream.of(
            BuiltInLootTables.BASTION_BRIDGE,
            BuiltInLootTables.BASTION_OTHER,
            BuiltInLootTables.BASTION_HOGLIN_STABLE,
            BuiltInLootTables.BASTION_TREASURE,
            BuiltInLootTables.NETHER_BRIDGE
    ).map(ResourceKey::location).toList();

    public static void init(MinecraftServer server) {
        REGISTRY = server.registryAccess().registryOrThrow(Registries.ITEM);
        RESULT_MAP.defaultReturnValue(Collections.emptySet());

        ENDER_EYE = REGISTRY.getKey(Items.ENDER_EYE);
        OBSIDIAN = REGISTRY.getKey(Items.OBSIDIAN);

        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (recipe.isSpecial()) continue;
            ItemStack result = recipe.getResultItem(server.registryAccess());

            addRecipe(recipe.getIngredients(), result, holder.id());
        }

        for (ResourceLocation key : BlockDropRecipe.getKeys()) {
            addBlockDrop(BlockDropRecipe.get(key), key);
        }
    }

    public static void addRecipe(NonNullList<Ingredient> ingredients, ItemStack result, ResourceLocation id) {
        if (!RandomizerConfig.ensureCompletability) return;

        AtomicInteger index = new AtomicInteger();
        ingredients.stream()
                .distinct()
                .map(Ingredient::getItems)
                .forEach(stacks -> {
                    int i = index.getAndIncrement();
                    for (ItemStack stack : stacks) {
                        ResourceLocation key = REGISTRY.getKey(stack.getItem());
                        addIngredient(key, i, id);
                    }
                });

        addResult(REGISTRY.getKey(result.getItem()), id);
        DATA_MAP.put(id, RecipeRandomizer.getMapData());
    }

    public static void addBlockDrop(BlockDropRecipe recipe, ResourceLocation id) {
        if (!RandomizerConfig.ensureCompletability) return;
        addIngredient(REGISTRY.getKey(recipe.input().getItem()), -1, id);
        addResult(REGISTRY.getKey(recipe.output().getItem()), id);
        DATA_MAP.put(id, LootRandomizer.getMapData());
    }

    public static void addLootTable(ResourceLocation key, ItemStack[] stacks) {
        if (!RandomizerConfig.ensureCompletability) return;
        for (ItemStack stack : stacks) {
            addIngredient(REGISTRY.getKey(stack.getItem()), -1, key);
        }
        addResult(key, key);
        DATA_MAP.put(key, LootRandomizer.getMapData());
    }

    private static void addIngredient(ResourceLocation key, int index, ResourceLocation recipe) {
        INGREDIENT_MAP.computeIfAbsent(recipe, k -> new Int2ObjectArrayMap<>())
                .computeIfAbsent(index, i -> new HashSet<>())
                .add(key);
    }

    private static void addResult(ResourceLocation key, ResourceLocation recipe) {
        RESULT_MAP.computeIfAbsent(key, k -> new HashSet<>()).add(recipe);
        RECIPE_MAP.put(recipe, key);
    }

    private static RandomizationMapData getDataFor(ResourceLocation recipe) {
        return DATA_MAP.getOrDefault(recipe, RandomizationMapData.VANILLA);
    }

    public static void ensureCompletability() {
        Object2BooleanMap<ResourceLocation> map = new Object2BooleanArrayMap<>();
        Object2ObjectMap<ResourceLocation, String> strings = new Object2ObjectOpenHashMap<>();
        Int2ObjectArrayMap<Set<ResourceLocation>> indexMap = INGREDIENT_MAP.get(ENDER_EYE);
        for (var entry : indexMap.int2ObjectEntrySet()) {
            for (ResourceLocation ing : entry.getValue()) {
                recipePath.clear();
                map.put(ing, canCraftIngredient(ing, ENDER_EYE));
                strings.put(ing, printPath());
            }
        }

        if (requiresNether && INGREDIENT_MAP.containsKey(OBSIDIAN)) {
            indexMap = INGREDIENT_MAP.get(OBSIDIAN);
            for (var entry : indexMap.int2ObjectEntrySet()) {
                for (ResourceLocation ing : entry.getValue()) {
                    recipePath.clear();
                    map.put(ing, canCraftIngredient(ing, OBSIDIAN));
                    strings.put(ing, printPath());
                }
            }
        }

        if (requiresNether) RandomizerCore.LOGGER.warn("Requires nether access!");

        int i = 0;
        for (ResourceLocation ing : strings.keySet()) {
            if (map.getBoolean(ing)) {
                RandomizerCore.LOGGER.warn("can craft \"{}\"\n{}", ing, strings.get(ing));
                i++;
            } else {
                RandomizerCore.LOGGER.warn("unable to craft \"{}\"\n{}", ing, strings.get(ing));
            }
        }

        isCompletable = i == strings.size() - 1;

        if (requiresNether && !INGREDIENT_MAP.containsKey(OBSIDIAN)) {
            isCompletable = false;
        }

        if (!isCompletable) {
            RandomizerCore.LOGGER.warn("Game is Incompletable!");
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
            Int2ObjectArrayMap<Set<ResourceLocation>> indexMap = INGREDIENT_MAP.get(recipe);
            for (Set<ResourceLocation> ingredients : indexMap.values()) {
                boolean canCraft = false;
                for (ResourceLocation ing : ingredients) {
                    if (canCraftIngredient(ing, recipe)) {
                        canCraft = true;
                        break;
                    }
                }
                if (!canCraft) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean canCraftIngredient(ResourceLocation ingredient, ResourceLocation recipe) {
        Item item = REGISTRY.get(ingredient);
        Item vanilla = getDataFor(recipe).getOriginalItem(item);
        if (OVERWORLD_ITEMS.contains(vanilla)) {
            return true;
        } else {
            if (!requiresNether && NETHER_ITEMS.contains(vanilla)) {
                requiresNether = true;
            }

            return ensureCompletability(ingredient);
        }
    }

    private static String printPath() {
        StringBuilder b = new StringBuilder();
        b.append("Recipe Path:\n");
        int i = 0;
        for (ResourceLocation loc : recipePath) {
            b.append(RECIPE_MAP.get(loc));
            b.append("{recipe=%s}".formatted(loc));
            if (i++ != recipePath.size() - 1) {
                b.append('\n').append(" -> ");
            }
        }
        return b.toString();
    }
}
