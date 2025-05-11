package com.ghzdude.randomizer.compat.jei;

import com.ghzdude.randomizer.loot.LootRandomizer;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;

public record BlockDropRecipe(Item input, Item output, Type type, ResourceLocation lootTable) {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object2ObjectMap<ResourceLocation, BlockDropRecipe> REGISTRY = new Object2ObjectOpenHashMap<>();

    public static void registerRecipe(Item input, Item output, @NotNull Type type, @NotNull ResourceLocation id) {
        ResourceLocation inKey = Objects.requireNonNull(LootRandomizer.ITEM_REGISTRY.getKey(input));
        ResourceLocation outKey = Objects.requireNonNull(LootRandomizer.ITEM_REGISTRY.getKey(output));
        ResourceLocation recipeId = RandomizerUtil.location("%s_drops_%s".formatted(inKey.getPath(), outKey.getPath()));

        if (input == Items.AIR) {
            LOGGER.warn("Input cannot be air for '{}'", recipeId);
            return;
        } else if (output == Items.AIR) {
            LOGGER.warn("Output cannot be air for '{}'", recipeId);
            return;
        }
        
        REGISTRY.put(recipeId, new BlockDropRecipe(input, output, type, Objects.requireNonNull(id)));
    }

    public static void clearRegistry() {
        REGISTRY.clear();
    }

    public static List<BlockDropRecipe> getRecipes() {
        return ImmutableList.copyOf(REGISTRY.values());
    }

    public static List<ResourceLocation> getKeys() {
        return ImmutableList.copyOf(REGISTRY.keySet());
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
