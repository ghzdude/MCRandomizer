package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.api.IngredientRandomizable;

//@Mixin(Ingredient.class)
public abstract class IngredientMixin implements IngredientRandomizable {

//    @Shadow @Final
//    private Ingredient.Value[] values;
//
//    @Override
//    public void randomizer$randomizeInputs(Function<Ingredient.Value, Ingredient.Value> randomize) {
//        ArrayUtils.setAll(this.values, i -> randomize.apply(this.values[i]));
//    }
}
