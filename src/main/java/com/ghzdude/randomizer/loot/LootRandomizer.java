package com.ghzdude.randomizer.loot;

import com.ghzdude.randomizer.RandomizationMapData;
import com.ghzdude.randomizer.RandomizerConfig;
import com.ghzdude.randomizer.api.Loot;
import com.ghzdude.randomizer.compat.jei.BlockDropRecipe;
import com.ghzdude.randomizer.util.RandomizerUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.NotNull;

public class LootRandomizer {

    private static RandomizationMapData INSTANCE = null;

    public static void init(MinecraftServer server) {
        INSTANCE = RandomizationMapData.get(server, "loot");

        for (Item item : INSTANCE.getItems()) {
            if (!(item instanceof BlockItem blockItem)) continue;
            var table = server.reloadableRegistries().getLootTable(blockItem.getBlock().getLootTable());

            final var hand = Loot.createLootParams(server, blockItem, Items.AIR, false);
            final var withPick = Loot.createLootParams(server, blockItem, Items.NETHERITE_PICKAXE, false);
            final var withSilkPick = Loot.createLootParams(server, blockItem, Items.NETHERITE_PICKAXE, true);
            final var withShears = Loot.createLootParams(server, blockItem, Items.SHEARS, false);

            ItemStack normalDrop = getDrop(table, hand);
            if (normalDrop.isEmpty())
                normalDrop = getDrop(table, withPick);

            ItemStack silkDrop = getDrop(table, withSilkPick);
            ItemStack shearDrop = getDrop(table, withShears);

            BlockDropRecipe.registerRecipe(blockItem, INSTANCE.getStackFor(normalDrop));

            if (!normalDrop.isEmpty() && !ItemStack.isSameItemSameComponents(normalDrop, silkDrop))
                BlockDropRecipe.registerRecipe(blockItem, INSTANCE.getStackFor(silkDrop), BlockDropRecipe.Type.SILK_PICK);

            if (!ItemStack.isSameItemSameComponents(shearDrop, normalDrop)) {
                BlockDropRecipe.Type type;
                if (ItemStack.isSameItemSameComponents(shearDrop, silkDrop))
                    type = BlockDropRecipe.Type.SHEARS_OR_SILK;
                else type = BlockDropRecipe.Type.SHEARS;

                BlockDropRecipe.registerRecipe(blockItem, INSTANCE.getStackFor(shearDrop), type);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static ItemStack getDrop(LootTable table, LootParams params) {
        var list = new ObjectArrayList<ItemStack>();
        table.getRandomItemsRaw(params, LootTable.createStackSplitter(params.getLevel(), list::add));
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
}
