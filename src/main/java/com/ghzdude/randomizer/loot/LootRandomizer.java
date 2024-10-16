package com.ghzdude.randomizer.loot;

import com.ghzdude.randomizer.ItemRandomizer;
import com.ghzdude.randomizer.RandomizationMapData;
import com.ghzdude.randomizer.RandomizerConfig;
import com.ghzdude.randomizer.compat.jei.BlockDropRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import org.jetbrains.annotations.NotNull;

public class LootRandomizer {

    private static RandomizationMapData INSTANCE = null;

    public static void init(MinecraftServer server) {
        INSTANCE = RandomizationMapData.get(server, "loot");
//        var tables = server.registryAccess().registryOrThrow(Registries.LOOT_TABLE);
//        for (var loc : tables.keySet()) {
//            var table = Objects.requireNonNull(tables.get(loc));
//        }
        INSTANCE.streamItems().forEach(item -> {
            var drop = INSTANCE.getItemFor(item);
            if (!(item instanceof BlockItem blockItem)) return;
            var table = server.reloadableRegistries().getLootTable(blockItem.getBlock().getLootTable());
            BlockDropRecipe.registerRecipe(new ItemStack(blockItem), new ItemStack(drop));
        });

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
                ret.add(ItemRandomizer.itemToStack(random, stack.getCount()));
            }
        }

        return ret;
    }
}
