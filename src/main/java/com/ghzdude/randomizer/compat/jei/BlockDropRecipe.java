package com.ghzdude.randomizer.compat.jei;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record BlockDropRecipe(ItemStack input, ItemStack output, boolean silkTouch) {
    private static final List<BlockDropRecipe> REGISTRY = new ArrayList<>();

    public static void registerRecipe(Item in, ItemStack output, boolean silkTouch) {
        if (output.isEmpty()) return; // todo log?
        var recipe = new BlockDropRecipe(new ItemStack(in), output, silkTouch);
        REGISTRY.add(recipe);
    }

    public static List<BlockDropRecipe> getRecipes() {
        return REGISTRY;
    }
}
