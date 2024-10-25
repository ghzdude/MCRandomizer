package com.ghzdude.randomizer;

import com.ghzdude.randomizer.io.ConfigIO;
import com.ghzdude.randomizer.special.structure.SpecialStructures;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/* Structure Randomizer description
 * every so often, generate a structure at some random x, z coordinate near the player
 */
public class StructureRandomizer {
    private static final List<ResourceLocation> BLACKLISTED_STRUCTURES = ConfigIO.readStructureBlacklist();
    private static final Map<ResourceKey<Structure>, Integer> VALID_STRUCTURES = new Object2IntOpenHashMap<>();
    private static final List<ResourceKey<Structure>> STRUCTURES = new ArrayList<>();

    public static int placeStructure(int pointsToUse, ServerLevel level, ServerPlayer player) {
        if (pointsToUse < 1) return pointsToUse;

        ResourceKey<Structure> structureKey = selectStructure(pointsToUse);

        int offsetX = level.getRandom().nextIntBetweenInclusive(32, 64);
        int offsetZ = level.getRandom().nextIntBetweenInclusive(32, 64);

        BlockPos target = player.getOnPos();

        if (level.getRandom().nextBoolean()) {
            target = target.offset(offsetX, 0, offsetZ);
        } else if (level.getRandom().nextBoolean()) {
            target = target.offset(-offsetX, 0, offsetZ);
        } else if (level.getRandom().nextBoolean()) {
            target = target.offset(-offsetX, 0, -offsetZ);
        } else {
            target = target.offset(offsetX, 0, -offsetZ);
        }

        RandomizerCore.LOGGER.warn(String.format("Attempting to generate [%s] at %s", structureKey.location(), target));
        sendMessage(player, "structure.spawning", structureKey.location());

        boolean success = tryPlaceStructure(level, structureKey, target);
        if (!success) {
            sendMessage(player, "structure.spawning.failed", structureKey.location());
            if (RandomizerConfig.giveRandomItems) {
                pointsToUse -= ItemRandomizer.giveRandomItem(pointsToUse, player.getInventory());
            }
            return pointsToUse;
        }
        sendMessage(player, "structure.spawning.success", structureKey.location(), target);
        return pointsToUse - VALID_STRUCTURES.get(structureKey);
    }

    private static void sendMessage(ServerPlayer player, String lang, Object... keys) {
        Component[] args = new Component[keys.length];
        for (int i = 0; i < keys.length; i++) {
            var key = keys[i];
            args[i] = Component.literal(key.toString());
        }
        var contents = new TranslatableContents(lang, null, args);
        player.displayClientMessage(MutableComponent.create(contents), false);
    }

    private static ResourceKey<Structure> selectStructure(int points) {
        ResourceKey<Structure> structure;
        do {
            int id = RandomizerCore.seededRNG.nextInt(STRUCTURES.size());
            structure = STRUCTURES.get(id);
        } while (VALID_STRUCTURES.get(structure) > points);
        return structure;
    }

    private static boolean tryPlaceStructure(ServerLevel serverLevel, ResourceKey<Structure> structureKey, BlockPos blockPos) {
        ChunkGenerator chunkgenerator = serverLevel.getChunkSource().getGenerator();

        Registry<Structure> registry = getStructures(serverLevel.registryAccess());
        Structure structure = registry.getOrThrow(structureKey);

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

    public static void configureStructures(RegistryAccess access) {
        VALID_STRUCTURES.putAll(SpecialStructures.CONFIGURED_STRUCTURES);
        STRUCTURES.addAll(VALID_STRUCTURES.keySet());

        Registry<Structure> structures = getStructures(access);

        for (ResourceKey<Structure> key : structures.registryKeySet()) {
            if (BLACKLISTED_STRUCTURES.contains(key.location()))
                continue;

            VALID_STRUCTURES.put(key, 1);
            STRUCTURES.add(key);
        }
    }

    @NotNull
    public static Registry<Structure> getStructures(RegistryAccess access) {
        return access.registryOrThrow(Registries.STRUCTURE);
    }
}
