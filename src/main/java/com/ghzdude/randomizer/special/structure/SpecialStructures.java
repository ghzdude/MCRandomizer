package com.ghzdude.randomizer.special.structure;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

public class SpecialStructures {

    public static final Object2IntMap<ResourceLocation> CONFIGURED_STRUCTURES = new Object2IntOpenHashMap<>();

    static {
        put(BuiltinStructures.NETHER_FOSSIL, 0); // todo figure out why this structure doesn't work
        put(BuiltinStructures.ANCIENT_CITY, 10);
        put(BuiltinStructures.BASTION_REMNANT, 6);
        put(BuiltinStructures.FORTRESS, 6);
        put(BuiltinStructures.STRONGHOLD, 8);
        put(BuiltinStructures.END_CITY, 8);
        put(BuiltinStructures.WOODLAND_MANSION, 6);
    }

    private static void put(ResourceKey<Structure> resourceKey, int value) {
        CONFIGURED_STRUCTURES.put(resourceKey.location(), value);
    }
}
