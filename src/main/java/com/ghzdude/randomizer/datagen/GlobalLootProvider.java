package com.ghzdude.randomizer.datagen;

import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.loot.LootRandomizeModifier;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.GlobalLootModifierProvider;

public class GlobalLootProvider extends GlobalLootModifierProvider {
    public GlobalLootProvider(PackOutput output) {
        super(output, RandomizerCore.MODID);
    }

    @Override
    protected void start() {
        add("randomize_loot", new LootRandomizeModifier());
    }
}
