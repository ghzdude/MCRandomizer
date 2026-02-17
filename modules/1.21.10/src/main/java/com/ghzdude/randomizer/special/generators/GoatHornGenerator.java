package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.InstrumentComponent;

import java.util.List;

public class GoatHornGenerator {

    private static List<? extends Holder<Instrument>> INSTRUMENT_LIST;

    public static void init(HolderLookup.Provider provider) {
        INSTRUMENT_LIST = provider.lookupOrThrow(Registries.INSTRUMENT)
                .listElements().toList();
    }

    public static void applyGoatHornSound(ItemStack stack) {
        int id = RandomizerCore.unseededRNG.nextInt(INSTRUMENT_LIST.size());
        stack.set(DataComponents.INSTRUMENT, new InstrumentComponent(INSTRUMENT_LIST.get(id)));
    }
}
