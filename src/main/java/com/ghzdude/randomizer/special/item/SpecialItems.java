package com.ghzdude.randomizer.special.item;

import com.ghzdude.randomizer.io.ConfigIO;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpecialItems {
    public static final List<ResourceLocation> BLACKLISTED_ITEMS = ConfigIO.readItemBlacklist();

    public static final Map<Item, Integer> EFFECT_ITEMS = new Object2IntOpenHashMap<>();

    // todo read from config
    public static final Map<Item, Integer> SPECIAL_ITEMS = new Object2IntOpenHashMap<>();

    public static final List<Item> LEATHER_ARMOR = new ArrayList<>(List.of(
            Items.LEATHER_HELMET,
            Items.LEATHER_CHESTPLATE,
            Items.LEATHER_LEGGINGS,
            Items.LEATHER_BOOTS
    ));

    public static final List<Item> CHAINMAIL_ARMOR = new ArrayList<>(List.of(
            Items.CHAINMAIL_HELMET,
            Items.CHAINMAIL_CHESTPLATE,
            Items.CHAINMAIL_LEGGINGS,
            Items.CHAINMAIL_BOOTS
    ));

    public static final List<Item> IRON_ARMOR = new ArrayList<>(List.of(
            Items.IRON_HELMET,
            Items.IRON_CHESTPLATE,
            Items.IRON_LEGGINGS,
            Items.IRON_BOOTS
    ));

    public static final List<Item> DIAMOND_ARMOR = new ArrayList<>(List.of(
            Items.DIAMOND_HELMET,
            Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS,
            Items.DIAMOND_BOOTS
    ));

    public static final List<Item> NETHERITE_ARMOR = new ArrayList<>(List.of(
            Items.NETHERITE_HELMET,
            Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_LEGGINGS,
            Items.NETHERITE_BOOTS
    ));

    public static final List<Item> WOODEN_TOOLS = new ArrayList<>(List.of(
            Items.WOODEN_PICKAXE,
            Items.WOODEN_AXE,
            Items.WOODEN_HOE,
            Items.WOODEN_SHOVEL,
            Items.WOODEN_SWORD
    ));
    public static final List<Item> STONE_TOOLS = new ArrayList<>(List.of(
            Items.STONE_PICKAXE,
            Items.STONE_AXE,
            Items.STONE_HOE,
            Items.STONE_SHOVEL,
            Items.STONE_SWORD
    ));
    public static final List<Item> IRON_TOOLS = new ArrayList<>(List.of(
            Items.IRON_PICKAXE,
            Items.IRON_AXE,
            Items.IRON_HOE,
            Items.IRON_SHOVEL,
            Items.IRON_SWORD
    ));
    public static final List<Item> GOLDEN_TOOLS = new ArrayList<>(List.of(
            Items.GOLDEN_PICKAXE,
            Items.GOLDEN_AXE,
            Items.GOLDEN_HOE,
            Items.GOLDEN_SHOVEL,
            Items.GOLDEN_SWORD
    ));
    public static final List<Item> DIAMOND_TOOLS = new ArrayList<>(List.of(
            Items.DIAMOND_PICKAXE,
            Items.DIAMOND_AXE,
            Items.DIAMOND_HOE,
            Items.DIAMOND_SHOVEL,
            Items.DIAMOND_SWORD
    ));
    public static final List<Item> NETHERITE_TOOLS = new ArrayList<>(List.of(
            Items.NETHERITE_PICKAXE,
            Items.NETHERITE_AXE,
            Items.NETHERITE_HOE,
            Items.NETHERITE_SHOVEL,
            Items.NETHERITE_SWORD
    ));

    // enchantable tools/stuff
    public static final List<Item> ENCHANTABLE = new ArrayList<>(List.of(
            Items.ENCHANTED_BOOK,
            Items.CROSSBOW,
            Items.TRIDENT,
            Items.BOW
    ));

    public static final List<Item> SHULKER_BOXES = new ArrayList<>(List.of(
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
    ));

    static {
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

        EFFECT_ITEMS.put(Items.POTION, 4);
        EFFECT_ITEMS.put(Items.SPLASH_POTION, 6);
        EFFECT_ITEMS.put(Items.LINGERING_POTION, 6);
        EFFECT_ITEMS.put(Items.TIPPED_ARROW, 6);
        EFFECT_ITEMS.put(Items.SUSPICIOUS_STEW, 4);

        // misc
        SPECIAL_ITEMS.put(Items.NETHER_STAR, 15);
        SPECIAL_ITEMS.put(Items.CHEST, 3);
        SPECIAL_ITEMS.put(Items.TRAPPED_CHEST, 3);
        SPECIAL_ITEMS.put(Items.BUNDLE, 6);
        SPECIAL_ITEMS.put(Items.WRITTEN_BOOK, 4);
        SPECIAL_ITEMS.put(Items.END_PORTAL_FRAME, 7);
        SPECIAL_ITEMS.put(Items.MACE, 10);
        SPECIAL_ITEMS.put(Items.HEAVY_CORE, 8);

        // villager stations
        SPECIAL_ITEMS.put(Items.GRINDSTONE, 6);
        SPECIAL_ITEMS.put(Items.FLETCHING_TABLE, 3);
        SPECIAL_ITEMS.put(Items.ANVIL, 6);
        SPECIAL_ITEMS.put(Items.CHIPPED_ANVIL, 4);
        SPECIAL_ITEMS.put(Items.DAMAGED_ANVIL, 2);
        SPECIAL_ITEMS.put(Items.COMPOSTER, 8);
        SPECIAL_ITEMS.put(Items.STONECUTTER, 6);
        SPECIAL_ITEMS.put(Items.BLAST_FURNACE, 3);
        SPECIAL_ITEMS.put(Items.SMOKER, 3);

        SPECIAL_ITEMS.put(Items.CAMPFIRE, 2);
        SPECIAL_ITEMS.put(Items.SOUL_CAMPFIRE, 2);
    }

    private static void addItem(Item item, int value) {
        SPECIAL_ITEMS.put(item, value);
        if (item.isEnchantable(new ItemStack(item)))
            ENCHANTABLE.add(item);
    }

    public static boolean isBlacklisted(Item item) {
        return BLACKLISTED_ITEMS.contains(ForgeRegistries.ITEMS.getKey(item));
    }
}
