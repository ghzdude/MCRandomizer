package com.ghzdude.randomizer;

import com.ghzdude.randomizer.io.ConfigIO;
import com.ghzdude.randomizer.special.structure.SpecialStructure;
import com.ghzdude.randomizer.special.structure.SpecialStructureList;
import com.ghzdude.randomizer.special.structure.SpecialStructures;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/* Structure Randomizer description
 * every so often, generate a structure at some random x, z coordinate near the player
 */
public class StructureRandomizer {
    private static final ArrayList<ResourceLocation> BLACKLISTED_STRUCTURES = ConfigIO.readStructureBlacklist();
    private static final SpecialStructureList VALID_STRUCTURES = new SpecialStructureList();

    public static int placeStructure(int pointsToUse, ServerLevel level, ServerPlayer player) {
        if (pointsToUse < 1) return pointsToUse;

        SpecialStructure structure = selectStructure(pointsToUse);

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

        RandomizerCore.LOGGER.warn(String.format("Attempting to generate [%s] at %s", structure.key.location(), target));
//        player.sendSystemMessage(Component.translatable("structure.spawning", structure.key.location()));
        sendMessage(Component.translatable("structure.spawning", structure.key.location()), player);

        boolean success = tryPlaceStructure(level, structure.key.location(), target);
        if (!success) {
//            player.sendSystemMessage(Component.translatable("structure.spawning.failed", structure.key.location()));
            sendMessage(Component.translatable("structure.spawning.failed", structure.key.location()), player);
            if (RandomizerConfig.giveRandomItems.get()) {
                pointsToUse -= ItemRandomizer.giveRandomItem(pointsToUse, player.getInventory());
                RandomizerCore.incrementAmtItemsGiven();
            }
            return pointsToUse;
        }
//        player.sendSystemMessage(Component.translatable("structure.spawning.success", structure.key.location(), target));
        sendMessage(Component.translatable("structure.spawning.success", structure.key.location(), target), player);
        return pointsToUse - structure.value;
    }

    private static void sendMessage(Component component, ServerPlayer player) {
        player.sendChatMessage(new OutgoingChatMessage.Disguised(component),
                false,
                ChatType.bind(ChatType.CHAT, player));
    }

    private static SpecialStructure selectStructure(int points) {
        SpecialStructure structure;
        do {
            int id = RandomizerCore.seededRNG.nextInt(VALID_STRUCTURES.size());
            structure = VALID_STRUCTURES.get(id);
        } while (structure.value > points);
        return structure;
    }

    private static boolean tryPlaceStructure(ServerLevel serverLevel, ResourceLocation location, BlockPos blockPos) {
        ChunkGenerator chunkgenerator = serverLevel.getChunkSource().getGenerator();

        Registry<Structure> registry = getStructures(serverLevel.registryAccess());
        Structure structure = registry.get(location);
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
        VALID_STRUCTURES.addAll(SpecialStructures.CONFIGURED_STRUCTURES);

        Registry<Structure> structures = getStructures(access);

        for (ResourceKey<Structure> key : structures.registryKeySet()) {
            SpecialStructure toAdd;
            if (BLACKLISTED_STRUCTURES.contains(key.location())) continue;

            toAdd = new SpecialStructure(key, 1);
            VALID_STRUCTURES.add(toAdd);
        }
    }

    @NotNull
    public static Registry<Structure> getStructures(RegistryAccess access) {
        Optional<Registry<Structure>> optional = access.registry(Registries.STRUCTURE);
        if (optional.isEmpty()) {
            RandomizerCore.LOGGER.warn("Structure Registry cannot be found!", new IllegalStateException());
            return null;
        }
        return optional.get();
    }
}
