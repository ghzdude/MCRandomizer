package com.ghzdude.randomizer;

/* Item Randomizer Description
 * Goal is to give the player a random item every so often DONE
 * Every so often, points are added to a counter DONE
 * over time, the amount of points gained each cycle is increased
 * those points are then used to give the player an item DONE
 * more points give bigger stacksize of item at once DONE
 * items have a defined value, otherwise stacksize is used
 */


import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ItemRandomizer {
    private int points;
    private int pointMax;
    private int offset;
    private int amtItemsGiven;
    protected final ArrayList<SpecialItem> validItems;

    ItemRandomizer (ArrayList<SpecialItem> validItems) {
        this.points = 0;
        this.pointMax = 1;
        this.offset = 0;
        this.amtItemsGiven = 0;
        this.validItems = validItems;

        int lastMatch = -1;

        validItems.removeIf(specialItem -> SpecialItems.BLACKLISTED_ITEMS.contains(specialItem.item));
        for (int i = 0; i < validItems.size(); i++) {
            SpecialItem toUpdate = validItems.get(i);
            int match = SpecialItems.SPECIAL_ITEMS.indexOf(toUpdate);

            if (match != -1) {
                lastMatch = match;
                validItems.set(i, SpecialItems.SPECIAL_ITEMS.get(match));
            } else if (toUpdate.item.toString().contains("shulker_box")){
                validItems.get(i).value = SpecialItems.SPECIAL_ITEMS.get(lastMatch).value;
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event){
        if (event.side == LogicalSide.CLIENT) return;
        if (event.phase == TickEvent.Phase.END) return;
        if (offset < 0) offset = 0;
        offset++;

        if (offset % RandomizerConfig.getCooldown() == 0) {
            points += pointMax;

            Player player = event.player;
            if (player.getInventory().getFreeSlot() == -1) return;

            int tries = 0;
            int pointsToUse = (int) Math.max(Math.round(Math.random() * points), 1);
            points -= pointsToUse;

            // try to give up to five items while there are points to use
            SpecialItem selectedItem = getRandomItem();
            while (tries < 5 && pointsToUse > 0) {

                if (selectedItem.value > pointsToUse) {
                    continue;
                }

                if (selectedItem.isWrittenBook){
                    addStackToPlayer(getRandomBook(selectedItem), player.getInventory());
                    break;
                }

                if (selectedItem.isPotion) {
                    // do potion stuff
                }

                int amtToGive = Math.floorDiv(pointsToUse, selectedItem.value);
                ItemStack stack = new ItemStack(selectedItem.item);
                pointsToUse -= pointsToUse * amtToGive;

                if (amtToGive > stack.getMaxStackSize()) {
                    stack.setCount(stack.getMaxStackSize());
                    amtToGive -= stack.getMaxStackSize();
                } else {
                    stack.setCount(amtToGive);
                    break;
                }

                selectedItem = addStackToPlayer(stack, player.getInventory());
                tries++;

            }
        }
    }

    private SpecialItem getRandomItem() {
        SpecialItem selectedItem;

        do {
            int id = (int) (Math.random() * validItems.size());
            selectedItem = validItems.get(id);
        } while (SpecialItems.BLACKLISTED_ITEMS.contains(selectedItem.item));

        return selectedItem;
    }

    private ItemStack getRandomBook(SpecialItem item) {
        ItemStack stack = new ItemStack(item.item);
        stack.setCount(1);
        return stack;
    }

    private SpecialItem addStackToPlayer(ItemStack stack, Inventory inventory) {
        inventory.player.sendSystemMessage(Component.translatable("Given " + inventory.player.getDisplayName().getString() + " " + stack));
        inventory.add(stack);
        amtItemsGiven++;
        if (amtItemsGiven % 20 == 0) {
            inventory.player.sendSystemMessage(Component.translatable("Point max has gone up! Amount of items given is " + amtItemsGiven));
            pointMax++;
        }
        return getRandomItem();
    }

    protected static class SpecialItems {
        protected static final ArrayList<Item> BLACKLISTED_ITEMS = new ArrayList<>(Arrays.asList(
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

        protected static final ArrayList<SpecialItem> SPECIAL_ITEMS = new ArrayList<>(Arrays.asList(
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
            new SpecialItem(Items.WRITTEN_BOOK, 4, false, true),
            new SpecialItem(Items.POTION, 4, true,false),
            new SpecialItem(Items.SPLASH_POTION, 6, true,false),
            new SpecialItem(Items.LINGERING_POTION, 6, true, false),
            new SpecialItem(Items.TIPPED_ARROW, 6, true, false),
            new SpecialItem(Items.SUSPICIOUS_STEW, 4, true, false),

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
        )){
            @Override
            public boolean contains(Object o) {
                if (!(o instanceof SpecialItem)) return false;
                for (SpecialItem item : this) {
                    return item.item.equals(((SpecialItem) o).item);
                }
                return false;
            }

            @Override
            public int indexOf(Object o) {
                for (int i = 0; i < this.size(); i++) {
                    if (this.get(i).item == ((SpecialItem) o).item) return i;
                }
                return -1;
            }
        };
    }

    protected static class SpecialItem {
        public Item item;
        public int value;
        public boolean isPotion, isWrittenBook;

        SpecialItem(Item item, int value, boolean isPotion, boolean isWrittenBook) {
            this.item = item;
            this.value = value;
            this.isPotion = isPotion;
            this.isWrittenBook = isWrittenBook;
        }

        SpecialItem(Item item, int value) {
            this(item, value, false, false);
        }

        SpecialItem(Item item) {
            this(item, 1);
        }
    }
}
