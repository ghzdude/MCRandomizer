package com.ghzdude.randomizer;

import com.ghzdude.randomizer.loot.LootRandomizer;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Either;
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
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColorCollection;
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
    // store all overworld obtainable things in here
    private static final List<Identifier> ALL_OVERWORLD;
    private static final List<Identifier> OVERWORLD_LOOT;
    private static final List<Identifier> OVERWORLD_BLOCKS;

    // nether
    // store all nether obtainable things in here
    private static final List<Identifier> ALL_NETHER;
    private static final List<Identifier> NETHER_LOOT;
    private static final List<Identifier> NETHER_BLOCKS;

    static {
        OVERWORLD_LOOT = initOverworldLoot();
        OVERWORLD_BLOCKS = initOverworldBlocks();
        ALL_OVERWORLD = Stream.concat(OVERWORLD_LOOT.stream(), OVERWORLD_BLOCKS.stream()).toList();

        NETHER_LOOT = initNetherLoot();
        NETHER_BLOCKS = initNetherBlocks();
        ALL_NETHER = Stream.concat(NETHER_LOOT.stream(), NETHER_BLOCKS.stream())
                .filter(ALL_OVERWORLD::contains)
                .toList();

        RESULT_MAP.defaultReturnValue(Collections.emptySet());
        INGREDIENT_MAP.defaultReturnValue(Int2ObjectMaps.emptyMap());
    }

    private static List<Identifier> initOverworldBlocks() {
        List<Identifier> list = new ArrayList<>();

        // surface
        addBlockDrop(list, Blocks.GRASS_BLOCK);
        addBlockDrop(list, Blocks.DIRT);
        addBlockDrop(list, Blocks.COARSE_DIRT);
        addBlockDrop(list, Blocks.PODZOL);
        addBlockDrop(list, Blocks.MUD);

        // underground
        addBlockDrop(list, Blocks.STONE);
        addBlockDrop(list, Blocks.ANDESITE);
        addBlockDrop(list, Blocks.GRANITE);
        addBlockDrop(list, Blocks.DIORITE);
        addBlockDrop(list, Blocks.AMETHYST_BLOCK);
        addBlockDrop(list, Blocks.LARGE_AMETHYST_BUD);
        addBlockDrop(list, Blocks.MEDIUM_AMETHYST_BUD);
        addBlockDrop(list, Blocks.SMALL_AMETHYST_BUD);
        addBlockDrop(list, Blocks.OBSIDIAN);
        addBlockDrop(list, Blocks.COBBLESTONE);
        addBlockDrop(list, Blocks.POINTED_DRIPSTONE);
        addBlockDrop(list, Blocks.DRIPSTONE_BLOCK);
        addBlockDrop(list, Blocks.DEEPSLATE);
        addBlockDrop(list, Blocks.CHEST);

        // ancient city
        addBlockDrop(list, Blocks.CHISELED_DEEPSLATE);
        addBlockDrop(list, Blocks.COBBLED_DEEPSLATE);
        addBlockDrop(list, Blocks.DEEPSLATE_BRICK_STAIRS);
        addBlockDrop(list, Blocks.DEEPSLATE_BRICK_SLAB);
        addBlockDrop(list, Blocks.DEEPSLATE_BRICKS);
        addBlockDrop(list, Blocks.DEEPSLATE_TILES);
        addBlockDrop(list, Blocks.DEEPSLATE_TILE_STAIRS);
        addBlockDrop(list, Blocks.POLISHED_BASALT);
        addBlockDrop(list, Blocks.POLISHED_DEEPSLATE);
        addBlockDrop(list, Blocks.POLISHED_DEEPSLATE_WALL);
        addBlockDrop(list, Blocks.SMOOTH_BASALT);
        addBlockDrop(list, Blocks.CANDLE);
        addBlockDrop(list, Blocks.PACKED_ICE);
        addBlockDrop(list, Blocks.REDSTONE_WIRE);
        addSomeColoredBlocks(list, Blocks.DYED_CANDLE, DyeColor.WHITE);
        addSomeColoredBlocks(list, Blocks.CARPET, DyeColor.GRAY);
        addSomeColoredBlocks(list, Blocks.WOOL,
                DyeColor.GRAY, DyeColor.BLUE, DyeColor.LIGHT_BLUE, DyeColor.CYAN);

        // villages
        addBlockDrop(list, Blocks.BRICKS);
        addBlockDrop(list, Blocks.COBBLESTONE_SLAB);
        addBlockDrop(list, Blocks.COBBLESTONE_STAIRS);
        addBlockDrop(list, Blocks.COBBLESTONE_WALL);
        addBlockDrop(list, Blocks.DIRT_PATH);
        addBlockDrop(list, Blocks.FARMLAND);
        addBlockDrop(list, Blocks.OAK_PLANKS);
        addBlockDrop(list, Blocks.OAK_SLAB);
        addBlockDrop(list, Blocks.OAK_STAIRS);
        addBlockDrop(list, Blocks.ACACIA_PLANKS);
        addBlockDrop(list, Blocks.ACACIA_SLAB);
        addBlockDrop(list, Blocks.ACACIA_STAIRS);
        addBlockDrop(list, Blocks.SPRUCE_PLANKS);
        addBlockDrop(list, Blocks.SPRUCE_SLAB);
        addBlockDrop(list, Blocks.SPRUCE_STAIRS);
        addBlockDrop(list, Blocks.SMOOTH_SANDSTONE);
        addBlockDrop(list, Blocks.SMOOTH_SANDSTONE_SLAB);
        addBlockDrop(list, Blocks.SMOOTH_SANDSTONE_STAIRS);
        addBlockDrop(list, Blocks.SANDSTONE);
        addBlockDrop(list, Blocks.SANDSTONE_SLAB);
        addBlockDrop(list, Blocks.SANDSTONE_STAIRS);
        addBlockDrop(list, Blocks.WHEAT);
        addBlockDrop(list, Blocks.POTATOES);
        addBlockDrop(list, Blocks.CARROTS);
        addBlockDrop(list, Blocks.BEETROOTS);

        // stronghold
        addBlockDrop(list, Blocks.STONE_BRICKS);
        addBlockDrop(list, Blocks.MOSSY_STONE_BRICKS);
        addBlockDrop(list, Blocks.CRACKED_STONE_BRICKS);
        addBlockDrop(list, Blocks.STONE_BRICK_STAIRS);
        addBlockDrop(list, Blocks.STONE_BRICK_SLAB);
        addBlockDrop(list, Blocks.SMOOTH_STONE_SLAB);
        addBlockDrop(list, Blocks.IRON_BARS);
        addBlockDrop(list, Blocks.IRON_DOOR);
        addBlockDrop(list, Blocks.STONE_BUTTON);
        addBlockDrop(list, Blocks.OAK_DOOR);
        addBlockDrop(list, Blocks.BOOKSHELF);

        // mineshaft
        addBlockDrop(list, Blocks.OAK_FENCE);
        addBlockDrop(list, Blocks.DARK_OAK_PLANKS);
        addBlockDrop(list, Blocks.DARK_OAK_FENCE);
        addBlockDrop(list, Blocks.RAIL);
        addBlockDrop(list, Blocks.COBWEB);

        // trial ruins
        addBlockDrop(list, Blocks.MUD_BRICKS);
        addBlockDrop(list, Blocks.MUD_BRICK_STAIRS);
        addBlockDrop(list, Blocks.BRICK_SLAB);
        addSomeColoredBlocks(list, Blocks.DYED_TERRACOTTA,
                DyeColor.BLUE, DyeColor.YELLOW, DyeColor.RED, DyeColor.CYAN);

        // shipwreck
        addBlockDrop(list, Blocks.BIRCH_FENCE);
        addBlockDrop(list, Blocks.BIRCH_PLANKS);
        addBlockDrop(list, Blocks.BIRCH_SLAB);
        addBlockDrop(list, Blocks.BIRCH_STAIRS);
        addBlockDrop(list, Blocks.DARK_OAK_DOOR);
        addBlockDrop(list, Blocks.DARK_OAK_FENCE);
        addBlockDrop(list, Blocks.DARK_OAK_LOG);
        addBlockDrop(list, Blocks.DARK_OAK_PLANKS);
        addBlockDrop(list, Blocks.DARK_OAK_SLAB);
        addBlockDrop(list, Blocks.DARK_OAK_STAIRS);
        addBlockDrop(list, Blocks.JUNGLE_DOOR);
        addBlockDrop(list, Blocks.JUNGLE_FENCE);
        addBlockDrop(list, Blocks.JUNGLE_LOG);
        addBlockDrop(list, Blocks.JUNGLE_PLANKS);
        addBlockDrop(list, Blocks.JUNGLE_SLAB);
        addBlockDrop(list, Blocks.JUNGLE_STAIRS);
        addBlockDrop(list, Blocks.OAK_DOOR);
        addBlockDrop(list, Blocks.OAK_FENCE);
        addBlockDrop(list, Blocks.OAK_LOG);
        addBlockDrop(list, Blocks.OAK_PLANKS);
        addBlockDrop(list, Blocks.OAK_SLAB);
        addBlockDrop(list, Blocks.OAK_STAIRS);
        addBlockDrop(list, Blocks.SPRUCE_DOOR);
        addBlockDrop(list, Blocks.SPRUCE_FENCE);
        addBlockDrop(list, Blocks.SPRUCE_LOG);
        addBlockDrop(list, Blocks.SPRUCE_PLANKS);
        addBlockDrop(list, Blocks.SPRUCE_SLAB);
        addBlockDrop(list, Blocks.SPRUCE_STAIRS);

        // wood
        addBlockDrop(list, Blocks.ACACIA_LOG);
        addBlockDrop(list, Blocks.BIRCH_LOG);
        addBlockDrop(list, Blocks.CHERRY_LOG);
        addBlockDrop(list, Blocks.OAK_LOG);
        addBlockDrop(list, Blocks.DARK_OAK_LOG);
        addBlockDrop(list, Blocks.SPRUCE_LOG);

        // raw ores
        addBlockDrop(list, Blocks.IRON_ORE);
        addBlockDrop(list, Blocks.GOLD_ORE);
        addBlockDrop(list, Blocks.COPPER_ORE);
        addBlockDrop(list, Blocks.COAL_ORE);
        addBlockDrop(list, Blocks.DIAMOND_ORE);
        addBlockDrop(list, Blocks.DEEPSLATE_IRON_ORE);
        addBlockDrop(list, Blocks.DEEPSLATE_GOLD_ORE);
        addBlockDrop(list, Blocks.DEEPSLATE_COPPER_ORE);
        addBlockDrop(list, Blocks.DEEPSLATE_COAL_ORE);
        addBlockDrop(list, Blocks.DEEPSLATE_DIAMOND_ORE);

        // flowers
        addBlockDrop(list, Blocks.CORNFLOWER);
        addBlockDrop(list, Blocks.SUNFLOWER);
        addBlockDrop(list, Blocks.DANDELION);
        addBlockDrop(list, Blocks.ORANGE_TULIP);
        addBlockDrop(list, Blocks.PINK_TULIP);
        addBlockDrop(list, Blocks.RED_TULIP);
        addBlockDrop(list, Blocks.WHITE_TULIP);
        addBlockDrop(list, Blocks.ROSE_BUSH);
        addBlockDrop(list, Blocks.SMALL_DRIPLEAF);
        addBlockDrop(list, Blocks.BIG_DRIPLEAF);

        return Collections.unmodifiableList(list);
    }

    private static List<Identifier> initOverworldLoot() {

        List<Identifier> list = new ArrayList<>();

        // chests
        addResourceKey(list, BuiltInLootTables.BURIED_TREASURE);
        addResourceKey(list, BuiltInLootTables.ABANDONED_MINESHAFT);
        addResourceKey(list, BuiltInLootTables.SIMPLE_DUNGEON);
        addResourceKey(list, BuiltInLootTables.DESERT_PYRAMID);
        addResourceKey(list, BuiltInLootTables.ANCIENT_CITY);
        addResourceKey(list, BuiltInLootTables.ANCIENT_CITY_ICE_BOX);
        addResourceKey(list, BuiltInLootTables.STRONGHOLD_CORRIDOR);
        addResourceKey(list, BuiltInLootTables.STRONGHOLD_CROSSING);
        addResourceKey(list, BuiltInLootTables.STRONGHOLD_LIBRARY);
        addResourceKey(list, BuiltInLootTables.SHIPWRECK_TREASURE);
        addResourceKey(list, BuiltInLootTables.SHIPWRECK_MAP);
        addResourceKey(list, BuiltInLootTables.SHIPWRECK_SUPPLY);
        addResourceKey(list, BuiltInLootTables.CAT_MORNING_GIFT);
        addResourceKey(list, BuiltInLootTables.VILLAGE_WEAPONSMITH);
        addResourceKey(list, BuiltInLootTables.VILLAGE_TOOLSMITH);
        addResourceKey(list, BuiltInLootTables.VILLAGE_ARMORER);
        addResourceKey(list, BuiltInLootTables.VILLAGE_CARTOGRAPHER);
        addResourceKey(list, BuiltInLootTables.VILLAGE_MASON);
        addResourceKey(list, BuiltInLootTables.VILLAGE_SHEPHERD);
        addResourceKey(list, BuiltInLootTables.VILLAGE_BUTCHER);
        addResourceKey(list, BuiltInLootTables.VILLAGE_FLETCHER);
        addResourceKey(list, BuiltInLootTables.VILLAGE_FISHER);
        addResourceKey(list, BuiltInLootTables.VILLAGE_TANNERY);
        addResourceKey(list, BuiltInLootTables.VILLAGE_TEMPLE);
        addResourceKey(list, BuiltInLootTables.VILLAGE_DESERT_HOUSE);
        addResourceKey(list, BuiltInLootTables.VILLAGE_PLAINS_HOUSE);
        addResourceKey(list, BuiltInLootTables.VILLAGE_TAIGA_HOUSE);
        addResourceKey(list, BuiltInLootTables.VILLAGE_SNOWY_HOUSE);
        addResourceKey(list, BuiltInLootTables.RUINED_PORTAL);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_REWARD);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_REWARD_COMMON);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_REWARD_RARE);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_REWARD_UNIQUE);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_COMMON);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_RARE);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_UNIQUE);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_SUPPLY);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_CORRIDOR);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_INTERSECTION);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_INTERSECTION_BARREL);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_ENTRANCE);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_CORRIDOR_DISPENSER);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_CHAMBER_DISPENSER);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_WATER_DISPENSER);
        addResourceKey(list, BuiltInLootTables.TRIAL_CHAMBERS_CORRIDOR_POT);
        addResourceKey(list, BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER);
        addResourceKey(list, BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER_RANGED);
        addResourceKey(list, BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER_MELEE);
        addResourceKey(list, BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_COMMON);
        addResourceKey(list, BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_RARE);
        addResourceKey(list, BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY);
        addResourceKey(list, BuiltInLootTables.DESERT_WELL_ARCHAEOLOGY);
        addResourceKey(list, BuiltInLootTables.OCEAN_RUIN_COLD_ARCHAEOLOGY);
        addResourceKey(list, BuiltInLootTables.OCEAN_RUIN_WARM_ARCHAEOLOGY);

        // sheep
        addAllColors(list, BuiltInLootTables.SHEAR_DYED_SHEEP);

        // mobs
        addMobDrop(list, EntityTypes.COW);
        addMobDrop(list, EntityTypes.COD);
        addMobDrop(list, EntityTypes.RABBIT);
        addMobDrop(list, EntityTypes.TROPICAL_FISH);
        addMobDrop(list, EntityTypes.PUFFERFISH);
        addMobDrop(list, EntityTypes.SILVERFISH);
        addMobDrop(list, EntityTypes.ILLUSIONER);
        addMobDrop(list, EntityTypes.PIG);
        addMobDrop(list, EntityTypes.PILLAGER);
        addMobDrop(list, EntityTypes.ILLUSIONER);
        addMobDrop(list, EntityTypes.ZOMBIE);
        addMobDrop(list, EntityTypes.CREEPER);
        addMobDrop(list, EntityTypes.SPIDER);
        addMobDrop(list, EntityTypes.SKELETON);
        addMobDrop(list, EntityTypes.WITCH);
        addMobDrop(list, EntityTypes.BREEZE);
        addMobDrop(list, EntityTypes.ARMADILLO);

        return Collections.unmodifiableList(list);
    }

    private static List<Identifier> initNetherBlocks() {
        List<Identifier> list = new ArrayList<>();
        // nether wastes
        addBlockDrop(list, Blocks.NETHERRACK);
        addBlockDrop(list, Blocks.SOUL_SAND);
        addBlockDrop(list, Blocks.GLOWSTONE);
        addBlockDrop(list, Blocks.MAGMA_BLOCK);
        addBlockDrop(list, Blocks.GRAVEL);

        // soul sand valley
        addBlockDrop(list, Blocks.SOUL_SAND);
        addBlockDrop(list, Blocks.BASALT);
        addBlockDrop(list, Blocks.SOUL_SOIL);
        addBlockDrop(list, Blocks.BONE_BLOCK);

        // ores
        addBlockDrop(list, Blocks.NETHER_QUARTZ_ORE);
        addBlockDrop(list, Blocks.NETHER_GOLD_ORE);
        addBlockDrop(list, Blocks.ANCIENT_DEBRIS);

        // bastion
        addBlockDrop(list, Blocks.BASALT);
        addBlockDrop(list, Blocks.POLISHED_BASALT);
        addBlockDrop(list, Blocks.BLACKSTONE);
        addBlockDrop(list, Blocks.GILDED_BLACKSTONE);
        addBlockDrop(list, Blocks.POLISHED_BLACKSTONE_BRICKS);
        addBlockDrop(list, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);
        addBlockDrop(list, Blocks.CHISELED_POLISHED_BLACKSTONE);
        addBlockDrop(list, Blocks.GOLD_BLOCK);
        addBlockDrop(list, Blocks.MAGMA_BLOCK);
        addBlockDrop(list, Blocks.QUARTZ_BLOCK);
        addBlockDrop(list, Blocks.SMOOTH_QUARTZ);

        // nether fortress
        addBlockDrop(list, Blocks.NETHER_BRICKS);
        addBlockDrop(list, Blocks.NETHER_BRICK_FENCE);
        addBlockDrop(list, Blocks.NETHER_BRICK_STAIRS);
        addBlockDrop(list, Blocks.NETHER_WART);

        // crimson fungus
        addBlockDrop(list, Blocks.CRIMSON_STEM);
        addBlockDrop(list, Blocks.NETHER_WART_BLOCK);
        addBlockDrop(list, Blocks.SHROOMLIGHT);
        addBlockDrop(list, Blocks.WEEPING_VINES);

        // warped fungus
        addBlockDrop(list, Blocks.WARPED_STEM);
        addBlockDrop(list, Blocks.WARPED_WART_BLOCK);
        addBlockDrop(list, Blocks.SHROOMLIGHT);

        return Collections.unmodifiableList(list);
    }

    private static List<Identifier> initNetherLoot() {
        List<Identifier> list = new ArrayList<>();

        // chests
        addResourceKey(list, BuiltInLootTables.BASTION_BRIDGE);
        addResourceKey(list, BuiltInLootTables.BASTION_OTHER);
        addResourceKey(list, BuiltInLootTables.BASTION_HOGLIN_STABLE);
        addResourceKey(list, BuiltInLootTables.BASTION_TREASURE);
        addResourceKey(list, BuiltInLootTables.NETHER_BRIDGE);
        addResourceKey(list, BuiltInLootTables.PIGLIN_BARTERING);

        // mobs
        addMobDrop(list, EntityTypes.BLAZE);
        addMobDrop(list, EntityTypes.PIGLIN);
        addMobDrop(list, EntityTypes.PIGLIN_BRUTE);
        addMobDrop(list, EntityTypes.ZOGLIN);
        addMobDrop(list, EntityTypes.HOGLIN);
        addMobDrop(list, EntityTypes.GHAST);
        addMobDrop(list, EntityTypes.WITHER_SKELETON);
        addMobDrop(list, EntityTypes.MAGMA_CUBE);

        return Collections.unmodifiableList(list);
    }

    private static void addBlockDrop(List<Identifier> list, Block block) {
        block.getLootTable().ifPresent(key -> addResourceKey(list, key));
    }

    private static void addAllColoredBlocks(List<Identifier> list, ColorCollection<Block> blocks) {
        blocks.forEach(block -> addBlockDrop(list, block));
    }

    private static void addSomeColoredBlocks(List<Identifier> list, ColorCollection<Block> blocks, DyeColor... colors) {
        for (DyeColor color : colors) {
            addBlockDrop(list, blocks.pick(color));
        }
    }

    private static <T> void addResourceKey(List<Identifier> list, ResourceKey<T> key) {
        list.add(key.identifier());
    }

    private static <T extends Entity> void addMobDrop(List<Identifier> list, EntityType<T> type) {
        type.getDefaultLootTable().ifPresent(key -> addResourceKey(list, key));
    }

    private static <T> void addAllColors(List<Identifier> list, ColorCollection<ResourceKey<T>> colors) {
        colors.forEach(o -> addResourceKey(list, o));
    }

    private static <T> void addSomeColors(List<Identifier> list, ColorCollection<ResourceKey<T>> collection, DyeColor... colors) {
        for (DyeColor color : colors) {
            addResourceKey(list, collection.pick(color));
        }
    }

    static void init(MinecraftServer server) {
        ITEM_REGISTRY = server.registryAccess().lookupOrThrow(Registries.ITEM);

        data = VerifierSaveData.get(server.overworld().getDataStorage());

        ENDER_EYE = ITEM_REGISTRY.getKey(Items.ENDER_EYE);
        OBSIDIAN = ITEM_REGISTRY.getKey(Items.OBSIDIAN);

        // todo add villager trades
        // todo ensure emerald is craftable

        if (false && data.fromDisk) {
            LOGGER.info("Loading saved completability data!");
            for (ModificationData modificationData : data.modificationData) {
                LootRandomizer.registerSpecialDrop(modificationData.table, modificationData.original, modificationData.replacement);
            }
            LOGGER.info("Loaded {} entries!", data.modificationData.size());
            return;
        }

        for (ResourceKey<Recipe<?>> recipe : RecipeRandomizer.getKnownRecipes()) {
            ResourceKey<Item> result = RecipeRandomizer.getResultFor(recipe);

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

    public static void addRecipe(Collection<Either<TagKey<Item>, ResourceKey<Item>>> ingredients,
                                 ResourceKey<Item> output, ResourceKey<Recipe<?>> recipe) {
        if (!RandomizerConfig.ensureCompletability) return;

        int i = 0;
        for (Either<TagKey<Item>, ResourceKey<Item>> ing : ingredients) {
            Set<Identifier> items = new ObjectOpenHashSet<>();

            ing.ifLeft(tag -> ITEM_REGISTRY.get(tag).ifPresent(named -> named.stream()
                    .map(Holder::unwrapKey)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(ResourceKey::identifier)
                    .forEach(items::add))
            ).ifRight(k -> items.add(k.identifier()));

            addIngredients(recipe.identifier(), i++, items);
        }

        addResult(output.identifier(), recipe.identifier());
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
            LOGGER.info("Currently iterating recipe '{}' for their ingredients", recipe);
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
                LOGGER.info("Checking loot table '{}'", recipe);
            }
            // chest loot tends to be the end point
            return computeCompletion(recipe, CompletabilityVerifier::checkLoot);
        }

        if (RandomizerConfig.enableDebug) {
            LOGGER.info("Checking ingredient '{}' in recipe '{}'", ingredient, recipe);
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
