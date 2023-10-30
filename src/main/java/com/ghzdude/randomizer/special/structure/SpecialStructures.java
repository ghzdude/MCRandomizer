package com.ghzdude.randomizer.special.structure;

import net.minecraft.data.worldgen.Structures;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;

import java.util.List;

public class SpecialStructures {

    public static final SpecialStructureList CONFIGURED_STRUCTURES = new SpecialStructureList(List.of(
            new SpecialStructure(BuiltinStructures.NETHER_FOSSIL, 0),
            new SpecialStructure(BuiltinStructures.ANCIENT_CITY, 6),
            new SpecialStructure(BuiltinStructures.BASTION_REMNANT, 2),
            new SpecialStructure(BuiltinStructures.FORTRESS, 2),
            new SpecialStructure(BuiltinStructures.STRONGHOLD, 3),
            new SpecialStructure(BuiltinStructures.END_CITY, 3),
            new SpecialStructure(BuiltinStructures.WOODLAND_MANSION, 4)
    ));
}
