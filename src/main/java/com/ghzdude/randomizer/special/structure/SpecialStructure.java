package com.ghzdude.randomizer.special.structure;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;

public class SpecialStructure {
    public ResourceKey<Structure> key;
    public int value;

    public SpecialStructure(ResourceKey<Structure> structureKey, int value) {
        this.value = value;
        this.key = structureKey;
    }
}
