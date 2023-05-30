package com.ghzdude.randomizer.special.item;

import com.ghzdude.randomizer.io.ConfigIO;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpecialItems {
    public static final ArrayList<Item> BLACKLISTED_ITEMS = ConfigIO.readBlacklist();

    public static final SpecialItemList EFFECT_ITEMS = new SpecialItemList(Arrays.asList(
            new SpecialItem(Items.POTION, 4),
            new SpecialItem(Items.SPLASH_POTION, 6),
            new SpecialItem(Items.LINGERING_POTION, 6),
            new SpecialItem(Items.TIPPED_ARROW, 6),
            new SpecialItem(Items.SUSPICIOUS_STEW, 4)
    ));

    public static final ArrayList<Item> WOODEN_TOOLS = new ArrayList<>(List.of(
            Items.WOODEN_PICKAXE,
            Items.WOODEN_AXE,
            Items.WOODEN_HOE,
            Items.WOODEN_SHOVEL,
            Items.WOODEN_SWORD
    ));
    public static final ArrayList<Item> STONE_TOOLS = new ArrayList<>(List.of(
            Items.STONE_PICKAXE,
            Items.STONE_AXE,
            Items.STONE_HOE,
            Items.STONE_SHOVEL,
            Items.STONE_SWORD
    ));
    public static final ArrayList<Item> IRON_TOOLS = new ArrayList<>(List.of(
            Items.IRON_PICKAXE,
            Items.IRON_AXE,
            Items.IRON_HOE,
            Items.IRON_SHOVEL,
            Items.IRON_SWORD
    ));
    public static final ArrayList<Item> GOLDEN_TOOLS = new ArrayList<>(List.of(
            Items.GOLDEN_PICKAXE,
            Items.GOLDEN_AXE,
            Items.GOLDEN_HOE,
            Items.GOLDEN_SHOVEL,
            Items.GOLDEN_SWORD
    ));
    public static final ArrayList<Item> DIAMOND_TOOLS = new ArrayList<>(List.of(
            Items.DIAMOND_PICKAXE,
            Items.DIAMOND_AXE,
            Items.DIAMOND_HOE,
            Items.DIAMOND_SHOVEL,
            Items.DIAMOND_SWORD
    ));
    public static final ArrayList<Item> NETHERITE_TOOLS = new ArrayList<>(List.of(
            Items.NETHERITE_PICKAXE,
            Items.NETHERITE_AXE,
            Items.NETHERITE_HOE,
            Items.NETHERITE_SHOVEL,
            Items.NETHERITE_SWORD
    ));

    // enchantable tools/stuff
    public static final ArrayList<Item> ENCHANTABLE = new ArrayList<>(List.of(
            Items.ENCHANTED_BOOK
    ));


    public static final SpecialItemList SPECIAL_ITEMS = new SpecialItemList(Arrays.asList(
            new SpecialItem(Items.NETHER_STAR, 15),
            new SpecialItem(Items.CHEST, 3),
            new SpecialItem(Items.TRAPPED_CHEST, 3),
            new SpecialItem(Items.SHULKER_BOX, 6),
            new SpecialItem(Items.BUNDLE, 6),
            new SpecialItem(Items.WRITTEN_BOOK, 4),

            // villager stations
            new SpecialItem(Items.GRINDSTONE, 6),
            new SpecialItem(Items.FLETCHING_TABLE, 3),
            new SpecialItem(Items.ANVIL, 6),
            new SpecialItem(Items.CHIPPED_ANVIL, 4),
            new SpecialItem(Items.DAMAGED_ANVIL, 2),
            new SpecialItem(Items.COMPOSTER, 8),
            new SpecialItem(Items.STONECUTTER, 6),
            new SpecialItem(Items.BLAST_FURNACE, 3),
            new SpecialItem(Items.SMOKER, 3),

            new SpecialItem(Items.CAMPFIRE, 2),
            new SpecialItem(Items.SOUL_CAMPFIRE, 2)
    ));

    static {
        ENCHANTABLE.addAll(WOODEN_TOOLS);
        ENCHANTABLE.addAll(STONE_TOOLS);
        ENCHANTABLE.addAll(IRON_TOOLS);
        ENCHANTABLE.addAll(GOLDEN_TOOLS);
        ENCHANTABLE.addAll(DIAMOND_TOOLS);
        ENCHANTABLE.addAll(NETHERITE_TOOLS);

        WOODEN_TOOLS.forEach(item -> SPECIAL_ITEMS.add(new SpecialItem(item)));
        STONE_TOOLS.forEach(item -> SPECIAL_ITEMS.add(new SpecialItem(item, 2)));
        IRON_TOOLS.forEach(item -> SPECIAL_ITEMS.add(new SpecialItem(item, 3)));
        GOLDEN_TOOLS.forEach(item -> SPECIAL_ITEMS.add(new SpecialItem(item, 2)));
        DIAMOND_TOOLS.forEach(item -> SPECIAL_ITEMS.add(new SpecialItem(item, 5)));
        NETHERITE_TOOLS.forEach(item -> SPECIAL_ITEMS.add(new SpecialItem(item, 9)));
    }

}
