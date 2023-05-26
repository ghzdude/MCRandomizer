package com.ghzdude.randomizer.special.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpecialItems {
    public static final ArrayList<Item> BLACKLISTED_ITEMS = new ArrayList<>(Arrays.asList(
            Items.AIR,
            Items.COMMAND_BLOCK,
            Items.COMMAND_BLOCK_MINECART,
            Items.CHAIN_COMMAND_BLOCK,
            Items.REPEATING_COMMAND_BLOCK,
            Items.BARRIER,
            Items.LIGHT,
            Items.STRUCTURE_BLOCK,
            Items.STRUCTURE_VOID,
            Items.WRITTEN_BOOK,
            Items.KNOWLEDGE_BOOK,
            Items.JIGSAW
    ));

    /* create special logic for...
     * Written Book
     * Tipped Arrow
     * potions
     * sus stew
     */

    /* give point values to
     * wodden tools - 1 point
     * stone tools - 2 point
     * iron tools - 3 point
     * diamond tools - 5 point
     * netherite tools - 9 point
     * nether star - 15 points
     * shulker boxes 6 points
     * chest 3 points
     * bundle 6 points
     * written book 4 points
     * potions 4 points
     * splash/lingering potions 6 points
     * tipped arrows 6 points
     * villager stations 6-15
     * furnace 2 points
     * blast furnace 5 points
     * cooker 4 points
     * campfire 2 points
     * im fucking tired lol
     */

    public static final SpecialItemList EFFECT_ITEMS = new SpecialItemList(Arrays.asList(
            new SpecialItem(Items.POTION, 4),
            new SpecialItem(Items.SPLASH_POTION, 6),
            new SpecialItem(Items.LINGERING_POTION, 6),
            new SpecialItem(Items.TIPPED_ARROW, 6),
            new SpecialItem(Items.SUSPICIOUS_STEW, 4)
    ));

    // enchantable tools/stuff
    public static final ArrayList<Item> ENCHANTABLE = new ArrayList<>(List.of(
            Items.ENCHANTED_BOOK
    ));

    public static final SpecialItemList SPECIAL_ITEMS = new SpecialItemList(Arrays.asList(
            new SpecialItem(Items.WOODEN_PICKAXE),
            new SpecialItem(Items.WOODEN_AXE),
            new SpecialItem(Items.WOODEN_HOE),
            new SpecialItem(Items.WOODEN_SHOVEL),
            new SpecialItem(Items.WOODEN_SWORD),

            new SpecialItem(Items.GOLDEN_PICKAXE, 2),
            new SpecialItem(Items.GOLDEN_AXE, 2),
            new SpecialItem(Items.GOLDEN_HOE, 2),
            new SpecialItem(Items.GOLDEN_SHOVEL, 2),
            new SpecialItem(Items.GOLDEN_SWORD, 2),

            new SpecialItem(Items.STONE_PICKAXE, 2),
            new SpecialItem(Items.STONE_AXE, 2),
            new SpecialItem(Items.STONE_HOE, 2),
            new SpecialItem(Items.STONE_SHOVEL, 2),
            new SpecialItem(Items.STONE_SWORD, 2),

            new SpecialItem(Items.IRON_PICKAXE, 3),
            new SpecialItem(Items.IRON_AXE, 3),
            new SpecialItem(Items.IRON_HOE, 3),
            new SpecialItem(Items.IRON_SHOVEL, 3),
            new SpecialItem(Items.IRON_SWORD, 3),

            new SpecialItem(Items.DIAMOND_PICKAXE, 5),
            new SpecialItem(Items.DIAMOND_AXE, 5),
            new SpecialItem(Items.DIAMOND_HOE, 5),
            new SpecialItem(Items.DIAMOND_SHOVEL, 5),
            new SpecialItem(Items.DIAMOND_SWORD, 5),

            new SpecialItem(Items.NETHERITE_PICKAXE, 9),
            new SpecialItem(Items.NETHERITE_AXE, 9),
            new SpecialItem(Items.NETHERITE_HOE, 9),
            new SpecialItem(Items.NETHERITE_SHOVEL, 9),
            new SpecialItem(Items.NETHERITE_SWORD, 9),

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
}
