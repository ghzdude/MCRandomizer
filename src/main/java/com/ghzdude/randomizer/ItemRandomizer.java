package com.ghzdude.randomizer;

import com.ghzdude.randomizer.io.ConfigIO;
import com.ghzdude.randomizer.special.item.SpecialItems;
import com.ghzdude.randomizer.util.RandomizerUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;

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
    private static final List<ResourceLocation> BLACKLISTED_ITEMS = new ArrayList<>();

    private static RandomizationMapData INSTANCE;
    private static Registry<Item> REGISTRY;
    private static FeatureFlagSet ENABLED;

    private static final String POINT_KEY = "points";
    private static final String POINT_MAX_KEY = "point_max";
    private static final String CYCLE_KEY = "cycle";
    private static final String CYCLE_COUNTER_KEY = "cycle_counter";
    private static final String AMOUNT_KEY = "amount_items_given";

    private static int OFFSET = 0;
    private static final int COUNTER_MAX = 50;

    public static void init(MinecraftServer server) {
        ITEM_LIST.clear();
        BLACKLISTED_ITEMS.clear();
        VALID_ITEMS.clear();

        REGISTRY = server.registryAccess().lookupOrThrow(Registries.ITEM);
        ENABLED = server.getWorldData().enabledFeatures();
        SpecialItems.init(REGISTRY::getKey);

        BLACKLISTED_ITEMS.addAll(ConfigIO.read("blacklisted_items", Stream.of(
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
                .map(REGISTRY::getKey)
                .filter(Objects::nonNull)
                .toList(), REGISTRY));

        // hard code air blacklist
        BLACKLISTED_ITEMS.add(REGISTRY.getKey(Items.AIR));

        ConfigIO.readValues("items", SpecialItems.CONFIGURED_ITEMS, REGISTRY)
                .object2IntEntrySet().forEach(ItemRandomizer::putValidItem);

        for (ResourceLocation loc : REGISTRY.keySet()) {
            putValidItem(loc, 1);
        }

        for (ResourceLocation loc : VALID_ITEMS.keySet()) {
            var item = RandomizerUtil.getOrThrow(REGISTRY, loc);
            if (!RandomizerUtil.canEnchant(item) && !RandomizerUtil.canHaveEffect(item)) {
                SIMPLE_ITEMS.put(loc, VALID_ITEMS.getInt(item));
            }
        }
        ITEM_LIST.addAll(VALID_ITEMS.keySet());

        INSTANCE = RandomizationMapData.get(server, "item");
    }

    private static void putValidItem(Map.Entry<ResourceLocation, Integer> entry) {
        if (entry instanceof Object2IntMap.Entry<ResourceLocation> intEntry)
            putValidItem(entry.getKey(), intEntry.getIntValue());
        else putValidItem(entry.getKey(), entry.getValue());
    }

    private static void putValidItem(ResourceLocation loc, int value) {
        var item = RandomizerUtil.getOrThrow(REGISTRY, loc);
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
        return getPointValue(REGISTRY.getKey(item));
    }

    public static int getPointValue(ResourceLocation item) {
        return VALID_ITEMS.getInt(item);
    }

    public static Item getRandomItem(Random rng, int points) {
        ResourceLocation toReturn;
        do {
            toReturn = RandomizerUtil.getRandom(ITEM_LIST, rng);
        } while (getPointValue(toReturn) > points);
        return REGISTRY.get(toReturn).orElseThrow().get();
    }

    public static Item getRandomItem(int points) {
        return getRandomItem(RandomizerCore.unseededRNG, points);
    }

    public static ItemStack getRandomItemStack(Random rng) {
        var item = RandomizerUtil.getRandom(ITEM_LIST, rng);
        return RandomizerUtil.itemToStack(INSTANCE.getItemFor(REGISTRY.get(item).orElseThrow().get()));
    }

    public static Stream<Item> getValidItems() {
        return getKeys().map(REGISTRY::get)
                .map(Optional::orElseThrow)
                .map(Holder::get);
    }

    public static Stream<ResourceLocation> getKeys() {
        return ITEM_LIST.stream();
    }

    public static boolean isBlacklisted(Item item) {
        return isBlacklisted(REGISTRY.getKey(item));
    }

    public static boolean isBlacklisted(ResourceLocation item) {
        return BLACKLISTED_ITEMS.contains(item);
    }

    public static void playerTickPre(TickEvent.PlayerTickEvent.Pre event) {
        if (!shouldTick(event)) return;

        var player = (ServerPlayer) event.player;
        var data = player.getPersistentData();

        if (shouldUsePoints(player)) {

            int pointMax = data.getInt(POINT_MAX_KEY).orElseGet(() -> {
                data.putInt(POINT_MAX_KEY, 1);
                return 1;
            });

            int points = RandomizerConfig.pointsCarryover ?
                    data.getIntOr(POINT_KEY, 0) + pointMax : pointMax;

            int pointsToUse = RandomizerCore.seededRNG.nextInt(points) + 1;
            int remaining = pointsToUse;

            if (RandomizerConfig.generateStructures && RandomizerCore.seededRNG.nextInt(100) < RandomizerConfig.structureProbability) {
                remaining = StructureRandomizer.tryPlace(pointsToUse, player.level(), player);
            } else if (RandomizerConfig.giveRandomItems) {
                remaining = ItemRandomizer.giveRandomItem(pointsToUse, player.getInventory());
            }

            // we used points, so something succeeded
            if (remaining < pointsToUse) {
                increaseCycle(player, data);
            }

            data.putInt(POINT_KEY, remaining);
        }
    }

    private static boolean shouldUsePoints(ServerPlayer player) {
        return player.gameMode.isSurvival();
    }

    private static boolean shouldTick(TickEvent.PlayerTickEvent.Pre event) {
        if (event.side.isClient()) return false;
        if (OFFSET < 0) OFFSET = 0;
        return ++OFFSET % RandomizerConfig.itemCooldown == 0;
    }

    private static void increaseCycle(Player player, CompoundTag data) {
        int pointMax = data.getIntOr(POINT_MAX_KEY, 1);
        int cycle = data.getIntOr(CYCLE_KEY, 0) + 1;
        int cycleCounter = data.getIntOr(CYCLE_COUNTER_KEY, RandomizerConfig.cycleBase);

        if (cycle % cycleCounter == 0) {
            cycle = 0;
            int i = (cycleCounter / 2) + 1;
            cycleCounter = Math.min(cycleCounter + i, COUNTER_MAX);
            pointMax++;
            player.displayClientMessage(Component.translatable("randomizer.player.point_max.increased", pointMax), false);
        }

        data.putInt(POINT_MAX_KEY, pointMax);
        data.putInt(CYCLE_KEY, cycle);
        data.putInt(CYCLE_COUNTER_KEY, cycleCounter);
    }

    public static void incrementAmtItemsGiven(Player player) {
        incrementAmtItemsGiven(player.getPersistentData());
    }

    public static void incrementAmtItemsGiven(CompoundTag data) {
        data.putInt(AMOUNT_KEY, data.getIntOr(AMOUNT_KEY, 0) + 1);
    }
}
