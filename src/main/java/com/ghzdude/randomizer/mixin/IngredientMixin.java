package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.api.IngredientRandomizable;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Consumer;
import java.util.function.Function;

@Mixin(Ingredient.class)
public abstract class IngredientMixin implements IngredientRandomizable {
    @Shadow @Final
    private Ingredient.Value[] values;

    @Override
    public void randomizer$randomizeInputs(Function<Ingredient.Value, Ingredient.Value> randomize) {
        for (int i = 0; i < this.values.length; i++) {
            this.values[i] = randomize.apply(this.values[i]);
        }
    }
}
