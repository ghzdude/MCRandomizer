package com.ghzdude.randomizer;


import com.ghzdude.randomizer.io.ConfigIO;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/* Mob Spawn Randomizer description
 * when a mob is about to spawn, change the mob
 * should only randomize when naturally spawned, or from spawner.
 */
public class MobRandomizer {
    private static final ArrayList<EntityType<?>> BLACKLISTED_ENTITIES = ConfigIO.readMobBlacklist();
    private final ArrayList<EntityType<?>> entityTypes = new ArrayList<>(ForgeRegistries.ENTITY_TYPES.getValues()
            .stream().filter(entityType ->
                    !BLACKLISTED_ENTITIES.contains(entityType) && entityType.getCategory() != MobCategory.MISC
            ).toList());
    private final int entityCap = 150;
    private int entityCount;
    private boolean isEnabled;

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
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemRandomizer.getRandomItemStack());
        RandomizerCore.LOGGER.warn("Spawned mob " + mob.getType() + " with " + entityCount + " in total.");
        level.addFreshEntity(mob);
    }

    private void randomizeMobSpawn(MobSpawnType reason, Entity toSpawn) {
        if (entityCount <= entityCap) {
            ServerLevel level = (ServerLevel) toSpawn.getLevel();

            if (reason == MobSpawnType.CHUNK_GENERATION ||
                    reason == MobSpawnType.NATURAL ||
                    reason == MobSpawnType.SPAWNER
            ) {
                Entity mob = getRandomMob(level);
                spawnMob(level, mob, toSpawn);
                entityCount++;
            }
        }
    }

    @SubscribeEvent
    public void onServerStart(ServerStartedEvent event) {
        isEnabled = RandomizerConfig.mobRandomizerEnabled();
        if (isEnabled) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                for (Entity mob : level.getAllEntities()) {
                    if (level.isLoaded(mob.blockPosition()) && mob.getType().getCategory() != MobCategory.MISC) {
                        entityCount++;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onMobSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (isEnabled) {
            randomizeMobSpawn(event.getSpawnReason(), event.getEntity());
            if (event.getSpawnReason() != MobSpawnType.SPAWN_EGG) {
                event.setResult(Event.Result.DENY);
            }
        }
    }

    @SubscribeEvent
    public void onSpecialSpawn(LivingSpawnEvent.SpecialSpawn event) {
        if (isEnabled) {
            randomizeMobSpawn(event.getSpawnReason(), event.getEntity());
            if (event.getSpawnReason() != MobSpawnType.SPAWN_EGG) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (isEnabled) {
            EntityType<?> type = event.getEntity().getType();
            if (entityTypes.contains(type)) {
                entityCount--;
            }
            if (entityCount < 0) {
                entityCount = 0;
            }
        }
    }
}
