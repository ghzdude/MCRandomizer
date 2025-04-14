package com.ghzdude.randomizer.mixin.loot;

import com.ghzdude.randomizer.api.EntryAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.stream.Stream;

@Mixin(LootPool.class)
public class LootPoolMixin implements EntryAccessor {

    @Shadow @Final private List<LootPoolEntryContainer> entries;

    @Unique
    private ItemStack[] randomizer$stacks = null;

    @Override
    public ItemStack[] randomizer$getStacks() {
        if (randomizer$stacks == null || needsRecalculate()) {
            randomizer$id = counter.get();
            randomizer$stacks = this.entries.stream()
                    .filter(lootPool -> lootPool instanceof EntryAccessor)
                    .map(lootPool -> (EntryAccessor) lootPool)
                    .flatMap(accessor -> Stream.of(accessor.randomizer$getStacks()))
                    .toArray(ItemStack[]::new);
        }
        return randomizer$stacks;
    }

    @Unique
    private int randomizer$id;

    @Override
    public int randomizer$getId() {
        return this.randomizer$id;
    }
}
