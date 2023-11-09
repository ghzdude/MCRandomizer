package com.ghzdude.randomizer.loot;

import com.ghzdude.randomizer.RandomizationMapData;
import com.ghzdude.randomizer.RandomizerConfig;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

public class LootRandomizeModifier extends LootModifier {

    private static RandomizationMapData INSTANCE = null;
    public static final Supplier<Codec<LootRandomizeModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst ->
                    codecStart(inst).apply(inst, LootRandomizeModifier::new))
    );

    public LootRandomizeModifier(LootItemCondition[] c) {
        super(new LootItemCondition[]{});
    }

    public LootRandomizeModifier() {
        this(new LootItemCondition[]{});
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!RandomizerConfig.lootRandomizerEnabled()) return generatedLoot;

        if (INSTANCE == null) {
            DimensionDataStorage storage = context.getLevel().getServer().overworld().getDataStorage();
            INSTANCE = RandomizationMapData.configure(storage, "loot");
        }

        ObjectArrayList<ItemStack> ret = new ObjectArrayList<>();
        String path = context.getQueriedLootTableId().getPath();
        if (!RandomizerConfig.randomizeBlockLoot() && path.contains("blocks/") ||
            !RandomizerConfig.randomizeEntityLoot() && path.contains("entities/") ||
            !RandomizerConfig.randomizeChestLoot() && path.contains("chests/")
        ) {
            return generatedLoot;
        }

        generatedLoot.forEach(stack -> {
            if (stack.isEmpty()) return;
            ret.add(INSTANCE.getStackFor(stack));
        });

        return ret;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
