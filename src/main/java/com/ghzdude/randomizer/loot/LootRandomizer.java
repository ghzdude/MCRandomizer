package com.ghzdude.randomizer.loot;

import com.ghzdude.randomizer.RandomizationMapData;
import com.ghzdude.randomizer.RandomizerConfig;
import com.ghzdude.randomizer.compat.jei.BlockDropRecipe;
import com.ghzdude.randomizer.util.RandomizerUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class LootRandomizer {

    private static RandomizationMapData INSTANCE = null;

    public static void init(MinecraftServer server) {
        INSTANCE = RandomizationMapData.get(server, "loot");

        final var hand = createLootParams(server, Items.AIR, false);
        final var withPick = createLootParams(server, Items.NETHERITE_PICKAXE, false);
        final var withSilkPick = createLootParams(server, Items.NETHERITE_PICKAXE, true);
        final var withShears = createLootParams(server, Items.SHEARS, false);

        for (Item item : INSTANCE.getItems()) {
            if (!(item instanceof BlockItem blockItem)) continue;
            var table = server.reloadableRegistries().getLootTable(blockItem.getBlock().getLootTable());
            if (table == LootTable.EMPTY) continue;

            hand.updateState(blockItem);
            withPick.updateState(blockItem);
            withSilkPick.updateState(blockItem);
            withShears.updateState(blockItem);

            ItemStack handDrop = getDrop(table, hand);
            ItemStack pickDrop = getDrop(table, withPick);
            ItemStack silkDrop = getDrop(table, withSilkPick);
            ItemStack shearDrop = getDrop(table, withShears);

            BlockDropRecipe.registerRecipe(blockItem, INSTANCE.getStackFor(handDrop));

            if (!ItemStack.isSameItemSameComponents(pickDrop, handDrop))
                BlockDropRecipe.registerRecipe(blockItem, INSTANCE.getStackFor(pickDrop), BlockDropRecipe.Type.PICK);

            if (!ItemStack.isSameItemSameComponents(silkDrop, handDrop) && !ItemStack.isSameItemSameComponents(shearDrop, silkDrop))
                BlockDropRecipe.registerRecipe(blockItem, INSTANCE.getStackFor(silkDrop), BlockDropRecipe.Type.SILK_PICK);

            if (!ItemStack.isSameItemSameComponents(shearDrop, handDrop)) {
                var type = ItemStack.isSameItemSameComponents(shearDrop, silkDrop) ?
                        BlockDropRecipe.Type.SHEARS_OR_SILK :
                        BlockDropRecipe.Type.SHEARS;

                BlockDropRecipe.registerRecipe(blockItem, INSTANCE.getStackFor(shearDrop), type);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static ItemStack getDrop(LootTable table, MutableLootParams params) {
        var list = new ObjectArrayList<ItemStack>();
        table.getRandomItemsRaw(params, LootTable.createStackSplitter(params.getLevel(), stack -> {
            if (params.willDrop()) list.add(stack);
        }));
        return list.isEmpty() ? ItemStack.EMPTY : list.getFirst();
    }

    public static @NotNull ObjectArrayList<ItemStack> randomizeLoot(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        String path = context.getQueriedLootTableId().getPath();
        if (INSTANCE == null || !RandomizerConfig.randomizeBlockLoot && path.contains("blocks/") ||
            !RandomizerConfig.randomizeEntityLoot && path.contains("entities/") ||
            !RandomizerConfig.randomizeChestLoot && path.contains("chests/"))
        { return generatedLoot; }

        ObjectArrayList<ItemStack> ret = new ObjectArrayList<>();
        for (ItemStack stack : generatedLoot) {
            if (stack.isEmpty()) ret.add(ItemStack.EMPTY);
            else {
                var random = INSTANCE.getItemFor(stack.getItem());
                ret.add(RandomizerUtil.itemToStack(random, stack.getCount()));
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
