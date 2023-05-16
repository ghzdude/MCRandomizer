package com.ghzdude.randomizer;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
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

    RandomizerSavedData (CompoundTag tag) {
        SAVE_DATA = tag;
    }

    public RandomizerSavedData create () {
        return new RandomizerSavedData(new CompoundTag());
    }

    public RandomizerSavedData load (CompoundTag tag) {
        RandomizerSavedData data = this.create();
        data.save(tag);
        return data;
    }
    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        SAVE_DATA = tag;
        return tag;
    }

    public RandomizerSavedData computeIfAbsent (MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(this::load, this::create, RandomizerCore.MODID);
    }

    public static File getFile (IntegratedServer server) {

        File file = new File(server.getServerDirectory(), "/overworld/DIM-0/data/example.dat");

        if (file.canWrite() && file.canRead()) {
            return file;
        } else {
            try {
                NbtIo.write(new CompoundTag(), file);
                return file;
            } catch (IOException e) {
                RandomizerCore.LOGGER.warn("file " + file.getAbsolutePath() + " does not exist!");
            }
        }
        return null;
    }

    @Override
    public boolean isDirty() {
        return true;
    }
}
