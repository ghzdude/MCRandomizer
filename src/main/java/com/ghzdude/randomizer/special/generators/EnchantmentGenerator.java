package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class EnchantmentGenerator {

    private static final ArrayList<Holder<Enchantment>> VALID_ENCHANTS = new ArrayList<>();

    public static void init(Collection<Holder<Enchantment>> enchantments) {
        VALID_ENCHANTS.addAll(enchantments);
    }

    public static void applyEnchantment(ItemStack stack) {
        final Random rng = RandomizerCore.unseededRNG;
        int shouldEnchant = rng.nextInt(0, 100);
        if (shouldEnchant < 80 && !stack.is(Items.ENCHANTED_BOOK)) return;

        List<Holder<Enchantment>> applicable = VALID_ENCHANTS.stream()
                .filter(enchant -> stack.canApplyAtEnchantingTable(enchant.get()))
                .collect(Collectors.toList());

        if (applicable.isEmpty()) return;
        int numOfEnchants = rng.nextInt(applicable.size()) + 1;
        for (int i = 0; i < numOfEnchants; i++) {
            int id = rng.nextInt(applicable.size());
            Holder<Enchantment> toApply = applicable.get(id);
            applicable.remove(id);

            // todo handle max enchant level not translating properly
            stack.enchant(toApply, rng.nextInt(15));
        }
    }
}
