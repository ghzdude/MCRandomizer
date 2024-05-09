package com.ghzdude.randomizer.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerLevel.class)
public class ServerLevelMixin implements Randomizer$ServerLevel {

    @Shadow @Final
    private PersistentEntitySectionManager<Entity> entityManager;

    @Override
    public EntityLookup<Entity> randomizer$getLookUp() {
        return this.entityManager instanceof Randomizer$SectionManager sectionManager ? sectionManager.randomizer$getLookUp() : null;
    }
}
