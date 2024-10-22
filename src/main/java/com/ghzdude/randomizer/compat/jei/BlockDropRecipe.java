package com.ghzdude.randomizer.compat.jei;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public record BlockDropRecipe(ItemStack input, ItemStack output, Type type) {
    private static final List<BlockDropRecipe> REGISTRY = new ArrayList<>();

    public static void registerRecipe(Item in, ItemStack output, Type type) {
        if (output.isEmpty()) return;
        REGISTRY.add(new BlockDropRecipe(new ItemStack(in), output, type));
    }

    public static void registerRecipe(Item in, ItemStack output) {
        registerRecipe(in, output, Type.HAND);
    }

    public static List<BlockDropRecipe> getRecipes() {
        return REGISTRY;
    }

    public enum Type {
        HAND("Hand"),
        PICK("Pick"),
        SILK_PICK("Silk Touch"),
        SHEARS("Shears"),
        SHEARS_OR_SILK("Silk or Shears");

        final Component translation;
        final String name;
        final ItemStack stack;

        Type(String name) {
            this.translation = Component.translatable("randomizer.compat.jei.block_drop.type." + name.toLowerCase().replace(' ', '_'));
            this.name = name;
            this.stack = new ItemStack(Items.ENCHANTED_BOOK);
            this.stack.set(DataComponents.CUSTOM_NAME, this.translation);
        }

        @Override
        public String toString() {
            return name;
        }

        public ItemStack getStack() {
            return stack;
        }
    }
}
