package com.ghzdude.randomizer.compat.jei;

import com.ghzdude.randomizer.RandomizerCore;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockDropCategory implements IRecipeCategory<BlockDropRecipe> {

    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(RandomizerCore.MODID, "block_drop");
    public static final RecipeType<BlockDropRecipe> TYPE = new RecipeType<>(UID, BlockDropRecipe.class);
    private final IDrawable ICON;

    public BlockDropCategory(IGuiHelper helper) {
        ICON = helper.createDrawableItemLike(Items.GRASS_BLOCK);
    }

    @Override
    public @NotNull RecipeType<BlockDropRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public @NotNull Component getTitle() {
        return Component.literal("Block Drops");
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return ICON;
    }

    @Override
    public int getWidth() {
        return 16 * 3 + 4;
    }

    @Override
    public int getHeight() {
        return 16 + 2;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BlockDropRecipe recipe, IFocusGroup focusGroup) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1)
                .addIngredient(VanillaTypes.ITEM_STACK, recipe.input());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 1 + 16 + 1 + 16, 1)
                .addIngredient(VanillaTypes.ITEM_STACK, recipe.output());
    }
}
