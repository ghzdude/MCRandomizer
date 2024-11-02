package com.ghzdude.randomizer.special.structure;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

public class SpecialStructures {

    public static final Object2IntMap<Structure> CONFIGURED_STRUCTURES = new Object2IntOpenHashMap<>();

    public static void init(Registry<Structure> registry) {
        CONFIGURED_STRUCTURES.put(registry.getOrThrow(BuiltinStructures.NETHER_FOSSIL), 0);
        CONFIGURED_STRUCTURES.put(registry.getOrThrow(BuiltinStructures.ANCIENT_CITY), 6);
        CONFIGURED_STRUCTURES.put(registry.getOrThrow(BuiltinStructures.BASTION_REMNANT), 2);
        CONFIGURED_STRUCTURES.put(registry.getOrThrow(BuiltinStructures.FORTRESS), 2);
        CONFIGURED_STRUCTURES.put(registry.getOrThrow(BuiltinStructures.STRONGHOLD), 3);
        CONFIGURED_STRUCTURES.put(registry.getOrThrow(BuiltinStructures.END_CITY), 3);
        CONFIGURED_STRUCTURES.put(registry.getOrThrow(BuiltinStructures.WOODLAND_MANSION), 4);
    }
}
