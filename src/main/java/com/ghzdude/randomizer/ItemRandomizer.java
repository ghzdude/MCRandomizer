package com.ghzdude.randomizer;

import com.ghzdude.randomizer.special.item.SpecialItems;
import com.ghzdude.randomizer.util.RandomizerUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    private static final Object2IntMap<Item> VALID_ITEMS = new Object2IntOpenHashMap<>();
    private static final List<Item> ITEM_LIST = new ArrayList<>();
    private static final Object2IntMap<Item> SIMPLE_ITEMS = new Object2IntOpenHashMap<>();

    private static RandomizationMapData INSTANCE;

    public static void init(MinecraftServer server) {
        INSTANCE = RandomizationMapData.get(server, "item");

        configureValidItem(server.getWorldData().enabledFeatures());

        VALID_ITEMS.keySet().forEach(item -> {
            if (!RandomizerUtil.canEnchant(item) && !RandomizerUtil.canHaveEffect(item)) {
                SIMPLE_ITEMS.put(item, VALID_ITEMS.getInt(item));
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
                RandomizerUtil.giveMultiple(pointsToUse, inventory) :
                RandomizerUtil.giveOnce(pointsToUse, inventory);
    }

    public static int getPointValue(Item item) {
        return VALID_ITEMS.getInt(item);
    }

    public static Item getRandomItem(Random rng, int points) {
        Item toReturn;
        do {
            toReturn = RandomizerUtil.getRandomItemFrom(ITEM_LIST, rng);
        } while (getPointValue(toReturn) > points);
        return toReturn;
    }

    public static Item getRandomItem(int points) {
        return getRandomItem(RandomizerCore.unseededRNG, points);
    }

    public static ItemStack getRandomItemStack(Random rng) {
        var item = RandomizerUtil.getRandomItemFrom(ITEM_LIST, rng);
        return RandomizerUtil.itemToStack(INSTANCE.getItemFor(item));
    }

    public static List<Item> getValidItems() {
        return Collections.unmodifiableList(ITEM_LIST);
    }
}
