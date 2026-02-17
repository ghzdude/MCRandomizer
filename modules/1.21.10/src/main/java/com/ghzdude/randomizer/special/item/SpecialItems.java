package com.ghzdude.randomizer.special.item;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class SpecialItems {

    // potions, etc
    public static final List<Item> EFFECT_ITEMS = new ArrayList<>();

    // enchantable items
    public static final List<Item> ENCHANTABLE = new ArrayList<>();

    public static final Object2IntMap<ResourceLocation> CONFIGURED_ITEMS = new Object2IntOpenHashMap<>();

    public static final List<Item> LEATHER_ARMOR = List.of(
            Items.LEATHER_HELMET,
            Items.LEATHER_CHESTPLATE,
            Items.LEATHER_LEGGINGS,
            Items.LEATHER_BOOTS
    );

    public static final List<Item> CHAINMAIL_ARMOR = List.of(
            Items.CHAINMAIL_HELMET,
            Items.CHAINMAIL_CHESTPLATE,
            Items.CHAINMAIL_LEGGINGS,
            Items.CHAINMAIL_BOOTS
    );

    public static final List<Item> IRON_ARMOR = List.of(
            Items.IRON_HELMET,
            Items.IRON_CHESTPLATE,
            Items.IRON_LEGGINGS,
            Items.IRON_BOOTS
    );

    public static final List<Item> DIAMOND_ARMOR = List.of(
            Items.DIAMOND_HELMET,
            Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS,
            Items.DIAMOND_BOOTS
    );

    public static final List<Item> NETHERITE_ARMOR = List.of(
            Items.NETHERITE_HELMET,
            Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_LEGGINGS,
            Items.NETHERITE_BOOTS
    );

    public static final List<Item> WOODEN_TOOLS = List.of(
            Items.WOODEN_PICKAXE,
            Items.WOODEN_AXE,
            Items.WOODEN_HOE,
            Items.WOODEN_SHOVEL,
            Items.WOODEN_SWORD
    );

    public static final List<Item> STONE_TOOLS = List.of(
            Items.STONE_PICKAXE,
            Items.STONE_AXE,
            Items.STONE_HOE,
            Items.STONE_SHOVEL,
            Items.STONE_SWORD
    );

    public static final List<Item> IRON_TOOLS = List.of(
            Items.IRON_PICKAXE,
            Items.IRON_AXE,
            Items.IRON_HOE,
            Items.IRON_SHOVEL,
            Items.IRON_SWORD
    );

    public static final List<Item> GOLDEN_TOOLS = List.of(
            Items.GOLDEN_PICKAXE,
            Items.GOLDEN_AXE,
            Items.GOLDEN_HOE,
            Items.GOLDEN_SHOVEL,
            Items.GOLDEN_SWORD
    );

    public static final List<Item> DIAMOND_TOOLS = List.of(
            Items.DIAMOND_PICKAXE,
            Items.DIAMOND_AXE,
            Items.DIAMOND_HOE,
            Items.DIAMOND_SHOVEL,
            Items.DIAMOND_SWORD
    );

    public static final List<Item> NETHERITE_TOOLS = List.of(
            Items.NETHERITE_PICKAXE,
            Items.NETHERITE_AXE,
            Items.NETHERITE_HOE,
            Items.NETHERITE_SHOVEL,
            Items.NETHERITE_SWORD
    );

    public static final List<Item> SHULKER_BOXES = List.of(
            Items.SHULKER_BOX,
            Items.WHITE_SHULKER_BOX,
            Items.LIGHT_GRAY_SHULKER_BOX,
            Items.GRAY_SHULKER_BOX,
            Items.BLACK_SHULKER_BOX,
            Items.RED_SHULKER_BOX,
            Items.ORANGE_SHULKER_BOX,
            Items.YELLOW_SHULKER_BOX,
            Items.GREEN_SHULKER_BOX,
            Items.CYAN_SHULKER_BOX,
            Items.LIGHT_BLUE_SHULKER_BOX,
            Items.BLUE_SHULKER_BOX,
            Items.PURPLE_SHULKER_BOX,
            Items.BROWN_SHULKER_BOX,
            Items.LIME_SHULKER_BOX,
            Items.PINK_SHULKER_BOX
    );

    private static Function<Item, ResourceLocation> converter;

    public static void init(Function<Item, ResourceLocation> converter) {
        SpecialItems.converter = converter;
        WOODEN_TOOLS.forEach(item -> addItem(item, 1));
        STONE_TOOLS.forEach(item -> addItem(item, 2));
        IRON_TOOLS.forEach(item -> addItem(item, 3));
        GOLDEN_TOOLS.forEach(item -> addItem(item, 2));
        DIAMOND_TOOLS.forEach(item -> addItem(item, 5));
        NETHERITE_TOOLS.forEach(item -> addItem(item, 9));

        LEATHER_ARMOR.forEach(item -> addItem(item, 1));
        CHAINMAIL_ARMOR.forEach(item -> addItem(item, 2));
        IRON_ARMOR.forEach(item -> addItem(item, 3));
        DIAMOND_ARMOR.forEach(item -> addItem(item, 5));
        NETHERITE_ARMOR.forEach(item -> addItem(item, 9));

        SHULKER_BOXES.forEach(item -> addItem(item, 6));

        addItem(Items.POTION, 4);
        addItem(Items.SPLASH_POTION, 6);
        addItem(Items.LINGERING_POTION, 6);
        addItem(Items.TIPPED_ARROW, 6);
        addItem(Items.SUSPICIOUS_STEW, 4);
        addItem(Items.ENCHANTED_BOOK, 4);

        // misc
        addItem(Items.NETHER_STAR, 15);
        addItem(Items.CHEST, 3);
        addItem(Items.TRAPPED_CHEST, 3);
        addItem(Items.BUNDLE, 6);
        addItem(Items.WRITTEN_BOOK, 4);
        addItem(Items.END_PORTAL_FRAME, 7);
        addItem(Items.MACE, 10);
        addItem(Items.HEAVY_CORE, 8);

        // villager stations
        addItem(Items.GRINDSTONE, 6);
        addItem(Items.FLETCHING_TABLE, 3);
        addItem(Items.ANVIL, 6);
        addItem(Items.CHIPPED_ANVIL, 4);
        addItem(Items.DAMAGED_ANVIL, 2);
        addItem(Items.COMPOSTER, 8);
        addItem(Items.STONECUTTER, 6);
        addItem(Items.BLAST_FURNACE, 3);
        addItem(Items.SMOKER, 3);

        addItem(Items.CAMPFIRE, 2);
        addItem(Items.SOUL_CAMPFIRE, 2);
    }

    private static void addItem(Item item, int value) {
        CONFIGURED_ITEMS.put(converter.apply(item), value);
        if (canEnchant(item)) ENCHANTABLE.add(item);
        if (canHaveEffect(item)) EFFECT_ITEMS.add(item);
    }

    private static boolean canHaveEffect(Item item) {
        return item.components().has(DataComponents.POTION_CONTENTS) || item.components().has(DataComponents.SUSPICIOUS_STEW_EFFECTS);
    }

    private static boolean canEnchant(Item item) {
        if (item == Items.ENCHANTED_BOOK) return true;
        return item.components().has(DataComponents.MAX_DAMAGE) && item.getDefaultMaxStackSize() == 1;
    }
}
