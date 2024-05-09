package com.ghzdude.randomizer.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PersistentEntitySectionManager.class)
public class EntitySectionManagerMixin implements Randomizer$SectionManager{
    @Shadow @Final private EntityLookup<Entity> visibleEntityStorage;

    @Override
    public EntityLookup<Entity> randomizer$getLookUp() {
        return this.visibleEntityStorage;
    }
}
