package com.ghzdude.randomizer;

import com.ghzdude.randomizer.io.ConfigIO;
import com.ghzdude.randomizer.special.structure.SpecialStructure;
import com.ghzdude.randomizer.special.structure.SpecialStructureList;
import com.ghzdude.randomizer.special.structure.SpecialStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.Optional;

/* Structure Randomizer description
 * every so often, generate a structure at some random x, z coordinate near the player
 */
public class StructureRandomizer {
    private static final ArrayList<ResourceLocation> BLACKLISTED_STRUCTURES = new ArrayList<>(); // ConfigIO.readStructureBlacklist();
    private static final SpecialStructureList VALID_STRUCTURES = new SpecialStructureList();

    public static int placeStructure(int pointsToUse, ServerLevel level, Player player) {
        if (pointsToUse < 1) return pointsToUse;

        SpecialStructure structure = selectStructure(pointsToUse);

        int offsetX = level.getRandom().nextIntBetweenInclusive(32, 64);
        int offsetZ = level.getRandom().nextIntBetweenInclusive(32, 64);

        BlockPos playerPos = player.getOnPos();

        if (level.getRandom().nextBoolean()) {
            playerPos = playerPos.offset(offsetX, 0, offsetZ);
        } else if (level.getRandom().nextBoolean()) {
            playerPos = playerPos.offset(-offsetX, 0, offsetZ);
        } else if (level.getRandom().nextBoolean()) {
            playerPos = playerPos.offset(-offsetX, 0, -offsetZ);
        } else {
            playerPos = playerPos.offset(offsetX, 0, -offsetZ);
        }

        RandomizerCore.LOGGER.warn(String.format("Attempting to generate [%s] at %s", structure.key, playerPos));
        player.sendSystemMessage(Component.translatable("structure.spawning", structure.key));

        boolean success = tryPlaceStructure(level, structure.key.location(), playerPos);
        if (!success) {
            player.sendSystemMessage(Component.translatable("structure.spawning.failed", structure.key));
            if (RandomizerConfig.itemRandomizerEnabled()) {
                return pointsToUse - ItemRandomizer.giveRandomItem(pointsToUse, player.getInventory());
            } else {
                return pointsToUse;
            }
        }
        player.sendSystemMessage(Component.translatable("structure.spawning.success", structure.key));
        return pointsToUse - structure.value;
    }

    private static SpecialStructure selectStructure(int points) {
        SpecialStructure structure;
        do {
            int id = RandomizerCore.rng.nextInt(VALID_STRUCTURES.size());
            structure = VALID_STRUCTURES.get(id);
        } while (structure.value > points);
        return structure;
    }

    private static boolean tryPlaceStructure(ServerLevel serverLevel, ResourceLocation location, BlockPos blockPos) {
        ChunkGenerator chunkgenerator = serverLevel.getChunkSource().getGenerator();

        Registry<Structure> registry = getStructures(serverLevel.registryAccess());
        Structure structure = registry != null ? registry.get(location) : null;
        if (structure == null) return false;

        StructureStart structurestart = structure.generate(
                serverLevel.registryAccess(), chunkgenerator, chunkgenerator.getBiomeSource(),
                serverLevel.getChunkSource().randomState(), serverLevel.getStructureManager(),
                serverLevel.getSeed(), new ChunkPos(blockPos), 0, serverLevel, (biomes) -> true
        );

        if (!structurestart.isValid()) {
            RandomizerCore.LOGGER.warn("Invalid Structure Start for \"{}\"!", structure);
            return false;
        }

        BoundingBox boundingbox = structurestart.getBoundingBox();
        ChunkPos chunkpos = new ChunkPos(SectionPos.blockToSectionCoord(boundingbox.minX()), SectionPos.blockToSectionCoord(boundingbox.minZ()));
        ChunkPos chunkpos1 = new ChunkPos(SectionPos.blockToSectionCoord(boundingbox.maxX()), SectionPos.blockToSectionCoord(boundingbox.maxZ()));
        ChunkPos.rangeClosed(chunkpos, chunkpos1).forEach((chunkPos) -> {
            structurestart.placeInChunk(serverLevel, serverLevel.structureManager(), chunkgenerator, serverLevel.getRandom(), new BoundingBox(chunkPos.getMinBlockX(), serverLevel.getMinBuildHeight(), chunkPos.getMinBlockZ(), chunkPos.getMaxBlockX(), serverLevel.getMaxBuildHeight(), chunkPos.getMaxBlockZ()), chunkPos);
        });
        RandomizerCore.LOGGER.warn("Structure Generated!");
        return true;
    }

    public static void configureStructures(RegistryAccess access) {
        VALID_STRUCTURES.addAll(SpecialStructures.CONFIGURED_STRUCTURES);

        Registry<Structure> structures = getStructures(access);
        if (structures == null) return;

        for (ResourceKey<Structure> key : structures.registryKeySet()) {
            SpecialStructure toAdd;
            if (BLACKLISTED_STRUCTURES.contains(key.location())) continue;

            toAdd = new SpecialStructure(key, 1);
            VALID_STRUCTURES.add(toAdd);
        }
    }

    public static Registry<Structure> getStructures(RegistryAccess access) {
        Optional<Registry<Structure>> optional = access.registry(Registries.STRUCTURE);
        if (optional.isEmpty()) {
            RandomizerCore.LOGGER.warn("Structure Registry cannot be found!", new IllegalStateException());
            return null;
        }
        return optional.get();
    }
}
