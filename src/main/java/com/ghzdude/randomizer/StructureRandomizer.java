package com.ghzdude.randomizer;

import com.ghzdude.randomizer.special.structure.*;
import net.minecraft.core.BlockPos;
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

import java.util.ArrayList;
import java.util.Collection;

/* Structure Randomizer description
 * every so often, generate a structure at some random x, z coordinate near the player
 */
public class StructureRandomizer {
    private static final SpecialStructureList STRUCTURES = new SpecialStructureList(configureStructures());;

    public static int placeStructure(int pointsToUse, ServerLevel level, Player player) {
        if (pointsToUse < 1) return pointsToUse;

        SpecialStructure structure = selectStructure(pointsToUse);

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

        RandomizerCore.LOGGER.warn(String.format("Attempting to generate [%s] at %s", structure.location, blockPos));
        player.sendSystemMessage(Component.translatable("structure.spawning", structure.location));

        boolean success = tryPlaceStructure(level, structure.structure, blockPos);
        if (!success) {
            player.sendSystemMessage(Component.translatable("structure.spawning.failed", structure.location));
            return pointsToUse - ItemRandomizer.giveRandomItem(pointsToUse, player);
        } else {
            player.sendSystemMessage(Component.translatable("structure.spawning.success", structure.location));
            return pointsToUse - structure.value;
        }
    }

    private static SpecialStructure selectStructure(int points) {
        SpecialStructure structure;
        do {
            int id = RandomizerCore.RANDOM.nextInt(STRUCTURES.size());
            structure = STRUCTURES.get(id);
        } while (structure.value > points);
        return structure;
    }

    private static boolean tryPlaceStructure(ServerLevel serverLevel, Structure structure, BlockPos blockPos) {
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

    @SuppressWarnings("SuspiciousMethodCalls")
    private static Collection<SpecialStructure> configureStructures() {
        ArrayList<SpecialStructure> list = new SpecialStructureList();

        for (Structure structure : BuiltinRegistries.STRUCTURES) {
            SpecialStructure toAdd;
            if (SpecialStructures.CONFIGURED_STRUCTURES.contains(structure)) {
                toAdd = SpecialStructures.CONFIGURED_STRUCTURES.get(SpecialStructures.CONFIGURED_STRUCTURES.indexOf(structure));
            } else {
                toAdd = new SpecialStructure(structure, BuiltinRegistries.STRUCTURES.getKey(structure), 1);
            }
            list.add(toAdd);
        }

        return list;
    }
}
