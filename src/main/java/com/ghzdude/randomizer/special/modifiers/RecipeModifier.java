package com.ghzdude.randomizer.special.modifiers;

import com.ghzdude.randomizer.RecipeRandomizer;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.crafting.RecipeManager;

public class RecipeModifier implements ResourceManagerReloadListener {

    private final HolderLookup.Provider access;
    private final RecipeManager manager;
    public RecipeModifier(HolderLookup.Provider access, RecipeManager manager) {
        this.access = access;
        this.manager = manager;
    }

    @Override
    public void onResourceManagerReload(ResourceManager p_10758_) {
        RecipeRandomizer.randomizeRecipes(manager, access);
    }
}
