package com.ghzdude.randomizer.mixin.recipe;

import com.ghzdude.randomizer.RandomizerConfig;
import com.ghzdude.randomizer.RecipeRandomizer;
import com.ghzdude.randomizer.api.Randomizable;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin extends SimplePreparableReloadListener<RecipeMap> implements Randomizable {

    @Shadow private RecipeMap recipes;

    @Override
    public void randomizer$randomize() {
        if (RandomizerConfig.randomizeRecipes) {
            this.recipes = RecipeRandomizer.randomizeRecipeMap(this.recipes);
        }
    }
}
