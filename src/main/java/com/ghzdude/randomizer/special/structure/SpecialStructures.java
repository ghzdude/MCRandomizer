package com.ghzdude.randomizer.special.structure;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Map;

public class SpecialStructures {

    public static final Map<ResourceKey<Structure>, Integer> CONFIGURED_STRUCTURES = new Object2IntOpenHashMap<>();

    static {
        CONFIGURED_STRUCTURES.put(BuiltinStructures.NETHER_FOSSIL, 0);
        CONFIGURED_STRUCTURES.put(BuiltinStructures.ANCIENT_CITY, 6);
        CONFIGURED_STRUCTURES.put(BuiltinStructures.BASTION_REMNANT, 2);
        CONFIGURED_STRUCTURES.put(BuiltinStructures.FORTRESS, 2);
        CONFIGURED_STRUCTURES.put(BuiltinStructures.STRONGHOLD, 3);
        CONFIGURED_STRUCTURES.put(BuiltinStructures.END_CITY, 3);
        CONFIGURED_STRUCTURES.put(BuiltinStructures.WOODLAND_MANSION, 4);    }
}
