package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class EnchantmentGenerator {

    private static final ArrayList<Enchantment> VALID_ENCHANTS = new ArrayList<>(ForgeRegistries.ENCHANTMENTS.getValues());

    public static void applyEnchantment(ItemStack stack) {
        final Random rng = RandomizerCore.unseededRNG;
        int shouldEnchant = rng.nextInt(0, 100);
        if (shouldEnchant < 80 && !stack.is(Items.ENCHANTED_BOOK)) return;

        List<Enchantment> applicable = VALID_ENCHANTS.stream().filter(stack::canApplyAtEnchantingTable).collect(Collectors.toList());

        int numOfEnchants = rng.nextInt(applicable.size()) + 1;
        for (int i = 0; i < numOfEnchants; i++) {
            int id = rng.nextInt(applicable.size());
            Enchantment toApply = applicable.get(id);
            applicable.remove(id);

            // todo handle max enchant level not translating properly
            stack.enchant(toApply, rng.nextInt(15));
        }
    }
}
