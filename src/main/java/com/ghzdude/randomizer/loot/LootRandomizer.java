package com.ghzdude.randomizer.loot;

import com.ghzdude.randomizer.RandomizationMapData;
import com.ghzdude.randomizer.RandomizerConfig;
import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.api.EntryAccessor;
import com.ghzdude.randomizer.compat.jei.BlockDropRecipe;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LootRandomizer {

    private static RandomizationMapData INSTANCE = null;
    public static Registry<LootTable> LOOT_REGISTRY;
    public static Registry<Item> ITEM_REGISTRY;
    public static Registry<Block> BLOCK_REGISTRY;
    private static final ObjectOpenHashSet<ResourceLocation> TABLES = new ObjectOpenHashSet<>();
    private static final Object2ObjectMap<ResourceLocation, ResourceLocation> BLOCK_MAP = new Object2ObjectOpenHashMap<>();
    public static final Object2ObjectMap<ResourceLocation, ItemStack[]> LOOT_MAP = new Object2ObjectOpenHashMap<>();
    private static MutableLootParams HAND, PICK, SILK, SHEARS;
    private static boolean requiresSilk = false;

    private static TagKey<Block> PICKAXE_MINABLE;

    public static void init(MinecraftServer server) {
        INSTANCE = RandomizationMapData.get(server, "loot");
        Optional<Registry<LootTable>> optional = server.reloadableRegistries().get().registry(Registries.LOOT_TABLE);
        if (optional.isEmpty()) throw new NullPointerException();
        LOOT_REGISTRY = optional.get();
        ITEM_REGISTRY = server.registryAccess().registryOrThrow(Registries.ITEM);
        BLOCK_REGISTRY = server.registryAccess().registryOrThrow(Registries.BLOCK);
        PICKAXE_MINABLE = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("mineable/pickaxe"));

        HAND = createLootParams(server, Items.AIR, false);
        PICK = createLootParams(server, Items.NETHERITE_PICKAXE, false);
        SILK = createLootParams(server, Items.NETHERITE_PICKAXE, true);
        SHEARS = createLootParams(server, Items.SHEARS, false);

        for (Block block : BLOCK_REGISTRY) {
            if (block == Blocks.AIR) continue;

            BLOCK_MAP.put(block.getLootTable().location(), BLOCK_REGISTRY.getKey(block));
        }

        RegistryOps<JsonElement> registryOps = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());

        for (LootTable table : LOOT_REGISTRY) {
            Optional<ResourceKey<LootTable>> resourceKey = LOOT_REGISTRY.getResourceKey(table);
            if (resourceKey.isEmpty()) continue;
            ResourceLocation key = resourceKey.get().location();

            // serialize loot table into JSON for easy lookup
            DataResult<JsonElement> result = LootTable.DIRECT_CODEC.encodeStart(registryOps, table);
            if (result.isSuccess()) {
                result.result().ifPresent(LootRandomizer::handleJson);
            }

            if (!isBlacklisted(key)) {
                TABLES.add(key);
            }

            if (isBlock(key)) {
                // handle block
                handleBlock(table);
            }

            if (isChestLoot(key) || isEntityDrop(key)) {
                // handle chest and entity loot
                // get the drops somehow
                // we need to ignore chance
                ItemStack[] stacks;
                if (table instanceof EntryAccessor accessor) {
                    stacks = accessor.randomizer$getStacks();
                    LOOT_MAP.put(table.getLootTableId(), stacks);
                }
            }
        }
    }

    private static void handleJson(JsonElement element) {
        if (!element.isJsonObject()) return;
        JsonObject table = element.getAsJsonObject();
        if (!table.has("random_sequence"))
            return;

        String id = table.get("random_sequence").getAsString();

        if (!table.has("pools")) {
            RandomizerCore.LOGGER.warn("table {} has no pools", id);
            return;
        }

        if (isBlock(table.getAsJsonPrimitive("type"))) {
            RandomizerCore.LOGGER.warn("table {} is a block drop", id);
        } else {
            return;
        }

        Set<String> items = new ObjectOpenHashSet<>();
        JsonArray pools = table.getAsJsonArray("pools");
        for (JsonElement pool : pools) {
            JsonArray entries = pool.getAsJsonObject().getAsJsonArray("entries");
            for (JsonElement entry : entries) {
                if (entry.isJsonObject()) {
                    handleEntryObject(entry.getAsJsonObject(), items);
                }
            }
        }

        RandomizerCore.LOGGER.warn("table '{}' has entries: {}", id == null ? "unknown" : id, items);
    }

    private static void handleEntryObject(JsonObject object, Set<String> items) {
        if (object.has("name")) {
            String item = object.get("name").getAsString();
            Optional<Holder.Reference<Block>> block = BLOCK_REGISTRY.getHolder(ResourceLocation.parse(item));
            Optional<HolderSet.Named<Block>> tag = BLOCK_REGISTRY.getTag(PICKAXE_MINABLE);
            if (tag.isPresent() && block.isPresent() && tag.get().contains(block.get())) {
                RandomizerCore.LOGGER.warn("the entry '{}' requires a pick!", item);
            }
            items.add(item);
            requiresSilk = false;
            if (object.has("conditions")) {
                handleEntryArray(object.getAsJsonArray("conditions"), items);
            }
            if (requiresSilk) {
                RandomizerCore.LOGGER.warn("the entry '{}' requires silk touch!", item);
            }
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

    private static void handleEntryArray(JsonArray array, Set<String> items) {
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                handleEntryObject(element.getAsJsonObject(), items);
            }
        }
    }

    private static boolean isBlock(JsonPrimitive type) {
        return type.getAsString().equals("minecraft:block");
    }

    public static RandomizationMapData getMapData() {
        if (RandomizerConfig.randomizeLoot)
            return INSTANCE;
        return RandomizationMapData.VANILLA;
    }

    private static void handleBlock(LootTable blockTable) {
        if (!BLOCK_MAP.containsKey(blockTable.getLootTableId())) {
            RandomizerCore.LOGGER.warn("table is not in map when it should be! {}", blockTable.getLootTableId());
            return;
        }
        var loc = BLOCK_MAP.get(blockTable.getLootTableId());
        Block block = BLOCK_REGISTRY.get(loc);

        if (block == null) {
            RandomizerCore.LOGGER.warn("table does not give block! {}", blockTable.getLootTableId());
            return;
        }

        HAND.updateState(block);
        PICK.updateState(block);
        SILK.updateState(block);
        SHEARS.updateState(block);

        ItemStack handDrop = getDrop(blockTable, HAND);
        ItemStack pickDrop = getDrop(blockTable, PICK);
        ItemStack silkDrop = getDrop(blockTable, SILK);
        ItemStack shearDrop = getDrop(blockTable, SHEARS);

        handleDrop(block, handDrop, BlockDropRecipe.Type.HAND);

        if (!ItemStack.isSameItemSameComponents(pickDrop, handDrop)) {
            handleDrop(block, pickDrop, BlockDropRecipe.Type.PICK);
        }

        if (!ItemStack.isSameItemSameComponents(silkDrop, handDrop) && !ItemStack.isSameItemSameComponents(shearDrop, silkDrop)) {
            handleDrop(block, silkDrop, BlockDropRecipe.Type.SILK_PICK);
        }

        if (!ItemStack.isSameItemSameComponents(shearDrop, handDrop)) {
            var type = ItemStack.isSameItemSameComponents(shearDrop, silkDrop) ?
                    BlockDropRecipe.Type.SHEARS_OR_SILK :
                    BlockDropRecipe.Type.SHEARS;

            handleDrop(block, shearDrop, type);
        }
    }

    public static void handleDrop(Block blockItem, ItemStack drop, BlockDropRecipe.Type type) {
        if (!drop.isEmpty()) {
            BlockDropRecipe.registerRecipe(blockItem.asItem(), getMapData().getStackFor(drop), type);
        }
    }

    public static void dispose() {
        BlockDropRecipe.clearRegistry();
        TABLES.clear();
        EntryAccessor.counter.incrementAndGet();
    }

    @SuppressWarnings("deprecation")
    private static ItemStack getDrop(LootTable table, MutableLootParams params) {
        var list = new ObjectArrayList<ItemStack>();
        table.getRandomItemsRaw(params, LootTable.createStackSplitter(params.getLevel(), stack -> {
            if (params.willDrop()) list.add(stack);
        }));
        return list.isEmpty() ? ItemStack.EMPTY : list.getFirst();
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
                var random = getMapData().getItemFor(stack.getItem());
                ret.add(RandomizerUtil.itemToStack(random, stack.getCount()));
            } else {
                ret.add(ItemStack.EMPTY);
            }
        }

        return ret;
    }

    private static MutableLootParams createLootParams(ServerLevel level, @NotNull Item tool, boolean silk) {
        var stack = new ItemStack(tool);
        if (silk && !stack.isEmpty()) {
            stack.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        }

        Map<LootContextParam<?>, Object> lootContext = new Reference2ObjectArrayMap<>();
        lootContext.put(LootContextParams.TOOL, stack);
        lootContext.put(LootContextParams.ORIGIN, Vec3.ZERO);

        return new MutableLootParams(level, lootContext, Map.of(), 1f);
    }

    private static MutableLootParams createLootParams(MinecraftServer server, @NotNull Item tool, boolean silk) {
        return createLootParams(server.overworld(), tool, silk);
    }

    private static class MutableLootParams extends LootParams {

        private final Map<LootContextParam<?>, Object> params;

        public MutableLootParams(ServerLevel pLevel, Map<LootContextParam<?>, Object> pParams, Map<ResourceLocation, DynamicDrop> pDynamicDrops, float pLuck) {
            super(pLevel, pParams, pDynamicDrops, pLuck);
            this.params = pParams;
        }

        public void updateState(BlockItem item) {
            updateState(item.getBlock());
        }

        public void updateState(Block block) {
            params.put(LootContextParams.BLOCK_STATE, block.defaultBlockState());
        }

        public boolean willDrop() {
            var state = (BlockState) params.get(LootContextParams.BLOCK_STATE);
            if (!state.requiresCorrectToolForDrops()) return true;

            ItemStack tool = (ItemStack) params.get(LootContextParams.TOOL);
            return tool.isCorrectToolForDrops(state);
        }
    }
}
