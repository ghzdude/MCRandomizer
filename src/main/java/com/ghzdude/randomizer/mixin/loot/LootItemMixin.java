package com.ghzdude.randomizer.mixin.loot;

import com.ghzdude.randomizer.api.EntryAccessor;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LootItem.class)
public class LootItemMixin implements EntryAccessor {

    @Shadow @Final private Holder<Item> item;

    @Unique
    private ItemStack[] randomizer$stacks = null;

    @Override
    public ItemStack[] randomizer$getStacks() {
        if (randomizer$stacks == null || needsRecalculate()) {
            randomizer$id = counter.get();
            randomizer$stacks = new ItemStack[] { new ItemStack(this.item) };
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
