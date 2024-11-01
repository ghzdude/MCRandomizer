package com.ghzdude.randomizer;


import com.ghzdude.randomizer.io.ConfigIO;
import com.ghzdude.randomizer.util.RandomizerUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/* Mob Spawn Randomizer description
 * when a mob is about to spawn, change the mob
 * should only randomize when naturally spawned, or from spawner.
 */
public class MobRandomizer {
    private static final List<ResourceLocation> BLACKLISTED_ENTITIES = ConfigIO.readMobBlacklist();
    private static final List<MobCategory> BLACKLISTED_CATEGORIES = List.of(MobCategory.MISC);
    private static List<ResourceLocation> BLACKLISTED_ATTRIBUTES;
    private static final List<ResourceLocation> VALID_ATTRIBUTES = new ArrayList<>();
    private static final List<EntityType<?>> VALID_TYPES = new ArrayList<>();

    private static final int MAGIC_NUMBER = 289;
    private static Registry<Attribute> ATTRIBUTE_REGISTRY;
    private static Registry<EntityType<?>> TYPE_REGISTRY;

    public static void init(RegistryAccess access) {
        ATTRIBUTE_REGISTRY = access.registryOrThrow(Registries.ATTRIBUTE);
        TYPE_REGISTRY = access.registryOrThrow(Registries.ENTITY_TYPE);

        // todo add configuration
        for (var type : TYPE_REGISTRY.keySet()) {
            if (BLACKLISTED_ENTITIES.contains(type)) continue;
            var value = TYPE_REGISTRY.get(type);
            if (value == null || BLACKLISTED_CATEGORIES.contains(value.getCategory())) continue;
            VALID_TYPES.add(value);
        }

        // todo add configuration
        BLACKLISTED_ATTRIBUTES = List.of(
                getLocationOrThrow(Attributes.SCALE),
                getLocationOrThrow(Attributes.GRAVITY),
                getLocationOrThrow(Attributes.BURNING_TIME)
        );

        for (var att : ATTRIBUTE_REGISTRY.keySet()) {
            if (BLACKLISTED_ATTRIBUTES.contains(att)) continue;
            VALID_ATTRIBUTES.add(att);
        }
    }

    private static @NotNull ResourceLocation getLocationOrThrow(Holder<Attribute> attributeHolder) {
        var k = ATTRIBUTE_REGISTRY.getKey(attributeHolder.value());
        if (k == null) throw new NullPointerException();
        return k;
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || VALID_TYPES.isEmpty()) {
            return;
        }

        Entity mob = event.getEntity();
        if (!VALID_TYPES.contains(mob.getType())) return;

        if (RandomizerConfig.randomizeMobs) {
            var randomized = mob.getPersistentData().contains("randomized");
            if (!randomized && !event.loadedFromDisk()) {
                randomizeMobSpawn(mob);
                event.setCanceled(true);
            }
        }

        // randomize attributes
        // todo should this be a permanent modifier?
        if (RandomizerConfig.randomizeMobAttributes && mob instanceof LivingEntity livingEntity) {
            final double offset = 40d;

            for (var att : VALID_ATTRIBUTES) {
                if (mob.getRandom().nextBoolean()) continue;
                var h = ATTRIBUTE_REGISTRY.getHolder(att);
                if (h.isEmpty()) continue;
                var inst = livingEntity.getAttribute(h.get());
                if (inst == null) continue;
                double sanitizedMin = inst.getAttribute().get().sanitizeValue(offset / -2);
                inst.addOrUpdateTransientModifier(createModifier(sanitizedMin, sanitizedMin + offset));
            }
        }
    }

    private AttributeModifier createModifier(double min, double max) {
        var loc = ResourceLocation.fromNamespaceAndPath(RandomizerCore.MODID, "attribute");
        return new AttributeModifier(loc, RandomizerCore.unseededRNG.nextDouble(min, max), AttributeModifier.Operation.ADD_VALUE);
    }

    @NotNull
    private Entity getRandomMob(Level level) {
        Entity mob;
        do {
            EntityType<?> entityType = RandomizerUtil.getRandom(VALID_TYPES, RandomizerCore.unseededRNG);
            mob = entityType.create(level);
        } while (mob == null);
        return mob;
    }

    private void spawnMob(ServerLevel level, Entity mob, Entity reference) {
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

    private void randomizeMobSpawn(Entity toSpawn) {
        ServerLevel level = (ServerLevel) toSpawn.level();

        Entity mob = getRandomMob(level);
        spawnMob(level, mob, toSpawn);
    }
}
