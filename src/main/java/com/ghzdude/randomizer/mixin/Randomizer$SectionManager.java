package com.ghzdude.randomizer.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityLookup;

public interface Randomizer$SectionManager {
    EntityLookup<Entity> randomizer$getLookUp();
}
