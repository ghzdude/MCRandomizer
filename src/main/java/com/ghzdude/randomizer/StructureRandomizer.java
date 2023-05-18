package com.ghzdude.randomizer;

import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.List;
import java.util.Optional;

/* Structure Randomizer description
 * every so often, generate a structure at some random x, z coordinate near the player
 */
public class StructureRandomizer {

    private static final List<Holder.Reference<Structure>> STRUCTURES = BuiltinRegistries.STRUCTURES.holders().toList();

    public static int placeStructure(int pointsToUse, ServerLevel level) {
        // if (pointsToUse < 50) return pointsToUse;
        Player player = level.getRandomPlayer();
        if (player == null) return pointsToUse;
        ChunkGenerator generator = level.getChunkSource().getGenerator();


        int id = level.getRandom().nextInt(STRUCTURES.size());

        Holder.Reference<Structure> structure = STRUCTURES.get(id);

        int offsetX = 0; // level.getRandom().nextIntBetweenInclusive(32, 48);
        int offsetZ = 0; // level.getRandom().nextIntBetweenInclusive(32, 46);

        ChunkPos pos;
        if (level.getRandom().nextBoolean()) {
            pos = new ChunkPos((int) (player.getX() + offsetX), (int) (player.getZ() + offsetZ));
        } else if (level.getRandom().nextBoolean()) {
            pos = new ChunkPos((int) (player.getX() - offsetX), (int) (player.getZ() + offsetZ));
        } else if (level.getRandom().nextBoolean()) {
            pos = new ChunkPos((int) (player.getX() - offsetX), (int) (player.getZ() - offsetZ));
        } else {
            pos = new ChunkPos((int) (player.getX() + offsetX), (int) (player.getZ() - offsetZ));
        }

        RandomizerCore.LOGGER.warn(String.format("Attempting to generate a structure! (%s)", structure.key().location()));

        // attempt to make my own Structure.GenerationStub to make StructureStart
        Optional<Structure.GenerationStub> stub = structure.get().findGenerationPoint(
                new Structure.GenerationContext(
                        level.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
                        level.getStructureManager(), new WorldgenRandom(RandomizerCore.RANDOM), level.getSeed(), pos,
                        level, holder -> true
                    )
        );
        if (stub.isPresent()) {
            StructurePiecesBuilder piecesBuilder = stub.get().getPiecesBuilder();
            StructureStart start = new StructureStart(structure.get(), pos, 0, piecesBuilder.build());

            // previous attempt at using structure.generate(). may be issue, but unlikely.
            /*StructureStart start = structure.get().generate(
                    level.registryAccess(), generator,
                    generator.getBiomeSource(),
                    level.getChunkSource().randomState(),
                    level.getStructureManager(),
                    level.getSeed(),
                    pos, 0, level,
                    holder -> true
                );
            */
            BoundingBox boundingBox = start.getBoundingBox(); // unable to generate bb without pieces

            ChunkPos pos1 = new ChunkPos(SectionPos.blockToSectionCoord(boundingBox.minX()), SectionPos.blockToSectionCoord(boundingBox.minZ()));
            ChunkPos pos2 = new ChunkPos(SectionPos.blockToSectionCoord(boundingBox.maxX()), SectionPos.blockToSectionCoord(boundingBox.maxZ()));

            // if this is uncommented, checking for if chunk exists at position returns true. Could this cause problems?
            /*if (ChunkPos.rangeClosed(pos1, pos2).anyMatch(chunkPos -> !level.isLoaded(chunkPos.getWorldPosition()))){
            RandomizerCore.LOGGER.warn(String.format("Failed to generate structure! Chunk not loaded! \n(%s)", structure.key().location()));
            return pointsToUse;
            }*/


            ChunkPos.rangeClosed(pos1, pos2).forEach(chunkPos ->
                    start.placeInChunk(level, level.structureManager(), generator, RandomizerCore.RANDOM,
                            new BoundingBox(
                                    chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ(),
                                    chunkPos.getMaxBlockX(), level.getMaxBuildHeight(), chunkPos.getMaxBlockZ()
                            ), chunkPos
                    )
            );

            // this doesn't make sense, it also doesn't work
            /*generator.createStructures(
                    level.registryAccess(), level.getChunkSource().randomState(), level.structureManager(),
                    level.getChunk(player.getBlockX(), player.getBlockZ()), level.getStructureManager(), level.getSeed()
            );*/

            RandomizerCore.LOGGER.warn("Structure Generated... maybe");
        } else {
            RandomizerCore.LOGGER.warn("Structure failed to generate...");
        }
        return pointsToUse;
    }
}
