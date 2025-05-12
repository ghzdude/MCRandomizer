package com.ghzdude.randomizer;

import com.ghzdude.randomizer.loot.LootRandomizer;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
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

    /**
     * maps a recipe to its result item. loot table are not in this map. this is only used for the printing the recipe path
     */
    private static final Object2ObjectMap<ResourceLocation, ResourceLocation> RECIPE_MAP = new Object2ObjectOpenHashMap<>();

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

    public static ResourceLocation ENDER_EYE;
    public static ResourceLocation OBSIDIAN;

    private static final Deque<ResourceLocation> recipePath = new ArrayDeque<>();
    private static final Deque<ResourceLocation> COMPLETION_QUEUE = new ArrayDeque<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean requiresNether = false;
    private static boolean isCompletable = false;

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
//            BuiltInLootTables.TRIAL_CHAMBERS_REWARD,
//            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_COMMON,
//            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_RARE,
//            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_UNIQUE,
//            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS,
//            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_COMMON,
//            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_RARE,
//            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_UNIQUE,
//            BuiltInLootTables.TRIAL_CHAMBERS_SUPPLY,
//            BuiltInLootTables.TRIAL_CHAMBERS_CORRIDOR,
//            BuiltInLootTables.TRIAL_CHAMBERS_INTERSECTION,
//            BuiltInLootTables.TRIAL_CHAMBERS_INTERSECTION_BARREL,
//            BuiltInLootTables.TRIAL_CHAMBERS_ENTRANCE,
//            BuiltInLootTables.TRIAL_CHAMBERS_CORRIDOR_DISPENSER,
//            BuiltInLootTables.TRIAL_CHAMBERS_CHAMBER_DISPENSER,
//            BuiltInLootTables.TRIAL_CHAMBERS_WATER_DISPENSER,
//            BuiltInLootTables.TRIAL_CHAMBERS_CORRIDOR_POT,
//            BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER,
//            BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER_RANGED,
//            BuiltInLootTables.EQUIPMENT_TRIAL_CHAMBER_MELEE,
            BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_COMMON,
            BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_RARE,
            BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY,
            BuiltInLootTables.DESERT_WELL_ARCHAEOLOGY,
            BuiltInLootTables.OCEAN_RUIN_COLD_ARCHAEOLOGY,
            BuiltInLootTables.OCEAN_RUIN_WARM_ARCHAEOLOGY,

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
    ).distinct().map(block -> block.getLootTable().location()).toList();

    // store all overworld obtainable things in here
    private static final List<ResourceLocation> ALL_OVERWORLD = new ArrayList<>();

    // nether
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
            BuiltInLootTables.PIGLIN_BARTERING,

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
        ITEM_REGISTRY = server.registryAccess().registryOrThrow(Registries.ITEM);
        RESULT_MAP.defaultReturnValue(Collections.emptySet());
        INGREDIENT_MAP.defaultReturnValue(Int2ObjectMaps.emptyMap());

        ENDER_EYE = ITEM_REGISTRY.getKey(Items.ENDER_EYE);
        OBSIDIAN = ITEM_REGISTRY.getKey(Items.OBSIDIAN);

        for (ResourceLocation recipe : RecipeRandomizer.getKnownRecipes()) {
            ResourceLocation result = RecipeRandomizer.getResultFor(recipe);

            addRecipe(RecipeRandomizer.getIngredients(recipe), result, recipe);
        }

        for (ResourceLocation table : LootRandomizer.getKnownTables()) {
            addLootTable(table, LootRandomizer.getDrops(table));
        }

        ITEM_REGISTRY.stream()
                .map(item -> item instanceof SpawnEggItem egg ? egg : null)
                .filter(Objects::nonNull)
                .forEach(item -> {
                    EntityType<?> type = item.getType(item.getDefaultInstance());
                    addIngredient(type.getDefaultLootTable().location(), Objects.requireNonNull(ITEM_REGISTRY.getKey(item)));
                });

        ALL_OVERWORLD.clear();
        ALL_OVERWORLD.addAll(OVERWORLD_BLOCKS);
        ALL_OVERWORLD.addAll(OVERWORLD_LOOT);
    }

    public static void addRecipe(List<Ingredient> ingredients, ResourceLocation output, ResourceLocation recipe) {
        if (!RandomizerConfig.ensureCompletability) return;

        int i = 0;
        for (Ingredient ing : ingredients.stream().distinct().toList()) {
            DataResult<JsonElement> result = Ingredient.CODEC.encodeStart(JsonOps.INSTANCE, ing);

            if (result.isError()) {
                if (RandomizerConfig.enableDebug)
                    LOGGER.debug("Failed to read ingredient '{}' in recipe '{}}'!", ing, recipe);
                continue;
            }

            Optional<JsonElement> optional = result.result();
            if (optional.isEmpty()) continue;

            Set<ResourceLocation> items = new ObjectOpenHashSet<>();

            JsonElement element = optional.get();
            if (element.isJsonArray()) {
                for (JsonElement e : element.getAsJsonArray()) {
                    parseJson(e.getAsJsonObject(), items);
                }
            } else {
                parseJson(element.getAsJsonObject(), items);
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
            addIngredient(table, LootRandomizer.getBlockFor(table));
        }
    }

    private static void parseJson(JsonObject object, Set<ResourceLocation> items) {
        if (object.has("tag")) {
            ResourceLocation tag = ResourceLocation.parse(object.get("tag").getAsString());
            TAG_MAP.computeIfAbsent(tag, (ResourceLocation k) -> ITEM_REGISTRY.getTag(TagKey.create(Registries.ITEM, k))
                    .map(holders -> holders.stream()
                            .map(Holder::value)
                            .map(ITEM_REGISTRY::getKey)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toUnmodifiableSet()))
                    .orElse(Collections.emptySet()));
            items.add(tag);
        } else if (object.has("item")) {
            items.add(ResourceLocation.parse(object.get("item").getAsString()));
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
        if (!isLoot(recipe))
            RECIPE_MAP.put(recipe, item);
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
            COMPLETABILITY_CACHE.put(table, true);
        }
    }

    public static void ensureCompletability() {
        COMPLETABILITY_CACHE.clear();
        COMPLETION_QUEUE.clear();
        recipePath.clear();

        boolean validRecipe = ensureCompletability(ENDER_EYE);

        if (requiresNether) {
            LOGGER.info("Nether access is required!");
            validRecipe = ensureCompletability(OBSIDIAN);
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

        if (!isCompletable) {
            LOGGER.info("Game is Incompletable!");
        }
    }

    /**
     * @param ingredient the registry location of the item ingredient
     * @return true if this ingredient is obtainable from common blocks in the overworld or nether
     */
    private static boolean ensureCompletability(ResourceLocation ingredient) {
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

        int craftableRecipes = recipes.size();

        // iterate the recipes that make this ingredient
        for (ResourceLocation recipe : recipes) {

            // we are already walking this recipe, skip
            if (!addToPath(recipe)) continue;

            Int2ObjectMap<Set<ResourceLocation>> indexedIngredients = INGREDIENT_MAP.get(recipe);

            // this recipe does not exist in map, OR
            // this recipe has no ingredients to check, SKIP
            if (indexedIngredients.isEmpty()) {
                craftableRecipes--;
                walkBack(false);
                continue;
            }

            // we can't make anything give chest loot
            if (LootRandomizer.isChestLoot(recipe) && !checkLoot(recipe)) {
                // this is chest loot and it's end only
                craftableRecipes--;
                walkBack(false);
                continue;
            }

            if (iterateIngredients(indexedIngredients, recipe)) {
                // we succeed, but we still need to walk back
                // because we are done looking at this recipe
                walkBack(true);
                return true;
            }

            craftableRecipes--;
            walkBack(false);
        }

        boolean success = craftableRecipes != 0;

        // select a recipe to modify
        if (craftableRecipes == 0) {
            ResourceLocation random = RandomizerUtil.getRandom(ALL_OVERWORLD, RandomizerCore.seededRNG);
            success = modifyRecipe(random, ingredient);
        }

        return success;
    }

    private static Set<ResourceLocation> expandIngredients(Set<ResourceLocation> compactIngredients) {
        return compactIngredients.stream()
                .flatMap(location -> isTag(location) ? TAG_MAP.get(location).stream() : Stream.of(location))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean quickIterate(Set<ResourceLocation> ingredients, Set<ResourceLocation> iterated, Set<ResourceLocation> failed) {
        boolean quickSearch = false;
        for (ResourceLocation ingredient : ingredients) {
            if (!iterated.add(ingredient)) continue;
            if (RESULT_MAP.get(ingredient).isEmpty()) {
                if (!isTag(ingredient))
                    failed.add(ingredient);
                continue;
            }

            if (COMPLETABILITY_CACHE.containsKey(ingredient)) {
                if (COMPLETABILITY_CACHE.getBoolean(ingredient)) continue;
            }

            // iterate recipes that give this ingredient
            for (ResourceLocation r : RESULT_MAP.get(ingredient)) {
                if (isLoot(r) && checkLoot(r)) {
                    quickSearch = true;
                    break;
                }
            }

            if (quickSearch) {
                logIngredient(ingredient, recipePath.peekLast(), true);
                COMPLETABILITY_CACHE.put(ingredient, true);
                break;
            } else if (!isTag(ingredient)) {
                // sometimes compact ingredients can be normal items
                failed.add(ingredient);
            }
        }
        return quickSearch;
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

    private static boolean iterateIngredients(Int2ObjectMap<Set<ResourceLocation>> ingredientMap, ResourceLocation recipe) {
        // for each "index"
        int craftableSlots = ingredientMap.size();
        for (Set<ResourceLocation> compactIngredients : ingredientMap.values()) {

            if (compactIngredients.isEmpty()) {
                logEmptyIngredients(recipe);
                continue;
            }

            Set<ResourceLocation> iterated = new ObjectOpenHashSet<>();
            Set<ResourceLocation> failed = new ObjectOpenHashSet<>();

            if (RandomizerConfig.enableDebug) {
                LOGGER.debug("Currently iterating recipe '{}' for their ingredients", recipe);
            }

            // quickly search compact ingredients if any are immediately obtainable
            if (quickIterate(compactIngredients, iterated, failed)) continue;

            // iterate expanded ingredients
            if (quickIterate(expandIngredients(compactIngredients), iterated, failed)) continue;

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

        } else if (NETHER_LOOT.contains(table) || NETHER_BLOCKS.contains(table)) {
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
        if (recipePath.isEmpty()) {
            LOGGER.error("Cannot walk back on empty path!");
            return;
        }

        ResourceLocation last = recipePath.removeLast();
        if (!RandomizerConfig.enableDebug) return;
        if (success) LOGGER.debug("Recipe '{}' is obtainable, back to recipe '{}'", last, recipePath.peekLast());
        else LOGGER.debug("Recipe '{}' is not obtainable, back to recipe '{}'", last, recipePath.peekLast());
    }

    private static boolean addToPath(ResourceLocation recipe) {
        if (recipePath.contains(recipe)) return false;
        recipePath.add(recipe);
        return true;
    }

    private static void logIngredient(Object ingredient, ResourceLocation recipe, boolean success) {
        if (!RandomizerConfig.enableDebug) return;
        if (success) LOGGER.debug("Ingredient '{}' in recipe '{}' is obtainable!", ingredient, recipe);
        else LOGGER.debug("All ingredients for recipe '{}' are unobtainable!", recipe);
    }

    private static void logEmptyIngredients(ResourceLocation recipe) {
        if (!RandomizerConfig.enableDebug) return;
        String type = isLoot(recipe) ? "Table" : "Recipe";
        LOGGER.debug("{} '{}' has a set of ingredients that is empty!", type, recipe);
    }
}
