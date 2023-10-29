package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Random;

public class EnchantmentGenerator {

    private static final ArrayList<Enchantment> VALID_ENCHANTS = new ArrayList<>(ForgeRegistries.ENCHANTMENTS.getValues());

    public static void applyEnchantment(ItemStack stack) {
        final Random rng = RandomizerCore.rng;
        int shouldEnchant = rng.nextInt(0, 100);
        if (shouldEnchant < 80 && !stack.is(Items.ENCHANTED_BOOK)) return;

        int numOfEnchants = rng.nextInt(3) + 1;
        for (int i = 0; i < numOfEnchants; i++) {
            int id = rng.nextInt(VALID_ENCHANTS.size());
            Enchantment toApply = VALID_ENCHANTS.get(id);
            stack.enchant(toApply, rng.nextInt(toApply.getMaxLevel()) + 1);
        }
    }
}
