package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.api.IngredientRandomizable;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Ingredient.class)
public abstract class IngredientMixin implements IngredientRandomizable {

//    @Shadow @Final
//    private Ingredient.Value[] values;
//
//    @Override
//    public void randomizer$randomizeInputs(Function<Ingredient.Value, Ingredient.Value> randomize) {
//        ArrayUtils.setAll(this.values, i -> randomize.apply(this.values[i]));
//    }
}
