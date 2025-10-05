package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.api.IngredientRandomizable;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.UnaryOperator;

@Mixin(Ingredient.class)
public abstract class IngredientMixin implements IngredientRandomizable {

    @Mutable
    @Shadow @Final protected HolderSet<Item> values;

    @Override
    public void randomizer$randomizeInputs(UnaryOperator<HolderSet<Item>> randomize) {
        this.values = randomize.apply(this.values);
    }
}
