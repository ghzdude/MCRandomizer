package com.ghzdude.randomizer;


import com.ghzdude.randomizer.io.ConfigIO;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;

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

    //todo set entity cap to be a config
    private final int entityCap = 150;
    private int entityCount;
    private boolean isEnabled;

    private Entity getRandomMob(Level level) {
        Entity mob;
        do {
            int id = RandomizerCore.seededRNG.nextInt(entityTypes.size());
            EntityType<?> entityType = entityTypes.get(id);
            mob = entityType.create(level);
        } while (mob == null);
        return mob;
    }

    private void spawnMob(Level level, Entity mob, Entity reference) {
        mob.setPos(reference.position());
        mob.setXRot(reference.getXRot());
        mob.setYRot(reference.getYRot());
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemRandomizer.getRandomItemStack(RandomizerCore.unseededRNG));
        RandomizerCore.LOGGER.warn("Spawned mob " + mob.getType() + " with " + entityCount + " in total.");
        level.addFreshEntity(mob);
    }

    private void randomizeMobSpawn(MobSpawnType reason, Entity toSpawn) {
        if (entityCount <= entityCap) {
            ServerLevel level = (ServerLevel) toSpawn.level();

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

    private int countEntities(Iterable<ServerLevel> iterable) {
        int count = 0;
        for (ServerLevel level : iterable) {
            for (Entity mob : level.getAllEntities()) {
                if (level.isLoaded(mob.blockPosition()) && mob.getType().getCategory() != MobCategory.MISC) {
                    count++;
                }
            }
        }
        return count;
    }

    @SubscribeEvent
    public void onServerStart(ServerStartedEvent event) {
        isEnabled = RandomizerConfig.mobRandomizerEnabled();
    }

    private int TIMER = 0;
    @SubscribeEvent
    public void onServerStart(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END || event.side == LogicalSide.CLIENT) return;
        if (TIMER++ % 200 != 0) return;

        if (isEnabled) {
            entityCount = countEntities(event.getServer().getAllLevels());
        }

        if (TIMER < 0) TIMER = 0;
    }

    @SubscribeEvent
    public void onMobSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (isEnabled) {
            randomizeMobSpawn(event.getSpawnType(), event.getEntity());
            if (event.getSpawnType() != MobSpawnType.SPAWN_EGG) {
                event.setSpawnCancelled(true);
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

    @SubscribeEvent
    public void onUnload(EntityLeaveLevelEvent event) {
        entityCount--;
    }
}
