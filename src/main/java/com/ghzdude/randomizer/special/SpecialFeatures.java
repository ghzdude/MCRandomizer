package com.ghzdude.randomizer.special;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.data.worldgen.features.CaveFeatures;
import net.minecraft.data.worldgen.features.EndFeatures;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.data.worldgen.features.OreFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.stream.Stream;

public class SpecialFeatures {
    public static final Object2IntMap<ResourceLocation> DEFAULT_FEATURES = new Object2IntOpenHashMap<>();
    public static final List<ResourceLocation> BLACKLISTED_FEATURES = Stream.of(
                    CaveFeatures.SCULK_PATCH_DEEP_DARK,
                    CaveFeatures.SCULK_PATCH_ANCIENT_CITY,
                    CaveFeatures.SCULK_VEIN,
                    MiscOverworldFeatures.LAKE_LAVA,
                    MiscOverworldFeatures.DISK_SAND,
                    MiscOverworldFeatures.DISK_GRAVEL,
                    MiscOverworldFeatures.DISK_CLAY,
                    MiscOverworldFeatures.DISK_GRASS)
            .map(ResourceKey::location).toList();

    static {

        // Ore Features
        put(OreFeatures.ORE_COAL, 2);
        put(OreFeatures.ORE_COAL_BURIED, 2);

        put(OreFeatures.ORE_IRON_SMALL, 3);
        put(OreFeatures.ORE_IRON, 4);

        put(OreFeatures.ORE_GOLD, 5);
        put(OreFeatures.ORE_GOLD_BURIED, 5);
        put(OreFeatures.ORE_NETHER_GOLD, 5);

        put(OreFeatures.ORE_DIAMOND_SMALL, 5);
        put(OreFeatures.ORE_DIAMOND_BURIED, 5);
        put(OreFeatures.ORE_DIAMOND_MEDIUM, 6);
        put(OreFeatures.ORE_DIAMOND_LARGE, 7);

        put(OreFeatures.ORE_ANCIENT_DEBRIS_SMALL, 8);
        put(OreFeatures.ORE_ANCIENT_DEBRIS_LARGE, 10);

        put(OreFeatures.ORE_REDSTONE, 4);

        put(OreFeatures.ORE_LAPIS, 4);

        put(OreFeatures.ORE_COPPPER_SMALL, 2);
        put(OreFeatures.ORE_COPPER_LARGE, 4);

        // Cave Features
        put(CaveFeatures.AMETHYST_GEODE, 8);
        put(CaveFeatures.FOSSIL_COAL, 3);
        put(CaveFeatures.FOSSIL_DIAMONDS, 7);
        put(CaveFeatures.MONSTER_ROOM, 5);

        put(EndFeatures.END_ISLAND, 15);
    }

    private static void put(ResourceKey<?> key, int value) {
        DEFAULT_FEATURES.put(key.location(), value);
    }
}
