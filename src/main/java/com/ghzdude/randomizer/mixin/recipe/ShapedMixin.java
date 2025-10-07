package com.ghzdude.randomizer.mixin.recipe;

import com.ghzdude.randomizer.api.OutputSetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.function.UnaryOperator;

@Mixin(ShapedRecipe.class)
public class ShapedMixin implements OutputSetter {
    @Mutable
    @Final
    @Shadow
    ItemStack result;

    @Shadow @Final
    ShapedRecipePattern pattern;

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
        return this.pattern.ingredients().stream()
                .map(optional -> optional.orElse(null))
                .toList();
    }
}
