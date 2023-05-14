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

public class ItemRandomizer {
    private int points;
    private int pointMax;
    private int offset;
    private int amtItemsGiven;

    ItemRandomizer () {
        this.points = 0;
        this.pointMax = 1;
        this.offset = 0;
        this.amtItemsGiven = 0;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event){
        if (event.side == LogicalSide.CLIENT) return;
        if (event.phase == TickEvent.Phase.END) return;
        if (offset < 0) offset = 0;
        offset++;

        if (offset % RandomizerConfig.getCooldown() == 0) {
            points += pointMax;
            if (points > 0) {
                Player player = event.player;
                if (player.getInventory().getFreeSlot() == -1) return;


                int tries = 0;
                int pointsToUse = (int) Math.max(Math.round(Math.random() * points), 1);
                points -= pointsToUse;

                ItemStack stack = getRandomStack();

                while (tries < 5) {
                    if (pointsToUse <= stack.getMaxStackSize()) {
                        stack.setCount(pointsToUse);
                        addStackToPlayer(stack, player.getInventory());
                        return;
                    }

                    stack.setCount(stack.getMaxStackSize());
                    pointsToUse -= stack.getMaxStackSize();
                    stack = addStackToPlayer(stack, player.getInventory());
                    tries++;
                }
            }
        }
    }

    private ItemStack getRandomStack () {
        ItemStack stack;
        do {
            int id = (int) (Math.random() * RandomizerCore.REGISTERED_ITEMS.getValues().size());
            stack = new ItemStack(RandomizerCore.REGISTERED_ITEMS.getValue(id));
        } while (SpecialItems.BLACKLISTED_ITEMS.contains(stack.getItem()));
        return stack;
    }

    private ItemStack addStackToPlayer(ItemStack stack, Inventory inventory) {
        inventory.player.sendSystemMessage(Component.translatable("Given " + stack + ", " + pointMax + " points per cycle"));
        inventory.add(stack);
        amtItemsGiven++;
        if (amtItemsGiven % 20 == 0) {
            inventory.player.sendSystemMessage(Component.translatable("Point max has gone up! Amount of items given is " + amtItemsGiven));
            pointMax++;
        }
        return getRandomStack();
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
                Items.KNOWLEDGE_BOOK
        ));

        /* create special logic for...
         * Written Book
         * Tipped Arrow
         * potions
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
    }
}
