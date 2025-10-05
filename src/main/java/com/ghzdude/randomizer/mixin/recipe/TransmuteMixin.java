package com.ghzdude.randomizer.mixin.recipe;

import com.ghzdude.randomizer.api.OutputSetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.crafting.TransmuteResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.function.UnaryOperator;

@Mixin(TransmuteRecipe.class)
public class TransmuteMixin implements OutputSetter {
    @Mutable
    @Shadow @Final
    TransmuteResult result;

    @Shadow @Final
    Ingredient input;

    @Shadow @Final
    Ingredient material;

    @Override
    public void randomizer$randomize(UnaryOperator<ItemStack> stack) {
        TransmuteResult old = this.result;
        ItemStack stack1 = stack.apply(new ItemStack(old.item()));
        //noinspection deprecation
        this.result = new TransmuteResult(stack1.getItem().builtInRegistryHolder(), old.count(), old.components());
    }

    @Override
    public ItemStack randomizer$getResult() {
        return new ItemStack(this.result.item());
    }

    @Override
    public List<Ingredient> randomizer$getIngredients() {
        return List.of(this.input, this.material);
    }
}
