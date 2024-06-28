package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

import java.util.ArrayList;
import java.util.List;

public class GoatHornGenerator {
    private static final IdMap<Holder<Instrument>> INSTRUMENT_LIST = BuiltInRegistries.INSTRUMENT.asHolderIdMap();
    public static void applyGoatHornSound(ItemStack stack) {
        int id = RandomizerCore.unseededRNG.nextInt(INSTRUMENT_LIST.size());
        stack.set(DataComponents.INSTRUMENT, INSTRUMENT_LIST.byId(id));
    }
}
