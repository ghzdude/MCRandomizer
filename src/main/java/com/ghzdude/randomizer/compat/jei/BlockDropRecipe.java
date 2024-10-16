package com.ghzdude.randomizer.compat.jei;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record BlockDropRecipe(ItemStack input, ItemStack output) {
    private static final List<BlockDropRecipe> REGISTRY = new ArrayList<>();

    public static void registerRecipe(ItemStack in, ItemStack output) {
        var recipe = new BlockDropRecipe(in, output);
        REGISTRY.add(recipe);
    }

    public static List<BlockDropRecipe> getRecipes() {
        return REGISTRY;
    }
}
