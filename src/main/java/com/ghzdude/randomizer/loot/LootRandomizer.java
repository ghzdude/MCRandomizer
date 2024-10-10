package com.ghzdude.randomizer.loot;

import com.ghzdude.randomizer.RandomizationMapData;
import com.ghzdude.randomizer.RandomizerConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import org.jetbrains.annotations.NotNull;

public class LootRandomizer {

    private static RandomizationMapData INSTANCE = null;

    public static @NotNull ObjectArrayList<ItemStack> randomizeLoot(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (INSTANCE == null) {
            INSTANCE = RandomizationMapData.get(context.getLevel(), "loot");
        }

        ObjectArrayList<ItemStack> ret = new ObjectArrayList<>();
        String path = context.getQueriedLootTableId().getPath();
        if (!RandomizerConfig.randomizeBlockLoot && path.contains("blocks/") ||
            !RandomizerConfig.randomizeEntityLoot && path.contains("entities/") ||
            !RandomizerConfig.randomizeChestLoot && path.contains("chests/")
        ) {
            return generatedLoot;
        }

        for (ItemStack stack : generatedLoot) {
            if (stack.isEmpty()) continue;
            ret.add(INSTANCE.getStackFor(stack));
        }

        return ret;
    }
}
