package com.ghzdude.randomizer.compat.jei;

import com.ghzdude.randomizer.RandomizerCore;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockDropCategory implements IRecipeCategory<BlockDropRecipe> {

    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(RandomizerCore.MODID, "block_drop");
    public static final RecipeType<BlockDropRecipe> TYPE = new RecipeType<>(UID, BlockDropRecipe.class);
    private final IDrawable ICON;
    private final  IGuiHelper helper;

    public BlockDropCategory(IGuiHelper helper) {
        this.helper = helper;
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
        return 104;
    }

    @Override
    public int getHeight() {
        return 34;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BlockDropRecipe recipe, IFocusGroup focusGroup) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1)
                .setStandardSlotBackground()
                .addIngredient(VanillaTypes.ITEM_STACK, recipe.input());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 37 + 6, 1)
                .setStandardSlotBackground()
                .addIngredient(VanillaTypes.ITEM_STACK, recipe.output());
        if (recipe.type() != BlockDropRecipe.Type.HAND) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 19+3, 3+16)
                    .addIngredient(VanillaTypes.ITEM_STACK, recipe.type().getStack());
        }
    }

    @Override
    public void draw(BlockDropRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        helper.getRecipeArrow().draw(guiGraphics, 19, 1);
    }
}
