package com.ghzdude.randomizer.special.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ColorCollection;

import java.util.*;

import static net.minecraft.references.BlockItemIds.*;
import static net.minecraft.references.ItemIds.*;

public class SpecialItems {

    // potions, etc
    public static final List<ResourceKey<Item>> EFFECT_ITEMS = new ArrayList<>();

    // enchantable items
    public static final List<ResourceKey<Item>> ENCHANTABLE = new ArrayList<>();

    public static final List<ValueItem> CONFIGURED_ITEMS = new ArrayList<>();

    public static final List<ResourceKey<Item>> LEATHER_ARMOR = List.of(
            LEATHER_HELMET,
            LEATHER_CHESTPLATE,
            LEATHER_LEGGINGS,
            LEATHER_BOOTS
    );

    public static final List<ResourceKey<Item>> CHAINMAIL_ARMOR = List.of(
            CHAINMAIL_HELMET,
            CHAINMAIL_CHESTPLATE,
            CHAINMAIL_LEGGINGS,
            CHAINMAIL_BOOTS
    );

    public static final List<ResourceKey<Item>> IRON_ARMOR = List.of(
            IRON_HELMET,
            IRON_CHESTPLATE,
            IRON_LEGGINGS,
            IRON_BOOTS
    );

    public static final List<ResourceKey<Item>> DIAMOND_ARMOR = List.of(
            DIAMOND_HELMET,
            DIAMOND_CHESTPLATE,
            DIAMOND_LEGGINGS,
            DIAMOND_BOOTS
    );

    public static final List<ResourceKey<Item>> NETHERITE_ARMOR = List.of(
            NETHERITE_HELMET,
            NETHERITE_CHESTPLATE,
            NETHERITE_LEGGINGS,
            NETHERITE_BOOTS
    );

    public static final List<ResourceKey<Item>> WOODEN_TOOLS = List.of(
            WOODEN_PICKAXE,
            WOODEN_AXE,
            WOODEN_HOE,
            WOODEN_SHOVEL,
            WOODEN_SWORD
    );

    public static final List<ResourceKey<Item>> STONE_TOOLS = List.of(
            STONE_PICKAXE,
            STONE_AXE,
            STONE_HOE,
            STONE_SHOVEL,
            STONE_SWORD
    );

    public static final List<ResourceKey<Item>> IRON_TOOLS = List.of(
            IRON_PICKAXE,
            IRON_AXE,
            IRON_HOE,
            IRON_SHOVEL,
            IRON_SWORD
    );

    public static final List<ResourceKey<Item>> GOLDEN_TOOLS = List.of(
            GOLDEN_PICKAXE,
            GOLDEN_AXE,
            GOLDEN_HOE,
            GOLDEN_SHOVEL,
            GOLDEN_SWORD
    );

    public static final List<ResourceKey<Item>> DIAMOND_TOOLS = List.of(
            DIAMOND_PICKAXE,
            DIAMOND_AXE,
            DIAMOND_HOE,
            DIAMOND_SHOVEL,
            DIAMOND_SWORD
    );

    public static final List<ResourceKey<Item>> NETHERITE_TOOLS = List.of(
            NETHERITE_PICKAXE,
            NETHERITE_AXE,
            NETHERITE_HOE,
            NETHERITE_SHOVEL,
            NETHERITE_SWORD
    );


    public static void init() {
        Helper helper = new Helper();
        helper.addAll(WOODEN_TOOLS, 1);
        helper.addAll(STONE_TOOLS, 2);
        helper.addAll(IRON_TOOLS, 3);
        helper.addAll(GOLDEN_TOOLS, 2);
        helper.addAll(DIAMOND_TOOLS, 5);
        helper.addAll(NETHERITE_TOOLS, 9);

        helper.addAll(LEATHER_ARMOR, 1);
        helper.addAll(CHAINMAIL_ARMOR, 2);
        helper.addAll(IRON_ARMOR, 3);
        helper.addAll(DIAMOND_ARMOR, 5);
        helper.addAll(NETHERITE_ARMOR, 9);

        helper.addAll(DYED_SHULKER_BOX.map(BlockItemId::item), 6);
        helper.addItem(SHULKER_BOX, 6);

        helper.addItem(POTION, 4);
        helper.addItem(SPLASH_POTION, 6);
        helper.addItem(LINGERING_POTION, 6);
        helper.addItem(TIPPED_ARROW, 6);
        helper.addItem(SUSPICIOUS_STEW, 4);
        helper.addItem(ENCHANTED_BOOK, 4);

        // misc
        helper.addItem(NETHER_STAR, 15);
        helper.addItem(BUNDLE, 6);
        helper.addItem(WRITTEN_BOOK, 4);
        helper.addItem(MACE, 10);
        helper.addItem(CHEST, 3);
        helper.addItem(TRAPPED_CHEST, 3);
        helper.addItem(END_PORTAL_FRAME, 7);
        helper.addItem(HEAVY_CORE, 8);

        // villager stations
        helper.addItem(GRINDSTONE, 6);
        helper.addItem(FLETCHING_TABLE, 3);
        helper.addItem(ANVIL, 6);
        helper.addItem(CHIPPED_ANVIL, 4);
        helper.addItem(DAMAGED_ANVIL, 2);
        helper.addItem(COMPOSTER, 8);
        helper.addItem(STONECUTTER, 6);
        helper.addItem(BLAST_FURNACE, 3);
        helper.addItem(SMOKER, 3);

        helper.addItem(CAMPFIRE, 2);
        helper.addItem(SOUL_CAMPFIRE, 2);
    }


    private static boolean canHaveEffect(ResourceKey<Item> item) {
        return BuiltInRegistries.ITEM.get(item)
                .filter(Holder.Reference::isBound)
                .map(ref -> ref.value().components())
                .map(map -> map.has(DataComponents.POTION_CONTENTS) || map.has(DataComponents.SUSPICIOUS_STEW_EFFECTS))
                .orElse(false);
    }

    private static boolean canEnchant(ResourceKey<Item> item) {
        if (item == ENCHANTED_BOOK) return true;
        return BuiltInRegistries.ITEM.get(item)
                .filter(Holder.Reference::isBound)
                .map(ref -> ref.value().components())
                .map(map -> map.has(DataComponents.ENCHANTABLE))
                .orElse(false);
    }

    private static class Helper {

        private Helper() {}

        public void addItem(ResourceKey<Item> item, int points) {
            CONFIGURED_ITEMS.add(new ValueItem(item, points));
            if (canEnchant(item)) ENCHANTABLE.add(item);
            if (canHaveEffect(item)) EFFECT_ITEMS.add(item);
        }

        public void addAll(Collection<ResourceKey<Item>> items, int points) {
            items.forEach(item -> addItem(item, points));
        }

        public void addAll(ColorCollection<ResourceKey<Item>> items, int points) {
            items.forEach(item -> addItem(item, points));
        }

        public void addItem(BlockItemId id, int points) {
            addItem(id.item(), points);
        }
    }

    public record ValueItem(ResourceKey<Item> item, int value) {

        public static Codec<ValueItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceKey.codec(Registries.ITEM).fieldOf("item").forGetter(ValueItem::item),
                Codec.INT.fieldOf("value").forGetter(ValueItem::value)
        ).apply(instance, ValueItem::new));

        public boolean is(ItemStack stack) {
            return stack.is(item());
        }

        public boolean is(Identifier item) {
            return item().identifier().equals(item);
        }

        public Optional<? extends Holder<Item>> get(Registry<Item> registry) {
            return registry.get(this.item());
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ValueItem valueItem)) return false;
            return Objects.equals(item, valueItem.item);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(item);
        }
    }
}
