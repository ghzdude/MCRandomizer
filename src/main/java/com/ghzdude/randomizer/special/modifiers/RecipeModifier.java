package com.ghzdude.randomizer.special.modifiers;

import com.ghzdude.randomizer.RecipeRandomizer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.crafting.RecipeManager;

public class RecipeModifier implements ResourceManagerReloadListener {

    private final RecipeManager manager;
    public RecipeModifier(RecipeManager manager) {
        this.manager = manager;
    }

    @Override
    public void onResourceManagerReload(ResourceManager p_10758_) {
        RecipeRandomizer.randomizeRecipes(manager);
    }
}
