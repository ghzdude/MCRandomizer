package com.ghzdude.randomizer.compat.jei;

import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public record BlockDropRecipe(ItemStack input, ItemStack output, Type type) {

    private static final Object2ObjectMap<ResourceLocation, BlockDropRecipe> REGISTRY = new Object2ObjectOpenHashMap<>();

    public static void registerRecipe(Item in, ItemStack output, Type type) {
        if (output.isEmpty()) return;
        BlockDropRecipe recipe = new BlockDropRecipe(new ItemStack(in), output, type);
        REGISTRY.put(RandomizerUtil.location("%s_drops_%s_%s".formatted(in, output.getItem(), type)), recipe);
//        CompletabilityVerifier.addBlockDrop(recipe, ItemRandomizer.getRegistry());
    }

    public static void registerRecipe(Item in, ItemStack output) {
        registerRecipe(in, output, Type.HAND);
    }

    public static void clearRegistry() {
        REGISTRY.clear();
    }

    public static List<BlockDropRecipe> getRecipes() {
        return ImmutableList.copyOf(REGISTRY.values());
    }

    public static BlockDropRecipe get(ResourceLocation location) {
        return REGISTRY.get(location);
    }

    public enum Type {
        HAND("Hand"),
        PICK("Pick"),
        SILK_PICK("Silk Touch"),
        SHEARS("Shears"),
        SHEARS_OR_SILK("Silk or Shears");

        final Component translation;
        final String name;
        final ItemStack stack;

        Type(String name) {
            this.translation = Component.translatable("randomizer.compat.jei.block_drop.type." + name.toLowerCase().replace(' ', '_'));
            this.name = name;
            this.stack = new ItemStack(Items.ENCHANTED_BOOK);
            this.stack.set(DataComponents.CUSTOM_NAME, this.translation);
        }

        @Override
        public String toString() {
            return name;
        }

        public ItemStack getStack() {
            return stack;
        }
    }
}
