package com.ghzdude.randomizer;

import com.ghzdude.randomizer.io.ConfigIO;
import com.ghzdude.randomizer.special.item.SpecialItems;
import com.ghzdude.randomizer.util.RandomizerUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.stream.Stream;

/* Item Randomizer Description
 * Goal is to give the player a random item every so often DONE
 * Every so often, points are added to a counter DONE
 * over time, the amount of points gained each cycle is increased
 * those points are then used to give the player an item DONE
 * more points give bigger stacksize of item at once DONE
 * items have a defined value, otherwise stacksize is used
 */
public class ItemRandomizer {
    private static final Object2IntMap<ResourceLocation> VALID_ITEMS = new Object2IntOpenHashMap<>();
    private static final List<ResourceLocation> ITEM_LIST = new ArrayList<>();
    private static final Object2IntMap<ResourceLocation> SIMPLE_ITEMS = new Object2IntOpenHashMap<>();
    public static final List<ResourceLocation> BLACKLISTED_ITEMS = new ArrayList<>();

    private static Registry<Item> ITEM_REGISTRY;
    private static RandomizationMapData INSTANCE;
    private static FeatureFlagSet ENABLED;

    public static void init(MinecraftServer server) {
        ITEM_REGISTRY = server.registryAccess().registryOrThrow(Registries.ITEM);
        INSTANCE = RandomizationMapData.get(server, "item");
        ENABLED = server.getWorldData().enabledFeatures();
        SpecialItems.init(ITEM_REGISTRY::getKey);

        if (BLACKLISTED_ITEMS.isEmpty()) {
            BLACKLISTED_ITEMS.addAll(ConfigIO.read("blacklisted_items", Stream.of(
                            Items.AIR,
                            Items.COMMAND_BLOCK,
                            Items.COMMAND_BLOCK_MINECART,
                            Items.CHAIN_COMMAND_BLOCK,
                            Items.REPEATING_COMMAND_BLOCK,
                            Items.BARRIER,
                            Items.LIGHT,
                            Items.STRUCTURE_BLOCK,
                            Items.STRUCTURE_VOID,
                            Items.KNOWLEDGE_BOOK,
                            Items.JIGSAW,
                            Items.DEBUG_STICK)
                    .map(ITEM_REGISTRY::getKey)
                    .filter(Objects::nonNull)
                    .toList(), ITEM_REGISTRY));
        }

        ConfigIO.readValues("items", SpecialItems.CONFIGURED_ITEMS, ITEM_REGISTRY)
                .object2IntEntrySet().forEach(ItemRandomizer::putValidItem);

        for (ResourceLocation loc : ITEM_REGISTRY.keySet()) {
            putValidItem(loc, 1);
        }

        for (ResourceLocation loc : VALID_ITEMS.keySet()) {
            var item = RandomizerUtil.getOrThrow(ITEM_REGISTRY, loc);
            if (!RandomizerUtil.canEnchant(item) && !RandomizerUtil.canHaveEffect(item)) {
                SIMPLE_ITEMS.put(loc, VALID_ITEMS.getInt(item));
            }
        }
        ITEM_LIST.addAll(VALID_ITEMS.keySet());
    }

    private static void putValidItem(Map.Entry<ResourceLocation, Integer> entry) {
        if (entry instanceof Object2IntMap.Entry<ResourceLocation> intEntry)
            putValidItem(entry.getKey(), intEntry.getIntValue());
        else putValidItem(entry.getKey(), entry.getValue());
    }

    private static void putValidItem(ResourceLocation loc, int value) {
        var item = RandomizerUtil.getOrThrow(ITEM_REGISTRY, loc);
        if (isBlacklisted(item) || VALID_ITEMS.containsKey(loc) || ENABLED == null || !item.isEnabled(ENABLED))
            return;
        VALID_ITEMS.put(loc, value);
    }

    public static int giveRandomItem(int pointsToUse, Inventory inventory) {
        inventory.player.displayClientMessage(Component.translatable("randomizer.giving_item.label"), true);
        return RandomizerConfig.giveMultipleItems ?
                RandomizerUtil.giveMultiple(pointsToUse, inventory) :
                RandomizerUtil.giveOnce(pointsToUse, inventory);
    }

    public static int getPointValue(Item item) {
        return getPointValue(ITEM_REGISTRY.getKey(item));
    }

    public static int getPointValue(ResourceLocation item) {
        return VALID_ITEMS.getInt(item);
    }

    public static Item getRandomItem(Random rng, int points) {
        ResourceLocation toReturn;
        do {
            toReturn = RandomizerUtil.getRandom(ITEM_LIST, rng);
        } while (getPointValue(toReturn) > points);
        return ITEM_REGISTRY.get(toReturn);
    }

    public static Item getRandomItem(int points) {
        return getRandomItem(RandomizerCore.unseededRNG, points);
    }

    public static ItemStack getRandomItemStack(Random rng) {
        var item = RandomizerUtil.getRandom(ITEM_LIST, rng);
        return RandomizerUtil.itemToStack(INSTANCE.getItemFor(ITEM_REGISTRY.get(item)));
    }

    public static List<ResourceLocation> getValidItems() {
        return Collections.unmodifiableList(ITEM_LIST);
    }

    private static boolean isBlacklisted(Item item) {
        return BLACKLISTED_ITEMS.contains(ITEM_REGISTRY.getKey(item));
    }
}
