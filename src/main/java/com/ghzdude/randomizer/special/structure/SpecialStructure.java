package com.ghzdude.randomizer.special.structure;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;

public class SpecialStructure {
    public Structure structure;
    public ResourceLocation location;
    public int value;

    public SpecialStructure(Holder<Structure> structure, int value) {
        this.structure = structure.get();
        this.value = value;

        if (structure.unwrapKey().isPresent()) {
            this.location = structure.unwrapKey().get().location();
        } else {
            this.location = new ResourceLocation("UnknownStructure!");
        }
    }

    public SpecialStructure(Structure structure, ResourceLocation location, int value) {
        this.structure = structure;
        this.location = location;
        this.value = value;
    }
}
