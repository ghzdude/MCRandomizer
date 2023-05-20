package com.ghzdude.randomizer.saveddata;

import com.ghzdude.randomizer.RandomizerCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;


public class RandomizerData extends SavedData {
    public boolean recipesChanged;

    public ArrayList<ItemStack> recipeSet;
    public boolean lootChanged;
    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("has_recipes_changed", this.recipesChanged);
        tag.putBoolean("has_loot_changed", this.lootChanged);
        return tag;
    }

    public static RandomizerData create() {
        return new RandomizerData();
    }

    public static RandomizerData load(CompoundTag tag) {
        RandomizerData data = create();
        data.recipesChanged = tag.getBoolean("has_recipes_changed");
        data.lootChanged = tag.getBoolean("has_loot_changed");
        return data;
    }

    public static RandomizerData get(DimensionDataStorage storage){
        return storage.computeIfAbsent(RandomizerData::load, RandomizerData::create, RandomizerCore.MODID);
    }
}

