package com.ghzdude.randomizer.mixin.recipe;

import com.ghzdude.randomizer.api.OutputSetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ShapedRecipe.class)
public class ShapedMixin implements OutputSetter {
    @Mutable
    @Final
    @Shadow
    ItemStack result;

    @Override
    public void randomizer$setResult(ItemStack stack) {
        this.result = stack;
    }
}
