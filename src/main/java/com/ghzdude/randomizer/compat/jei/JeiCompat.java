package com.ghzdude.randomizer.compat.jei;

import com.ghzdude.randomizer.util.RandomizerUtil;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

@JeiPlugin
public class JeiCompat implements IModPlugin {

    public LootTableCategory lootTableCategory;
    public static final ResourceLocation UID = RandomizerUtil.location("jei_compat");

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        lootTableCategory = new LootTableCategory(registration.getJeiHelpers().getGuiHelper());
        registration.addRecipeCategories(lootTableCategory);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(lootTableCategory.getRecipeType(), ParsedLootTable.getRecipes());
    }
}
