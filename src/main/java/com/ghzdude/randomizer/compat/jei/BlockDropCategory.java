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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockDropCategory implements IRecipeCategory<BlockDropRecipe> {

    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(RandomizerCore.MODID, "block_drop");
    public static final RecipeType<BlockDropRecipe> TYPE = new RecipeType<>(UID, BlockDropRecipe.class);
    private final IDrawable ICON;
    private final ItemStack SILK_TOUCH;

    public BlockDropCategory(IGuiHelper helper) {
        ICON = helper.createDrawableItemLike(Items.GRASS_BLOCK);
        SILK_TOUCH = new ItemStack(Items.ENCHANTED_BOOK);
        SILK_TOUCH.set(DataComponents.CUSTOM_NAME, Component.literal("Requires Silk Touch"));
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
        builder.addSlot(RecipeIngredientRole.OUTPUT, 37, 1)
                .setStandardSlotBackground()
                .addIngredient(VanillaTypes.ITEM_STACK, recipe.output());
        if (recipe.silkTouch()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 19, 3+16)
                    .setStandardSlotBackground()
                    .addIngredient(VanillaTypes.ITEM_STACK, SILK_TOUCH);
        }
    }
}
