package com.ghzdude.randomizer.special.structure;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

public class SpecialStructures {

    // todo make configurable
    public static final Object2IntMap<ResourceLocation> CONFIGURED_STRUCTURES = new Object2IntOpenHashMap<>();

    static {
        put(BuiltinStructures.NETHER_FOSSIL, 0);
        put(BuiltinStructures.ANCIENT_CITY, 6);
        put(BuiltinStructures.BASTION_REMNANT, 2);
        put(BuiltinStructures.FORTRESS, 2);
        put(BuiltinStructures.STRONGHOLD, 3);
        put(BuiltinStructures.END_CITY, 3);
        put(BuiltinStructures.WOODLAND_MANSION, 4);
    }

    private static void put(ResourceKey<Structure> resourceKey, int value) {
        CONFIGURED_STRUCTURES.put(resourceKey.location(), value);
    }
}
