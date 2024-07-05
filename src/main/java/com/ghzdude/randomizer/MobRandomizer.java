package com.ghzdude.randomizer;


import com.ghzdude.randomizer.io.ConfigIO;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/* Mob Spawn Randomizer description
 * when a mob is about to spawn, change the mob
 * should only randomize when naturally spawned, or from spawner.
 */
public class MobRandomizer {
    private static final ArrayList<EntityType<?>> BLACKLISTED_ENTITIES = ConfigIO.readMobBlacklist();
    private final ArrayList<EntityType<?>> entityTypes = new ArrayList<>(ForgeRegistries.ENTITY_TYPES.getValues()
            .stream()
            .filter(entityType ->
                    !BLACKLISTED_ENTITIES.contains(entityType) && entityType.getCategory() != MobCategory.MISC
            ).toList());
    private boolean isEnabled;
    static final int MAGIC_NUMBER = (int)Math.pow(17.0D, 2.0D);

    @NotNull
    private Entity getRandomMob(Level level) {
        Entity mob;
        do {
            int id = RandomizerCore.seededRNG.nextInt(entityTypes.size());
            EntityType<?> entityType = entityTypes.get(id);
            mob = entityType.create(level);
        } while (mob == null);
        return mob;
    }

    private boolean spawnMob(ServerLevel level, Entity mob, Entity reference) {
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
                return true;
            }
        }
        return false;
    }

    private boolean randomizeMobSpawn(Entity toSpawn) {
        ServerLevel level = (ServerLevel) toSpawn.level();

        Entity mob = getRandomMob(level);
        return spawnMob(level, mob, toSpawn);
    }

    @SubscribeEvent
    public void onServerStart(ServerStartedEvent event) {
        isEnabled = RandomizerConfig.randomizeMobs;
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (!isEnabled || event.getLevel().isClientSide) return;

        var mob = event.getEntity();
        if (mob.getType().getCategory() == MobCategory.MISC) return;
        var randomized = mob.getPersistentData().contains("randomized");
        if (!randomized && !event.loadedFromDisk()) {
            if (randomizeMobSpawn(mob))
                event.setCanceled(true);
        }
    }
}
