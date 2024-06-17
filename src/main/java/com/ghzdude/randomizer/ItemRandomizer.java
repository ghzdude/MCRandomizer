package com.ghzdude.randomizer;

import com.ghzdude.randomizer.special.generators.*;
import com.ghzdude.randomizer.special.item.SpecialItem;
import com.ghzdude.randomizer.special.item.SpecialItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/* Item Randomizer Description
 * Goal is to give the player a random item every so often DONE
 * Every so often, points are added to a counter DONE
 * over time, the amount of points gained each cycle is increased
 * those points are then used to give the player an item DONE
 * more points give bigger stacksize of item at once DONE
 * items have a defined value, otherwise stacksize is used
 */
public class ItemRandomizer {
    private static final List<SpecialItem> VALID_ITEMS = configureValidItem();
    private static final List<SpecialItem> SIMPLE_ITEMS = VALID_ITEMS.stream()
            .filter(specialItem ->
                    !SpecialItems.EFFECT_ITEMS.contains(specialItem) &&
                    !SpecialItems.ENCHANTABLE.contains(specialItem.item))
            .toList();

    private static RandomizationMapData INSTANCE;

    public static void init(DimensionDataStorage storage) {
        INSTANCE = RandomizationMapData.get(storage, "item");
    }

    private static List<SpecialItem> configureValidItem() {
        ArrayList<SpecialItem> validItems = new ArrayList<>();

        for (SpecialItem item : ForgeRegistries.ITEMS.getValues().stream().map(SpecialItem::new).toList()) {
            if (SpecialItems.BLACKLISTED_ITEMS.contains(item.item)) continue;
            SpecialItem toUpdate = item;

            if (SpecialItems.SPECIAL_ITEMS.contains(item)) {
                toUpdate = SpecialItems.SPECIAL_ITEMS.get(SpecialItems.SPECIAL_ITEMS.indexOf(item));
            } else if (SpecialItems.EFFECT_ITEMS.contains(item)) {
                toUpdate = SpecialItems.EFFECT_ITEMS.get(SpecialItems.EFFECT_ITEMS.indexOf(item));
            } else if (SpecialItems.SHULKER_BOXES.contains(toUpdate.item)) {
                toUpdate.value = 6;
            }

            validItems.add(toUpdate);
        }
        return validItems;
    }

    public static int giveRandomItem(int pointsToUse, Inventory inventory){
        if (RandomizerConfig.giveMultipleItems.get()) {
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
        SpecialItem selectedItem = getRandomSpecialItem(pointsToUse);

        ItemStack stack = specialItemToStack(selectedItem, pointsToUse);

        pointsToUse -= stack.getCount() * selectedItem.value;
        addStackToPlayer(stack, playerInventory);
        return pointsToUse;
    }

    public static SpecialItem getRandomItemFrom(List<SpecialItem> list, Random rng) {
        return list.get(rng.nextInt(list.size()));
    }

    public static SpecialItem getRandomSpecialItem(Random rng, int points) {
        SpecialItem toReturn;
        do {
            toReturn = getRandomItemFrom(VALID_ITEMS, rng);
        } while (toReturn.value > points);
        return toReturn;
    }

    public static SpecialItem getRandomSpecialItem(int points) {
        return getRandomSpecialItem(RandomizerCore.unseededRNG, points);
    }

    public static ItemStack getRandomItemStack(Random rng) {
        return itemToStack(INSTANCE.getRandomItem(rng));
    }

    public static ItemStack specialItemToStack (SpecialItem item, int points) {
        int amtToGive = Math.floorDiv(points, item.value);
        return itemToStack(item.item, amtToGive);
    }

    public static ItemStack itemToStack(Item item) {
        return itemToStack(item, 1);
    }

    public static ItemStack itemToStack(Item item, int size) {
        ItemStack stack = new ItemStack(item);
        stack.setCount(Math.min(size, stack.getMaxStackSize()));

        if (SpecialItems.ENCHANTABLE.contains(item)) {
            EnchantmentGenerator.applyEnchantment(stack);
        } else if (SpecialItems.EFFECT_ITEMS.contains(item)) {
            PotionGenerator.applyEffect(stack);
        } else if (item == Items.WRITTEN_BOOK) {
            BookGenerator.applyPassages(stack);
            stack.setCount(1);
        } else if (item == Items.FIREWORK_ROCKET) {
            FireworkGenerator.applyFirework(stack);
        } else if (item == Items.FIREWORK_STAR) {
            FireworkGenerator.applyFireworkStar(stack);
        } else if (item == Items.GOAT_HORN) {
            GoatHornGenerator.applyGoatHornSound(stack);
        }
        return stack;
    }

    private static void addStackToPlayer(ItemStack stack, Inventory inventory) {
        RandomizerCore.LOGGER.warn(String.format("Given %s to %s.",  stack.copy(), inventory.player.getDisplayName().getString()));
        if (!inventory.add(stack)) {
            inventory.player.drop(stack, false);
        }
    }

    public static List<SpecialItem> getValidItems() {
        return Collections.unmodifiableList(VALID_ITEMS);
    }
}
