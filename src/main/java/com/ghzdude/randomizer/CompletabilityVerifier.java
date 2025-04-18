package com.ghzdude.randomizer;

import com.ghzdude.randomizer.loot.LootRandomizer;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/* Description
 * This is to ensure that eyes of ender are always obtainable regardless of randomization
 * Iterate through known recipes and create a path to the most common blocks found in the overworld
 * if an item from the nether is selected, then make sure that obsidian is obtainable
 */

public class CompletabilityVerifier {

    /**
     * Maps an item (result) to a set of recipes that make this item
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

    private static final Object2BooleanMap<ResourceLocation> COMPLETABILITY_CACHE = new Object2BooleanOpenHashMap<>();

    public static ResourceLocation ENDER_EYE;
    public static ResourceLocation OBSIDIAN;

    private static final Deque<ResourceLocation> recipePath = new ArrayDeque<>();

    private static boolean requiresNether = false;
    private static boolean isCompletable = false;

    private static Registry<Item> REGISTRY;

    // overworld
    private static final List<Item> OVERWORLD_ITEMS = List.of(
            // surface
            Items.GRASS_BLOCK,
            Items.DIRT,

            // underground
            Items.STONE,
            Items.ANDESITE,
            Items.GRANITE,
            Items.DIORITE,
            Items.AMETHYST_BLOCK,
            Items.LARGE_AMETHYST_BUD,
            Items.MEDIUM_AMETHYST_BUD,
            Items.SMALL_AMETHYST_BUD,
            Items.AMETHYST_SHARD,
            Items.OBSIDIAN,
            Items.COBBLESTONE,
            Items.POINTED_DRIPSTONE,
            Items.DRIPSTONE_BLOCK,

            // wood
            Items.ACACIA_WOOD,
            Items.BIRCH_WOOD,
            Items.CHERRY_WOOD,
            Items.OAK_WOOD,
            Items.DARK_OAK_WOOD,
            Items.SPRUCE_WOOD,

            // raw ores
            Items.RAW_IRON,
            Items.RAW_GOLD,
            Items.RAW_COPPER,
            Items.COAL,
            Items.DIAMOND,

            // flowers
            Items.CORNFLOWER,
            Items.SUNFLOWER,
            Items.DANDELION,
            Items.ORANGE_TULIP,
            Items.PINK_TULIP,
            Items.RED_TULIP,
            Items.WHITE_TULIP,
            Items.ROSE_BUSH,
            Items.SMALL_DRIPLEAF,
            Items.BIG_DRIPLEAF
    );

    private static final List<ResourceLocation> OVERWORLD_LOOT = Stream.of(
            // chests
            BuiltInLootTables.BURIED_TREASURE,
            BuiltInLootTables.ABANDONED_MINESHAFT,
            BuiltInLootTables.SIMPLE_DUNGEON,
            BuiltInLootTables.DESERT_PYRAMID,
            BuiltInLootTables.ANCIENT_CITY,
            BuiltInLootTables.ANCIENT_CITY_ICE_BOX,
            BuiltInLootTables.STRONGHOLD_CORRIDOR,
            BuiltInLootTables.STRONGHOLD_CROSSING,
            BuiltInLootTables.STRONGHOLD_LIBRARY,
            BuiltInLootTables.SHIPWRECK_TREASURE,
            BuiltInLootTables.SHIPWRECK_MAP,
            BuiltInLootTables.SHIPWRECK_SUPPLY,
            BuiltInLootTables.SPAWN_BONUS_CHEST,
            BuiltInLootTables.VILLAGE_WEAPONSMITH,
            BuiltInLootTables.VILLAGE_TOOLSMITH,
            BuiltInLootTables.VILLAGE_ARMORER,
            BuiltInLootTables.VILLAGE_CARTOGRAPHER,
            BuiltInLootTables.VILLAGE_MASON,
            BuiltInLootTables.VILLAGE_SHEPHERD,
            BuiltInLootTables.VILLAGE_BUTCHER,
            BuiltInLootTables.VILLAGE_FLETCHER,
            BuiltInLootTables.VILLAGE_FISHER,
            BuiltInLootTables.VILLAGE_TANNERY,
            BuiltInLootTables.VILLAGE_TEMPLE,
            BuiltInLootTables.VILLAGE_DESERT_HOUSE,
            BuiltInLootTables.VILLAGE_PLAINS_HOUSE,
            BuiltInLootTables.VILLAGE_TAIGA_HOUSE,
            BuiltInLootTables.VILLAGE_SNOWY_HOUSE,
            BuiltInLootTables.RUINED_PORTAL,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_COMMON,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_RARE,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_UNIQUE,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_COMMON,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_RARE,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_UNIQUE,
            BuiltInLootTables.TRIAL_CHAMBERS_SUPPLY,
            BuiltInLootTables.TRIAL_CHAMBERS_CORRIDOR,
            BuiltInLootTables.TRIAL_CHAMBERS_INTERSECTION,
            BuiltInLootTables.TRIAL_CHAMBERS_INTERSECTION_BARREL,
            BuiltInLootTables.TRIAL_CHAMBERS_ENTRANCE,
            BuiltInLootTables.TRIAL_CHAMBERS_CORRIDOR_DISPENSER,
            BuiltInLootTables.TRIAL_CHAMBERS_CHAMBER_DISPENSER,
            BuiltInLootTables.TRIAL_CHAMBERS_WATER_DISPENSER,
            BuiltInLootTables.TRIAL_CHAMBERS_CORRIDOR_POT,
            BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER,
            BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER_RANGED,
            BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER_MELEE,

            // sheep
            BuiltInLootTables.SHEEP_BLACK,
            BuiltInLootTables.SHEEP_GRAY,
            BuiltInLootTables.SHEEP_LIGHT_GRAY,
            BuiltInLootTables.SHEEP_WHITE,
            BuiltInLootTables.SHEEP_RED,
            BuiltInLootTables.SHEEP_ORANGE,
            BuiltInLootTables.SHEEP_YELLOW,
            BuiltInLootTables.SHEEP_GREEN,
            BuiltInLootTables.SHEEP_CYAN,
            BuiltInLootTables.SHEEP_BLUE,
            BuiltInLootTables.SHEEP_PURPLE,
            BuiltInLootTables.SHEEP_BROWN,
            BuiltInLootTables.SHEEP_LIGHT_BLUE,
            BuiltInLootTables.SHEEP_LIME,
            BuiltInLootTables.SHEEP_MAGENTA,
            BuiltInLootTables.SHEEP_PINK,

            // mobs
            EntityType.COW.getDefaultLootTable(),
            EntityType.COD.getDefaultLootTable(),
            EntityType.TROPICAL_FISH.getDefaultLootTable(),
            EntityType.PUFFERFISH.getDefaultLootTable(),
            EntityType.SILVERFISH.getDefaultLootTable(),
            EntityType.ILLUSIONER.getDefaultLootTable(),
            EntityType.PIG.getDefaultLootTable(),
            EntityType.PILLAGER.getDefaultLootTable(),
            EntityType.ILLUSIONER.getDefaultLootTable(),
            EntityType.ZOMBIE.getDefaultLootTable(),
            EntityType.CREEPER.getDefaultLootTable(),
            EntityType.SPIDER.getDefaultLootTable(),
            EntityType.SKELETON.getDefaultLootTable(),
            EntityType.WITCH.getDefaultLootTable(),
            EntityType.BREEZE.getDefaultLootTable(),
            EntityType.ARMADILLO.getDefaultLootTable()
    ).map(ResourceKey::location).toList();

    private static final List<ResourceLocation> OVERWORLD_BLOCKS = Stream.of(
            // surface
            Blocks.GRASS_BLOCK,
            Blocks.DIRT,

            // underground
            Blocks.STONE,
            Blocks.ANDESITE,
            Blocks.GRANITE,
            Blocks.DIORITE,
            Blocks.AMETHYST_BLOCK,
            Blocks.LARGE_AMETHYST_BUD,
            Blocks.MEDIUM_AMETHYST_BUD,
            Blocks.SMALL_AMETHYST_BUD,
            Blocks.OBSIDIAN,
            Blocks.COBBLESTONE,
            Blocks.POINTED_DRIPSTONE,
            Blocks.DRIPSTONE_BLOCK,

            // wood
            Blocks.ACACIA_WOOD,
            Blocks.BIRCH_WOOD,
            Blocks.CHERRY_WOOD,
            Blocks.OAK_WOOD,
            Blocks.DARK_OAK_WOOD,
            Blocks.SPRUCE_WOOD,

            // raw ores
            Blocks.IRON_ORE,
            Blocks.GOLD_ORE,
            Blocks.COPPER_ORE,
            Blocks.COAL_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_IRON_ORE,
            Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.DEEPSLATE_COAL_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,

            // flowers
            Blocks.CORNFLOWER,
            Blocks.SUNFLOWER,
            Blocks.DANDELION,
            Blocks.ORANGE_TULIP,
            Blocks.PINK_TULIP,
            Blocks.RED_TULIP,
            Blocks.WHITE_TULIP,
            Blocks.ROSE_BUSH,
            Blocks.SMALL_DRIPLEAF,
            Blocks.BIG_DRIPLEAF
    ).map(block -> block.getLootTable().location()).toList();

    // nether
    private static final List<Item> NETHER_ITEMS = List.of(
            Items.NETHERRACK,
            Items.SOUL_SAND,
            Items.SOUL_SOIL,
            Items.BLACKSTONE,
            Items.BASALT,
            Items.QUARTZ,
            Items.GLOWSTONE_DUST
    );

    private static final List<ResourceLocation> NETHER_BLOCKS = Stream.of(
            Blocks.NETHERRACK,
            Blocks.SOUL_SAND,
            Blocks.SOUL_SOIL,
            Blocks.BLACKSTONE,
            Blocks.BASALT,
            Blocks.NETHER_QUARTZ_ORE,
            Blocks.GLOWSTONE,
            Blocks.BONE_BLOCK,
            Blocks.POLISHED_BLACKSTONE_BRICKS,
            Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS,
            Blocks.NETHER_BRICKS,
            Blocks.NETHER_WART
    ).map(block -> block.getLootTable().location()).toList();

    private static final List<ResourceLocation> NETHER_LOOT = Stream.of(
            // chests
            BuiltInLootTables.BASTION_BRIDGE,
            BuiltInLootTables.BASTION_OTHER,
            BuiltInLootTables.BASTION_HOGLIN_STABLE,
            BuiltInLootTables.BASTION_TREASURE,
            BuiltInLootTables.NETHER_BRIDGE,

            // mobs
            EntityType.BLAZE.getDefaultLootTable(),
            EntityType.PIGLIN.getDefaultLootTable(),
            EntityType.PIGLIN_BRUTE.getDefaultLootTable(),
            EntityType.ZOGLIN.getDefaultLootTable(),
            EntityType.HOGLIN.getDefaultLootTable(),
            EntityType.GHAST.getDefaultLootTable(),
            EntityType.WITHER_SKELETON.getDefaultLootTable(),
            EntityType.MAGMA_CUBE.getDefaultLootTable()
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

        for (ResourceLocation key : LootRandomizer.getKnownTables()) {
            addLootTable(key, LootRandomizer.getItems(key));
        }
    }

    public static void addRecipe(NonNullList<Ingredient> ingredients, ItemStack result, ResourceLocation id) {
        if (!RandomizerConfig.ensureCompletability) return;

        ItemStack[][] stackList = ingredients.stream()
                .distinct().map(Ingredient::getItems)
                .toArray(ItemStack[][]::new);


        for (int i = 0; i < stackList.length; i++) {
            for (ItemStack stack : stackList[i]) {
                addIngredient(REGISTRY.getKey(stack.getItem()), i, id);
            }
        }

        addResult(REGISTRY.getKey(result.getItem()), id);
        DATA_MAP.put(id, RecipeRandomizer.getMapData());
    }

    public static void addLootTable(ResourceLocation key, Set<ResourceLocation> stacks) {
        if (!RandomizerConfig.ensureCompletability) return;
        for (ResourceLocation stack : stacks) {
            addIngredient(stack, 0, key);
            addResult(stack, key);
        }
        DATA_MAP.put(key, LootRandomizer.getMapData(key));
    }

    private static void addIngredient(ResourceLocation item, int index, ResourceLocation recipe) {
        INGREDIENT_MAP.computeIfAbsent(recipe, k -> new Int2ObjectArrayMap<>())
                .computeIfAbsent(index, i -> new HashSet<>())
                .add(item);
    }

    private static void addResult(ResourceLocation item, ResourceLocation recipe) {
        RESULT_MAP.computeIfAbsent(item, k -> new HashSet<>()).add(recipe);
        RECIPE_MAP.put(recipe, item);
    }

    private static RandomizationMapData getDataFor(ResourceLocation recipe) {
        return DATA_MAP.getOrDefault(recipe, RandomizationMapData.VANILLA);
    }

    public static void ensureCompletability() {
        COMPLETABILITY_CACHE.clear();
        Object2BooleanMap<ResourceLocation> completabilityMap = COMPLETABILITY_CACHE;
        Object2ObjectMap<ResourceLocation, String> pathMap = new Object2ObjectOpenHashMap<>();
        Int2ObjectArrayMap<Set<ResourceLocation>> indexMap = INGREDIENT_MAP.get(ENDER_EYE);

        for (var entry : indexMap.int2ObjectEntrySet()) {
            for (ResourceLocation ing : entry.getValue()) {
                recipePath.clear();
                completabilityMap.put(ing, canObtainIngredient(ing, ENDER_EYE));
                pathMap.put(ing, printPath());
            }
        }

//        if (requiresNether && RESULT_MAP.containsKey(OBSIDIAN)) {
//            recipePath.clear();
//            if (ensureCompletability(OBSIDIAN)) {
//                completabilityMap.put(OBSIDIAN, true);
//                pathMap.put(OBSIDIAN, printPath());
//            }
//        }

        if (requiresNether) RandomizerCore.LOGGER.info("Requires nether access!");

        int i = 0;
        for (ResourceLocation ing : pathMap.keySet()) {
            if (completabilityMap.getBoolean(ing)) {
                RandomizerCore.LOGGER.info("can craft \"{}\"\n{}", ing, pathMap.get(ing));
                i++;
            } else {
                RandomizerCore.LOGGER.info("unable to craft \"{}\"\n{}", ing, pathMap.get(ing));
            }
        }

        isCompletable = i == pathMap.size();

        if (!isCompletable) {
            RandomizerCore.LOGGER.info("Game is Incompletable!");
        }
    }

    /**
     * @param ingredient the registry location of the item ingredient
     * @return true if this ingredient is obtainable from common blocks in the overworld or nether
     */
    private static boolean ensureCompletability(ResourceLocation ingredient) {
        for (ResourceLocation recipe : RESULT_MAP.get(ingredient)) {
            if (!INGREDIENT_MAP.containsKey(recipe) || recipePath.contains(recipe)) continue;

            if (isLoot(recipe)) {
                if (checkLoot(recipe)) {
                    return true;
                } else continue;
            }

            // path adding is handled in check loot
            recipePath.add(recipe);

            Int2ObjectArrayMap<Set<ResourceLocation>> ingredients = INGREDIENT_MAP.get(recipe);

            // for each index
            int success = 0;
            for (int index : ingredients.keySet()) {
                // for each ingredient
                for (ResourceLocation ing : ingredients.get(index)) {
                    // if any ingredient is obtainable, break
                    if (canObtainIngredient(ing, recipe)) {
                        success++;
                        break;
                    }
                }
            }

            // every ingredient slot is obtainable
            if (success == ingredients.size()) {
                return true;
            }
        }
        return false;
    }

    private static boolean canObtainIngredient(ResourceLocation ingredient, ResourceLocation recipe) {
        if (isLoot(recipe)) {
            // loot tends to be the end point
            return computeCompletion(recipe, CompletabilityVerifier::checkLoot);
        }

        return computeCompletion(ingredient, CompletabilityVerifier::ensureCompletability);
    }

    private static boolean isLoot(ResourceLocation key) {
        return LootRandomizer.hasTable(key);
    }

    private static boolean checkLoot(ResourceLocation table) {
        recipePath.add(table);
        if (OVERWORLD_LOOT.contains(table) || OVERWORLD_BLOCKS.contains(table)) {
            return computeCompletion(table);
        }
        if (NETHER_LOOT.contains(table) || NETHER_BLOCKS.contains(table)) {
            requiresNether = true;
            return computeCompletion(OBSIDIAN, CompletabilityVerifier::ensureCompletability);
        } else {
            recipePath.removeLast();
            return false;
        }
    }

    private static boolean checkItem(Item item) {
        if (OVERWORLD_ITEMS.contains(item)) {
            return true;
        }
        if (NETHER_ITEMS.contains(item)) {
            requiresNether = true;
            return computeCompletion(OBSIDIAN, CompletabilityVerifier::ensureCompletability);
        }
        return false;
    }

    private static boolean computeCompletion(ResourceLocation location) {
        return computeCompletion(location, k -> true);
    }

    private static boolean computeCompletion(ResourceLocation location, Predicate<ResourceLocation> predicate) {
        if (!COMPLETABILITY_CACHE.containsKey(location)) {
            COMPLETABILITY_CACHE.put(location, predicate.test(location));
        }
        return COMPLETABILITY_CACHE.getBoolean(location);
    }

    private static String printPath() {
        StringBuilder b = new StringBuilder();
        b.append("Recipe Path:\n");
        int i = 0;
        for (ResourceLocation loc : recipePath) {
            if (isLoot(loc)) {
                b.append("loot={%s}".formatted(loc));
            } else {
                b.append(RECIPE_MAP.get(loc));
                b.append("={recipe=%s}".formatted(loc));
            }
            if (i++ != recipePath.size() - 1) {
                b.append('\n').append(" -> ");
            }
        }
        return b.toString();
    }
}
