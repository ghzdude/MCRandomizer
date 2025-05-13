package com.ghzdude.randomizer.compat.jei;

import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public record ParsedLootTable(ItemStack input, List<ItemStack> drops, ResourceLocation lootTable) {

    private static final Object2ObjectMap<ResourceLocation, ParsedLootTable> REGISTRY = new Object2ObjectOpenHashMap<>();

    public static void registerRecipe(ItemStack input, List<ItemStack> drops, @NotNull ResourceLocation table) {
        ResourceLocation recipeId = RandomizerUtil.location(table.getNamespace() + "-" + table.getPath());
        REGISTRY.put(recipeId, new ParsedLootTable(input, drops, Objects.requireNonNull(table)));
    }

    public static void clearRegistry() {
        REGISTRY.clear();
    }

    public static List<ParsedLootTable> getRecipes() {
        return ImmutableList.copyOf(REGISTRY.values());
    }

    public static List<ResourceLocation> getKeys() {
        return ImmutableList.copyOf(REGISTRY.keySet());
    }

    public static ParsedLootTable get(ResourceLocation location) {
        return REGISTRY.get(location);
    }

    public enum Type {
        HAND("Hand"),
        PICK("Pick"),
        SILK_PICK("Silk Touch"),
        SHEARS("Shears"),
        SHEARS_OR_SILK("Silk or Shears");

        private final String name;
        private final String lower;

        Type(String name) {
            this.name = name;
            this.lower = name.toLowerCase().replace(' ', '_');
        }

        public Component getName() {
            return Component.translatable("randomizer.compat.jei.block_drop.type." + this.lower);
        }

        @Override
        public String toString() {
            return "Type{%s}".formatted(name);
        }
    }
}
