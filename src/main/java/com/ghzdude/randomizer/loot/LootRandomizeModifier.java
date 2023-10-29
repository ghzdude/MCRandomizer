package com.ghzdude.randomizer.loot;

import com.ghzdude.randomizer.LootRandomizer;
import com.ghzdude.randomizer.RandomizerCore;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

public class LootRandomizeModifier extends LootModifier {
    public static final Supplier<Codec<LootRandomizeModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst ->
                    codecStart(inst)
                            .apply(inst, LootRandomizeModifier::new)
            )
    );

    public LootRandomizeModifier(LootItemCondition[] c) {
        super(c);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ObjectArrayList<ItemStack> ret = new ObjectArrayList<>();
        generatedLoot.forEach(stack -> ret.add(new ItemStack(LootRandomizer.ITEM_MAP.get(stack.getItem()))));

        return ret;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
