package com.ghzdude.randomizer.compat.jei;

import com.ghzdude.randomizer.CompletabilityVerifier;
import com.ghzdude.randomizer.loot.LootRandomizer;
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
        String s = LootRandomizer.ITEM_REGISTRY.getKey(in).getPath(), s1 = LootRandomizer.ITEM_REGISTRY.getKey(output.getItem()).getPath();
        ResourceLocation loc = RandomizerUtil.location("%s_drops_%s_%s".formatted(s, s1, type.lower));
        REGISTRY.put(loc, recipe);
        CompletabilityVerifier.addBlockDrop(recipe, loc);
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

        private final String name;
        private final ItemStack stack;
        private final String lower;

        Type(String name) {
            this.name = name;
            this.lower = name.toLowerCase().replace(' ', '_');
            this.stack = new ItemStack(Items.ENCHANTED_BOOK);
            this.stack.set(DataComponents.CUSTOM_NAME, Component.translatable("randomizer.compat.jei.block_drop.type." + this.lower));
        }

        @Override
        public String toString() {
            return "Type{%s}".formatted(name);
        }

        public ItemStack getStack() {
            return stack;
        }
    }
}
