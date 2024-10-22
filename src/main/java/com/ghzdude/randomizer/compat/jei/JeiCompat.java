package com.ghzdude.randomizer.compat.jei;

import com.ghzdude.randomizer.RandomizerCore;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

@JeiPlugin
public class JeiCompat implements IModPlugin {

    public BlockDropCategory blockDropCategory;
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(RandomizerCore.MODID, "block_drops");

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        blockDropCategory = new BlockDropCategory(registration.getJeiHelpers().getGuiHelper());
        registration.addRecipeCategories(blockDropCategory);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(blockDropCategory.getRecipeType(), BlockDropRecipe.getRecipes());
    }
}
