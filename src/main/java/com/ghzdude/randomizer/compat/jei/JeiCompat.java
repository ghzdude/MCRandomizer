package com.ghzdude.randomizer.compat.jei;

import com.ghzdude.randomizer.util.RandomizerUtil;
import net.minecraft.resources.Identifier;
//import org.jetbrains.annotations.NotNull;

//@JeiPlugin
public class JeiCompat /*implements IModPlugin*/ {

    public LootTableCategory lootTableCategory;
    public static final Identifier UID = RandomizerUtil.location("jei_compat");

//    @Override
//    public @NotNull Identifier getPluginUid() {
//        return UID;
//    }
//
//    @Override
//    public void registerCategories(IRecipeCategoryRegistration registration) {
//        lootTableCategory = new LootTableCategory(registration.getJeiHelpers().getGuiHelper());
//        registration.addRecipeCategories(lootTableCategory);
//    }
//
//    @Override
//    public void registerRecipes(IRecipeRegistration registration) {
//        registration.addRecipes(lootTableCategory.getRecipeType(), ParsedLootTable.getRecipes());
//    }
}
