package com.ghzdude.randomizer.loot;

import com.ghzdude.randomizer.RandomizationMapData;
import com.ghzdude.randomizer.RandomizerConfig;
import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.api.EntryAccessor;
import com.ghzdude.randomizer.compat.jei.BlockDropRecipe;
import com.ghzdude.randomizer.util.RandomizerUtil;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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

public class LootRandomizer {

    private static RandomizationMapData INSTANCE = null;
    public static Registry<LootTable> LOOT_REGISTRY;
    public static Registry<Item> ITEM_REGISTRY;
    public static Registry<Block> BLOCK_REGISTRY;
    private static final ObjectOpenHashSet<ResourceLocation> TABLES = new ObjectOpenHashSet<>();
    private static final Object2ObjectMap<ResourceLocation, ResourceLocation> BLOCK_MAP = new Object2ObjectOpenHashMap<>();
    private static MutableLootParams HAND, PICK, SILK, SHEARS;

    public static void init(MinecraftServer server) {
        INSTANCE = RandomizationMapData.get(server, "loot");
        Optional<Registry<LootTable>> optional = server.reloadableRegistries().get().registry(Registries.LOOT_TABLE);
        if (optional.isEmpty()) throw new NullPointerException();
        LOOT_REGISTRY = optional.get();
        ITEM_REGISTRY = server.registryAccess().registryOrThrow(Registries.ITEM);
        BLOCK_REGISTRY = server.registryAccess().registryOrThrow(Registries.BLOCK);

        HAND = createLootParams(server, Items.AIR, false);
        PICK = createLootParams(server, Items.NETHERITE_PICKAXE, false);
        SILK = createLootParams(server, Items.NETHERITE_PICKAXE, true);
        SHEARS = createLootParams(server, Items.SHEARS, false);

        for (Block block : BLOCK_REGISTRY) {
            if (block == Blocks.AIR) continue;

            BLOCK_MAP.put(block.getLootTable().location(), BLOCK_REGISTRY.getKey(block));
        }

        for (LootTable table : LOOT_REGISTRY) {
            Optional<ResourceKey<LootTable>> resourceKey = LOOT_REGISTRY.getResourceKey(table);
            if (resourceKey.isEmpty()) continue;
            ResourceLocation key = resourceKey.get().location();

            if (!isBlacklisted(key)) {
                TABLES.add(key);
            }

            if (isBlock(key)) {
                // handle block
                handleBlock(table);
            }

            if (isChestLoot(key)) {
                // handle chest loot
                // get the drops somehow
                // we need to ignore chance
                ItemStack[] stacks = new ItemStack[0];
                if (table instanceof EntryAccessor accessor) {
                    stacks = accessor.randomizer$getStacks();
                }
                RandomizerCore.LOGGER.warn("{} has {} stacks", key, stacks.length);
            }
        }
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
        BlockItem blockItem = null;
        if (ITEM_REGISTRY.get(loc) instanceof BlockItem) {
            blockItem = (BlockItem) ITEM_REGISTRY.get(loc);
        }

        if (blockItem == null) {
            RandomizerCore.LOGGER.warn("table does not give block! {}", blockTable.getLootTableId());
            return;
        }

        HAND.updateState(blockItem);
        PICK.updateState(blockItem);
        SILK.updateState(blockItem);
        SHEARS.updateState(blockItem);

        ItemStack handDrop = getDrop(blockTable, HAND);
        ItemStack pickDrop = getDrop(blockTable, PICK);
        ItemStack silkDrop = getDrop(blockTable, SILK);
        ItemStack shearDrop = getDrop(blockTable, SHEARS);

        handleDrop(blockItem, handDrop, BlockDropRecipe.Type.HAND);

        if (!ItemStack.isSameItemSameComponents(pickDrop, handDrop)) {
            handleDrop(blockItem, pickDrop, BlockDropRecipe.Type.PICK);
        }

        if (!ItemStack.isSameItemSameComponents(silkDrop, handDrop) && !ItemStack.isSameItemSameComponents(shearDrop, silkDrop)) {
            handleDrop(blockItem, silkDrop, BlockDropRecipe.Type.SILK_PICK);
        }

        if (!ItemStack.isSameItemSameComponents(shearDrop, handDrop)) {
            var type = ItemStack.isSameItemSameComponents(shearDrop, silkDrop) ?
                    BlockDropRecipe.Type.SHEARS_OR_SILK :
                    BlockDropRecipe.Type.SHEARS;

            handleDrop(blockItem, shearDrop, type);
        }
    }

    public static void handleDrop(BlockItem blockItem, ItemStack drop, BlockDropRecipe.Type type) {
        if (!drop.isEmpty()) {
            BlockDropRecipe.registerRecipe(blockItem, getMapData().getStackFor(drop), type);
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
            params.put(LootContextParams.BLOCK_STATE, item.getBlock().defaultBlockState());
        }

        public boolean willDrop() {
            var state = (BlockState) params.get(LootContextParams.BLOCK_STATE);
            if (!state.requiresCorrectToolForDrops()) return true;

            ItemStack tool = (ItemStack) params.get(LootContextParams.TOOL);
            return tool.isCorrectToolForDrops(state);
        }
    }
}
