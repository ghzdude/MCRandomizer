package com.ghzdude.randomizer;

import com.ghzdude.randomizer.io.ConfigIO;
import com.ghzdude.randomizer.special.SpecialFeatures;
import com.ghzdude.randomizer.special.structure.SpecialStructures;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/* Structure Randomizer description
 * every so often, generate a structure at some random x, z coordinate near the player
 */
public class StructureRandomizer {
    private static List<ResourceLocation> BLACKLISTED_STRUCTURES = null;
    private static final Object2IntMap<ResourceLocation> VALID_STRUCTURES = new Object2IntOpenHashMap<>();
    private static final List<ResourceLocation> STRUCTURES = new ArrayList<>();

    private static List<ResourceLocation> BLACKLISTED_FEATURES = null;
    private static final Object2IntMap<ResourceLocation> VALID_FEATURES = new Object2IntOpenHashMap<>();
    private static final List<ResourceLocation> FEATURES = new ArrayList<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Registry<Structure> STRUCTURE_REGISTRY;
    private static Registry<ConfiguredFeature<?, ?>> FEATURE_REGISTRY;

    public static void init(RegistryAccess access) {
        STRUCTURE_REGISTRY = access.registryOrThrow(Registries.STRUCTURE);
        FEATURE_REGISTRY = access.registryOrThrow(Registries.CONFIGURED_FEATURE);

        // stronghold is causing log spam
        // Structures
        if (BLACKLISTED_STRUCTURES == null) {
            BLACKLISTED_STRUCTURES = ConfigIO.read("blacklisted_structures",
                List.of(ResourceLocation.parse("namespace:structure_name_here")),
                STRUCTURE_REGISTRY);
        }

        if (VALID_STRUCTURES.isEmpty()) {
            ConfigIO.readValues("structures", SpecialStructures.CONFIGURED_STRUCTURES, STRUCTURE_REGISTRY)
                .object2IntEntrySet().forEach(StructureRandomizer::putValidStructure);
        }

        for (var structure : STRUCTURE_REGISTRY.keySet()) {
            putValidStructure(structure, 1);
        }

        STRUCTURES.addAll(VALID_STRUCTURES.keySet());

        // Features
        if (BLACKLISTED_FEATURES == null) {
            BLACKLISTED_FEATURES = ConfigIO.read("blacklisted_features", SpecialFeatures.BLACKLISTED_FEATURES, FEATURE_REGISTRY);
        }

        if (VALID_FEATURES.isEmpty()) {
            ConfigIO.readValues("features", SpecialFeatures.DEFAULT_FEATURES, FEATURE_REGISTRY)
                .object2IntEntrySet().forEach(StructureRandomizer::putValidFeature);
        }

        for (var loc : FEATURE_REGISTRY.keySet()) {
            putValidFeature(loc, 1);
        }

        FEATURES.addAll(VALID_FEATURES.keySet());
    }

    private static void putValidStructure(Map.Entry<ResourceLocation, Integer> entry) {
        if (entry instanceof Object2IntMap.Entry<ResourceLocation> intEntry)
            putValidStructure(entry.getKey(), intEntry.getIntValue());
        else putValidStructure(entry.getKey(), entry.getValue());
    }

    private static void putValidStructure(ResourceLocation structure, int value) {
        if (BLACKLISTED_STRUCTURES.contains(structure) || VALID_STRUCTURES.containsKey(structure))
            return;
        VALID_STRUCTURES.put(structure, value);
    }

    private static void putValidFeature(Map.Entry<ResourceLocation, Integer> entry) {
        if (entry instanceof Object2IntMap.Entry<ResourceLocation> intEntry)
            putValidFeature(entry.getKey(), intEntry.getIntValue());
        else putValidFeature(entry.getKey(), entry.getValue());
    }

    private static void putValidFeature(ResourceLocation feature, int value) {
        if (BLACKLISTED_FEATURES.contains(feature) || VALID_FEATURES.containsKey(feature))
            return;
        VALID_FEATURES.put(feature, value);
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

        LOGGER.warn("Attempting to generate structure \"{}\"", structure);

        if (!tryPlaceStructure(level, structure, target)) {
            LOGGER.warn("Failed to place structure \"{}\"", structure);
            if (RandomizerConfig.giveRandomItems) {
                pointsToUse -= ItemRandomizer.giveRandomItem(pointsToUse, player.getInventory());
            }
            return pointsToUse;
        }

        LOGGER.warn("Placed \"{}\" at [{}X, {}Y, {}Z]", structure, target.getX(), target.getY(), target.getZ());
        return pointsToUse - VALID_STRUCTURES.getInt(structure);
    }

    private static int placeFeature(int pointsToUse, ServerLevel level, ServerPlayer player) {
        ResourceLocation feature;
        do {
            feature = RandomizerUtil.getRandom(FEATURES);
        } while (VALID_FEATURES.getInt(feature) > pointsToUse);

        if (!tryPlaceFeature(level, feature, getPos(player, level, 48))) {
            LOGGER.warn("Failed to place feature \"{}\"", feature);
            if (RandomizerConfig.giveRandomItems) {
                pointsToUse -= ItemRandomizer.giveRandomItem(pointsToUse, player.getInventory());
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
        int offsetX = level.getRandom().nextIntBetweenInclusive(upperBound / 4, upperBound);
        int offsetZ = level.getRandom().nextIntBetweenInclusive(upperBound / 4, upperBound);

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

    private static boolean tryPlaceStructure(ServerLevel serverLevel, ResourceLocation resourceKey, BlockPos blockPos) {
        Structure structure = RandomizerUtil.getOrThrow(STRUCTURE_REGISTRY, resourceKey);

        ChunkGenerator chunkgenerator = serverLevel.getChunkSource().getGenerator();
        StructureStart structurestart = structure.generate(
                serverLevel.registryAccess(), chunkgenerator, chunkgenerator.getBiomeSource(),
                serverLevel.getChunkSource().randomState(), serverLevel.getStructureManager(),
                serverLevel.getSeed(), new ChunkPos(blockPos), 0, serverLevel, biomes -> true
        );

        if (!structurestart.isValid()) {
            // maybe try again?
            return false;
        }

        BoundingBox boundingbox = structurestart.getBoundingBox();
        ChunkPos minpos = new ChunkPos(SectionPos.blockToSectionCoord(boundingbox.minX()), SectionPos.blockToSectionCoord(boundingbox.minZ()));
        ChunkPos maxpos = new ChunkPos(SectionPos.blockToSectionCoord(boundingbox.maxX()), SectionPos.blockToSectionCoord(boundingbox.maxZ()));
        List<ChunkPos> toCheck = ChunkPos.rangeClosed(minpos, maxpos).toList();
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

    private static boolean tryPlaceFeature(ServerLevel serverLevel, ResourceLocation location, BlockPos blockPos) {
        var feature = RandomizerUtil.getOrThrow(FEATURE_REGISTRY, location);

        LOGGER.warn("Placing feature \"{}\"", location);
        if (feature.config() instanceof OreConfiguration oreConfiguration) {
            // todo special handling of ore features?
        }
        var optional = BlockPos.findClosestMatch(blockPos, 8, 32, featurePredicate(serverLevel, feature));
        if (optional.isEmpty()) return false;

        var pos = optional.get();
        LOGGER.warn("Feature \"{}\" placed at [{}X, {}Y, {}Z]", location, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
    private static Predicate<BlockPos> featurePredicate(ServerLevel level, ConfiguredFeature<?, ?> feature) {
        return pos -> feature.place(level, level.getChunkSource().getGenerator(), level.getRandom(), pos);
    }
}
