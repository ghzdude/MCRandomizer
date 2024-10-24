package com.ghzdude.randomizer.util;

import com.ghzdude.randomizer.ItemRandomizer;
import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.StructureRandomizer;
import com.ghzdude.randomizer.special.generators.*;
import com.ghzdude.randomizer.special.item.SpecialItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Random;

public class RandomizerUtil {

    private static boolean init;

    public static void init(RegistryAccess access) {
        StructureRandomizer.init(access);
        EnchantmentGenerator.init(access);
        PotionGenerator.init(access);
        init = true;
    }

    public static void dispose() {
        init = false;
    }

    public static int giveMultiple(int pointsToUse, Inventory playerInventory) {
        int tries = 0;
        while (tries < 5 && pointsToUse > 0) {
            pointsToUse = giveOnce(pointsToUse, playerInventory);
            tries++;
        }
        return pointsToUse;
    }

    public static int giveOnce(int pointsToUse, Inventory playerInventory) {
        Item selectedItem = ItemRandomizer.getRandomItem(pointsToUse);

        ItemStack stack = specialItemToStack(selectedItem, pointsToUse);

        pointsToUse -= stack.getCount() * ItemRandomizer.getPointValue(selectedItem);
        addStackToPlayer(stack, playerInventory);
        return pointsToUse;
    }

    public static boolean canHaveEffect(ItemStack stack) {
        return canHaveEffect(stack.getItem());
    }

    public static boolean canEnchant(ItemStack stack) {
        return canEnchant(stack.getItem());
    }

    public static boolean canHaveEffect(Item item) {
        return SpecialItems.EFFECT_ITEMS.containsKey(item);
    }

    public static boolean canEnchant(Item item) {
        return SpecialItems.ENCHANTABLE.contains(item);
    }

    public static void addStackToPlayer(ItemStack stack, Inventory inventory) {
        RandomizerCore.LOGGER.warn("Given {} to {}.", stack.copy(), inventory.player.getName().getString());
        if (!inventory.add(stack)) {
            inventory.player.drop(stack, false);
        }
        RandomizerCore.incrementAmtItemsGiven(inventory.player);
    }

    public static <T> T getRandom(List<T> list, Random rng) {
        return list.get(rng.nextInt(list.size()));
    }

    public static ItemStack specialItemToStack(Item item, int points) {
        int amtToGive = Math.floorDiv(points, ItemRandomizer.getPointValue(item));
        return itemToStack(item, amtToGive);
    }

    public static ItemStack itemToStack(Item item) {
        return itemToStack(item, 1);
    }

    public static ItemStack itemToStack(Item item, int size) {
        ItemStack stack = new ItemStack(item);
        stack.setCount(Math.min(size, stack.getMaxStackSize()));

        if (!init) return stack;

        if (canEnchant(stack)) {
            EnchantmentGenerator.applyEnchantment(stack);
        } else if (canHaveEffect(stack)) {
            PotionGenerator.applyEffect(stack);
        } else if (item == Items.WRITTEN_BOOK) {
            BookGenerator.applyPassages(stack);
        } else if (item == Items.FIREWORK_ROCKET) {
            FireworkGenerator.applyFirework(stack);
        } else if (item == Items.FIREWORK_STAR) {
            FireworkGenerator.applyFireworkStar(stack);
        } else if (item == Items.GOAT_HORN) {
            GoatHornGenerator.applyGoatHornSound(stack);
        }
        return stack;
    }
}
