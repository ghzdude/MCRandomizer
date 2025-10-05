package com.ghzdude.randomizer.api;

import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Item;

import java.util.function.UnaryOperator;

public interface IngredientRandomizable {

    void randomizer$randomizeInputs(UnaryOperator<HolderSet<Item>> randomize);
}
