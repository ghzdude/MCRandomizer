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
    private static boolean requiresSilk;
    private static boolean requiresShears;

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
                    if (entry.tag() || entry.reference()) continue;
                    Item output = ITEM_REGISTRY.get(entry.location());
                    BlockDropRecipe.registerRecipe(block.asItem(), output, entry.getType(), table);
                }
            }
        }
    }

    private static void collectFromTag(TagKey<Block> key, Set<ResourceLocation> collection) {
        BLOCK_REGISTRY.getTag(key).ifPresent(blocks -> blocks.stream()
                .map(holder -> BLOCK_REGISTRY.getKey(holder.get()))
                .forEach(collection::add));
    }

    private static void handleJson(JsonObject table) {
        if (!table.has("random_sequence") || !table.has("pools"))
            return;

        ResourceLocation id = ResourceLocation.parse(table.get("random_sequence").getAsString());
        activeLocation = id;
        appliesToAll = false;

        if (!isBlacklisted(id)) TABLES.add(id);

        Set<LootData> items = LOOT_MAP.computeIfAbsent(id, k -> new ObjectOpenHashSet<>());

        requiresPick = isBlock(id) && PICKAXE_MINABLE.contains(BLOCK_MAP.get(id));

        handleJsonRaw(table, items);

        RandomizerCore.LOGGER.info("added {} entries for table '{}'", items.size(), id);
    }

    private static void handleJsonRaw(JsonObject table, Set<LootData> items) {
        if (!table.has("pools"))
            return;

        if (!appliesToAll) {
            requiresShears = false;
            requiresSilk = false;
        }

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
                handleEntry(entry, items);
            }
        }
    }

    private static void handleEntry(JsonObject entry, Set<LootData> items) {
        if (isType(entry, "alternatives")) {
            handleAlternatives(entry, items);
        } else if (isType(entry, "item")) {
            handleItem(entry, items);
        } else if (isType(entry, "empty")) {
            // no-op
        } else if (isType(entry, "loot_table")) {
            JsonElement value = entry.get("value");
            if (value.isJsonObject()) {
                // table within table
                handleJsonRaw(value.getAsJsonObject(), items);
            } else {
                // table location
                ResourceLocation reference = ResourceLocation.parse(value.getAsString());
                addEntry(LootData.table(reference), items);
            }
        } else if (isType(entry, "dynamic")) {
            ResourceLocation name = getName(entry);
            List<ResourceLocation> list = ITEM_REGISTRY.getTags()
                    // this isn't really a great solution, but it should work for sherds
                    .filter(pair -> pair.getFirst().location().getPath().contains(name.getPath()))
                    .flatMap(pair -> pair.getSecond().stream())
                    .map(holder -> ITEM_REGISTRY.getKey(holder.get()))
                    .toList();

            for (ResourceLocation item : list) {
                addEntry(LootData.standard(item), items);
            }

        } else if (isType(entry, "tag")) {
            ResourceLocation tag = getName(entry);
            addEntry(LootData.tag(tag), items);
        } else {
            RandomizerCore.LOGGER.debug("unhandled entry: {}", entry);
        }
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

    private static boolean isCondition(JsonObject object, String type) {
        if (!object.has("condition")) return false;
        return object.get("condition").getAsString().contains(type);
    }

    private static void handleAlternatives(JsonObject entry, Set<LootData> items) {
        List<JsonObject> children = entry.getAsJsonArray("children")
                .asList().stream().map(JsonElement::getAsJsonObject).toList();

        for (JsonObject child : children) {
            handleEntry(child, items);
        }
    }

    private static void handleItem(JsonObject entry, Set<LootData> items) {

        ResourceLocation item = getName(entry);
        LootData data = LootData.standard(item).pick(requiresPick);

        if (appliesToAll) {
            data.silk(requiresSilk);
            data.shears(requiresShears);
        } else {
            requiresShears = false;
            requiresSilk = false;
            data.silk(hasCondition(entry, "match_tool", LootRandomizer::handleMatchTool));
            data.shears(hasCondition(entry, "can_tool_perform_action", LootRandomizer::handleShears));

            if (entry.has("functions")) {
                List<JsonObject> functions = entry.getAsJsonArray("functions").asList().stream()
                        .map(JsonElement::getAsJsonObject)
                        .toList();

                for (JsonObject function : functions) {
                    Optional<ResourceLocation> location = canSmelt(function, item);
                    if (location.isPresent()) {
                        addEntry(LootData.standard(location.get()).smelt(true), items);
                        break;
                    }
                }
            }
        }
        addEntry(data, items);
    }

    private static boolean hasCondition(JsonObject object, String type, Function<JsonObject, Boolean> function) {
        if (!object.has("conditions")) return false;
        List<JsonObject> conditions = object.getAsJsonArray("conditions")
                .asList().stream().map(JsonElement::getAsJsonObject).toList();

        for (JsonObject condition : conditions) {
            if (isCondition(condition, type) && function.apply(condition)) {
                    return true;
            } else if (isCondition(condition, "any_of")) {
                List<JsonObject> terms = condition.getAsJsonArray("terms")
                        .asList().stream().map(JsonElement::getAsJsonObject).toList();

                for (JsonObject term : terms) {
                    if (isCondition(term, type) && function.apply(term)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean handleMatchTool(JsonObject object) {
        if (!object.has("predicate")) return false;

        JsonObject predicate = object.getAsJsonObject("predicate");
        if (!predicate.has("predicates")) return false;

        JsonObject predicates = predicate.getAsJsonObject("predicates");
        if (!predicates.has("minecraft:enchantments")) return false;

        return hasEnchantment(predicates, "silk_touch");
    }

    private static boolean hasEnchantment(JsonObject predicate, String enchantment) {
        return predicate.getAsJsonArray("minecraft:enchantments")
                .asList().stream().map(JsonElement::getAsJsonObject)
                .filter(object -> object.has("enchantments"))
                .map(object -> object.get("enchantments").getAsString())
                .anyMatch(s -> s.contains(enchantment));
    }

    private static boolean handleShears(JsonObject object) {
        if (!object.has("action")) return false;
        return object.get("action").getAsString().contains("shears");
    }

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
        public static final int TABLE_REFERENCE = 4;
        public static final int TAG_REFERENCE = 5;

        public static LootData standard(ResourceLocation item) {
            return new LootData(item);
        }

        public static LootData tag(ResourceLocation item) {
            return new LootData(item).tag(true);
        }

        public static LootData table(ResourceLocation item) {
            return new LootData(item).reference(true);
        }

        private final ResourceLocation location;
        private final BitSet data = new BitSet();

        public LootData(@NotNull ResourceLocation location) {
            this.location = Objects.requireNonNull(location);
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

        public boolean reference() {
            return data.get(TABLE_REFERENCE);
        }

        public boolean tag() {
            return data.get(TAG_REFERENCE);
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

        public LootData reference(boolean b) {
            data.set(TABLE_REFERENCE, b);
            return this;
        }

        public LootData tag(boolean b) {
            data.set(TAG_REFERENCE, b);
            return this;
        }

        public BlockDropRecipe.Type getType() {
            if (silk() && shears()) return BlockDropRecipe.Type.SHEARS_OR_SILK;
            if (shears()) return BlockDropRecipe.Type.SHEARS;
            if (!pick()) return BlockDropRecipe.Type.HAND;
            if (silk()) return BlockDropRecipe.Type.SILK_PICK;
            return BlockDropRecipe.Type.PICK;
        }

        public ResourceLocation location() {
            return location;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (LootData) obj;
            return Objects.equals(this.location, that.location) &&
                    Objects.equals(this.data, that.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(location, data);
        }

        @Override
        public String toString() {
            return "LootData[" +
                    "location=" + location +
                    ", silk=" + silk() +
                    ", shears=" + shears() +
                    ", pick=" + pick() +
                    ", smelt=" + smelt() +
                    ", table=" + reference() +
                    ", tag=" + tag() +
                    ']';
        }
    }
}
