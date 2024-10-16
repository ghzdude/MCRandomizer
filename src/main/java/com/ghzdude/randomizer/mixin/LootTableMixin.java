package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.api.LootTableInfo;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(LootTable.class)
public class LootTableMixin implements LootTableInfo {

    @Shadow @Final private List<LootPool> pools;

    @Override
    public boolean randomizer$requiresSilkTouch() {
        boolean b = false;
        for (var p : pools) {

        }
        return b;
    }

    @Mixin(LootPool.class)
    public static class LootPoolMixin implements LootPoolInfo {

        @Shadow @Final private List<LootPoolEntryContainer> entries;

        @Override
        public boolean randomizer$checkForSilkTouch() {
            for (var e : entries) {

            }
            return false;
        }
    }
}
