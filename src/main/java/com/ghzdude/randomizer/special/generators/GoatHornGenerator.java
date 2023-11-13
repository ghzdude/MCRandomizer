package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryManager;

import java.util.ArrayList;

public class GoatHornGenerator {
    private static final ArrayList<ResourceLocation> INSTRUMENT_LIST = new ArrayList<>(RegistryManager.ACTIVE.getRegistry(Registry.INSTRUMENT_REGISTRY).getKeys());
    public static void applyGoatHornSound(ItemStack stack) {
        CompoundTag baseTag = new CompoundTag();
        int id = RandomizerCore.unseededRNG.nextInt(INSTRUMENT_LIST.size());
        baseTag.putString("instrument", INSTRUMENT_LIST.get(id).toString());
        stack.setTag(baseTag);
    }
}
