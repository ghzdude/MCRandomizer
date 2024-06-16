package com.ghzdude.randomizer.api;

import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Consumer;
import java.util.function.Function;

public interface IngredientRandomizable {

    void randomizer$randomizeInputs(Function<Ingredient.Value, Ingredient.Value> randomize);
}
