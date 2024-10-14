package com.ghzdude.randomizer;

import com.ghzdude.randomizer.special.generators.*;
import com.ghzdude.randomizer.special.item.SpecialItems;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/* Item Randomizer Description
 * Goal is to give the player a random item every so often DONE
 * Every so often, points are added to a counter DONE
 * over time, the amount of points gained each cycle is increased
 * those points are then used to give the player an item DONE
 * more points give bigger stacksize of item at once DONE
 * items have a defined value, otherwise stacksize is used
 */
public class ItemRandomizer {
    private static final Map<Item, Integer> VALID_ITEMS = new Object2IntOpenHashMap<>();
    private static final List<Item> ITEM_LIST = new ArrayList<>();
    private static final Map<Item, Integer> SIMPLE_ITEMS = new Object2IntOpenHashMap<>();

    private static RandomizationMapData INSTANCE;

    public static void init(MinecraftServer server) {
        INSTANCE = RandomizationMapData.get(server, "item");

        // TODO prevent items from disabled datapacks
        configureValidItem(server.getWorldData().enabledFeatures());

        VALID_ITEMS.forEach((item, integer) -> {
            if (!SpecialItems.EFFECT_ITEMS.containsKey(item) &&
                    !SpecialItems.ENCHANTABLE.contains(item)) {
                SIMPLE_ITEMS.put(item, integer);
            }
            ITEM_LIST.add(item);
        });
    }

    private static void configureValidItem(FeatureFlagSet flagSet) {
        for (var item : ForgeRegistries.ITEMS.getValues()) {
            if (SpecialItems.isBlacklisted(item) || !item.isEnabled(flagSet)) continue;
            int value = 1;

            if (SpecialItems.SPECIAL_ITEMS.containsKey(item)) {
                value = SpecialItems.SPECIAL_ITEMS.get(item);
            } else if (SpecialItems.EFFECT_ITEMS.containsKey(item)) {
                value = SpecialItems.EFFECT_ITEMS.get(item);
            } else if (SpecialItems.SHULKER_BOXES.contains(item)) {
                value = 6;
            }

            VALID_ITEMS.put(item, value);
        }
    }

    public static int giveRandomItem(int pointsToUse, Inventory inventory){
        return RandomizerConfig.giveMultipleItems ?
                giveMultiple(pointsToUse, inventory) :
                giveOnce(pointsToUse, inventory);
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
        Item selectedItem = getRandomItem(pointsToUse);

        ItemStack stack = specialItemToStack(selectedItem, pointsToUse);

        pointsToUse -= stack.getCount() * VALID_ITEMS.get(selectedItem);
        addStackToPlayer(stack, playerInventory);
        return pointsToUse;
    }

    public static Item getRandomItemFrom(List<Item> list, Random rng) {
        return list.get(rng.nextInt(list.size()));
    }

    public static Item getRandomItem(Random rng, int points) {
        Item toReturn;
        do {
            toReturn = getRandomItemFrom(ITEM_LIST, rng);
        } while (VALID_ITEMS.get(toReturn) > points);
        return toReturn;
    }

    public static Item getRandomItem(int points) {
        return getRandomItem(RandomizerCore.unseededRNG, points);
    }

    public static ItemStack getRandomItemStack(Random rng) {
        var item = getRandomItemFrom(ITEM_LIST, rng);
        return itemToStack(INSTANCE.getItemFor(item));
    }

    public static ItemStack specialItemToStack (Item item, int points) {
        int amtToGive = Math.floorDiv(points, VALID_ITEMS.get(item));
        return itemToStack(item, amtToGive);
    }

    public static ItemStack itemToStack(Item item) {
        return itemToStack(item, 1);
    }

    public static ItemStack itemToStack(Item item, int size) {
        ItemStack stack = new ItemStack(item);
        stack.setCount(Math.min(size, stack.getMaxStackSize()));

        if (SpecialItems.ENCHANTABLE.contains(item)) {
            EnchantmentGenerator.applyEnchantment(stack);
        } else if (SpecialItems.EFFECT_ITEMS.containsKey(item)) {
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
        RandomizerCore.LOGGER.warn("Given {} to {}.", stack.copy(), inventory.player.getName().getString());
        if (!inventory.add(stack)) {
            inventory.player.drop(stack, false);
        }
    }

    public static List<Item> getValidItems() {
        return Collections.unmodifiableList(ITEM_LIST);
    }
}
