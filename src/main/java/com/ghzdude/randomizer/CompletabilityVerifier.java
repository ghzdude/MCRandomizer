package com.ghzdude.randomizer;

import com.ghzdude.randomizer.loot.LootRandomizer;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/* Description
 * This is to ensure that eyes of ender are always obtainable regardless of randomization
 * Iterate through known recipes and create a path to the most common blocks found in the overworld
 * if an item from the nether is selected, then make sure that obsidian is obtainable
 */

@SuppressWarnings("SameReturnValue")
public class CompletabilityVerifier {

    /**
     * Maps an item (result) to a set of recipes that make this item
     */
    // use this to iterate over recipes whose result is the item key
    // keys may also be tags
    private static final Object2ObjectMap<Identifier, Set<Identifier>> RESULT_MAP = new Object2ObjectOpenHashMap<>();

    /**
     * Maps a recipe to the ingredients used in it
     */
    // this stores tag locations, and also a set of item locations if no tags for a given slot
    private static final Object2ObjectMap<Identifier, Int2ObjectMap<Set<Identifier>>> INGREDIENT_MAP = new Object2ObjectOpenHashMap<>();

    private static final Object2BooleanMap<Identifier> COMPLETABILITY_CACHE = new Object2BooleanOpenHashMap<>();

    // map ingredient -> items
    // not actually possible
    // sometimes recipes just use a set of items
    // recipe -> map -> items
    private static final Object2ObjectMap<Identifier, Set<Identifier>> TAG_MAP = new Object2ObjectOpenHashMap<>();

    /**
     * Maps a recipe to a set of its ingredients that needs to be modified
     */
    private static final Map<Identifier, Identifier> MODIFY_RECIPES = new Object2ObjectOpenHashMap<>();

    private static VerifierSaveData data;

    public static Identifier ENDER_EYE;
    public static Identifier OBSIDIAN;

    private static final Deque<Identifier> RECIPE_PATH = new ArrayDeque<>();
    private static final Deque<Identifier> COMPLETION_QUEUE = new ArrayDeque<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean requiresNether = false;
    private static boolean isCompletable = false;

    private static Registry<Item> ITEM_REGISTRY;

    // overworld
    private static final List<Identifier> OVERWORLD_LOOT = Stream.of(
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
//            BuiltInLootTables.SPAWN_BONUS_CHEST,
            BuiltInLootTables.CAT_MORNING_GIFT,
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
            BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_COMMON,
            BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_RARE,
            BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY,
            BuiltInLootTables.DESERT_WELL_ARCHAEOLOGY,
            BuiltInLootTables.OCEAN_RUIN_COLD_ARCHAEOLOGY,
            BuiltInLootTables.OCEAN_RUIN_WARM_ARCHAEOLOGY,

            // sheep
            BuiltInLootTables.SHEAR_DYED_SHEEP.black(),
            BuiltInLootTables.SHEAR_DYED_SHEEP.gray(),
            BuiltInLootTables.SHEAR_DYED_SHEEP.lightGray(),
            BuiltInLootTables.SHEAR_DYED_SHEEP.white(),
            BuiltInLootTables.SHEAR_DYED_SHEEP.red(),
            BuiltInLootTables.SHEAR_DYED_SHEEP.orange(),
            BuiltInLootTables.SHEAR_DYED_SHEEP.yellow(),
            BuiltInLootTables.SHEAR_DYED_SHEEP.green(),
            BuiltInLootTables.SHEAR_DYED_SHEEP.cyan(),
            BuiltInLootTables.SHEAR_DYED_SHEEP.blue(),
            BuiltInLootTables.SHEAR_DYED_SHEEP.purple(),
            BuiltInLootTables.SHEAR_DYED_SHEEP.brown(),
            BuiltInLootTables.SHEAR_DYED_SHEEP.lightBlue(),
            BuiltInLootTables.SHEAR_DYED_SHEEP.lime(),
            BuiltInLootTables.SHEAR_DYED_SHEEP.magenta(),
            BuiltInLootTables.SHEAR_DYED_SHEEP.pink(),

            // mobs
            EntityTypes.COW.getDefaultLootTable().orElseThrow(),
            EntityTypes.COD.getDefaultLootTable().orElseThrow(),
            EntityTypes.RABBIT.getDefaultLootTable().orElseThrow(),
            EntityTypes.TROPICAL_FISH.getDefaultLootTable().orElseThrow(),
            EntityTypes.PUFFERFISH.getDefaultLootTable().orElseThrow(),
            EntityTypes.SILVERFISH.getDefaultLootTable().orElseThrow(),
            EntityTypes.ILLUSIONER.getDefaultLootTable().orElseThrow(),
            EntityTypes.PIG.getDefaultLootTable().orElseThrow(),
            EntityTypes.PILLAGER.getDefaultLootTable().orElseThrow(),
            EntityTypes.ILLUSIONER.getDefaultLootTable().orElseThrow(),
            EntityTypes.ZOMBIE.getDefaultLootTable().orElseThrow(),
            EntityTypes.CREEPER.getDefaultLootTable().orElseThrow(),
            EntityTypes.SPIDER.getDefaultLootTable().orElseThrow(),
            EntityTypes.SKELETON.getDefaultLootTable().orElseThrow(),
            EntityTypes.WITCH.getDefaultLootTable().orElseThrow(),
            EntityTypes.BREEZE.getDefaultLootTable().orElseThrow(),
            EntityTypes.ARMADILLO.getDefaultLootTable().orElseThrow()
    ).map(ResourceKey::identifier).toList();

    private static final List<Identifier> OVERWORLD_BLOCKS = Stream.of(
            // surface
            Blocks.GRASS_BLOCK,
            Blocks.DIRT,
            Blocks.COARSE_DIRT,
            Blocks.PODZOL,
            Blocks.MUD,

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
            Blocks.DEEPSLATE,
            Blocks.CHEST,

            // ancient city
            Blocks.CHISELED_DEEPSLATE,
            Blocks.COBBLED_DEEPSLATE,
            Blocks.DEEPSLATE_BRICK_STAIRS,
            Blocks.DEEPSLATE_BRICK_SLAB,
            Blocks.DEEPSLATE_BRICKS,
            Blocks.DEEPSLATE_TILES,
            Blocks.DEEPSLATE_TILE_STAIRS,
            Blocks.POLISHED_BASALT,
            Blocks.POLISHED_DEEPSLATE,
            Blocks.POLISHED_DEEPSLATE_WALL,
            Blocks.SMOOTH_BASALT,
            Blocks.CANDLE,
            Blocks.DYED_CANDLE.white(),
            Blocks.CARPET.gray(),
            Blocks.WOOL.gray(),
            Blocks.WOOL.blue(),
            Blocks.WOOL.lightBlue(),
            Blocks.WOOL.cyan(),
            Blocks.PACKED_ICE,
            Blocks.REDSTONE_WIRE,

            // villages
            Blocks.BRICKS,
            Blocks.COBBLESTONE_SLAB,
            Blocks.COBBLESTONE_STAIRS,
            Blocks.COBBLESTONE_WALL,
            Blocks.DIRT_PATH,
            Blocks.FARMLAND,
            Blocks.OAK_PLANKS,
            Blocks.OAK_SLAB,
            Blocks.OAK_STAIRS,
            Blocks.ACACIA_PLANKS,
            Blocks.ACACIA_SLAB,
            Blocks.ACACIA_STAIRS,
            Blocks.SPRUCE_PLANKS,
            Blocks.SPRUCE_SLAB,
            Blocks.SPRUCE_STAIRS,
            Blocks.SMOOTH_SANDSTONE,
            Blocks.SMOOTH_SANDSTONE_SLAB,
            Blocks.SMOOTH_SANDSTONE_STAIRS,
            Blocks.SANDSTONE,
            Blocks.SANDSTONE_SLAB,
            Blocks.SANDSTONE_STAIRS,
            Blocks.WHEAT,
            Blocks.POTATOES,
            Blocks.CARROTS,
            Blocks.BEETROOTS,

            // stronghold
            Blocks.STONE_BRICKS,
            Blocks.MOSSY_STONE_BRICKS,
            Blocks.CRACKED_STONE_BRICKS,
            Blocks.STONE_BRICK_STAIRS,
            Blocks.STONE_BRICK_SLAB,
            Blocks.SMOOTH_STONE_SLAB,
            Blocks.IRON_BARS,
            Blocks.IRON_DOOR,
            Blocks.STONE_BUTTON,
            Blocks.OAK_DOOR,
            Blocks.BOOKSHELF,

            // mineshaft
            Blocks.OAK_FENCE,
            Blocks.DARK_OAK_PLANKS,
            Blocks.DARK_OAK_FENCE,
            Blocks.RAIL,
            Blocks.COBWEB,

            // trial ruins
            Blocks.MUD_BRICKS,
            Blocks.MUD_BRICK_STAIRS,
            Blocks.DYED_TERRACOTTA.blue(),
            Blocks.DYED_TERRACOTTA.yellow(),
            Blocks.DYED_TERRACOTTA.red(),
            Blocks.DYED_TERRACOTTA.cyan(),
            Blocks.BRICK_SLAB,

            // shipwreck
            Blocks.BIRCH_FENCE,
            Blocks.BIRCH_PLANKS,
            Blocks.BIRCH_SLAB,
            Blocks.BIRCH_STAIRS,
            Blocks.DARK_OAK_DOOR,
            Blocks.DARK_OAK_FENCE,
            Blocks.DARK_OAK_LOG,
            Blocks.DARK_OAK_PLANKS,
            Blocks.DARK_OAK_SLAB,
            Blocks.DARK_OAK_STAIRS,
            Blocks.JUNGLE_DOOR,
            Blocks.JUNGLE_FENCE,
            Blocks.JUNGLE_LOG,
            Blocks.JUNGLE_PLANKS,
            Blocks.JUNGLE_SLAB,
            Blocks.JUNGLE_STAIRS,
            Blocks.OAK_DOOR,
            Blocks.OAK_FENCE,
            Blocks.OAK_LOG,
            Blocks.OAK_PLANKS,
            Blocks.OAK_SLAB,
            Blocks.OAK_STAIRS,
            Blocks.SPRUCE_DOOR,
            Blocks.SPRUCE_FENCE,
            Blocks.SPRUCE_LOG,
            Blocks.SPRUCE_PLANKS,
            Blocks.SPRUCE_SLAB,
            Blocks.SPRUCE_STAIRS,

            // wood
            Blocks.ACACIA_LOG,
            Blocks.BIRCH_LOG,
            Blocks.CHERRY_LOG,
            Blocks.OAK_LOG,
            Blocks.DARK_OAK_LOG,
            Blocks.SPRUCE_LOG,

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
    ).distinct().map(block -> block.getLootTable().orElseThrow().identifier()).toList();

    // store all overworld obtainable things in here
    private static final List<Identifier> ALL_OVERWORLD = new ArrayList<>();

    // nether
    private static final List<Identifier> NETHER_BLOCKS = Stream.of(
            // nether wastes
            Blocks.NETHERRACK,
            Blocks.SOUL_SAND,
            Blocks.GLOWSTONE,
            Blocks.MAGMA_BLOCK,
            Blocks.GRAVEL,

            // soul sand valley
            Blocks.SOUL_SAND,
            Blocks.BASALT,
            Blocks.SOUL_SOIL,
            Blocks.BONE_BLOCK,

            // ores
            Blocks.NETHER_QUARTZ_ORE,
            Blocks.NETHER_GOLD_ORE,
            Blocks.ANCIENT_DEBRIS,

            // bastion
            Blocks.BASALT,
            Blocks.POLISHED_BASALT,
            Blocks.BLACKSTONE,
            Blocks.GILDED_BLACKSTONE,
            Blocks.POLISHED_BLACKSTONE_BRICKS,
            Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS,
            Blocks.CHISELED_POLISHED_BLACKSTONE,
            Blocks.GOLD_BLOCK,
            Blocks.MAGMA_BLOCK,
            Blocks.QUARTZ_BLOCK,
            Blocks.SMOOTH_QUARTZ,

            // nether fortress
            Blocks.NETHER_BRICKS,
            Blocks.NETHER_BRICK_FENCE,
            Blocks.NETHER_BRICK_STAIRS,
            Blocks.NETHER_WART,

            // crimson fungus
            Blocks.CRIMSON_STEM,
            Blocks.NETHER_WART_BLOCK,
            Blocks.SHROOMLIGHT,
            Blocks.WEEPING_VINES,

            // warped fungus
            Blocks.WARPED_STEM,
            Blocks.WARPED_WART_BLOCK,
            Blocks.SHROOMLIGHT
    ).distinct().map(block -> block.getLootTable().orElseThrow().identifier()).toList();

    // store all nether obtainable things in here
    private static final List<Identifier> ALL_NETHER = new ArrayList<>();

    private static final List<Identifier> NETHER_LOOT = Stream.of(
            // chests
            BuiltInLootTables.BASTION_BRIDGE,
            BuiltInLootTables.BASTION_OTHER,
            BuiltInLootTables.BASTION_HOGLIN_STABLE,
            BuiltInLootTables.BASTION_TREASURE,
            BuiltInLootTables.NETHER_BRIDGE,
            BuiltInLootTables.PIGLIN_BARTERING,

            // mobs
            EntityTypes.BLAZE.getDefaultLootTable().orElseThrow(),
            EntityTypes.PIGLIN.getDefaultLootTable().orElseThrow(),
            EntityTypes.PIGLIN_BRUTE.getDefaultLootTable().orElseThrow(),
            EntityTypes.ZOGLIN.getDefaultLootTable().orElseThrow(),
            EntityTypes.HOGLIN.getDefaultLootTable().orElseThrow(),
            EntityTypes.GHAST.getDefaultLootTable().orElseThrow(),
            EntityTypes.WITHER_SKELETON.getDefaultLootTable().orElseThrow(),
            EntityTypes.MAGMA_CUBE.getDefaultLootTable().orElseThrow()
    ).map(ResourceKey::identifier).toList();

    static void init(MinecraftServer server) {
        ITEM_REGISTRY = server.registryAccess().lookupOrThrow(Registries.ITEM);
        RESULT_MAP.defaultReturnValue(Collections.emptySet());
        INGREDIENT_MAP.defaultReturnValue(Int2ObjectMaps.emptyMap());

        data = VerifierSaveData.get(server.overworld().getDataStorage());

        ENDER_EYE = ITEM_REGISTRY.getKey(Items.ENDER_EYE);
        OBSIDIAN = ITEM_REGISTRY.getKey(Items.OBSIDIAN);

        // todo add villager trades
        // todo ensure emerald is craftable

        ALL_OVERWORLD.clear();
        ALL_OVERWORLD.addAll(OVERWORLD_BLOCKS);
        ALL_OVERWORLD.addAll(OVERWORLD_LOOT);

        ALL_NETHER.clear();
        ALL_NETHER.addAll(NETHER_BLOCKS);
        ALL_NETHER.addAll(NETHER_LOOT);
        ALL_NETHER.removeIf(ALL_OVERWORLD::contains);

        if (false && data.fromDisk) {
            LOGGER.info("Loading saved completability data!");
            for (ModificationData modificationData : data.modificationData) {
                LootRandomizer.registerSpecialDrop(modificationData.table, modificationData.original, modificationData.replacement);
            }
            LOGGER.info("Loaded {} entries!", data.modificationData.size());
            return;
        }

        for (Identifier recipe : RecipeRandomizer.getKnownRecipes()) {
            Identifier result = RecipeRandomizer.getResultFor(recipe);

            addRecipe(RecipeRandomizer.getIngredients(recipe), result, recipe);
        }

        for (Identifier table : LootRandomizer.getKnownTables()) {
            addLootTable(table, LootRandomizer.getDrops(table));
        }
    }

    public static void dispose() {
        RESULT_MAP.clear();
        INGREDIENT_MAP.clear();
        COMPLETION_QUEUE.clear();
        RECIPE_PATH.clear();
        data = null;
    }

    public static void addRecipe(Set<JsonElement> ingredients, Identifier output, Identifier recipe) {
        if (!RandomizerConfig.ensureCompletability) return;

        int i = 0;
        for (JsonElement ing : ingredients) {
            Set<Identifier> items = new ObjectOpenHashSet<>();

            if (ing.isJsonArray()) {
                for (JsonElement e : ing.getAsJsonArray()) {
                    parseJson(e, items);
                }
            } else {
                parseJson(ing, items);
            }

            addIngredients(recipe, i++, items);
        }

        addResult(output, recipe);
    }

    public static void addLootTable(Identifier table, Set<Identifier> stacks) {
        if (!RandomizerConfig.ensureCompletability) return;
        for (Identifier stack : stacks) {
            addResult(stack, table);
        }
        if (LootRandomizer.isBlock(table)) {
            Identifier block = LootRandomizer.getBlockFor(table);
            if (block == null) return;
            addIngredient(table, block);
        }
        if (LootRandomizer.isEntityDrop(table)) {
            Identifier egg = LootRandomizer.getEggForEntityTable(table);
            if (egg == null) return;
            addIngredient(table, egg);
        }
    }

    private static void parseJson(JsonElement element, Set<Identifier> items) {
        String s = element.getAsString();
        if (s.startsWith("#")) {
            Identifier tag = Identifier.parse(s.substring(1));
            TAG_MAP.computeIfAbsent(tag, (Identifier k) -> ITEM_REGISTRY.get(ItemTags.create(k))
                    .map(holders -> holders.stream()
                            .map(Holder::get)
                            .map(ITEM_REGISTRY::getKey)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toUnmodifiableSet()))
                    .orElse(Collections.emptySet()));
            items.add(tag);
        } else{
            items.add(Identifier.parse(s));
        }
    }

    private static void addIngredient(@NotNull Identifier recipe, @NotNull Identifier item) {
        addIngredients(recipe, 0, Set.of(item));
    }

    private static void addIngredients(@NotNull Identifier recipe, @NotNull Set<Identifier> items) {
        addIngredients(recipe, 0, items);
    }

    private static void addIngredients(@NotNull Identifier recipe, int index, @NotNull Set<Identifier> items) {
        if (items.isEmpty()) return;
        INGREDIENT_MAP.computeIfAbsent(recipe, k -> new Int2ObjectArrayMap<>())
                .computeIfAbsent(index, i -> new ObjectOpenHashSet<>())
                .addAll(items.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet()));
    }

    private static boolean isTag(Identifier location) {
        return TAG_MAP.containsKey(location);
    }

    private static void addResult(Identifier item, Identifier recipe) {
        RESULT_MAP.computeIfAbsent(item, k -> new ObjectOpenHashSet<>()).add(recipe);
    }

    private static boolean modifyRecipe(Identifier table, Identifier ingredient) {
        // certain loot tables do not have drops, so are not able to be modified
        if (!isLoot(table)) return false;

        // we will modify this recipe to give this ingredient
        MODIFY_RECIPES.put(table, ingredient);
        if (RandomizerConfig.enableDebug) {
            LOGGER.info("Table '{}' will be modified to drop '{}'", table, ingredient);
        }
        return true;
    }

    private static void commitModifiedRecipes() {
        if (RandomizerConfig.enableDebug) {
            LOGGER.info("Commiting {} modified recipes", MODIFY_RECIPES.size());
        }
        for (Identifier table : MODIFY_RECIPES.keySet()) {
            // modify
            // pick a random item from this table to replace with the value ingredient
            Set<Identifier> drops = LootRandomizer.getItems(table);
            Identifier randomDrop = drops.stream().findAny().orElseThrow();
            // map this random drop to the failed item, specifically for this table
            LootRandomizer.registerSpecialDrop(table, randomDrop, MODIFY_RECIPES.get(table));
            computeCompletion(table);
            data.addEntry(table, randomDrop, MODIFY_RECIPES.get(table));
        }

        if (!MODIFY_RECIPES.isEmpty())
            data.setDirty(true);
    }

    static void ensureCompletability() {
        if (false && data.fromDisk) {
            // we loaded from disk, no need to check again
            return;
        }

        LOGGER.info("Iterating all ender eye recipes");
        boolean validRecipe = ensureCompletability(ENDER_EYE);

        if (requiresNether) {
            RECIPE_PATH.clear();
            LOGGER.info("Requires nether access, iterating obsidian recipes");
            validRecipe = ensureCompletability(OBSIDIAN);
            if (!validRecipe) {
                LOGGER.info("Obsidian is not obtainable!");
            }
            for (Identifier location : COMPLETION_QUEUE) {
                COMPLETABILITY_CACHE.put(location, validRecipe);
            }
        }

        commitModifiedRecipes();

        if (validRecipe) {
            isCompletable = true;
        }

        if (!isCompletable) {
            LOGGER.info("Game is Incompletable!");
        }
    }

    /**
     * @param ingredient the registry location of the item ingredient
     * @return true if this ingredient is obtainable from common blocks in the overworld or nether
     */
    private static boolean ensureCompletability(Identifier ingredient) {
        Set<Identifier> recipes = RESULT_MAP.get(ingredient);

        if (RandomizerConfig.enableDebug) {
            if (recipes.isEmpty()) {
                LOGGER.info("No recipes found for ingredient: {}!", ingredient);
            } else {
                LOGGER.info("Iterating recipes that make '{}'", ingredient);
                LOGGER.info("{} recipes found: {}", recipes.size(), recipes);
            }
        }

        Set<Identifier> passed = iterateRecipes(recipes, false);

        if (!passed.isEmpty()) return true;

        passed = iterateRecipes(recipes, true);

        // select a recipe to modify
        if (passed.isEmpty()) {
            // i shouldn't modify recipes just yet
            // should just store it for later
            Identifier random = RandomizerUtil.getRandom(ALL_OVERWORLD, RandomizerCore.seededRNG);
            LOGGER.info("Ingredient '{}' can be obtained from '{}'", ingredient, random);
            return modifyRecipe(random, ingredient);
        } else {
            // this might be duplicated?
            LOGGER.info("Ingredient '{}' can be obtained from {}", ingredient, passed);
            return true;
        }
    }

    private static Set<Identifier> iterateRecipes(Set<Identifier> recipes, boolean deep) {
        if (recipes.isEmpty()) return Collections.emptySet();

        // quick iterate
        if (!deep) {
            Set<Identifier> obtainableRecipes = recipes.stream()
                    .filter(ALL_OVERWORLD::contains)
                    .collect(Collectors.toUnmodifiableSet());

            if (!obtainableRecipes.isEmpty()) {
                return obtainableRecipes;
            }

            obtainableRecipes = recipes.stream()
                    .filter(ALL_NETHER::contains)
                    .collect(Collectors.toUnmodifiableSet());

            if (!obtainableRecipes.isEmpty()) {
                requiresNether = true;
                return obtainableRecipes;
            }

            return Collections.emptySet();
        }

        // look deeper
        // otherwise iterate the failed recipes
        for (Identifier recipe : recipes) {

            // we are already walking this recipe, skip
            if (!addToPath(recipe)) {
                continue;
            }

            // we can't make anything give chest loot
            if (LootRandomizer.isChestLoot(recipe)) {
                // this is chest loot and it's end only
                walkBack(false);
                continue;
            }

            ObjectCollection<Set<Identifier>> indexedIngredients = INGREDIENT_MAP.get(recipe).values();

            // this recipe does not exist in map, OR
            // this recipe has no ingredients to check, SKIP
            if (indexedIngredients.isEmpty()) {
                walkBack(false);
                continue;
            }

            // if obtainable, we succeed, but we still need to walk back
            // because we are done looking at this recipe

            if (iterateIngredients(indexedIngredients, recipe)) {
                walkBack(true);
                // return the first recipe hit
                return Set.of(recipe);
            }
        }

        return Collections.emptySet();
    }

    private static Set<Identifier> expandIngredients(Set<Identifier> compactIngredients) {
        return compactIngredients.stream()
                .flatMap(location -> isTag(location) ? TAG_MAP.get(location).stream() : Stream.of(location))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean quickIterateIngredient(Set<Identifier> ingredients, Set<Identifier> iterated, Set<Identifier> failed) {
        for (Identifier ingredient : ingredients) {
            if (!iterated.add(ingredient)) continue;

            // we've already computed this ingredient
            if (COMPLETABILITY_CACHE.containsKey(ingredient)) {
                if (COMPLETABILITY_CACHE.getBoolean(ingredient)) {
                    return true;
                } else {
                    if (!isTag(ingredient)) {
                        failed.add(ingredient);
                    }
                    continue;
                }
            }

            // if no recipes make this item, SKIP
            if (RESULT_MAP.get(ingredient).isEmpty()) {
                if (!isTag(ingredient))
                    failed.add(ingredient);
                continue;
            }

            // iterate recipes that give this ingredient
            Set<Identifier> quickSearch = iterateRecipes(RESULT_MAP.get(ingredient), false);

            if (!quickSearch.isEmpty()) {
                logIngredient(ingredient, RECIPE_PATH.peekLast(), true);
                LOGGER.info("Ingredient '{}' can be obtained from {}", ingredient, quickSearch);
                return computeCompletion(ingredient);
            } else if (!isTag(ingredient)) {
                // sometimes compact ingredients can be normal items
                failed.add(ingredient);
            }
        }
        return false;
    }

    private static boolean deepSearch(Set<Identifier> ingredients, Identifier recipe) {
        for (Identifier ingredient : ingredients) {
            // for each ingredient
            // if any ingredient is obtainable, break
            if (canObtainIngredient(ingredient, recipe)) {
                logIngredient(ingredient, recipe, true);
                return true;
            }
        }
        logIngredient(ingredients, recipe, false);
        return false;
    }

    private static boolean iterateIngredients(ObjectCollection<Set<Identifier>> ingredientMap, Identifier recipe) {
        // for each "index"
        int craftableSlots = ingredientMap.size();

        if (RandomizerConfig.enableDebug) {
            LOGGER.debug("Currently iterating recipe '{}' for their ingredients", recipe);
        }

        for (Set<Identifier> compactIngredients : ingredientMap) {
            if (compactIngredients.isEmpty()) {
                logEmptyIngredients(recipe);
                continue;
            }

            Set<Identifier> iterated = new ObjectOpenHashSet<>();
            Set<Identifier> failed = new ObjectOpenHashSet<>();

            // quickly search compact ingredients if any are immediately obtainable
            if (quickIterateIngredient(compactIngredients, iterated, failed)) continue;

            // iterate expanded ingredients
            if (quickIterateIngredient(expandIngredients(compactIngredients), iterated, failed)) continue;

            if (!deepSearch(failed, recipe)) {
                craftableSlots--;
            }
        }

        // all ingredients for this recipe are obtainable
        return craftableSlots == ingredientMap.size();
    }

    private static boolean canObtainIngredient(Identifier ingredient, Identifier recipe) {
        if (LootRandomizer.isChestLoot(recipe)) {
            if (RandomizerConfig.enableDebug) {
                LOGGER.debug("Checking loot table '{}'", recipe);
            }
            // chest loot tends to be the end point
            return computeCompletion(recipe, CompletabilityVerifier::checkLoot);
        }

        if (RandomizerConfig.enableDebug) {
            LOGGER.debug("Checking ingredient '{}' in recipe '{}'", ingredient, recipe);
        }
        return computeCompletion(ingredient, CompletabilityVerifier::ensureCompletability);
    }

    private static boolean isLoot(Identifier key) {
        return LootRandomizer.hasTable(key);
    }

    private static boolean checkLoot(Identifier table) {
        // ideally this should only be called for chest loot
        if (ALL_OVERWORLD.contains(table)) {
            return computeCompletion(table);

        } else if (ALL_NETHER.contains(table)) {
            requiresNether = true;
            // defer completion for later
            if (!COMPLETION_QUEUE.contains(table))
                COMPLETION_QUEUE.add(table);
            return true;
        }

        // this table is end only, which is not obtainable
        return false;
    }

    private static boolean computeCompletion(Identifier location) {
        return computeCompletion(location, k -> true);
    }

    private static boolean computeCompletion(Identifier location, Predicate<Identifier> predicate) {
        if (!COMPLETABILITY_CACHE.containsKey(location)) {
            COMPLETABILITY_CACHE.put(location, predicate.test(location));
        }
        return COMPLETABILITY_CACHE.getBoolean(location);
    }

    private static void walkBack(boolean success) {
        if (RECIPE_PATH.isEmpty()) {
            LOGGER.error("Cannot walk back on empty path!");
            return;
        }

        Identifier last = RECIPE_PATH.removeLast();
        if (!RandomizerConfig.enableDebug) return;
        if (success) {
            LOGGER.info("Back to recipe '{}'", RECIPE_PATH.peekLast());
            LOGGER.info("Recipe {} is obtainable!", last);
        } else {
            LOGGER.info("Recipe '{}' is not obtainable, back to recipe '{}'", last, RECIPE_PATH.peekLast());
        }
    }

    private static boolean addToPath(Identifier recipe) {
        if (RECIPE_PATH.contains(recipe)) return false;
        RECIPE_PATH.add(recipe);
        return true;
    }

    private static void logIngredient(Object ingredient, Identifier recipe, boolean success) {
        if (!RandomizerConfig.enableDebug) return;
        if (success) {
            LOGGER.info("Ingredient '{}' in recipe '{}' is obtainable!", ingredient, recipe);
        } else {
            LOGGER.info("All ingredients for recipe '{}' are unobtainable!", recipe);
        }
    }

    private static void logEmptyIngredients(Identifier recipe) {
        if (!RandomizerConfig.enableDebug) return;
        String type = isLoot(recipe) ? "Table" : "Recipe";
        LOGGER.info("{} '{}' has a set of ingredients that is empty!", type, recipe);
    }

    private static class VerifierSaveData extends SavedData {

        public static final Codec<VerifierSaveData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ModificationData.CODEC.listOf().fieldOf("data").forGetter(data -> data.modificationData)
        ).apply(instance, VerifierSaveData::new));

        public static final SavedDataType<VerifierSaveData> FACTORY = new SavedDataType<>(
                RandomizerCore.withPath("modified_data"),
                VerifierSaveData::new, VerifierSaveData.CODEC, null);

        /**
         * Maps a loot table id to a map of a recipe to a set of its ingredients that needs to be modified
         */
        private final List<ModificationData> modificationData = new ArrayList<>();

        public boolean fromDisk = false;

        private VerifierSaveData() {}

        private VerifierSaveData(Collection<ModificationData> c) {
            c.forEach(this::addEntry);
            fromDisk = true;
        }

        public void addEntry(Identifier table, Identifier original, Identifier replacement) {
            addEntry(new ModificationData(table, original, replacement));
        }

        private void addEntry(ModificationData data) {
            if (!modificationData.contains(data))
                modificationData.add(data);
        }


        public static VerifierSaveData get(SavedDataStorage storage) {
            return storage.computeIfAbsent(FACTORY);
        }
    }

    private record ModificationData(Identifier table, Identifier original, Identifier replacement) {

        public static final Codec<ModificationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(ModificationData::table),
                Identifier.CODEC.fieldOf("original").forGetter(ModificationData::original),
                Identifier.CODEC.fieldOf("replacement").forGetter(ModificationData::replacement)
        ).apply(instance, ModificationData::new));
    }
}
