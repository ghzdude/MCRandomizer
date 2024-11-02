package com.ghzdude.randomizer.special;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.data.worldgen.features.EndFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import static net.minecraft.data.worldgen.features.CaveFeatures.*;
import static net.minecraft.data.worldgen.features.OreFeatures.*;

public class SpecialFeatures {
    public static final Object2IntMap<ResourceLocation> DEFAULT_FEATURES = new Object2IntOpenHashMap<>();

    static {

        // Ore Features
        put(ORE_COAL, 2);
        put(ORE_COAL_BURIED, 2);

        put(ORE_IRON_SMALL, 3);
        put(ORE_IRON, 4);

        put(ORE_GOLD, 5);
        put(ORE_GOLD_BURIED, 5);
        put(ORE_NETHER_GOLD, 5);

        put(ORE_DIAMOND_SMALL, 5);
        put(ORE_DIAMOND_BURIED, 5);
        put(ORE_DIAMOND_MEDIUM, 6);
        put(ORE_DIAMOND_LARGE, 7);

        put(ORE_ANCIENT_DEBRIS_SMALL, 8);
        put(ORE_ANCIENT_DEBRIS_LARGE, 10);

        put(ORE_REDSTONE, 4);

        put(ORE_LAPIS, 4);

        put(ORE_COPPPER_SMALL, 2);
        put(ORE_COPPER_LARGE, 4);

        // Cave Features
        put(AMETHYST_GEODE, 8);
        put(FOSSIL_COAL, 3);
        put(FOSSIL_DIAMONDS, 7);
        put(MONSTER_ROOM, 5);

        put(EndFeatures.END_ISLAND, 15);
    }

    private static void put(ResourceKey<?> key, int value) {
        DEFAULT_FEATURES.put(key.location(), value);
    }
}
