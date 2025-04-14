package com.ghzdude.randomizer.loot;

import com.ghzdude.randomizer.RandomizationMapData;
import com.ghzdude.randomizer.RandomizerConfig;
import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.compat.jei.BlockDropRecipe;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.Optional;
import java.util.Set;

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
    private static boolean requiresSilk = false;
    private static boolean requiresPick = false;

    private static final ObjectOpenHashSet<ResourceLocation> PICKAXE_MINABLE = new ObjectOpenHashSet<>();
    private static final ObjectOpenHashSet<ResourceLocation> REQUIRES_STONE = new ObjectOpenHashSet<>();
    private static final ObjectOpenHashSet<ResourceLocation> REQUIRES_IRON = new ObjectOpenHashSet<>();
    private static final ObjectOpenHashSet<ResourceLocation> REQUIRES_DIAMOND = new ObjectOpenHashSet<>();
    private static RecipeManager RECIPE_MANAGER;
    private static MinecraftServer SERVER;

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

        if (!isBlacklisted(id)) TABLES.add(id);

        JsonPrimitive type = table.getAsJsonPrimitive("type");

        requiresPick = isBlock(type) && requiresPick(BLOCK_MAP.get(id));

        Set<LootData> items = LOOT_MAP.computeIfAbsent(id, k -> new ObjectOpenHashSet<>());

        table.getAsJsonArray("pools")
                .asList()
                .stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .forEach(object -> {
                    requiresSilk = false;
                    if (object.has("conditions")) {
                        handleEntryArray(object.getAsJsonArray("conditions"), items);
                    }
                    object.getAsJsonArray("entries")
                            .asList()
                            .stream()
                            .map(JsonElement::getAsJsonObject)
                            .forEach(inner -> handleEntryObject(inner, items));
                });
    }

    private static void handleEntryObject(JsonObject object, Set<LootData> items) {
        if (object.has("name")) {
            ResourceLocation item = ResourceLocation.parse(object.get("name").getAsString());

            if (object.has("conditions")) {
                handleEntryArray(object.getAsJsonArray("conditions"), items);
            }
            if (object.has("functions")) {
                handleFunctions(object.getAsJsonArray("functions"), items, item);
            }
            items.add(LootData.standard(item)
                    .silk(requiresSilk)
                    .pick(requiresPick));
        } else if (object.has("children")) {
            handleEntryArray(object.getAsJsonArray("children"), items);
        } else if (object.has("condition")) {
            if (object.get("condition").getAsString().equals("minecraft:match_tool")) {
                handleMatchTool(object.getAsJsonObject("predicate"));
            }
        }
    }

    private static void handleMatchTool(JsonObject predicate) {
        if (predicate.has("predicates")) {
            handleMatchTool(predicate.getAsJsonObject("predicates"));
        } else if (predicate.has("minecraft:enchantments")) {
            for (JsonElement enchantment : predicate.getAsJsonArray("minecraft:enchantments")) {
                if (enchantment.isJsonObject()) {
                    String e = enchantment.getAsJsonObject().get("enchantments").getAsString();
                    if (e.contains("silk_touch")) {
                        requiresSilk = true;
                    }
                }
            }
        }
    }

    private static void handleFunctions(JsonArray predicate, Set<LootData> items, ResourceLocation currentItem) {
        predicate.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .forEach(object -> {
                    if (object.has("function") && object.getAsJsonPrimitive("function").getAsString().equals("minecraft:furnace_smelt")) {
                        Optional<Holder.Reference<Item>> item = ITEM_REGISTRY.getHolder(currentItem);
                        if (item.isEmpty()) return;
                        ItemStack stack = new ItemStack(item.get());
                        Optional<ItemStack> smelted = RECIPE_MANAGER.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), SERVER.overworld())
                                .map(RecipeHolder::value)
                                .map(r -> r.getResultItem(SERVER.registryAccess()));
                        if (smelted.isEmpty()) return;
                        items.add(LootData.standard(ITEM_REGISTRY.getKey(smelted.get().getItem())).smelt(true));
                    }
                });
    }

    private static void handleEntryArray(JsonArray array, Set<LootData> items) {
        array.asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .forEach(object -> handleEntryObject(object, items));
    }

    private static boolean isBlock(JsonPrimitive type) {
        return type.getAsString().endsWith("block");
    }

    private static boolean isEntity(JsonPrimitive type) {
        return type.getAsString().endsWith("entity");
    }

    private static boolean isChest(JsonPrimitive type) {
        return type.getAsString().endsWith("chest");
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

    /**
     * @param item loot entry
     * @param data BitSet storing the properties of the given loot entry
     */
    public record LootData(ResourceLocation item, BitSet data) {

        public static final int REQUIRES_SILK = 0;
        public static final int REQUIRES_SHEARS = 1;
        public static final int REQUIRES_SMELT = 2;
        public static final int REQUIRES_PICK = 3;

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
            if (!pick()) return BlockDropRecipe.Type.HAND;
            if (silk() && shears()) return BlockDropRecipe.Type.SHEARS_OR_SILK;
            if (silk()) return BlockDropRecipe.Type.SILK_PICK;
            else if (shears()) return BlockDropRecipe.Type.SHEARS;
            else return BlockDropRecipe.Type.PICK;
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
    }
}
