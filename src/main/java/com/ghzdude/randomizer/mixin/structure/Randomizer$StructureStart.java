package com.ghzdude.randomizer.mixin.structure;

import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StructureStart.class)
public interface Randomizer$StructureStart {

    @Accessor("pieceContainer")
    PiecesContainer getContainer();
}
