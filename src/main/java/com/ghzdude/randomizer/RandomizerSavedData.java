package com.ghzdude.randomizer;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraftforge.common.util.LevelCapabilityData;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class RandomizerSavedData extends SavedData {
    public static CompoundTag SAVE_DATA = new CompoundTag();
    public int points;
    public int pointMax;
    public int amtItemsGiven;

    RandomizerSavedData () {
        // SAVE_DATA = tag;
        this.points = 0;
        this.pointMax = 1;
        this.amtItemsGiven = 0;
    }

    public static RandomizerSavedData create () {
        return new RandomizerSavedData();
    }

    public static RandomizerSavedData load (CompoundTag tag) {
        RandomizerSavedData data = create();
        data.points = tag.getInt("points");
        data.pointMax = tag.getInt("point_max");
        data.amtItemsGiven = tag.getInt("amount_items_given");
        return data;
    }
    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.put("points", IntTag.valueOf(this.points));
        tag.put("point_max", IntTag.valueOf(this.pointMax));
        tag.put("amount_items_given", IntTag.valueOf(this.amtItemsGiven));
        return tag;
    }

    public static RandomizerSavedData getInstance(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RandomizerSavedData::load, RandomizerSavedData::create, RandomizerCore.MODID);
    }
}
