package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

import java.util.ArrayList;

public class GoatHornGenerator {
    private static final ArrayList<ResourceLocation> INSTRUMENT_LIST = new ArrayList<>(BuiltInRegistries.INSTRUMENT.keySet());
    public static void applyGoatHornSound(ItemStack stack) {
        CompoundTag baseTag = new CompoundTag();
        int id = RandomizerCore.rng.nextInt(INSTRUMENT_LIST.size());
        baseTag.putString("instrument", INSTRUMENT_LIST.get(id).toString());
        stack.setTag(baseTag);
    }
}
