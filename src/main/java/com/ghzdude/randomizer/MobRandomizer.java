package com.ghzdude.randomizer;


import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/* Mob Spawn Randomizer description
 * when a mob is about to spawn, change the mob
 * should only randomize when naturally spawned, from spawner, or from breeding.
 */
public class MobRandomizer {
    private static final ArrayList<EntityType<?>> BLACKLISTED_ENTITIES = new ArrayList<>(List.of(
            EntityType.ENDER_DRAGON,
            EntityType.GIANT
    ));
    private final ArrayList<EntityType<?>> entityTypes = new ArrayList<>(ForgeRegistries.ENTITY_TYPES.getValues()
            .stream().filter(entityType ->
                    !BLACKLISTED_ENTITIES.contains(entityType) && entityType.getCategory() != MobCategory.MISC
            ).toList());
    private final int MAX_ENTITIES = 100;

    private Entity getRandomMob(Level level) {
        Entity mob;
        do {
            int id = RandomizerCore.RANDOM.nextInt(entityTypes.size());
            EntityType<?> entityType = entityTypes.get(id);
            mob = entityType.create(level);
        } while (mob == null);
        return mob;
    }

    private void spawnMob(Level level, Entity mob, Entity reference) {
        mob.setPos(reference.position());
        mob.setXRot(reference.getXRot());
        mob.setYRot(reference.getYRot());
        level.addFreshEntity(mob);
    }

    private int entityCount (ServerLevel level) {
        int count = 0;
        for (Entity mob : level.getAllEntities()) {
             if (level.isPositionEntityTicking(mob.blockPosition())) count++;
        }
        return count;
    }
    @SubscribeEvent
    public void onMobSpawn(LivingSpawnEvent.CheckSpawn event) {
        MobSpawnType type = event.getSpawnReason();
        ServerLevel level = (ServerLevel) event.getEntity().getLevel();

        if (type == MobSpawnType.CHUNK_GENERATION ||
                type == MobSpawnType.NATURAL ||
                type == MobSpawnType.SPAWNER
        ) {
            if (entityCount(level) <= MAX_ENTITIES) {
                Entity mob = getRandomMob(event.getEntity().getLevel());
                spawnMob(level, mob, event.getEntity());
            }
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void onSpecialSpawn(LivingSpawnEvent.SpecialSpawn event) {
        MobSpawnType type = event.getSpawnReason();
        ServerLevel level = (ServerLevel) event.getEntity().getLevel();

        if (type == MobSpawnType.CHUNK_GENERATION ||
                type == MobSpawnType.NATURAL ||
                type == MobSpawnType.SPAWNER
        ) {
            if (entityCount(level) < MAX_ENTITIES) {
                Entity mob = getRandomMob(event.getEntity().getLevel());
                spawnMob(level, mob, event.getEntity());
            }
            event.setCanceled(true);
        }
    }
}
