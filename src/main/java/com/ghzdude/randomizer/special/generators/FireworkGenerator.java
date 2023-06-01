package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class FireworkGenerator {
    public static void applyFirework(ItemStack stack) {
        RandomSource random = RandomizerCore.RANDOM;

        CompoundTag fireworksTag = new CompoundTag();
        CompoundTag baseTag = new CompoundTag();
        fireworksTag.putInt("Flight", random.nextInt(6) + 1);

        int chance = random.nextInt(100);
        if (chance > 25) { // 25% chance to not add explosions
            ListTag explosionTags = new ListTag();
            int numOfEffects = random.nextInt(4) + 1;
            for (int i = 0; i < numOfEffects; i++) {
                explosionTags.add(applyExplosions());
            }
            // do explosions
            fireworksTag.put("Explosions", explosionTags);
        }
        baseTag.put("Fireworks", fireworksTag);
        stack.setTag(baseTag);
    }
    public static void applyFireworkStar(ItemStack stack) {
        CompoundTag baseTag = new CompoundTag();
        baseTag.put("Explosion", applyExplosions());
        stack.setTag(baseTag);
    }
    private static CompoundTag applyExplosions() {
        RandomSource random = RandomizerCore.RANDOM;
        CompoundTag base = new CompoundTag();

        int colorIndex = random.nextInt(DyeColor.values().length);
        base.putIntArray("Colors", List.of(DyeColor.byId(colorIndex).getFireworkColor()));

        colorIndex = random.nextInt(DyeColor.values().length);
        base.putIntArray("FadeColors", List.of(DyeColor.byId(colorIndex).getFireworkColor()));

        base.putInt("Flicker", random.nextInt(2));
        base.putInt("Trail", random.nextInt(2));
        base.putInt("Type", random.nextInt(5));
        return base;
    }
}
