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
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.references.BlockItemIds;
import net.minecraft.references.ItemIds;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    private static final List<SpecialItems.ValueItem> VALID_ITEMS = new ArrayList<>();
    private static final List<Identifier> ITEM_LIST = new ArrayList<>();
    private static final List<SpecialItems.ValueItem> SIMPLE_ITEMS = new ArrayList<>();
    private static final Set<ResourceKey<Item>> BLACKLISTED_ITEMS = new HashSet<>();
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
        SpecialItems.init();

        ConfigIO.ofList(ResourceKey.codec(Registries.ITEM))
                .setPath("blacklisted_items", ConfigIO.BLACKLIST_DIR)
                .read(List.of(
                        BlockItemIds.COMMAND_BLOCK.item(),
                        ItemIds.COMMAND_BLOCK_MINECART,
                        BlockItemIds.CHAIN_COMMAND_BLOCK.item(),
                        BlockItemIds.REPEATING_COMMAND_BLOCK.item(),
                        BlockItemIds.BARRIER.item(),
                        BlockItemIds.LIGHT.item(),
                        BlockItemIds.STRUCTURE_BLOCK.item(),
                        BlockItemIds.STRUCTURE_VOID.item(),
                        ItemIds.KNOWLEDGE_BOOK,
                        BlockItemIds.JIGSAW.item(),
                        BlockItemIds.TEST_BLOCK.item(),
                        BlockItemIds.TEST_INSTANCE_BLOCK.item(),
                        ItemIds.DEBUG_STICK
                ))
                .validateElements(REGISTRY::containsKey)
                .result().ifSuccess(BLACKLISTED_ITEMS::addAll);

        // hard code air blacklist
        BLACKLISTED_ITEMS.add(BlockItemIds.AIR.item());

        ConfigIO.ofList(SpecialItems.ValueItem.CODEC)
                .setPath("items", ConfigIO.VALUE_DIR)
                .read(SpecialItems.CONFIGURED_ITEMS)
                .validateElements(valueItem -> REGISTRY.containsKey(valueItem.item()))
                .result().ifSuccess(list -> list.forEach(ItemRandomizer::putValidItem));

        for (ResourceKey<Item> loc : REGISTRY.registryKeySet()) {
            putValidItem(loc, 1);
        }

        VALID_ITEMS.stream()
                .filter(item -> {
                    ItemStack stack = item.get(REGISTRY).map(ItemStack::new).orElse(ItemStack.EMPTY);
                    return !EnchantmentGenerator.canEnchant(stack) && !PotionGenerator.canHaveEffect(stack);
                })
                .forEach(SIMPLE_ITEMS::add);

        VALID_ITEMS.stream()
                .map(SpecialItems.ValueItem::item)
                .map(ResourceKey::identifier)
                .forEach(ITEM_LIST::add);

        INSTANCE = RandomizationMapData.get(server, "item");
    }

    private static void putValidItem(ResourceKey<Item> loc, int i) {
        putValidItem(new SpecialItems.ValueItem(loc, i));
    }

    private static void putValidItem(SpecialItems.ValueItem entry) {
        boolean enabled = REGISTRY.get(entry.item()).map(item -> item.value().isEnabled(ENABLED)).orElse(false);
        if (isBlacklisted(entry.item()) || VALID_ITEMS.contains(entry) || ENABLED == null || !enabled)
            return;
        VALID_ITEMS.add(entry);
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
        for (var validItem : VALID_ITEMS) {
            if (validItem.is(item)) {
                return validItem.value();
            }
        }
        return 0;
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
        return isBlacklisted(REGISTRY.getResourceKey(item).orElseThrow());
    }

    public static boolean isBlacklisted(Identifier loc) {
        return BLACKLISTED_ITEMS.stream().anyMatch(key -> key.identifier().equals(loc));
    }

    public static boolean isBlacklisted(ResourceKey<Item> item) {
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
