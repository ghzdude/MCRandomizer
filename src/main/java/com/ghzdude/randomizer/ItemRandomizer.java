package com.ghzdude.randomizer;

import com.ghzdude.randomizer.special.generators.*;
import com.ghzdude.randomizer.special.item.SpecialItem;
import com.ghzdude.randomizer.special.item.SpecialItemList;
import com.ghzdude.randomizer.special.item.SpecialItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;

/* Item Randomizer Description
 * Goal is to give the player a random item every so often DONE
 * Every so often, points are added to a counter DONE
 * over time, the amount of points gained each cycle is increased
 * those points are then used to give the player an item DONE
 * more points give bigger stacksize of item at once DONE
 * items have a defined value, otherwise stacksize is used
 */
public class ItemRandomizer {
    private static final SpecialItemList VALID_ITEMS = new SpecialItemList(configureValidItem());

    private static Collection<SpecialItem> configureValidItem() {
        int lastMatch;
        ArrayList<SpecialItem> validItems = new ArrayList<>();

        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (SpecialItems.BLACKLISTED_ITEMS.contains(item)) continue;
            SpecialItem toUpdate;

            if (SpecialItems.SPECIAL_ITEMS.contains(item)) {
                toUpdate = SpecialItems.SPECIAL_ITEMS.get(SpecialItems.SPECIAL_ITEMS.indexOf(item));
            } else if (SpecialItems.EFFECT_ITEMS.contains(item)) {
                toUpdate = SpecialItems.EFFECT_ITEMS.get(SpecialItems.EFFECT_ITEMS.indexOf(item));
            } else {
                toUpdate = new SpecialItem(item);
            }

            if (toUpdate.item.toString().contains("shulker_box")) {
                lastMatch = SpecialItems.SPECIAL_ITEMS.indexOf(Items.SHULKER_BOX);
                toUpdate.value = SpecialItems.SPECIAL_ITEMS.get(lastMatch).value;
            }

            validItems.add(toUpdate);
        }
        return validItems;
    }

    public static int giveRandomItem(int pointsToUse, Inventory inventory){
        if (RandomizerConfig.giveMultipleItems()) {
            pointsToUse = giveMultiple(pointsToUse, inventory);
        } else {
            pointsToUse = giveOnce(pointsToUse, inventory);
        }
        return pointsToUse;
    }

    private static int giveMultiple(int pointsToUse, Inventory playerInventory) {
        int tries = 0;
        while (tries < 5 && pointsToUse > 0) {
            pointsToUse = giveOnce(pointsToUse, playerInventory);
            tries++;
        }
        return pointsToUse;
    }

    private static int giveOnce(int pointsToUse, Inventory playerInventory) {
        SpecialItem selectedItem = getRandomItem(pointsToUse);

        int amtToGive = Math.floorDiv(pointsToUse, selectedItem.value);
        ItemStack stack = specialItemToStack(selectedItem);

        stack.setCount(Math.min(amtToGive, stack.getMaxStackSize()));

        pointsToUse -= stack.getCount() * selectedItem.value;
        addStackToPlayer(stack, playerInventory);
        return pointsToUse;
    }

    public static SpecialItem getRandomItem() {
        int id = RandomizerCore.RANDOM.nextInt(VALID_ITEMS.size());
        return VALID_ITEMS.get(id);
    }

    public static ItemStack getRandomItemStack() {
        int id = RandomizerCore.RANDOM.nextInt(VALID_ITEMS.size());
        return specialItemToStack(VALID_ITEMS.get(id));
    }

    public static SpecialItem getRandomItem(int points) {
        SpecialItem toReturn;
        do {
            int id = RandomizerCore.RANDOM.nextInt(VALID_ITEMS.size());
            toReturn = VALID_ITEMS.get(id);
        } while (toReturn.value > points);
        return toReturn;
    }

    public static SpecialItem getRandomSimpleItem() {
        SpecialItem item;
        do {
            item = getRandomItem();
        } while (SpecialItems.EFFECT_ITEMS.contains(item) || SpecialItems.ENCHANTABLE.contains(item.item));
        return item;
    }

    public static ItemStack specialItemToStack (SpecialItem item) {
        ItemStack stack = new ItemStack(item.item);

        if (SpecialItems.ENCHANTABLE.contains(item.item)) {
            EnchantmentGenerator.applyEnchantment(stack);
        } else if (SpecialItems.EFFECT_ITEMS.contains(item)) {
            PotionGenerator.applyEffect(stack);
        } else if (item.item == Items.WRITTEN_BOOK) {
            BookGenerator.applyPassages(stack);
        } else if (item.item == Items.FIREWORK_ROCKET) {
            FireworkGenerator.applyFirework(stack);
        } else if (item.item == Items.FIREWORK_STAR) {
            FireworkGenerator.applyFireworkStar(stack);
        } else if (item.item == Items.GOAT_HORN) {
            GoatHornGenerator.applyGoatHornSound(stack);
        }

        return stack;
    }

    private static void addStackToPlayer(ItemStack stack, Inventory inventory) {
        RandomizerCore.LOGGER.warn(String.format("Given %s to %s.",  stack.copy(), inventory.player.getDisplayName().getString()));
        if (inventory.getFreeSlot() == -1) {
            Entity itemEnt = stack.getEntityRepresentation();
            if (itemEnt != null) {
                itemEnt.setPos(inventory.player.position());
                inventory.player.level().addFreshEntity(itemEnt);
            }
        } else {
            inventory.add(stack);
        }
        RandomizerCore.incrementAmtItemsGiven();
    }
}
