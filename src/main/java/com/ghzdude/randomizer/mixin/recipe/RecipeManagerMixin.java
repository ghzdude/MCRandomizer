package com.ghzdude.randomizer.mixin.recipe;

import com.ghzdude.randomizer.RecipeRandomizer;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin extends SimplePreparableReloadListener<RecipeMap> {

    @ModifyReturnValue(method =
            "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/world/item/crafting/RecipeMap;",
            at = @At("RETURN"))
    public RecipeMap randomizMap(RecipeMap original) {
        return RecipeRandomizer.randomizeRecipeMap(original);
    }
}
