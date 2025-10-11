package com.ghzdude.randomizer;


import com.ghzdude.randomizer.io.ConfigIO;
import com.ghzdude.randomizer.util.RandomizerUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobSpawnEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/* Mob Spawn Randomizer description
 * when a mob is about to spawn, change the mob
 * should only randomize when naturally spawned, or from spawner.
 */
public class MobRandomizer {
    private static final List<ResourceLocation> BLACKLISTED_ENTITIES = new ArrayList<>();
    private static final List<MobCategory> BLACKLISTED_CATEGORIES = List.of(MobCategory.MISC);
    private static final List<ResourceLocation> BLACKLISTED_ATTRIBUTES = new ArrayList<>();
    private static final List<ResourceLocation> VALID_ATTRIBUTES = new ArrayList<>();
    private static final List<EntityType<?>> VALID_TYPES = new ArrayList<>();
    private static final List<EntitySpawnReason> VALID_REASONS = new ArrayList<>();

    private static final int MAGIC_NUMBER = 289;
    private static Registry<Attribute> ATTRIBUTE_REGISTRY;
    private static Registry<EntityType<?>> TYPE_REGISTRY;

    // todo utilize SpawnPlacementCheck, PositionCheck, and FinalizeSpawn somehow
    static void init(RegistryAccess access) {
        ATTRIBUTE_REGISTRY = access.lookupOrThrow(Registries.ATTRIBUTE);
        TYPE_REGISTRY = access.lookupOrThrow(Registries.ENTITY_TYPE);

        if (BLACKLISTED_ENTITIES.isEmpty()) {
            BLACKLISTED_ENTITIES.addAll(ConfigIO.read("blacklisted_mobs", Stream.of(
                            EntityType.ENDER_DRAGON,
                            EntityType.WITHER,
                            EntityType.WARDEN,
                            EntityType.GIANT)
                    .map(TYPE_REGISTRY::getKey)
                    .filter(Objects::nonNull)
                    .toList(), TYPE_REGISTRY));
        }

        if (BLACKLISTED_ATTRIBUTES.isEmpty()) {
            BLACKLISTED_ATTRIBUTES.addAll(ConfigIO.read("blacklisted_attributes", Stream.of(
                    Attributes.SCALE,
                    Attributes.GRAVITY,
                    Attributes.LUCK,
                    Attributes.SWEEPING_DAMAGE_RATIO,
                    Attributes.WAYPOINT_RECEIVE_RANGE,
                    Attributes.TEMPT_RANGE,
                    Attributes.BLOCK_INTERACTION_RANGE,
                    Attributes.BLOCK_BREAK_SPEED,
                    Attributes.CAMERA_DISTANCE,
                    Attributes.BURNING_TIME)
                    .map(Holder::get)
                    .map(ATTRIBUTE_REGISTRY::getKey)
                    .filter(Objects::nonNull)
                    .toList(), ATTRIBUTE_REGISTRY));
        }

        // todo add configuration
        for (var type : TYPE_REGISTRY.keySet()) {
            if (BLACKLISTED_ENTITIES.contains(type)) continue;
            TYPE_REGISTRY.get(type).map(Holder::get)
                    .filter(e -> !BLACKLISTED_CATEGORIES.contains(e.getCategory()))
                    .ifPresent(VALID_TYPES::add);
        }

        for (var att : ATTRIBUTE_REGISTRY.keySet()) {
            if (BLACKLISTED_ATTRIBUTES.contains(att)) continue;
            VALID_ATTRIBUTES.add(att);
        }



        // todo make configurable
        VALID_REASONS.addAll(List.of(
                EntitySpawnReason.SPAWNER,
                EntitySpawnReason.BREEDING,
                EntitySpawnReason.CHUNK_GENERATION,
                EntitySpawnReason.TRIAL_SPAWNER,
                EntitySpawnReason.PATROL,
                EntitySpawnReason.NATURAL,
                EntitySpawnReason.JOCKEY,
                EntitySpawnReason.STRUCTURE,
                EntitySpawnReason.BUCKET,
                EntitySpawnReason.CONVERSION
        ));
    }

    static boolean randomizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        boolean cancel = false;

        Mob entity = event.getEntity();
        CompoundTag data = entity.getPersistentData();

        if (RandomizerConfig.randomizeMobs) {
            EntitySpawnReason spawnReason = event.getSpawnReason();
            if (VALID_REASONS.contains(spawnReason) && !data.contains("randomized")) {
                entity = randomizeMobSpawn(entity, spawnReason);
                cancel = true;
            }
        }

        // randomize attributes
        if (RandomizerConfig.randomizeMobAttributes &&
                !data.contains("added_attribute") &&
                RandomizerCore.seededRNG.nextInt(100) < 30) {

            final double offset = 1d;
            int amt = RandomizerCore.seededRNG.nextInt(3);
            for (int i = 0; i < amt; i++) {
                var att = RandomizerUtil.getRandom(VALID_ATTRIBUTES, RandomizerCore.seededRNG);
                ATTRIBUTE_REGISTRY.get(att).map(entity::getAttribute)
                        .ifPresent(inst -> {
                            double sanitizedMin = inst.getAttribute().get().sanitizeValue(offset / -2);
                            inst.addOrReplacePermanentModifier(createModifier(sanitizedMin, sanitizedMin + offset, att));
                        });

            }
            data.putBoolean("added_attribute", true);
        }

        return cancel;
    }

    private static AttributeModifier createModifier(double min, double max, ResourceLocation location) {
        return new AttributeModifier(location,
                RandomizerCore.unseededRNG.nextDouble(min, max),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static Mob getRandomMob(Level level, EntitySpawnReason reason) {
        EntityType<?> entityType = RandomizerUtil.getRandom(VALID_TYPES, RandomizerCore.unseededRNG);
        Entity mob = entityType.create(level, reason);
        if (!(mob instanceof Mob)) {
            throw new IllegalStateException("mob failed to create for some reason!");
        }
        return (Mob) mob;
    }

    private static void spawnMob(ServerLevel level, Entity mob, Entity reference) {
        mob.setPos(reference.position());
        mob.setXRot(reference.getXRot());
        mob.setYRot(reference.getYRot());
        mob.getSlot(EquipmentSlot.MAINHAND.getIndex()).set(ItemRandomizer.getRandomItemStack(RandomizerCore.unseededRNG));

        mob.getPersistentData().putBoolean("randomized", true);
        var state = level.getChunkSource().getLastSpawnState();
        if (state != null) {
            var category = mob.getType().getCategory();
            int count = state.getMobCategoryCounts().getOrDefault(category, 0);
            if (count <= category.getMaxInstancesPerChunk() * state.getSpawnableChunkCount() / MAGIC_NUMBER) {
                level.addFreshEntity(mob);
            }
        }
    }

    private static Mob randomizeMobSpawn(Entity toSpawn, EntitySpawnReason reason) {
        ServerLevel level = (ServerLevel) toSpawn.level();

        Mob mob = getRandomMob(level, reason);
        spawnMob(level, mob, toSpawn);
        return mob;
    }
}
