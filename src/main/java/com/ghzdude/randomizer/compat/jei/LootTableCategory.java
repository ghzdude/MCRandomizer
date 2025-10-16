package com.ghzdude.randomizer.compat.jei;

import com.ghzdude.randomizer.util.RandomizerUtil;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LootTableCategory implements IRecipeCategory<ParsedLootTable> {

    public static final ResourceLocation UID = RandomizerUtil.location("block_drop_category");
    public static final IRecipeType<ParsedLootTable> TYPE = IRecipeType.create(UID, ParsedLootTable.class);
    private final IDrawable ICON;
    private final Component TITLE = Component.translatable("randomizer.compat.jei.block_drop_category");
    private final IGuiHelper helper;

    public LootTableCategory(IGuiHelper helper) {
        this.helper = helper;
        ICON = helper.createDrawableItemLike(Items.GRASS_BLOCK);
    }

    @Override
    public @NotNull IRecipeType<ParsedLootTable> getRecipeType() {
        return TYPE;
    }

    @Override
    public @NotNull Component getTitle() {
        return TITLE;
    }

    @Override
    public @Nullable IDrawable getIcon() {
        return ICON;
    }

    @Override
    public int getWidth() {
        return (18 * 9) + 4;
    }

    @Override
    public int getHeight() {
        return (18 * 5) + 4;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ParsedLootTable recipe, @NotNull IFocusGroup focusGroup) {
        int startX = getWidth() / 2;
        int starty = 1;

        builder.addSlot(RecipeIngredientRole.INPUT, startX - 20, starty)
                .setStandardSlotBackground()
                .add(recipe.input());

        starty += 18 + 2;

        int maxSlots = 4 * 9;
        boolean willFit = recipe.drops().size() <= maxSlots;

        startX = 1;
        List<ItemStack> drops = recipe.drops();
        for (int i = 0; i < maxSlots; i++) {
            ItemStack drop = i < drops.size() ? drops.get(i) : ItemStack.EMPTY;
            int row = i / 9;
            int col = i % 9;

            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, startX + (18 * col), starty + (18 * row))
                    .setStandardSlotBackground();

            if (i + 1 == maxSlots && !willFit) {
                RandomizerUtil.addLines(drop, lines -> {
                    lines.add(Component.translatable("randomizer.compat.jei.block_drop.cannot_fit")
                            .withStyle(ChatFormatting.BOLD, ChatFormatting.RED));
                });
                slot.add(drop);
                break;
            }

            if (!drop.isEmpty())
                slot.add(drop);
        }
    }

    @Override
    public void draw(@NotNull ParsedLootTable recipe, @NotNull IRecipeSlotsView recipeSlotsView, @NotNull GuiGraphics guiGraphics, double mouseX, double mouseY) {
        helper.getRecipeArrow().draw(guiGraphics, (getWidth() / 2) - 2, 1);
    }
}
