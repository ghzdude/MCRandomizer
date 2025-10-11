package com.ghzdude.randomizer.util;

import com.ghzdude.randomizer.*;
import com.ghzdude.randomizer.special.generators.*;
import com.ghzdude.randomizer.special.item.SpecialItems;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class RandomizerUtil {

    private static boolean init;
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void init(RegistryAccess access) {
        StructureRandomizer.init(access);
        EnchantmentGenerator.init(access);
        PotionGenerator.init(access);
        MobRandomizer.init(access);
        init = true;
    }

    public static void dispose() {
        RecipeRandomizer.dispose();
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
        return SpecialItems.EFFECT_ITEMS.contains(item);
    }

    public static boolean canEnchant(Item item) {
        return SpecialItems.ENCHANTABLE.contains(item);
    }

    public static void addStackToPlayer(ItemStack stack, Inventory inventory) {
        LOGGER.warn("Given {} to {}.", stack.copy(), inventory.player.getName().getString());
        if (!inventory.add(stack)) {
            inventory.player.drop(stack, false);
        }
        ItemRandomizer.incrementAmtItemsGiven(inventory.player);
    }

    public static <T> T getRandom(List<T> list, Random rng) {
        return list.get(rng.nextInt(list.size()));
    }

    public static <T> T getRandom(List<T> list) {
        return getRandom(list, RandomizerCore.unseededRNG);
    }

    public static <T> @NotNull T getOrThrow(Registry<T> registry, ResourceLocation location) {
        return registry.get(location).orElseThrow().get();
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

    public static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath(RandomizerCore.MODID, path);
    }

    public static @NotNull List<Component> getOrCreateLines(ItemStack inputStack) {
        return Optional.ofNullable(inputStack.get(DataComponents.LORE))
                .map(itemLore -> new ArrayList<>(itemLore.lines())).orElse(new ArrayList<>());
    }
}
