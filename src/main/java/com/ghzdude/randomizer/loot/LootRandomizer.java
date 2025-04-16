package com.ghzdude.randomizer.loot;

import com.ghzdude.randomizer.RandomizationMapData;
import com.ghzdude.randomizer.RandomizerConfig;
import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.compat.jei.BlockDropRecipe;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public class LootRandomizer {

    private static RandomizationMapData INSTANCE = null;
    public static Registry<LootTable> LOOT_REGISTRY;
    public static Registry<Item> ITEM_REGISTRY;
    public static Registry<Block> BLOCK_REGISTRY;

    /**
     * Set of all the valid tables
     */
    private static final ObjectOpenHashSet<ResourceLocation> TABLES = new ObjectOpenHashSet<>();

    /**
     * Maps a block's loot table to the block
     */
    private static final Object2ObjectMap<ResourceLocation, ResourceLocation> BLOCK_MAP = new Object2ObjectOpenHashMap<>();

    /**
     * Maps a loot table to its drops
     */
    private static final Object2ObjectMap<ResourceLocation, Set<LootData>> LOOT_MAP = new Object2ObjectOpenHashMap<>();
    public static ResourceLocation activeLocation;

    private static final ObjectOpenHashSet<ResourceLocation> PICKAXE_MINABLE = new ObjectOpenHashSet<>();
    private static final ObjectOpenHashSet<ResourceLocation> REQUIRES_STONE = new ObjectOpenHashSet<>();
    private static final ObjectOpenHashSet<ResourceLocation> REQUIRES_IRON = new ObjectOpenHashSet<>();
    private static final ObjectOpenHashSet<ResourceLocation> REQUIRES_DIAMOND = new ObjectOpenHashSet<>();
    private static RecipeManager RECIPE_MANAGER;
    private static MinecraftServer SERVER;
    private static boolean appliesToAll;
    private static boolean requiresPick;

    public static void init(MinecraftServer server) {
        INSTANCE = RandomizationMapData.get(server, "loot");
        SERVER = server;
        LOOT_REGISTRY = server.reloadableRegistries().get().registryOrThrow(Registries.LOOT_TABLE);
        ITEM_REGISTRY = server.registryAccess().registryOrThrow(Registries.ITEM);
        BLOCK_REGISTRY = server.registryAccess().registryOrThrow(Registries.BLOCK);
        RECIPE_MANAGER = server.getRecipeManager();

        TagKey<Block> pickaxeMineable = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("mineable/pickaxe"));

        TagKey<Block> needsStone = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("needs_stone_tool"));
        TagKey<Block> needsIron = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("needs_iron_tool"));
        TagKey<Block> needsDiamond = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("needs_diamond_tool"));

//        TagKey<Block> notWooden = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_wooden_tool"));
//        TagKey<Block> notStone = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool"));
//        TagKey<Block> notIron = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_iron_tool"));
//        TagKey<Block> notGold = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_gold_tool"));
//        TagKey<Block> notDiamond = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_diamond_tool"));
//        TagKey<Block> notNetherite = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_netherite_tool"));

        collectFromTag(pickaxeMineable, PICKAXE_MINABLE);
        collectFromTag(needsStone, REQUIRES_STONE);
        collectFromTag(needsIron, REQUIRES_IRON);
        collectFromTag(needsDiamond, REQUIRES_DIAMOND);

        for (Block block : BLOCK_REGISTRY) {
            if (block == Blocks.AIR) continue;

            BLOCK_MAP.put(block.getLootTable().location(), BLOCK_REGISTRY.getKey(block));
        }

        RegistryOps<JsonElement> registryOps = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());

        RandomizerCore.LOGGER.warn("Iterating through loot tables!");

        for (LootTable table : LOOT_REGISTRY) {
            // serialize loot table into JSON for easy lookup
            DataResult<JsonElement> result = LootTable.DIRECT_CODEC.encodeStart(registryOps, table);
            if (result.isSuccess()) result.result()
                    .filter(JsonElement::isJsonObject)
                    .map(JsonElement::getAsJsonObject)
                    .ifPresent(LootRandomizer::handleJson);
        }

        RandomizerCore.LOGGER.warn("loot map size: {}", LOOT_MAP.size());

        for (ResourceLocation table : LOOT_MAP.keySet()) {
            Set<LootData> lootData = LOOT_MAP.get(table);

            if (isBlock(table)) {
                Block block = BLOCK_REGISTRY.get(BLOCK_MAP.get(table));
                if (block == null) continue;
                for (LootData entry : lootData) {
                    Item item = ITEM_REGISTRY.get(entry.item());
                    BlockDropRecipe.registerRecipe(block.asItem(), item, entry.getType(), table);
                }
            }
        }
    }

    private static void collectFromTag(TagKey<Block> key, Set<ResourceLocation> collection) {
        BLOCK_REGISTRY.getTag(key).ifPresent(blocks -> blocks.stream()
                .map(holder -> BLOCK_REGISTRY.getKey(holder.get()))
                .forEach(collection::add));
    }

    public static boolean requiresPick(ResourceLocation block) {
        return PICKAXE_MINABLE.contains(block);
    }

    private static void handleJson(JsonObject table) {
        if (!table.has("random_sequence") || !table.has("pools"))
            return;

        ResourceLocation id = ResourceLocation.parse(table.get("random_sequence").getAsString());
        activeLocation = id;

        if (!isBlacklisted(id)) TABLES.add(id);

        Set<LootData> items = LOOT_MAP.computeIfAbsent(id, k -> new ObjectOpenHashSet<>());

        requiresPick = isBlock(id) && PICKAXE_MINABLE.contains(BLOCK_MAP.get(id));
        boolean requiresShears = false;
        boolean requiresSilk = false;
        appliesToAll = false;

        List<JsonObject> pools = table.getAsJsonArray("pools")
                .asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .toList();

        for (JsonObject pool : pools) {
            List<JsonObject> entries = pool.getAsJsonArray("entries")
                    .asList().stream().map(JsonElement::getAsJsonObject).toList();

            if (pool.has("conditions")) {
                List<JsonObject> conditions = pool.getAsJsonArray("conditions")
                        .asList().stream().map(JsonElement::getAsJsonObject).toList();

                for (JsonObject condition : conditions) {
                    requiresSilk = hasCondition(condition, "match_tool", LootRandomizer::handleMatchTool);
                    requiresShears = handleShears(condition);
                }
                appliesToAll = requiresSilk || requiresShears;
            }

            for (JsonObject entry : entries) {
                if (isType(entry, "alternatives")) {
                    handleAlternatives(entry, items);
                } else if (isType(entry, "item")) {
                    handleItem(entry, items);
                } else {
                    RandomizerCore.LOGGER.debug("unhandled entry: {}", entry);
                }
            }
        }

        RandomizerCore.LOGGER.info("added {} entries for table '{}'", items.size(), id);
    }

    private static void addEntry(LootData data, Set<LootData> items) {
        if (items.add(data)) {
            RandomizerCore.LOGGER.info("added entry '{}' to table '{}'", data, activeLocation);
        }
    }

    private static ResourceLocation getName(JsonObject entry) {
        if (entry.has("name")) {
            return ResourceLocation.parse(entry.get("name").getAsString());
        }
        throw new IllegalArgumentException(String.format("Cannot get item from Entry '%s'", entry));
    }

    private static boolean isType(JsonObject object, String type) {
        if (!object.has("type")) return false;
        return object.get("type").getAsString().contains(type);
    }

    private static void handleAlternatives(JsonObject entry, Set<LootData> items) {
        entry.getAsJsonArray("children")
                .asList().stream().map(JsonElement::getAsJsonObject)
                .forEach(object -> handleItem(object, items));
    }

    private static void handleItem(JsonObject entry, Set<LootData> items) {
//        boolean requiresShears = false;
//        boolean requiresSilk = false;
        ResourceLocation item = getName(entry);
        LootData data = LootData.standard(item).pick(requiresPick);

//        if (appliesToAll) {
//            data.silk(requiresSilk);
//            data.shears(requiresShears);
//        } else
        {
            data.silk(hasCondition(entry, "match_tool", LootRandomizer::handleMatchTool));
            if (entry.has("functions")) {
                ResourceLocation smelted = null;
                List<JsonObject> functions = entry.getAsJsonArray("functions").asList().stream()
                        .map(JsonElement::getAsJsonObject)
                        .toList();
                for (JsonObject function : functions) {
                    Optional<ResourceLocation> location = canSmelt(function, item);
                    if (location.isPresent()) {
                        smelted = location.get();
                        break;
                    }
                }
                addEntry(LootData.standard(smelted).smelt(true), items);
            }
            data.shears(handleShears(entry));
        }
        addEntry(data, items);
    }

    private static boolean hasCondition(JsonObject object, String condition, Function<JsonObject, Boolean> function) {
        if (object.has("condition") && object.get("condition").getAsString().contains(condition)) {
            return function.apply(object.getAsJsonObject("predicate"));
        }
        return false;
    }

//    private static void handleEntryObject(JsonObject object, Set<LootData> items) {
//        if (object.has("name")) {
//            ResourceLocation item = ResourceLocation.parse(object.get("name").getAsString());
//
//            if (object.has("conditions")) {
//                handleEntryArray(object.getAsJsonArray("conditions"), items);
//            }
//            if (object.has("functions")) {
//                handleFunctions(object.getAsJsonArray("functions"), items, item);
//            }
//            requiresShears = handleShears(object);
//
//            if (requiresPick(item)) {
//                requiresPick = true;
//            }
//            items.add(LootData.standard(item)
//                    .silk(requiresSilk)
//                    .pick(requiresPick)
//                    .shears(requiresShears));
//        } else if (object.has("children")) {
//            handleEntryArray(object.getAsJsonArray("children"), items);
//        } else if (object.has("condition")) {
//            if (object.get("condition").getAsString().equals("minecraft:match_tool")) {
//                handleMatchTool(object.getAsJsonObject("predicate"));
//            }
//        }
//    }

    private static boolean handleMatchTool(JsonObject predicates) {
        if (predicates.has("predicates")) {
            JsonObject predicate = predicates.getAsJsonObject("predicates");
            if (predicate.has("minecraft:enchantments")) {
                return hasEnchantment(predicate, "silk_touch");
            }
        }
        return false;
    }

    private static boolean hasEnchantment(JsonObject predicate, String enchantment) {
        return predicate.getAsJsonArray("minecraft:enchantments")
                .asList().stream().map(JsonElement::getAsJsonObject)
                .filter(object -> object.has("enchantments"))
                .map(object -> object.get("enchantments").getAsString())
                .anyMatch(s -> s.contains(enchantment));
    }

    private static boolean handleShears(JsonObject object) {
        if (!object.has("terms")) return false;

        return object.getAsJsonArray("terms")
                .asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(o -> o.has("action"))
                .map(o -> o.get("action").getAsString())
                .anyMatch(s -> s.contains("shears"));
    }

//    private static void handleFunctions(JsonArray predicate, Set<LootData> items, ResourceLocation currentItem) {
//        List<JsonObject> functions = predicate.asList().stream()
//                .map(JsonElement::getAsJsonObject)
//                .toList();
//
//        for (JsonObject object : functions) {
//            if (object.has("function") && object.getAsJsonPrimitive("function").getAsString().equals("minecraft:furnace_smelt")) {
//                Optional<Holder.Reference<Item>> item = ITEM_REGISTRY.getHolder(currentItem);
//                if (item.isEmpty()) continue;
//                ItemStack stack = new ItemStack(item.get());
//                Optional<ItemStack> smelted = RECIPE_MANAGER.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), SERVER.overworld())
//                        .map(RecipeHolder::value)
//                        .map(r -> r.getResultItem(SERVER.registryAccess()));
//                if (smelted.isEmpty()) continue;
//                items.add(LootData.standard(ITEM_REGISTRY.getKey(smelted.get().getItem())).smelt(true));
//            }
//        }
//    }

    private static Optional<ResourceLocation> canSmelt(JsonObject function, ResourceLocation currentItem) {
        if (function.has("function") && function.get("function").getAsString().contains("furnace_smelt")) {
            List<RecipeHolder<SmeltingRecipe>> recipes = RECIPE_MANAGER.getAllRecipesFor(RecipeType.SMELTING);
            Optional<Holder.Reference<Item>> item = ITEM_REGISTRY.getHolder(currentItem);
            if (item.isEmpty()) return Optional.empty();
            ItemStack stack = new ItemStack(item.get());
            return recipes.stream().filter(holder -> holder.value().getIngredients().getFirst().test(stack))
                    .map(holder -> holder.value().getResultItem(SERVER.registryAccess()))
                    .map(is -> Objects.requireNonNull(ITEM_REGISTRY.getKey(is.getItem())))
                    .findAny();
        }
        return Optional.empty();
    }

//    private static void handleEntryArray(JsonArray array, Set<LootData> items) {
//        array.asList().stream()
//                .filter(JsonElement::isJsonObject)
//                .map(JsonElement::getAsJsonObject)
//                .forEach(object -> handleEntryObject(object, items));
//    }

//    private static boolean isBlock(JsonPrimitive type) {
//        return type.getAsString().endsWith("block");
//    }
//
//    private static boolean isEntity(JsonPrimitive type) {
//        return type.getAsString().endsWith("entity");
//    }
//
//    private static boolean isChest(JsonPrimitive type) {
//        return type.getAsString().endsWith("chest");
//    }

    public static RandomizationMapData getMapData(ResourceLocation location) {
        if (RandomizerConfig.randomizeLoot && TABLES.contains(location))
            return INSTANCE;
        return RandomizationMapData.VANILLA;
    }

    public static void dispose() {
        BlockDropRecipe.clearRegistry();
        TABLES.clear();
        BLOCK_MAP.clear();
        LOOT_MAP.clear();
    }

    private static boolean isBlacklisted(ResourceLocation location) {
        return !RandomizerConfig.randomizeBlockLoot && isBlock(location) ||
                !RandomizerConfig.randomizeEntityLoot && isEntityDrop(location) ||
                !RandomizerConfig.randomizeChestLoot && isChestLoot(location);
    }

    private static boolean isBlock(ResourceLocation location) {
        return location.getPath().contains("blocks/");
    }

    private static boolean isEntityDrop(ResourceLocation location) {
        return location.getPath().contains("entities/");
    }

    private static boolean isChestLoot(ResourceLocation location) {
        return location.getPath().contains("chests/");
    }

    public static @NotNull ObjectArrayList<ItemStack> randomizeLoot(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!TABLES.contains(context.getQueriedLootTableId())) return generatedLoot;

        ObjectArrayList<ItemStack> ret = new ObjectArrayList<>();
        for (ItemStack stack : generatedLoot) {
            if (!stack.isEmpty()) {
                var random = getMapData(context.getQueriedLootTableId()).getItemFor(stack.getItem());
                ret.add(RandomizerUtil.itemToStack(random, stack.getCount()));
            } else {
                ret.add(ItemStack.EMPTY);
            }
        }

        return ret;
    }

    public static final class LootData {
        public static final int REQUIRES_SILK = 0;
        public static final int REQUIRES_SHEARS = 1;
        public static final int REQUIRES_SMELT = 2;
        public static final int REQUIRES_PICK = 3;

        private final ResourceLocation item;
        private final BitSet data;

        /**
         * @param item loot entry
         * @param data BitSet storing the properties of the given loot entry
         */
        public LootData(ResourceLocation item, BitSet data) {
            this.item = Objects.requireNonNull(item);
            this.data = data;
        }

        public boolean silk() {
            return data.get(REQUIRES_SILK);
        }

        public boolean shears() {
            return data.get(REQUIRES_SHEARS);
        }

        public boolean smelt() {
            return data.get(REQUIRES_SMELT);
        }

        public boolean pick() {
            return data.get(REQUIRES_PICK);
        }

        public BlockDropRecipe.Type getType() {
            if (silk() && shears()) return BlockDropRecipe.Type.SHEARS_OR_SILK;
            if (shears()) return BlockDropRecipe.Type.SHEARS;
            if (!pick()) return BlockDropRecipe.Type.HAND;
            if (silk()) return BlockDropRecipe.Type.SILK_PICK;
            return BlockDropRecipe.Type.PICK;
        }

        public LootData silk(boolean b) {
            data.set(REQUIRES_SILK, b);
            return this;
        }

        public LootData shears(boolean b) {
            data.set(REQUIRES_SHEARS, b);
            return this;
        }

        public LootData smelt(boolean b) {
            data.set(REQUIRES_SMELT, b);
            return this;
        }

        public LootData pick(boolean b) {
            data.set(REQUIRES_PICK, b);
            return this;
        }

        public static LootData standard(ResourceLocation item) {
            return new LootData(item, new BitSet());
        }

        public ResourceLocation item() {
            return item;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (LootData) obj;
            return Objects.equals(this.item, that.item) &&
                    Objects.equals(this.data, that.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(item, data);
        }

        @Override
        public String toString() {
            return "LootData[" +
                    "item=" + item +
                    ", silk=" + silk() +
                    ", shears=" + shears() +
                    ", pick=" + pick() +
                    ", smelt=" + smelt() +
                    ']';
        }
    }
}
