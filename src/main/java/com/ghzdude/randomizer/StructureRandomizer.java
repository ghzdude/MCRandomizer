package com.ghzdude.randomizer;

import com.ghzdude.randomizer.io.ConfigIO;
import com.ghzdude.randomizer.special.structure.SpecialStructures;
import com.ghzdude.randomizer.util.RandomizerUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;

/* Structure Randomizer description
 * every so often, generate a structure at some random x, z coordinate near the player
 */
public class StructureRandomizer {
    private static final List<ResourceLocation> BLACKLISTED_STRUCTURES = ConfigIO.readStructureBlacklist();
    private static final Object2IntMap<ResourceLocation> VALID_STRUCTURES = new Object2IntOpenHashMap<>();
    private static final List<ResourceLocation> STRUCTURES = new ArrayList<>();

    private static final List<ResourceLocation> BLACKLISTED_FEATURES = List.of();
    private static final Object2IntMap<ResourceLocation> VALID_FEATURES = new Object2IntOpenHashMap<>();
    private static final List<ResourceLocation> FEATURES = new ArrayList<>();

    private static Registry<Structure> STRUCTURE_REGISTRY;
    private static Registry<ConfiguredFeature<?, ?>> FEATURE_REGISTRY;

    public static void init(RegistryAccess access) {
        STRUCTURE_REGISTRY = access.registryOrThrow(Registries.STRUCTURE);
        FEATURE_REGISTRY = access.registryOrThrow(Registries.CONFIGURED_FEATURE);

        VALID_STRUCTURES.putAll(SpecialStructures.CONFIGURED_STRUCTURES);
        STRUCTURES.addAll(VALID_STRUCTURES.keySet());

        for (var loc : STRUCTURE_REGISTRY.keySet()) {
            if (BLACKLISTED_STRUCTURES.contains(loc) || VALID_STRUCTURES.containsKey(loc))
                continue;

            VALID_STRUCTURES.put(loc, 1);
            STRUCTURES.add(loc);
        }

        // todo blacklist
        for (var loc : FEATURE_REGISTRY.keySet()) {
            if (BLACKLISTED_FEATURES.contains(loc) || VALID_FEATURES.containsKey(loc))
                continue;

            VALID_FEATURES.put(loc, 1);
            FEATURES.add(loc);
        }
    }

    public static int tryPlace(int pointsToUse, ServerLevel level, ServerPlayer player) {
        return RandomizerCore.seededRNG.nextInt(10) == 0 ?
                placeStructure(pointsToUse, level, player) :
                placeFeature(pointsToUse, level, player);
    }

    private static int placeStructure(int pointsToUse, ServerLevel level, ServerPlayer player) {
        if (pointsToUse < 1) return pointsToUse;

        var structure = selectStructure(pointsToUse);

        BlockPos target = getPos(player, level, 128);

        RandomizerCore.LOGGER.warn("Attempting to generate \"{}\"", structure);

        if (!tryPlaceStructure(level, ResourceKey.create(STRUCTURE_REGISTRY.key(), structure), target)) {
            if (RandomizerConfig.giveRandomItems) {
                pointsToUse -= ItemRandomizer.giveRandomItem(pointsToUse, player.getInventory());
            }
            return pointsToUse;
        }

        RandomizerCore.LOGGER.warn("Placed \"{}\" at [{}X, {}Y, {}Z]", structure, target.getX(), target.getY(), target.getZ());
        return pointsToUse - VALID_STRUCTURES.getInt(structure);
    }

    private static int placeFeature(int pointsToUse, ServerLevel level, ServerPlayer player) {
        ResourceLocation feature;
        do {
            int id = RandomizerCore.seededRNG.nextInt(FEATURES.size());
            feature = FEATURES.get(id);
        } while (VALID_FEATURES.getInt(feature) > pointsToUse);

        if (!tryPlaceFeature(level, ResourceKey.create(FEATURE_REGISTRY.key(), feature), getPos(player, level, 64))) {
            RandomizerCore.LOGGER.warn("Failed to place feature \"{}\"", feature);
            if (RandomizerConfig.giveRandomItems) {
                pointsToUse -= ItemRandomizer.giveRandomItem(pointsToUse, player.getInventory());
                RandomizerCore.incrementAmtItemsGiven();
            }
            return pointsToUse;
        }

        return pointsToUse - VALID_FEATURES.getInt(feature);
    }

    private static ResourceLocation selectStructure(int points) {
        ResourceLocation structure;
        do {
            structure = RandomizerUtil.getRandom(STRUCTURES);
        } while (VALID_STRUCTURES.getInt(structure) > points);
        return structure;
    }

    private static BlockPos getPos(ServerPlayer player, ServerLevel level, int upperBound) {
        if (upperBound < 32) upperBound = 32;
        int offsetX = level.getRandom().nextIntBetweenInclusive(32, upperBound);
        int offsetZ = level.getRandom().nextIntBetweenInclusive(32, upperBound);

        switch (level.getRandom().nextInt(4)) {
            case 1 -> offsetX = -offsetX;
            case 2 -> offsetZ = -offsetZ;
            case 3 -> {
                offsetX = -offsetX;
                offsetZ = -offsetZ;
            }
        }

        return player.getOnPos().offset(offsetX, 1, offsetZ);
    }

    private static boolean tryPlaceStructure(ServerLevel serverLevel, ResourceKey<Structure> resourceKey, BlockPos blockPos) {
        Structure structure = STRUCTURE_REGISTRY.getOrThrow(resourceKey);

        ChunkGenerator chunkgenerator = serverLevel.getChunkSource().getGenerator();
        StructureStart structurestart = structure.generate(
                serverLevel.registryAccess(), chunkgenerator, chunkgenerator.getBiomeSource(),
                serverLevel.getChunkSource().randomState(), serverLevel.getStructureManager(),
                serverLevel.getSeed(), new ChunkPos(blockPos), 0, serverLevel, biomes -> true
        );

        if (!structurestart.isValid()) {
            RandomizerCore.LOGGER.warn("Invalid Structure Start for \"{}\"!", structure);
            return false;
        }

        BoundingBox boundingbox = structurestart.getBoundingBox();
        ChunkPos chunkpos = new ChunkPos(SectionPos.blockToSectionCoord(boundingbox.minX()), SectionPos.blockToSectionCoord(boundingbox.minZ()));
        ChunkPos chunkpos1 = new ChunkPos(SectionPos.blockToSectionCoord(boundingbox.maxX()), SectionPos.blockToSectionCoord(boundingbox.maxZ()));
        List<ChunkPos> toCheck = ChunkPos.rangeClosed(chunkpos, chunkpos1).toList();
        for (ChunkPos chunkPos : toCheck) {
            BoundingBox bb = new BoundingBox(
                    chunkPos.getMinBlockX(), serverLevel.getMinBuildHeight(), chunkPos.getMinBlockZ(),
                    chunkPos.getMaxBlockX(), serverLevel.getMaxBuildHeight(), chunkPos.getMaxBlockZ()
            );

            // todo maybe place blocks here?
            structurestart.placeInChunk(
                    serverLevel, serverLevel.structureManager(), chunkgenerator,
                    serverLevel.getRandom(), bb, chunkPos
            );
        }
        return true;
    }

    private static boolean tryPlaceFeature(ServerLevel serverLevel, ResourceKey<ConfiguredFeature<?, ?>> resourceKey, BlockPos blockPos) {
        var feature = FEATURE_REGISTRY.getOrThrow(resourceKey);

        RandomizerCore.LOGGER.warn("Placing feature \"{}\"", resourceKey.location());

        var optional = BlockPos.findClosestMatch(blockPos, 5, 32, pos ->
                feature.place(serverLevel, serverLevel.getChunkSource().getGenerator(), serverLevel.getRandom(), pos));
        if (optional.isEmpty()) return false;

        var pos = optional.get();
        RandomizerCore.LOGGER.warn("Feature \"{}\" placed at [{}X, {}Y, {}Z]", resourceKey.location(), pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
}
