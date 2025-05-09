package com.ghzdude.randomizer;

import com.ghzdude.randomizer.loot.LootRandomizer;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
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
import java.util.stream.Collectors;
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
    // this stores tag locations, and also a set of item locations if no tags for a given slot
    private static final Object2ObjectMap<ResourceLocation, Int2ObjectMap<Set<ResourceLocation>>> INGREDIENT_MAP = new Object2ObjectOpenHashMap<>();

    /**
     * maps a recipe to its result item.
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

    private static boolean requiresNether = false;
    private static boolean isCompletable = false;

    private static Registry<Item> REGISTRY;

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
            addLootTable(key, LootRandomizer.getIngredients(key));
        }
    }

    public static void addRecipe(NonNullList<Ingredient> ingredients, ItemStack output, ResourceLocation recipe) {
        if (!RandomizerConfig.ensureCompletability) return;

        int i = 0;
        for (Ingredient ing : ingredients.stream().distinct().toList()) {
            DataResult<JsonElement> result = Ingredient.CODEC.encodeStart(JsonOps.INSTANCE, ing);
            if (result.isError()) continue;
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

            for (ResourceLocation item : items) {
                addIngredient(recipe, i, item);
            }
            
            i++;
        }

        addResult(REGISTRY.getKey(output.getItem()), recipe);
    }

    private static void parseJson(JsonObject object, Set<ResourceLocation> items) {
        if (object.has("tag")) {
            ResourceLocation tag = ResourceLocation.parse(object.get("tag").getAsString());
            TAG_MAP.computeIfAbsent(tag, (ResourceLocation k) -> REGISTRY.getTag(TagKey.create(Registries.ITEM, k))
                    .map(holders -> holders.stream()
                            .map(Holder::value)
                            .map(REGISTRY::getKey)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toUnmodifiableSet()))
                    .orElse(Collections.emptySet()));
            items.add(tag);
        } else if (object.has("item")) {
            items.add(ResourceLocation.parse(object.get("item").getAsString()));
        }
    }

    public static void addLootTable(ResourceLocation table, Set<ResourceLocation> stacks) {
        if (!RandomizerConfig.ensureCompletability) return;
        for (ResourceLocation stack : stacks) {
            addIngredient(table, 0, stack);
            addResult(stack, table);
        }
    }

    private static void addIngredient(ResourceLocation recipe, int index, ResourceLocation item) {
        INGREDIENT_MAP.computeIfAbsent(recipe, k -> new Int2ObjectArrayMap<>())
                .computeIfAbsent(index, i -> new ObjectOpenHashSet<>())
                .add(item);
    }

    private static boolean isTag(ResourceLocation location) {
        return TAG_MAP.containsKey(location);
    }

    private static void addResult(ResourceLocation item, ResourceLocation recipe) {
        RESULT_MAP.computeIfAbsent(item, k -> new ObjectOpenHashSet<>()).add(recipe);
        RECIPE_MAP.put(recipe, item);
    }

    private static void modifyRecipe(ResourceLocation table, ResourceLocation ingredient) {
        // we will modify this recipe to give this ingredient
        MODIFY_RECIPES.put(table, ingredient);

        if (RandomizerConfig.enableDebug) {
            RandomizerCore.LOGGER.debug("Table '{}' will be modified to give '{}'", table, ingredient);
        }
    }

    private static void commitModifiedRecipes() {
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
        Object2ObjectMap<ResourceLocation, String> pathMap = new Object2ObjectOpenHashMap<>();

        Set<ResourceLocation> recipes = RESULT_MAP.get(ENDER_EYE);

        if (recipes.isEmpty()) {
            RandomizerCore.LOGGER.error("No recipes that give an ender eye, something is seriously wrong!");
            return;
        }

        boolean validRecipe = false;
        for (ResourceLocation recipe : recipes) {
            Int2ObjectMap<Set<ResourceLocation>> ingredientSet = INGREDIENT_MAP.get(recipe);

            if (isLoot(recipe)) {
                validRecipe = computeCompletion(recipe, CompletabilityVerifier::checkLoot);
                if (validRecipe) break;
            }

            int obtainableSlots = 0;
            for (int index : ingredientSet.keySet()) {
                Set<ResourceLocation> ingredients = ingredientSet.get(index).stream()
                        .flatMap(location -> isTag(location) ? TAG_MAP.get(location).stream() : Stream.of(location))
                        .collect(Collectors.toUnmodifiableSet());

                boolean obtainable;
                for (ResourceLocation ingredient : ingredients) {
                    obtainable = computeCompletion(ingredient, CompletabilityVerifier::ensureCompletability);
                    pathMap.put(ingredient, printPath());
                    if (obtainable) {
                        obtainableSlots++;
                        break;
                    }
                }
            }

            if (obtainableSlots != ingredientSet.size()) {
                pathMap.clear();
                continue;
            }

            validRecipe = true;
            break;
        }

        if (requiresNether) {
            recipes = RESULT_MAP.get(OBSIDIAN);

            if (recipes.isEmpty()) {
                RandomizerCore.LOGGER.error("No recipes that give obsidian, something is seriously wrong!");
                return;
            }

            validRecipe = false;
            for (ResourceLocation recipe : recipes) {

                // loot tables need to be checked more carefully
                if (NETHER_LOOT.contains(recipe) || NETHER_BLOCKS.contains(recipe)) {
                    // we can't get this, skip
                    continue;
                } else if (OVERWORLD_LOOT.contains(recipe) || OVERWORLD_BLOCKS.contains(recipe)) {
                    // this is obtainable
                    validRecipe = true;
                    break;
                }

                // otherwise treat as recipe

                Int2ObjectMap<Set<ResourceLocation>> ingredientSet = INGREDIENT_MAP.get(recipe);

                int obtainableSlots = 0;
                for (int index : ingredientSet.keySet()) {
                    Set<ResourceLocation> ingredients = ingredientSet.get(index).stream()
                            .flatMap(location -> isTag(location) ? TAG_MAP.get(location).stream() : Stream.of(location))
                            .collect(Collectors.toUnmodifiableSet());

                    boolean obtainable = false;
                    for (ResourceLocation ingredient : ingredients) {
                        if (canObtainIngredient(ingredient, recipe)) {
                            obtainable = true;
                        }
                        pathMap.put(ingredient, printPath());
                        if (obtainable) break;
                    }
                    if (obtainable) obtainableSlots++;
                }

                if (obtainableSlots == ingredientSet.size()) {
                    validRecipe = true;
                    break;
                } else {
                    pathMap.clear();
                }
            }
        }

        commitModifiedRecipes();

        if (validRecipe) {
            for (ResourceLocation location : COMPLETION_QUEUE) {
                COMPLETABILITY_CACHE.put(location, true);
            }
            int i = 0;
            for (ResourceLocation ing : pathMap.keySet()) {
                if (COMPLETABILITY_CACHE.getBoolean(ing)) {
                    RandomizerCore.LOGGER.info("can craft \"{}\"\n{}", ing, pathMap.get(ing));
                    i++;
                } else {
                    RandomizerCore.LOGGER.info("unable to craft \"{}\"\n{}", ing, pathMap.get(ing));
                }
            }

            isCompletable = i == pathMap.size();
        }

        if (!isCompletable) {
            RandomizerCore.LOGGER.info("Game is Incompletable!");
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
                RandomizerCore.LOGGER.debug("No recipes found for ingredient: {}!", ingredient);
            }
            return false;
        }

        if (RandomizerConfig.enableDebug) {
            RandomizerCore.LOGGER.debug("Iterating {} recipes for ingredient {}", recipes.size(), ingredient);
            RandomizerCore.LOGGER.debug("Recipes found: {}", recipes);
        }

        for (ResourceLocation recipe : recipes) {
            // we are already walking this recipe, skip
            if (recipePath.contains(recipe)) continue;

            // handle loot first, then treat as recipe
            if (isLoot(recipe)) {
                if (checkLoot(recipe)) {
                    return true;
                } else continue;
            }

            // this recipe does not exist in map, skip
            if (!INGREDIENT_MAP.containsKey(recipe)) continue;

            Int2ObjectMap<Set<ResourceLocation>> ingredientSet = INGREDIENT_MAP.get(recipe);

            // this recipe has no ingredients to check, skip
            if (ingredientSet.isEmpty()) continue;

            // path adding is handled in check loot
            recipePath.add(recipe);


            // for each index
            Int2ObjectMap<ResourceLocation> failed = new Int2ObjectArrayMap<>(ingredientSet.size());
            for (int index : ingredientSet.keySet()) {
                Set<ResourceLocation> ingredients = ingredientSet.get(index).stream()
                        .flatMap(location -> isTag(location) ? TAG_MAP.get(location).stream() : Stream.of(location))
                        .collect(Collectors.toUnmodifiableSet());

                if (RandomizerConfig.enableDebug) {
                    RandomizerCore.LOGGER.debug("index '{}' for recipe '{}' has these ingredients: \n{}", index, recipe, ingredients);
                }

                boolean obtainable = false;
                for (ResourceLocation ing : ingredients) {
                    // for each ingredient
                    // if any ingredient is obtainable, break
                    if (canObtainIngredient(ing, recipe)) {
                        obtainable = true;
                        break;
                    }
                }

                if (!obtainable) {
                    // put a random ingredient for this index
                    failed.put(index, ingredients.stream().findAny().orElseThrow());
                }
            }

            // every ingredient slot is obtainable
            if (failed.isEmpty()) return true;

            for (int i : failed.keySet()) {
                // some slots were not craftable, change them
                // get a random, known recipe that can be obtained
                // cab be block drops, chest loot, mob drops,
                // this will need to be cached to a file somewhere
                ResourceLocation random = RandomizerUtil.getRandom(OVERWORLD_LOOT, RandomizerCore.seededRNG);

                // this recipe must craft the needed item
                // store the recipe
                // recipes will always be loot tables
                modifyRecipe(random, failed.get(i));
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
        if (OVERWORLD_LOOT.contains(table) || OVERWORLD_BLOCKS.contains(table)) {
            recipePath.add(table);
            return computeCompletion(table);

        } else if (NETHER_LOOT.contains(table) || NETHER_BLOCKS.contains(table)) {
            recipePath.add(table);
            requiresNether = true;
            // defer completion for later
            COMPLETION_QUEUE.add(table);
            return true;
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
                // ingredient in recipe
                b.append("recipe={%s in %s}".formatted(RECIPE_MAP.get(loc), loc));
            }
            if (i++ != recipePath.size() - 1) {
                b.append('\n');
            }
        }
        recipePath.clear();
        return b.toString();
    }
}
