package com.ghzdude.randomizer;

import com.ghzdude.randomizer.loot.LootRandomizer;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.DimensionDataStorage;
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
    private static final Object2ObjectMap<ResourceLocation, Set<ResourceLocation>> RESULT_MAP = new Object2ObjectOpenHashMap<>();

    /**
     * Maps a recipe to the ingredients used in it
     */
    // this stores tag locations, and also a set of item locations if no tags for a given slot
    private static final Object2ObjectMap<ResourceLocation, Int2ObjectMap<Set<ResourceLocation>>> INGREDIENT_MAP = new Object2ObjectOpenHashMap<>();

    private static final Object2BooleanMap<ResourceLocation> COMPLETABILITY_CACHE = new Object2BooleanOpenHashMap<>();

    // map ingredient -> items
    // not actually possible
    // sometimes recipes just use a set of items
    // recipe -> map -> items
    private static final Object2ObjectMap<ResourceLocation, Set<ResourceLocation>> TAG_MAP = new Object2ObjectOpenHashMap<>();

    /**
     * Maps a recipe to a set of its ingredients that needs to be modified
     */
    private static final Map<ResourceLocation, ResourceLocation> MODIFY_RECIPES = new Object2ObjectOpenHashMap<>();

    private static VerifierSaveData data;

    public static ResourceLocation ENDER_EYE;
    public static ResourceLocation OBSIDIAN;

    private static final Deque<ResourceLocation> RECIPE_PATH = new ArrayDeque<>();
    private static final Deque<Component> PRINT_PATH = new ArrayDeque<>();
    private static final Deque<ResourceLocation> COMPLETION_QUEUE = new ArrayDeque<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean requiresNether = false;
    private static boolean isCompletable = false;
    private static boolean walkingBack = false;

    private static Registry<Item> ITEM_REGISTRY;

    // overworld
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
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.BLACK),
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.GRAY),
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.LIGHT_GRAY),
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.WHITE),
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.RED),
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.ORANGE),
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.YELLOW),
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.GREEN),
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.CYAN),
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.BLUE),
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.PURPLE),
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.BROWN),
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.LIGHT_BLUE),
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.LIME),
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.MAGENTA),
            BuiltInLootTables.SHEAR_SHEEP_BY_DYE.get(DyeColor.PINK),

            // mobs
            EntityType.COW.getDefaultLootTable().orElseThrow(),
            EntityType.COD.getDefaultLootTable().orElseThrow(),
            EntityType.RABBIT.getDefaultLootTable().orElseThrow(),
            EntityType.TROPICAL_FISH.getDefaultLootTable().orElseThrow(),
            EntityType.PUFFERFISH.getDefaultLootTable().orElseThrow(),
            EntityType.SILVERFISH.getDefaultLootTable().orElseThrow(),
            EntityType.ILLUSIONER.getDefaultLootTable().orElseThrow(),
            EntityType.PIG.getDefaultLootTable().orElseThrow(),
            EntityType.PILLAGER.getDefaultLootTable().orElseThrow(),
            EntityType.ILLUSIONER.getDefaultLootTable().orElseThrow(),
            EntityType.ZOMBIE.getDefaultLootTable().orElseThrow(),
            EntityType.CREEPER.getDefaultLootTable().orElseThrow(),
            EntityType.SPIDER.getDefaultLootTable().orElseThrow(),
            EntityType.SKELETON.getDefaultLootTable().orElseThrow(),
            EntityType.WITCH.getDefaultLootTable().orElseThrow(),
            EntityType.BREEZE.getDefaultLootTable().orElseThrow(),
            EntityType.ARMADILLO.getDefaultLootTable().orElseThrow()
    ).map(ResourceKey::location).toList();

    private static final List<ResourceLocation> OVERWORLD_BLOCKS = Stream.of(
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
            Blocks.WHITE_CANDLE,
            Blocks.GRAY_CARPET,
            Blocks.GRAY_WOOL,
            Blocks.BLUE_WOOL,
            Blocks.LIGHT_BLUE_WOOL,
            Blocks.CYAN_WOOL,
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
            Blocks.BLUE_TERRACOTTA,
            Blocks.YELLOW_TERRACOTTA,
            Blocks.RED_TERRACOTTA,
            Blocks.CYAN_TERRACOTTA,
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
    ).distinct().map(block -> block.getLootTable().orElseThrow().location()).toList();

    // store all overworld obtainable things in here
    private static final List<ResourceLocation> ALL_OVERWORLD = new ArrayList<>();

    // nether
    private static final List<ResourceLocation> NETHER_BLOCKS = Stream.of(
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
    ).distinct().map(block -> block.getLootTable().orElseThrow().location()).toList();

    // store all nether obtainable things in here
    private static final List<ResourceLocation> ALL_NETHER = new ArrayList<>();

    private static final List<ResourceLocation> NETHER_LOOT = Stream.of(
            // chests
            BuiltInLootTables.BASTION_BRIDGE,
            BuiltInLootTables.BASTION_OTHER,
            BuiltInLootTables.BASTION_HOGLIN_STABLE,
            BuiltInLootTables.BASTION_TREASURE,
            BuiltInLootTables.NETHER_BRIDGE,
            BuiltInLootTables.PIGLIN_BARTERING,

            // mobs
            EntityType.BLAZE.getDefaultLootTable().orElseThrow(),
            EntityType.PIGLIN.getDefaultLootTable().orElseThrow(),
            EntityType.PIGLIN_BRUTE.getDefaultLootTable().orElseThrow(),
            EntityType.ZOGLIN.getDefaultLootTable().orElseThrow(),
            EntityType.HOGLIN.getDefaultLootTable().orElseThrow(),
            EntityType.GHAST.getDefaultLootTable().orElseThrow(),
            EntityType.WITHER_SKELETON.getDefaultLootTable().orElseThrow(),
            EntityType.MAGMA_CUBE.getDefaultLootTable().orElseThrow()
    ).map(ResourceKey::location).toList();

    static void init(MinecraftServer server) {
        ITEM_REGISTRY = server.registryAccess().lookupOrThrow(Registries.ITEM);
        RESULT_MAP.defaultReturnValue(Collections.emptySet());
        INGREDIENT_MAP.defaultReturnValue(Int2ObjectMaps.emptyMap());

        data = VerifierSaveData.get(server.overworld().getDataStorage());

        ENDER_EYE = ITEM_REGISTRY.getKey(Items.ENDER_EYE);
        OBSIDIAN = ITEM_REGISTRY.getKey(Items.OBSIDIAN);

        if (data.fromDisk) {
            LOGGER.info("Loading saved completability data!");
            for (ModificationData modificationData : data.modificationData) {
                LootRandomizer.registerSpecialDrop(modificationData.table, modificationData.original, modificationData.replacement);
            }
            LOGGER.info("Loaded {} entries!", data.modificationData.size());
            return;
        }

        for (ResourceLocation recipe : RecipeRandomizer.getKnownRecipes()) {
            ResourceLocation result = RecipeRandomizer.getResultFor(recipe);

            addRecipe(RecipeRandomizer.getIngredients(recipe), result, recipe);
        }

        for (ResourceLocation table : LootRandomizer.getKnownTables()) {
            addLootTable(table, LootRandomizer.getDrops(table));
        }

        // todo add villager trades
        // todo ensure emerald is craftable

        ALL_OVERWORLD.clear();
        ALL_OVERWORLD.addAll(OVERWORLD_BLOCKS);
        ALL_OVERWORLD.addAll(OVERWORLD_LOOT);

        ALL_NETHER.clear();
        ALL_NETHER.addAll(NETHER_BLOCKS);
        ALL_NETHER.addAll(NETHER_LOOT);
        ALL_NETHER.removeIf(ALL_OVERWORLD::contains);
    }

    public static void dispose() {
        RESULT_MAP.clear();
        INGREDIENT_MAP.clear();
        COMPLETION_QUEUE.clear();
        RECIPE_PATH.clear();
        data = null;
    }

    public static void addRecipe(Set<JsonElement> ingredients, ResourceLocation output, ResourceLocation recipe) {
        if (!RandomizerConfig.ensureCompletability) return;

        int i = 0;
        for (JsonElement ing : ingredients) {
            Set<ResourceLocation> items = new ObjectOpenHashSet<>();

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

    public static void addLootTable(ResourceLocation table, Set<ResourceLocation> stacks) {
        if (!RandomizerConfig.ensureCompletability) return;
        for (ResourceLocation stack : stacks) {
            addResult(stack, table);
        }
        if (LootRandomizer.isBlock(table)) {
            ResourceLocation block = LootRandomizer.getBlockFor(table);
            if (block == null) return;
            addIngredient(table, block);
        }
        if (LootRandomizer.isEntityDrop(table)) {
            ResourceLocation egg = LootRandomizer.getEggForEntityTable(table);
            if (egg == null) return;
            addIngredient(table, egg);
        }
    }

    private static void parseJson(JsonElement element, Set<ResourceLocation> items) {
        String s = element.getAsString();
        if (s.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.parse(s.substring(1));
            TAG_MAP.computeIfAbsent(tag, (ResourceLocation k) -> ITEM_REGISTRY.get(ItemTags.create(k))
                    .map(holders -> holders.stream()
                            .map(Holder::get)
                            .map(ITEM_REGISTRY::getKey)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toUnmodifiableSet()))
                    .orElse(Collections.emptySet()));
            items.add(tag);
        } else{
            items.add(ResourceLocation.parse(s));
        }
    }

    private static void addIngredient(@NotNull ResourceLocation recipe, @NotNull ResourceLocation item) {
        addIngredients(recipe, 0, Set.of(item));
    }

    private static void addIngredients(@NotNull ResourceLocation recipe, @NotNull Set<ResourceLocation> items) {
        addIngredients(recipe, 0, items);
    }

    private static void addIngredients(@NotNull ResourceLocation recipe, int index, @NotNull Set<ResourceLocation> items) {
        if (items.isEmpty()) return;
        INGREDIENT_MAP.computeIfAbsent(recipe, k -> new Int2ObjectArrayMap<>())
                .computeIfAbsent(index, i -> new ObjectOpenHashSet<>())
                .addAll(items.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet()));
    }

    private static boolean isTag(ResourceLocation location) {
        return TAG_MAP.containsKey(location);
    }

    private static void addResult(ResourceLocation item, ResourceLocation recipe) {
        RESULT_MAP.computeIfAbsent(item, k -> new ObjectOpenHashSet<>()).add(recipe);
    }

    private static boolean modifyRecipe(ResourceLocation table, ResourceLocation ingredient) {
        // certain loot tables do not have drops, so are not able to be modified
        if (!isLoot(table)) return false;

        // we will modify this recipe to give this ingredient
        MODIFY_RECIPES.put(table, ingredient);
        if (RandomizerConfig.enableDebug) {
            LOGGER.debug("Table '{}' will be modified to drop '{}'", table, ingredient);
        }
        return true;
    }

    private static void commitModifiedRecipes() {
        if (RandomizerConfig.enableDebug) {
            LOGGER.debug("Commiting {} modified recipes", MODIFY_RECIPES.size());
        }
        for (ResourceLocation table : MODIFY_RECIPES.keySet()) {
            // modify
            // pick a random item from this table to replace with the value ingredient
            Set<ResourceLocation> drops = LootRandomizer.getItems(table);
            ResourceLocation randomDrop = drops.stream().findAny().orElseThrow();
            // map this random drop to the failed item, specifically for this table
            LootRandomizer.registerSpecialDrop(table, randomDrop, MODIFY_RECIPES.get(table));
            computeCompletion(table);
            data.addEntry(table, randomDrop, MODIFY_RECIPES.get(table));
        }

        if (!MODIFY_RECIPES.isEmpty())
            data.setDirty(true);
    }

    static void ensureCompletability() {
        if (data.fromDisk) {
            // we loaded from disk, no need to check again
            return;
        }

        PRINT_PATH.add(Component.translatable("Iterating all ender eye recipes"));
        boolean validRecipe = ensureCompletability(ENDER_EYE, true);

        if (requiresNether) {
            RECIPE_PATH.clear();
            PRINT_PATH.add(Component.translatable("Requires nether access, iterating obsidian recipes"));
            LOGGER.info("Nether access is required!");
            validRecipe = ensureCompletability(OBSIDIAN, true);
            if (!validRecipe) {
                LOGGER.warn("Obsidian is not obtainable!");
            }
            for (ResourceLocation location : COMPLETION_QUEUE) {
                COMPLETABILITY_CACHE.put(location, validRecipe);
            }
        }

        commitModifiedRecipes();

        if (validRecipe) {
            isCompletable = true;
        }

        for (Component component : PRINT_PATH) {
            LOGGER.debug(component.getString());
        }

        if (!isCompletable) {
            LOGGER.info("Game is Incompletable!");
        }
    }

    /**
     * @param ingredient the registry location of the item ingredient
     * @return true if this ingredient is obtainable from common blocks in the overworld or nether
     */
    private static boolean ensureCompletability(ResourceLocation ingredient) {
        return ensureCompletability(ingredient, false);
    }

    /**
     * @param ingredient the registry location of the item ingredient
     * @param init if this is the first method call
     * @return true if this ingredient is obtainable from common blocks in the overworld or nether
     */
    private static boolean ensureCompletability(ResourceLocation ingredient, boolean init) {
        Set<ResourceLocation> recipes = RESULT_MAP.get(ingredient);

        if (recipes.isEmpty()) {
            if (RandomizerConfig.enableDebug) {
                LOGGER.debug("No recipes found for ingredient: {}!", ingredient);
            }
            // we should walk back later
            return false;
        }

        if (RandomizerConfig.enableDebug) {
            LOGGER.debug("Iterating recipes that make '{}'", ingredient);
            LOGGER.debug("{} recipes found: {}", recipes.size(), recipes);
        }

        Set<ResourceLocation> passed = iterateRecipes(recipes, false, true);

        if (!passed.isEmpty()) return true;

        passed = iterateRecipes(recipes, true, true);

        // select a recipe to modify
        if (passed.isEmpty()) {
            // i shouldn't modify recipes just yet
            // should just store it for later
            ResourceLocation random = RandomizerUtil.getRandom(ALL_OVERWORLD, RandomizerCore.seededRNG);
            print("Ingredient '%s' can be obtained from '%s'", ingredient, random);
            return modifyRecipe(random, ingredient);
        } else {
            // this might be duplicated?
            print("Ingredient '%s' can be obtained from %s", ingredient, passed);
            return true;
        }
    }

    private static void print(String key, Object... args) {
        PRINT_PATH.add(Component.translatable(key, args));
    }

    private static Set<ResourceLocation> iterateRecipes(Set<ResourceLocation> recipes, boolean deep, boolean init) {
        // quick iterate
        if (!deep) {
            Set<ResourceLocation> obtainableRecipes = recipes.stream()
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
        for (ResourceLocation recipe : recipes) {

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

            ObjectCollection<Set<ResourceLocation>> indexedIngredients = INGREDIENT_MAP.get(recipe).values();

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

    private static Set<ResourceLocation> expandIngredients(Set<ResourceLocation> compactIngredients) {
        return compactIngredients.stream()
                .flatMap(location -> isTag(location) ? TAG_MAP.get(location).stream() : Stream.of(location))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean quickIterateIngredient(Set<ResourceLocation> ingredients, Set<ResourceLocation> iterated, Set<ResourceLocation> failed) {
        for (ResourceLocation ingredient : ingredients) {
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
            Set<ResourceLocation> quickSearch = iterateRecipes(RESULT_MAP.get(ingredient), false, false);

            if (!quickSearch.isEmpty()) {
                logIngredient(ingredient, RECIPE_PATH.peekLast(), true);
                print("Ingredient '%s' can be obtained from %s", ingredient, quickSearch);
                return computeCompletion(ingredient);
            } else if (!isTag(ingredient)) {
                // sometimes compact ingredients can be normal items
                failed.add(ingredient);
            }
        }
        return false;
    }

    private static boolean deepSearch(Set<ResourceLocation> ingredients, ResourceLocation recipe) {
        for (ResourceLocation ingredient : ingredients) {
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

    private static boolean iterateIngredients(ObjectCollection<Set<ResourceLocation>> ingredientMap, ResourceLocation recipe) {
        // for each "index"
        int craftableSlots = ingredientMap.size();

        if (RandomizerConfig.enableDebug) {
            LOGGER.debug("Currently iterating recipe '{}' for their ingredients", recipe);
        }

        for (Set<ResourceLocation> compactIngredients : ingredientMap) {
            if (compactIngredients.isEmpty()) {
                logEmptyIngredients(recipe);
                continue;
            }

            Set<ResourceLocation> iterated = new ObjectOpenHashSet<>();
            Set<ResourceLocation> failed = new ObjectOpenHashSet<>();

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

    private static boolean canObtainIngredient(ResourceLocation ingredient, ResourceLocation recipe) {
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

    private static boolean isLoot(ResourceLocation key) {
        return LootRandomizer.hasTable(key);
    }

    private static boolean checkLoot(ResourceLocation table) {
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

    private static boolean computeCompletion(ResourceLocation location) {
        return computeCompletion(location, k -> true);
    }

    private static boolean computeCompletion(ResourceLocation location, Predicate<ResourceLocation> predicate) {
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

        ResourceLocation last = RECIPE_PATH.removeLast();
        if (!RandomizerConfig.enableDebug) return;
        if (success) {
            LOGGER.debug("Back to recipe '{}'", RECIPE_PATH.peekLast());
            PRINT_PATH.add(Component.translatable("Recipe %s is obtainable!", last));
        } else {
            LOGGER.debug("Recipe '{}' is not obtainable, back to recipe '{}'", last, RECIPE_PATH.peekLast());
        }
    }

    private static boolean addToPath(ResourceLocation recipe) {
        if (RECIPE_PATH.contains(recipe)) return false;
        RECIPE_PATH.add(recipe);
        return true;
    }

    private static void logIngredient(Object ingredient, ResourceLocation recipe, boolean success) {
        if (!RandomizerConfig.enableDebug) return;
        if (success) {
            LOGGER.debug("Ingredient '{}' in recipe '{}' is obtainable!", ingredient, recipe);
        } else {
            LOGGER.debug("All ingredients for recipe '{}' are unobtainable!", recipe);
        }
    }

    private static void logEmptyIngredients(ResourceLocation recipe) {
        if (!RandomizerConfig.enableDebug) return;
        String type = isLoot(recipe) ? "Table" : "Recipe";
        LOGGER.debug("{} '{}' has a set of ingredients that is empty!", type, recipe);
    }

    private static class VerifierSaveData extends SavedData {

        public static final Codec<VerifierSaveData> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<T> encode(VerifierSaveData saveData, DynamicOps<T> dynamicOps, T t) {
                return CompoundTag.CODEC.encode(data.save(), dynamicOps, t);
            }

            @Override
            public <T> DataResult<Pair<VerifierSaveData, T>> decode(DynamicOps<T> dynamicOps, T t) {
                return CompoundTag.CODEC.decode(dynamicOps, t)
                        .map(p -> p.mapFirst(VerifierSaveData::load));
            }
        };

        public static final SavedDataType<VerifierSaveData> FACTORY = new SavedDataType<>("%s_modified_data".formatted(RandomizerCore.MODID),
                VerifierSaveData::new, VerifierSaveData.CODEC, DataFixTypes.LEVEL);


        public static VerifierSaveData get(DimensionDataStorage storage) {
            return storage.computeIfAbsent(FACTORY);
        }

        /**
         * Maps a loot table id to a map of a recipe to a set of its ingredients that needs to be modified
         */
        private final List<ModificationData> modificationData = new ArrayList<>();

        public boolean fromDisk = false;

        public void addEntry(ResourceLocation table, ResourceLocation original, ResourceLocation replacement) {
            addEntry(new ModificationData(table, original, replacement));
        }

        private void addEntry(ModificationData data) {
            if (!modificationData.contains(data))
                modificationData.add(data);
        }

        public @NotNull CompoundTag save() {
            return save(new CompoundTag());
        }

        public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
            ListTag data = new ListTag();
            LOGGER.info("Saving Verification Data!");
            for (ModificationData table : modificationData) {
                data.add(table.toNBT());
            }
            LOGGER.info("Wrote {} entries!", modificationData.size());
            tag.put("data", data);
            return tag;
        }

        public static VerifierSaveData load(CompoundTag tag) {
            VerifierSaveData data = new VerifierSaveData();
            tag.getList("data")
                    .map(ListTag::stream)
                    .ifPresent(stream -> stream
                            .map(Tag::asCompound)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .map(ModificationData::fromNBT)
                            .filter(data1 -> LootRandomizer.getKnownTables().contains(data1.table()))
                            .forEach(data::addEntry));

            if (!data.modificationData.isEmpty())
                data.fromDisk = true;

            return data;
        }
    }

    private record ModificationData(ResourceLocation table, ResourceLocation original, ResourceLocation replacement) {

        public CompoundTag toNBT() {
            return CompoundTag.builder()
                    .put("id", table.toString())
                    .put("original", original.toString())
                    .put("replacement", replacement.toString())
                    .build();
        }

        public static ModificationData fromNBT(CompoundTag tag) {
            return new ModificationData(
                    ResourceLocation.parse(tag.getString("id").orElseThrow()),
                    ResourceLocation.parse(tag.getString("original").orElseThrow()),
                    ResourceLocation.parse(tag.getString("replacement").orElseThrow())
            );
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ModificationData modificationData &&
                    this.table().equals(modificationData.table());
        }
    }
}
