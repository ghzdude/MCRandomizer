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
        if (output.isEmpty()) return; // todo log?
        var recipe = new BlockDropRecipe(new ItemStack(in), output, type);
        REGISTRY.add(recipe);
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

        final String s;
        final ItemStack stack;

        Type(String s) {
            this.s = "Requires %s".formatted(s);
            this.stack = new ItemStack(Items.ENCHANTED_BOOK);
            this.stack.set(DataComponents.CUSTOM_NAME, Component.literal(toString()));
        }

        @Override
        public String toString() {
            return s;
        }

        public ItemStack getStack() {
            return stack;
        }
    }
}
