package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FireworkGenerator {

    private static final FireworkExplosion.Shape[] SHAPES = FireworkExplosion.Shape.values();
    private static final DyeColor[] COLORS = DyeColor.values();
    public static void applyFirework(ItemStack stack) {
        Random random = RandomizerCore.unseededRNG;

        int chance = random.nextInt(100) + 1;
        List<FireworkExplosion> explosions = new ArrayList<>();

        // 25% chance to not add explosions
        if (chance > 25) {
            for (int i = 0; i < random.nextInt(4) + 1; i++) {
                explosions.add(createExplosion());
            }
        }

        stack.set(DataComponents.FIREWORKS, new Fireworks(random.nextInt(6) + 1, explosions));
    }

    public static void applyFireworkStar(ItemStack stack) {
        stack.set(DataComponents.FIREWORK_EXPLOSION, createExplosion());
    }

    private static FireworkExplosion createExplosion() {
        Random random = RandomizerCore.unseededRNG;

        var shape = SHAPES[random.nextInt(SHAPES.length)];

        var colors = IntList.of(COLORS[random.nextInt(COLORS.length)].getFireworkColor());
        var fade = IntList.of(COLORS[random.nextInt(COLORS.length)].getFireworkColor());

        return new FireworkExplosion(shape, colors, fade, random.nextBoolean(), random.nextBoolean());
    }
}
