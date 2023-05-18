package com.ghzdude.randomizer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.List;

/* Structure Randomizer description
 * every so often, generate a structure at some random x, z coordinate near the player
 */
public class StructureRandomizer {

    private static final List<Holder.Reference<Structure>> STRUCTURES = BuiltinRegistries.STRUCTURES.holders().toList();

    public static int placeStructure(int pointsToUse, ServerLevel level, Player player) {
        if (pointsToUse < 50) return pointsToUse;

        int id = level.getRandom().nextInt(STRUCTURES.size());

        Holder.Reference<Structure> structure = STRUCTURES.get(id);

        int offsetX = level.getRandom().nextIntBetweenInclusive(32, 64);
        int offsetZ = level.getRandom().nextIntBetweenInclusive(32, 64);

        BlockPos blockPos = new BlockPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());

        // ChunkPos pos = new ChunkPos(player.getBlockX(), player.getBlockZ());
        if (level.getRandom().nextBoolean()) {
            blockPos = blockPos.offset(offsetX, 0, offsetZ);
        } else if (level.getRandom().nextBoolean()) {
            blockPos = blockPos.offset(-offsetX, 0, offsetZ);
        } else if (level.getRandom().nextBoolean()) {
            blockPos = blockPos.offset(-offsetX, 0, -offsetZ);
        } else {
            blockPos = blockPos.offset(offsetX, 0, -offsetZ);
        }

        RandomizerCore.LOGGER.warn(String.format("Attempting to generate a structure! (%s AT %s)", structure.key().location(), blockPos));
        player.sendSystemMessage(Component.literal(String.format("Attempting to generate a %s! The game may lag for a while!", structure.key().location(), blockPos)));

        boolean success = tryPlaceStructure(level, structure, blockPos);
        if (!success) return pointsToUse;

        return pointsToUse - 50;
    }

    public static boolean tryPlaceStructure(ServerLevel serverLevel, Holder<Structure> structureHolder, BlockPos blockPos) {
        Structure structure = structureHolder.value();
        ChunkGenerator chunkgenerator = serverLevel.getChunkSource().getGenerator();
        StructureStart structurestart = structure.generate(serverLevel.registryAccess(), chunkgenerator, chunkgenerator.getBiomeSource(), serverLevel.getChunkSource().randomState(), serverLevel.getStructureManager(), serverLevel.getSeed(), new ChunkPos(blockPos), 0, serverLevel, (p_214580_) -> true);
        if (!structurestart.isValid()) {
            RandomizerCore.LOGGER.warn("Invalid Structure Start!");
            return false;
        }

        BoundingBox boundingbox = structurestart.getBoundingBox();
        ChunkPos chunkpos = new ChunkPos(SectionPos.blockToSectionCoord(boundingbox.minX()), SectionPos.blockToSectionCoord(boundingbox.minZ()));
        ChunkPos chunkpos1 = new ChunkPos(SectionPos.blockToSectionCoord(boundingbox.maxX()), SectionPos.blockToSectionCoord(boundingbox.maxZ()));
        ChunkPos.rangeClosed(chunkpos, chunkpos1).forEach((p_214558_) -> {
            structurestart.placeInChunk(serverLevel, serverLevel.structureManager(), chunkgenerator, serverLevel.getRandom(), new BoundingBox(p_214558_.getMinBlockX(), serverLevel.getMinBuildHeight(), p_214558_.getMinBlockZ(), p_214558_.getMaxBlockX(), serverLevel.getMaxBuildHeight(), p_214558_.getMaxBlockZ()), p_214558_);
        });
        RandomizerCore.LOGGER.warn("Structure Generated!");
        return true;
    }
}
