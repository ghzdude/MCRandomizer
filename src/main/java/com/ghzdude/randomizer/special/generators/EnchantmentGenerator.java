package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;

public class EnchantmentGenerator {

    private static final ArrayList<Enchantment> VALID_ENCHANTS = new ArrayList<>(ForgeRegistries.ENCHANTMENTS.getValues());

    public static void applyEnchantment(ItemStack stack) {
        final RandomSource random = RandomizerCore.RANDOM;
        int shouldEnchant = random.nextIntBetweenInclusive(0, 100);
        if (shouldEnchant < 80 && !stack.is(Items.ENCHANTED_BOOK)) return;

        int numOfEnchants = random.nextInt(3) + 1;
        for (int i = 0; i < numOfEnchants; i++) {
            int id = random.nextInt(VALID_ENCHANTS.size());
            Enchantment toApply = VALID_ENCHANTS.get(id);
            stack.enchant(toApply, random.nextInt(toApply.getMaxLevel()) + 1);
        }
    }
}
