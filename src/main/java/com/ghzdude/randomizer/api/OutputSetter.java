package com.ghzdude.randomizer.api;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.function.UnaryOperator;

public interface OutputSetter {

    void randomizer$randomize(UnaryOperator<ItemStack> stack);

    ItemStack randomizer$getResult();

    List<Ingredient> randomizer$getIngredients();
}
