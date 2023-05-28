package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;

public class EnchantmentGenerator {

    private static final ArrayList<Enchantment> VALID_ENCHANTS = new ArrayList<>(ForgeRegistries.ENCHANTMENTS
            .getValues()
            .stream()
            .toList()
    );

    public static void applyEnchantment(ItemStack stack) {
        final RandomSource random = RandomizerCore.RANDOM;
        int shouldEnchant = random.nextIntBetweenInclusive(0, 100);
        if (shouldEnchant < 80) return;
        int id = random.nextInt(VALID_ENCHANTS.size());

        int numOfEnchants = random.nextInt(3) + 1;
        for (int i = 0; i < numOfEnchants; i++) {
            Enchantment toApply;
            do {
                toApply = VALID_ENCHANTS.get(id);
            } while (!toApply.canEnchant(stack));

            stack.enchant(toApply, random.nextInt(50) + 1);
        }
        // CompoundTag baseTag = new CompoundTag();
        // stack.setTag(baseTag);
    }
}
