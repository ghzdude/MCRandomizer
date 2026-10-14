package com.ghzdude.randomizer;

import com.ghzdude.randomizer.io.ConfigIO;
import com.ghzdude.randomizer.special.generators.EnchantmentGenerator;
import com.ghzdude.randomizer.special.generators.PotionGenerator;
import com.ghzdude.randomizer.special.item.SpecialItems;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLLoader;

import java.util.*;
import java.util.function.Function;
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
    private static final Object2IntMap<Identifier> VALID_ITEMS = new Object2IntOpenHashMap<>();
    private static final List<Identifier> ITEM_LIST = new ArrayList<>();
    private static final Object2IntMap<Identifier> SIMPLE_ITEMS = new Object2IntOpenHashMap<>();
    private static final List<Identifier> BLACKLISTED_ITEMS = new ArrayList<>();
    public static final String DATA_KEY = "randomizer";

    private static RandomizationMapData INSTANCE;
    private static Registry<Item> REGISTRY;
    private static FeatureFlagSet ENABLED;

    private static int OFFSET = 0;

    static void init(MinecraftServer server) {
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
                        Items.TEST_BLOCK,
                        Items.TEST_INSTANCE_BLOCK,
                        Items.DEBUG_STICK)
                .map(REGISTRY::getKey)
                .filter(Objects::nonNull)
                .toList(), REGISTRY));

        // hard code air blacklist
        BLACKLISTED_ITEMS.add(REGISTRY.getKey(Items.AIR));

        ConfigIO.readValues("items", SpecialItems.CONFIGURED_ITEMS, REGISTRY)
                .object2IntEntrySet().forEach(ItemRandomizer::putValidItem);

        for (Identifier loc : REGISTRY.keySet()) {
            putValidItem(loc, 1);
        }

        for (Identifier loc : VALID_ITEMS.keySet()) {
            REGISTRY.get(loc).map(ItemStack::new)
                    .filter(stack -> !EnchantmentGenerator.canEnchant(stack) && !PotionGenerator.canHaveEffect(stack))
                    .map(ItemStack::getItem)
                    .ifPresent(stack -> SIMPLE_ITEMS.put(loc, VALID_ITEMS.getInt(stack)));
        }
        ITEM_LIST.addAll(VALID_ITEMS.keySet());

        INSTANCE = RandomizationMapData.get(server, "item");
    }

    private static void putValidItem(Map.Entry<Identifier, Integer> entry) {
        if (entry instanceof Object2IntMap.Entry<Identifier> intEntry)
            putValidItem(entry.getKey(), intEntry.getIntValue());
        else putValidItem(entry.getKey(), entry.getValue());
    }

    private static void putValidItem(Identifier loc, int value) {
        var item = RandomizerUtil.getOrThrow(REGISTRY, loc);
        if (isBlacklisted(item) || VALID_ITEMS.containsKey(loc) || ENABLED == null || !item.isEnabled(ENABLED))
            return;
        VALID_ITEMS.put(loc, value);
    }

    public static int giveRandomItem(int pointsToUse, Inventory inventory) {
        inventory.player.sendSystemMessage(Component.translatable("randomizer.giving_item.label"));
        return RandomizerConfig.giveMultipleItems ?
                RandomizerUtil.giveMultiple(pointsToUse, inventory) :
                RandomizerUtil.giveOnce(pointsToUse, inventory);
    }

    public static int getPointValue(Item item) {
        return getPointValue(REGISTRY.getKey(item));
    }

    public static int getPointValue(Identifier item) {
        return VALID_ITEMS.getInt(item);
    }

    public static Item getRandomItem(Random rng, int points) {
        Identifier toReturn;
        do {
            toReturn = RandomizerUtil.getRandom(ITEM_LIST, rng);
        } while (getPointValue(toReturn) > points);
        return REGISTRY.get(toReturn).orElseThrow().get();
    }

    public static Item getRandomItem(int points) {
        return getRandomItem(RandomizerCore.unseededRNG, points);
    }

    public static ItemStack getRandomItemStack(Random rng) {
        Identifier vanilla = RandomizerUtil.getRandom(ITEM_LIST, rng);
        return REGISTRY.get(vanilla)
                .map(ref -> INSTANCE.getHolderFor(ref, REGISTRY))
                .map(Holder::get)
                .map(RandomizerUtil::itemToStack)
                .orElseGet(() -> {
                    if (RandomizerConfig.enableDebug) {
                        RandomizerCore.LOGGER.warn("Failed to get random item!");
                    }
                    return ItemStack.EMPTY;
                });
    }

    public static Stream<Item> getValidItems() {
        return getKeys().map(REGISTRY::get)
                .map(Optional::orElseThrow)
                .map(Holder::get);
    }

    public static Stream<Identifier> getKeys() {
        return ITEM_LIST.stream();
    }

    public static boolean isBlacklisted(Item item) {
        return isBlacklisted(REGISTRY.getKey(item));
    }

    public static boolean isBlacklisted(Identifier item) {
        return BLACKLISTED_ITEMS.contains(item);
    }

    static void playerTickPre(TickEvent.PlayerTickEvent.Pre event) {
        if (!shouldTick(event)) return;

        var player = (ServerPlayer) event.player();

        if (shouldUsePoints(player)) {
            player.getPersistentData()
                    .getCompound(DATA_KEY)
                    .map(tag -> PlayerRandomizationData.CODEC.decode(NbtOps.INSTANCE, tag))
                    .flatMap(DataResult::result).map(Pair::getFirst)
                    .ifPresent(d -> d.execute(player));
        }
    }

    private static boolean shouldUsePoints(ServerPlayer player) {
        return player.gameMode.isSurvival() || (!FMLLoader.isProduction() && player.gameMode.isCreative());
    }

    private static boolean shouldTick(TickEvent.PlayerTickEvent.Pre event) {
        if (event.side().isClient()) return false;
        if (RandomizerConfig.giveRandomItems || RandomizerConfig.generateStructures) {
            if (OFFSET < 0) OFFSET = 0;
            return ++OFFSET % RandomizerConfig.itemCooldown == 0;
        }
        return false;
    }

    public static void incrementAmtItemsGiven(Player player) {
        incrementAmtItemsGiven(player.getPersistentData());
    }

    public static void incrementAmtItemsGiven(CompoundTag tag) {
        PlayerRandomizationData.CODEC.decode(NbtOps.INSTANCE, tag.getCompoundOrEmpty(DATA_KEY))
                .map(Pair::getFirst).map(PlayerRandomizationData::incrementGiven)
                .flatMap(data -> PlayerRandomizationData.CODEC.encodeStart(NbtOps.INSTANCE, data))
                .ifSuccess(t -> tag.put(DATA_KEY, t));
    }

    public record PlayerRandomizationData(int points, int maxPoints, int cycle, int cycleCounter, int amountGiven) {

        private static final int COUNTER_MAX = 50;

        public static final Codec<PlayerRandomizationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("points", 0).forGetter(PlayerRandomizationData::points),
                Codec.INT.optionalFieldOf("point_max", 1).forGetter(PlayerRandomizationData::maxPoints),
                Codec.INT.optionalFieldOf("cycle", 0).forGetter(PlayerRandomizationData::cycle),
                Codec.INT.optionalFieldOf("cycle_counter", -1)
                        .xmap(result -> result == -1 ? RandomizerConfig.cycleBase : result, Function.identity())
                        .forGetter(PlayerRandomizationData::cycleCounter),
                Codec.INT.optionalFieldOf("amount_items_given", 0).forGetter(PlayerRandomizationData::amountGiven)
        ).apply(instance, PlayerRandomizationData::new));
        
        public void execute(ServerPlayer player) {
            int points = RandomizerConfig.pointsCarryover ? points() + maxPoints() : maxPoints();
            int pointsToUse = RandomizerCore.seededRNG.nextInt(points) + 1;
            int remaining;

            if (RandomizerConfig.generateStructures && RandomizerCore.seededRNG.nextInt(100) < RandomizerConfig.structureProbability) {
                remaining = StructureRandomizer.tryPlace(pointsToUse, player.level(), player);
            } else if (RandomizerConfig.giveRandomItems) {
                remaining = ItemRandomizer.giveRandomItem(pointsToUse, player.getInventory());
            } else {
                return;
            }
            PlayerRandomizationData data = withPoints(remaining).increaseCycle();

            CODEC.encodeStart(NbtOps.INSTANCE, data)
                    .ifSuccess(result -> player.getPersistentData().put(DATA_KEY, result));
        }

        public PlayerRandomizationData withPoints(int points) {
            return new PlayerRandomizationData(points, maxPoints, cycle, cycleCounter, amountGiven);
        }

        public PlayerRandomizationData incrementGiven() {
            return new PlayerRandomizationData(points, maxPoints, cycle, cycleCounter, amountGiven + 1);
        }

        private PlayerRandomizationData increaseCycle() {
            int pointMax = maxPoints();
            int cycle = cycle();
            int cycleCounter = cycleCounter();

            if (cycle % cycleCounter == 0) {
                cycle = 0;
                int i = (cycleCounter / 2) + 1;
                cycleCounter = Math.min(cycleCounter + i, COUNTER_MAX);
                pointMax++;
            }
            return new PlayerRandomizationData(points, pointMax, cycle, cycleCounter, amountGiven);
        }
    }
}
