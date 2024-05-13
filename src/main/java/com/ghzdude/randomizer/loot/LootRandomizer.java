package com.ghzdude.randomizer.loot;

import com.ghzdude.randomizer.RandomizationMapData;
import com.ghzdude.randomizer.RandomizerConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.loot.LootContext;
import org.jetbrains.annotations.NotNull;

public class LootRandomizer {

    private static RandomizationMapData INSTANCE = null;

    public static @NotNull ObjectArrayList<ItemStack> randomizeLoot(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (INSTANCE == null) {
            DimensionDataStorage storage = context.getLevel().getServer().overworld().getDataStorage();
            INSTANCE = RandomizationMapData.get(storage, "loot");
        }

        ObjectArrayList<ItemStack> ret = new ObjectArrayList<>();
        String path = context.getQueriedLootTableId().getPath();
        if (!RandomizerConfig.randomizeBlockLoot.get() && path.contains("blocks/") ||
            !RandomizerConfig.randomizeEntityLoot.get() && path.contains("entities/") ||
            !RandomizerConfig.randomizeChestLoot.get() && path.contains("chests/")
        ) {
            return generatedLoot;
        }

        generatedLoot.forEach(stack -> {
            if (stack.isEmpty()) return;
            ret.add(INSTANCE.getStackFor(stack));
        });

        return ret;
    }
}
