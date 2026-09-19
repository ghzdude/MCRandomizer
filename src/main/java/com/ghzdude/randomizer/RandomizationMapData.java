package com.ghzdude.randomizer;

import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class RandomizationMapData extends SavedData {

    public static final RandomizationMapData VANILLA = new DefaultedMapData();
    public static final Codec<RandomizationMapData> CODEC = Codec.of(
            new Encoder<>() {
                @Override
                public <T> DataResult<T> encode(RandomizationMapData randomizationMapData, DynamicOps<T> dynamicOps, T t) {
                    CompoundTag tag = randomizationMapData.save(new CompoundTag());
                    return CompoundTag.CODEC.encode(tag, dynamicOps, t);
                }
            }, new Decoder<>() {
                @Override
                public <T> DataResult<Pair<RandomizationMapData, T>> decode(DynamicOps<T> dynamicOps, T t) {
                    DataResult<Pair<CompoundTag, T>> result = CompoundTag.CODEC.decode(dynamicOps, t);
                    if (result.isSuccess()) {
                        RandomizationMapData data = RandomizationMapData.load(result.getOrThrow().getFirst());
                        return DataResult.success(Pair.of(data, t));
                    }
                    return DataResult.error(() -> "failed");
                }
            }
    );

    private static final Object2ObjectMap<Identifier, SavedDataType<RandomizationMapData>> TYPE_MAP = new Object2ObjectOpenHashMap<>();

    private static final Identifier AIR = Identifier.parse("minecraft:air");
    private static final Random RNG = new Random();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Object2ObjectMap<Identifier, Identifier> ITEM_MAP = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<Identifier, Identifier> ITEM_MAP_REVERSE = new Object2ObjectOpenHashMap<>();

    private final Object2ObjectMap<Identifier, Identifier> TAGKEY_MAP = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<Identifier, Identifier> TAGKEY_MAP_REVERSE = new Object2ObjectOpenHashMap<>();

    private static Registry<Item> ITEM_REGISTRY;

    private boolean isLoaded = false;

    public RandomizationMapData() {
        ITEM_MAP.defaultReturnValue(AIR);
        ITEM_MAP_REVERSE.defaultReturnValue(AIR);
        TAGKEY_MAP.defaultReturnValue(AIR);
        TAGKEY_MAP_REVERSE.defaultReturnValue(AIR);
    }

    static void init(RegistryAccess access) {
        ITEM_REGISTRY = access.lookupOrThrow(Registries.ITEM);
    }

    public static RandomizationMapData get(SavedDataStorage storage, String prefix) {
        Identifier name = Identifier.fromNamespaceAndPath(RandomizerCore.MODID, prefix);
        SavedDataType<RandomizationMapData> type = TYPE_MAP.computeIfAbsent(name, k -> new SavedDataType<>(name, RandomizationMapData::new, RandomizationMapData.CODEC, DataFixTypes.LEVEL));
        RandomizationMapData data = storage.computeIfAbsent(type);
        if (!data.isLoaded()) {
            data.generateItemMap();
            data.generateTagMap();
            data.setDirty();
            data.isLoaded = true;
        }
        return data;
    }

    public static RandomizationMapData get(MinecraftServer server, String prefix) {
        return get(server.overworld().getDataStorage(),  prefix);
    }

    public static RandomizationMapData get(ServerLevel serverLevel, String prefix) {
        return get(serverLevel.getServer(), prefix);
    }

    private static boolean isInvalid(Identifier loc) {
        return ItemRandomizer.isBlacklisted(loc) || loc.getPath().isEmpty();
    }

    public @NotNull CompoundTag save(CompoundTag tag) {
        LOGGER.warn("Saving randomizations to disk!");
        CompoundTag itemMap = new CompoundTag();
        CompoundTag tagKeyMap = new CompoundTag();

        ITEM_MAP.forEach((vanilla, random) -> {
            if (isInvalid(vanilla) || isInvalid(random)) return;
            itemMap.putString(vanilla.toString(), random.toString());
        });

        TAGKEY_MAP.forEach((vanilla, random) -> {
            if (isInvalid(vanilla) || isInvalid(random)) return;
            tagKeyMap.putString(vanilla.toString(), random.toString());
        });

        tag.put("item_map", itemMap);
        tag.put("tag_key_map", tagKeyMap);
        return tag;
    }

    public static RandomizationMapData load(CompoundTag tag) {
        RandomizationMapData data = new RandomizationMapData();
        LOGGER.warn("Loading from disk!");

        CompoundTag itemMap = tag.getCompoundOrEmpty("item_map");
        CompoundTag tagMap = tag.getCompoundOrEmpty("tag_key_map");

        Set<Identifier> loadedKeys;
        Set<Identifier> validKeys;
        Sets.SetView<Identifier> difference;

        for (String item : itemMap.keySet()) {
            Identifier vanilla = Identifier.parse(item);
            Optional<Identifier> random = itemMap.getString(item).map(Identifier::tryParse);
            if (random.isEmpty() || isInvalid(vanilla) || isInvalid(random.get())) continue;
            data.putItem(vanilla, random.get());
        }

        loadedKeys = data.ITEM_MAP.keySet();
        validKeys = ItemRandomizer.getKeys().collect(Collectors.toSet());
        difference = Sets.difference(validKeys, loadedKeys);

        if (!difference.isEmpty()) {
            // randomize missing keys
            generateMap(difference, data::putItem);
            logDifference(difference);
        }

        for (String tagKey : tagMap.keySet()) {
            Identifier vanilla = Identifier.parse(tagKey);
            Optional<Identifier> random = tagMap.getString(tagKey).map(Identifier::tryParse);
            if (random.isEmpty() || isInvalid(vanilla) || isInvalid(random.get())) continue;
            data.putTag(vanilla, random.get());
        }

        data.getItems().stream().filter(l -> l.equals(data.getItemFor(l)))
                .forEach(RandomizationMapData::logMatchingKey);

            loadedKeys = data.TAGKEY_MAP.keySet();
            validKeys = ITEM_REGISTRY.getTags().map(HolderSet.Named::key)
                    .map(TagKey::location).collect(Collectors.toSet());
            difference = Sets.difference(validKeys, loadedKeys);

            if (!difference.isEmpty()) {
                generateMap(difference, data::putTag);
                logDifference(difference);
            }

            data.getTags().stream().filter(l -> l.equals(data.getTagKeyFor(l)))
                    .forEach(RandomizationMapData::logMatchingKey);

        data.setDirty();
        data.isLoaded = true;

        return data;
    }

    private static void generateMap(Set<Identifier> vanilla, BiConsumer<Identifier, Identifier> putItem) {
        generateMap(new ArrayList<>(vanilla), putItem);
    }

    private void generateTagMap() {
        List<Identifier> vanilla = ITEM_REGISTRY.getTags()
                .map(HolderSet.Named::key).map(TagKey::location).collect(Collectors.toList());

        generateMap(vanilla, this::putTag);

        Set<Identifier> loadedKeys = TAGKEY_MAP.keySet();
        Set<Identifier> validKeys = ITEM_REGISTRY.getTags()
                .map(HolderSet.Named::key).map(TagKey::location).collect(Collectors.toSet());
        validKeys.removeIf(loadedKeys::contains);
        if (!validKeys.isEmpty()) {
            logDifference(validKeys);
        }

        TAGKEY_MAP.keySet().stream().filter(l -> l.equals(TAGKEY_MAP.get(l)))
                .forEach(RandomizationMapData::logMatchingKey);
    }

    private void generateItemMap() {
        List<Identifier> vanilla = ItemRandomizer.getKeys().collect(Collectors.toList());

        generateMap(vanilla, this::putItem);

        Set<Identifier> loadedKeys = ITEM_MAP.keySet();
        Set<Identifier> validKeys = ItemRandomizer.getKeys().collect(Collectors.toSet());
        validKeys.removeIf(loadedKeys::contains);
        if (!validKeys.isEmpty()) {
            logDifference(validKeys);
        }

        ITEM_MAP.keySet().stream().filter(l -> l.equals(ITEM_MAP.get(l)))
                .forEach(RandomizationMapData::logMatchingKey);
    }

    private static void generateMap(List<Identifier> vanilla, BiConsumer<Identifier, Identifier> biConsumer) {
        if (vanilla.size() == 1) {
            // need to inject single element somehow
            return;
        }
        Identifier key, value, tail = vanilla.get(RNG.nextInt(1, vanilla.size()));

        while (!vanilla.isEmpty()) {
            key = vanilla.removeFirst();
            value = vanilla.isEmpty() ? tail : RandomizerUtil.getRandom(vanilla, RNG);

            biConsumer.accept(key, value);
        }
    }

    private void putItem(Identifier vanilla, Identifier random) {
        if (isInvalid(vanilla) || isInvalid(random)) {
            LOGGER.warn("Invalid mapping: [{}:{}]", vanilla, random);
            return;
        }
        ITEM_MAP.put(vanilla, random);
        ITEM_MAP_REVERSE.put(random, vanilla);
    }

    private void putTag(Identifier vanilla, Identifier random) {
        if (isInvalid(vanilla) || isInvalid(random)) {
            throw new IllegalArgumentException("Tags cannot be air!");
        }
        TAGKEY_MAP.put(vanilla, random);
        TAGKEY_MAP_REVERSE.put(random, vanilla);
    }

    public ItemStack getStackFor(ItemStack stack) {
        return getStackFor(stack.getItem(), stack.getCount());
    }

    public ItemStack getStackFor(Item vanilla, int count) {
        Item randomItem = getItemFor(vanilla);
        if (randomItem == Items.AIR || count < 1) {
            // cannot return empty
            return new ItemStack(vanilla, Math.max(count, 1));
        }

        ItemStack random = new ItemStack(randomItem);
        random.setCount(Math.min(random.getMaxStackSize(), count));
        return random;
    }

    public Item getItemFor(Item item) {
        Identifier vanilla = Objects.requireNonNull(ITEM_REGISTRY.getKey(item));
        Identifier random = getItemFor(vanilla);
        return ITEM_REGISTRY.get(random).map(Holder::get).orElseGet(() -> {
            LOGGER.warn("failed to get item for {}", item);
            return item;
        });
    }

    public Identifier getItemFor(Identifier vanilla) {
        if (isInvalid(vanilla)) throw new IllegalArgumentException("Cannot randomize Air!");
        if (!ITEM_MAP.containsKey(vanilla)) {
            LOGGER.warn("Item '{}' is not mapped to a random item!", vanilla);
            return vanilla;
        }
        return ITEM_MAP.get(vanilla);
    }

    public Identifier getOriginalItem(Identifier random) {
        return ITEM_MAP_REVERSE.get(random);
    }

    public TagKey<Item> getOriginalTagKey(TagKey<Item> random) {
        return TagKey.create(Registries.ITEM, getOriginalTagKey(random.location()));
    }

    public Identifier getOriginalTagKey(Identifier random) {
        return TAGKEY_MAP_REVERSE.get(random);
    }

    public TagKey<Item> getTagKeyFor(TagKey<Item> vanilla) {
        return TagKey.create(Registries.ITEM, getTagKeyFor(vanilla.location()));
    }

    public Identifier getTagKeyFor(Identifier vanilla) {
        return TAGKEY_MAP.get(vanilla);
    }

    @Nullable
    public TagKey<Item> getRandomTag(Random rng) {
        int s = rng.nextInt(TAGKEY_MAP.size());
        int i = 0;
        for (Identifier value : TAGKEY_MAP.values()) {
            if (i++ == s) return TagKey.create(ITEM_REGISTRY.key(), value);
        }
        return null;
    }

    @Nullable
    public Item getRandomItem(Random rng) {
        int s = rng.nextInt(ITEM_MAP.size());
        int i = 0;
        for (Identifier value : ITEM_MAP.values()) {
            if (i++ == s) return ITEM_REGISTRY.get(value).orElseThrow().get();
        }
        return null;
    }

    public Set<Identifier> getItems() {
        return ITEM_MAP.keySet();
    }

    public Set<Identifier> getTags() {
        return TAGKEY_MAP.keySet();
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    private static void logDifference(Set<Identifier> difference) {
        if (difference.isEmpty()) return;
        LOGGER.warn("Not all keys were associated with a random item/tag!");
        LOGGER.warn("Missed keys: {}", difference);
    }

    private static void logMatchingKey(Identifier l) {
        LOGGER.warn("The key '{}' has been mapped to itself!", l);
    }

    private static class DefaultedMapData extends RandomizationMapData {

        public DefaultedMapData() {
            super();
        }

        @Override
        public boolean isLoaded() {
            return true;
        }

        @Override
        public ItemStack getStackFor(Item vanilla, int count) {
            return new ItemStack(vanilla, count);
        }

        @Override
        public ItemStack getStackFor(ItemStack stack) {
            return stack;
        }

        @Override
        public Item getItemFor(Item item) {
            return item;
        }

        @Override
        public Identifier getItemFor(Identifier vanilla) {
            return vanilla;
        }

        @Override
        public Identifier getOriginalItem(Identifier random) {
            return random;
        }

        @Override
        public Identifier getOriginalTagKey(Identifier random) {
            return random;
        }

        @Override
        public TagKey<Item> getTagKeyFor(TagKey<Item> vanilla) {
            return vanilla;
        }

        @Override
        public TagKey<Item> getRandomTag(Random rng) {
            return null;
        }

        @Override
        public Item getRandomItem(Random rng) {
            return null;
        }
    }
}
