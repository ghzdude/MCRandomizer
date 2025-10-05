package com.ghzdude.randomizer.mixin.recipe;

import com.ghzdude.randomizer.api.OutputSetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.function.UnaryOperator;

@Mixin(ShapelessRecipe.class)
public class ShapelessMixin implements OutputSetter {
    @Mutable
    @Final
    @Shadow
    ItemStack result;

    @Shadow @Final
    List<Ingredient> ingredients;

    @Override
    public void randomizer$randomize(UnaryOperator<ItemStack> stack) {
        this.result = stack.apply(this.result);
    }

    @Override
    public ItemStack randomizer$getResult() {
        return this.result;
    }

    @Override
    public List<Ingredient> randomizer$getIngredients() {
        return this.ingredients;
    }
}
