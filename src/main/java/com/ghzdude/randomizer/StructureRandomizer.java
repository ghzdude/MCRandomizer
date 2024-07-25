package com.ghzdude.randomizer;

import com.ghzdude.randomizer.io.ConfigIO;
import com.ghzdude.randomizer.mixin.structure.Randomizer$StructureStart;
import com.ghzdude.randomizer.special.structure.SpecialStructures;
import com.ghzdude.randomizer.util.RandomizerUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/* Structure Randomizer description
 * every so often, generate a structure at some random x, z coordinate near the player
 */
public class StructureRandomizer {
    private static List<ResourceLocation> BLACKLISTED_STRUCTURES;
    private static final Object2IntMap<Structure> VALID_STRUCTURES = new Object2IntOpenHashMap<>();
    private static final List<Structure> STRUCTURES = new ArrayList<>();
    private static Registry<Structure> STRUCTURE_REGISTRY;

    public static void init(RegistryAccess access) {
        STRUCTURE_REGISTRY = access.registryOrThrow(Registries.STRUCTURE);
        SpecialStructures.init(STRUCTURE_REGISTRY);

        BLACKLISTED_STRUCTURES = ConfigIO.read("blacklisted_structures",
                List.of(ResourceLocation.parse("namespace:structure_name_here")),
                STRUCTURE_REGISTRY);

        ConfigIO.readValues("structures", SpecialStructures.CONFIGURED_STRUCTURES, STRUCTURE_REGISTRY)
                .object2IntEntrySet().forEach(StructureRandomizer::putValidStructure);

        for (var structure : STRUCTURE_REGISTRY) {
            putValidStructure(structure, 1);
        }
        STRUCTURES.addAll(VALID_STRUCTURES.keySet());
    }

    private static void putValidStructure(Map.Entry<Structure, Integer> entry) {
        if (entry instanceof Object2IntMap.Entry<Structure> intEntry)
            putValidStructure(entry.getKey(), intEntry.getIntValue());
        else putValidStructure(entry.getKey(), entry.getValue());
    }

    private static void putValidStructure(Structure structure, int value) {
        if (BLACKLISTED_STRUCTURES.contains(STRUCTURE_REGISTRY.getKey(structure)) || VALID_STRUCTURES.containsKey(structure))
            return;
        VALID_STRUCTURES.put(structure, value);
    }

    public static int placeStructure(int pointsToUse, ServerLevel level, ServerPlayer player) {
        if (pointsToUse < 1) return pointsToUse;

        var structure = selectStructure(pointsToUse);

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

        RandomizerCore.LOGGER.warn(String.format("Attempting to generate [%s] at %s", structure, target));
        sendMessage(player, "structure.spawning", structure);

        boolean success = tryPlaceStructure(level, structure, target);
        if (!success) {
            sendMessage(player, "structure.spawning.failed", structure);
            if (RandomizerConfig.giveRandomItems) {
                pointsToUse -= ItemRandomizer.giveRandomItem(pointsToUse, player.getInventory());
            }
            return pointsToUse;
        }
        sendMessage(player, "structure.spawning.success", structure, target);
        return pointsToUse - VALID_STRUCTURES.getInt(structure);
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

    private static ResourceLocation selectStructure(int points) {
        Structure structure;
        do {
            structure = RandomizerUtil.getRandom(STRUCTURES, RandomizerCore.unseededRNG);
        } while (VALID_STRUCTURES.getInt(structure) > points);
        return STRUCTURE_REGISTRY.getKey(structure);
    }

    private static boolean tryPlaceStructure(ServerLevel serverLevel, ResourceLocation location, BlockPos blockPos) {
        ChunkGenerator chunkgenerator = serverLevel.getChunkSource().getGenerator();

        Structure structure = Objects.requireNonNull(STRUCTURE_REGISTRY.get(location));

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

            placeStructure(structurestart, serverLevel, bb, chunkPos);
//            structurestart.placeInChunk(
//                    serverLevel, serverLevel.structureManager(), chunkgenerator,
//                    serverLevel.getRandom(), bb, chunkPos
//            );
        }
        return true;
    }

    private static void placeStructure(StructureStart structureStart, ServerLevel serverLevel, BoundingBox bb, ChunkPos chunkPos) {
        List<StructurePiece> list = structureStart.getPieces();
        if (!list.isEmpty()) {
            BoundingBox boundingbox = list.getFirst().getBoundingBox();
            BlockPos blockpos = boundingbox.getCenter();
            BlockPos blockpos1 = new BlockPos(blockpos.getX(), boundingbox.minY(), blockpos.getZ());

            for (StructurePiece structurepiece : list) {
                var bb2 = structurepiece.getBoundingBox();
                if (bb2.intersects(bb)) {
                    BlockPos.betweenClosedStream(bb2.moved(0, bb.getYSpan(), 0).inflatedBy(1))
                            .filter(p -> serverLevel.getBlockState(p).isAir())
                            .filter(p -> !bb2.isInside(p))
                            .forEach(p -> serverLevel.setBlock(p, Blocks.SANDSTONE.defaultBlockState(), 3));

                    structurepiece.postProcess(serverLevel, serverLevel.structureManager(),
                            serverLevel.getChunkSource().getGenerator(), serverLevel.getRandom(), bb, chunkPos, blockpos1);
                }
            }

            structureStart.getStructure().afterPlace(serverLevel, serverLevel.structureManager(),
                    serverLevel.getChunkSource().getGenerator(), serverLevel.getRandom(), bb, chunkPos,
                    ((Randomizer$StructureStart) (Object) structureStart).getContainer());
        }
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
