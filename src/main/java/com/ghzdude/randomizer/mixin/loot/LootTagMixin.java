package com.ghzdude.randomizer.mixin.loot;

import com.ghzdude.randomizer.api.EntryAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.entries.TagEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

@Mixin(TagEntry.class)
public class LootTagMixin implements EntryAccessor {

    @Shadow @Final private TagKey<Item> tag;
    @Unique
    private static ItemStack[] randomizer$stacks = null;

    @Override
    public ItemStack[] randomizer$getStacks() {
        if (randomizer$stacks == null || needsRecalculate()) {
            randomizer$id = counter.get();
            List<Holder<Item>> items = new ArrayList<>();
            BuiltInRegistries.ITEM.getTagOrEmpty(this.tag).forEach(items::add);
            randomizer$stacks = items.stream().map(ItemStack::new).toArray(ItemStack[]::new);
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
