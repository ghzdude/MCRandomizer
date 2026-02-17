package com.ghzdude.randomizer;


import com.ghzdude.randomizer.io.ConfigIO;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import org.slf4j.Logger;

import java.util.*;
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
    private static final List<AttributeInfo> SPECIAL_ATTRIBUTES = List.of(
            AttributeInfo.of(Attributes.SCALE, 0.1d, 32d),
            AttributeInfo.of(Attributes.MOVEMENT_SPEED, 0.5d, 4d),
            AttributeInfo.of(Attributes.FLYING_SPEED, 0.5d, 4d),
            AttributeInfo.of(Attributes.ARMOR, 0d, 32d, Op.ADD),
            AttributeInfo.of(Attributes.ATTACK_DAMAGE, 0d, 32d, Op.ADD),
            AttributeInfo.of(Attributes.ATTACK_KNOCKBACK, 0d, 32d, Op.ADD),
            AttributeInfo.of(Attributes.ATTACK_SPEED, 0.5d, 4d),
            AttributeInfo.of(Attributes.ARMOR_TOUGHNESS, 0d, 32d, Op.ADD),
            AttributeInfo.of(Attributes.WAYPOINT_TRANSMIT_RANGE, 4d, 4096d, Op.ADD),
            AttributeInfo.of(Attributes.GRAVITY, 0.5d, 2d),
            AttributeInfo.of(Attributes.STEP_HEIGHT, 0.5d, 16d),
            AttributeInfo.of(Attributes.MAX_HEALTH, 0d, 1024d, Op.ADD),
            AttributeInfo.of(Attributes.MAX_ABSORPTION, 0d, 1024d, Op.ADD)
    );
    private static final List<EntityType<?>> VALID_TYPES = new ArrayList<>();
    private static final List<EntitySpawnReason> VALID_REASONS = new ArrayList<>();

    private static final int MAGIC_NUMBER = 289;
    private static Registry<Attribute> ATTRIBUTE_REGISTRY;
    private static Registry<EntityType<?>> TYPE_REGISTRY;
    private static final Logger LOGGER = LogUtils.getLogger();

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
                // todo configurable frequency
                RandomizerCore.seededRNG.nextInt(100) < 30) {

            List<AttributeInstance> applicable = VALID_ATTRIBUTES.stream()
                    .map(ATTRIBUTE_REGISTRY::get)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(entity::getAttribute)
                    .filter(Objects::nonNull)
                    .toList();

            final double offset = 1d;
            int amt = 1 + RandomizerCore.seededRNG.nextInt(3);
            List<String> added = new ArrayList<>(amt);
            for (int i = 0; i < amt; i++) {
                AttributeInstance instance = RandomizerUtil.getRandom(applicable, RandomizerCore.seededRNG);
                Attribute attribute = instance.getAttribute().get();
                ResourceLocation att = ATTRIBUTE_REGISTRY.getKey(attribute);
                Optional<AttributeInfo> info = AttributeInfo.fromLocation(att);

                AttributeModifier modifier;
                if (info.isPresent()) {
                    modifier = info.get().toModifier();
                } else {
                    double sanitizedMin = attribute.sanitizeValue(offset / -2);
                    modifier = createModifier(sanitizedMin, sanitizedMin + offset, att);
                }
                instance.addOrReplacePermanentModifier(modifier);
                added.add("%s(x%f.2)".formatted(modifier.id().getPath(), modifier.amount()));
            }
            if (!added.isEmpty() && RandomizerConfig.enableDebug) {
                entity.setCustomName(Component.literal("%s".formatted(added)));
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

    record AttributeInfo(ResourceLocation loc, double min, double max, Op op) {

        static Map<ResourceLocation, AttributeInfo> MAP = new Object2ObjectOpenHashMap<>();

        public static AttributeInfo of(Holder<Attribute> holder, double min, double max) {
            return of(holder, min, max, Op.ADD_M_BASE);
        }

        public static AttributeInfo of(Holder<Attribute> holder, double min, double max, Op op) {
            return holder.unwrapKey().map(ResourceKey::location).map(location -> {
                AttributeInfo info = new AttributeInfo(location, min, max, op);
                MAP.put(location, info);
                return info;
            }).orElseThrow();
        }

        public static Optional<AttributeInfo> fromLocation(ResourceLocation loc) {
            return Optional.ofNullable(MAP.get(loc));
        }

        public AttributeModifier toModifier() {
            double d = RandomizerCore.unseededRNG.nextDouble(min(), max());
            return new AttributeModifier(loc(), d, op().getOperation());
        }
    }

    enum Op {
        ADD(AttributeModifier.Operation.ADD_VALUE),
        ADD_M_BASE(AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
        ADD_M_TOTAL(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        private final AttributeModifier.Operation operation;

        Op(AttributeModifier.Operation operation) {
            this.operation = operation;
        }

        public AttributeModifier.Operation getOperation() {
            return operation;
        }
    }
}
