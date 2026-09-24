package com.ghzdude.randomizer;

import com.ghzdude.randomizer.api.StackMutator;
import com.ghzdude.randomizer.api.TagKeyMutator;
import com.ghzdude.randomizer.util.KeyFactory;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class RandomizationMapData extends SavedData implements StackMutator, TagKeyMutator {

    public static final RandomizationMapData VANILLA = new DefaultedMapData();
    public static final Codec<RandomizationMapData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(Identifier.CODEC, Identifier.CODEC)
                    .fieldOf("item_map").forGetter(data -> data.itemMap),
            Codec.unboundedMap(Identifier.CODEC, Identifier.CODEC)
                    .fieldOf("tag_map").forGetter(data -> data.tagkeyMap)
    ).apply(inst, RandomizationMapData::new));

    private static final Object2ObjectMap<Identifier, SavedDataType<RandomizationMapData>> TYPE_MAP = new Object2ObjectOpenHashMap<>();

    private static final Identifier AIR = Identifier.parse("minecraft:air");
    private static final Random RNG = new Random();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Object2ObjectMap<Identifier, Identifier> itemMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<Identifier, Identifier> reversedItemMap = new Object2ObjectOpenHashMap<>();

    private final Object2ObjectMap<Identifier, Identifier> tagkeyMap = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<Identifier, Identifier> reversedTagkeyMap = new Object2ObjectOpenHashMap<>();

    private static Registry<Item> ITEM_REGISTRY;

    private boolean isLoaded = false;

    public RandomizationMapData() {
        itemMap.defaultReturnValue(AIR);
        reversedItemMap.defaultReturnValue(AIR);
        tagkeyMap.defaultReturnValue(AIR);
        reversedTagkeyMap.defaultReturnValue(AIR);
    }

    private RandomizationMapData(Map<Identifier, Identifier> itemMap, Map<Identifier, Identifier> tagMap) {
        this();
        //todo check maps
        for (Identifier vanilla : itemMap.keySet()) {
            putItem(vanilla, itemMap.get(vanilla));
        }
        for (Identifier vanilla : tagMap.keySet()) {
            putTag(vanilla, tagMap.get(vanilla));
        }
    }

    static void init(RegistryAccess access) {
        ITEM_REGISTRY = access.lookupOrThrow(Registries.ITEM);
    }

    public static RandomizationMapData get(SavedDataStorage storage, String prefix) {
        Identifier name = Identifier.fromNamespaceAndPath(RandomizerCore.MODID, prefix);
        //noinspection DataFlowIssue
        SavedDataType<RandomizationMapData> type = TYPE_MAP.computeIfAbsent(name,
                k -> new SavedDataType<>(name, RandomizationMapData::new, RandomizationMapData.CODEC, null));
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

    private void generateTagMap() {
        List<Identifier> vanilla = ITEM_REGISTRY.getTags()
                .map(HolderSet.Named::key).map(TagKey::location).collect(Collectors.toList());

        generateMap(vanilla, this::putTag);

        Set<Identifier> loadedKeys = tagkeyMap.keySet();
        Set<Identifier> validKeys = ITEM_REGISTRY.getTags()
                .map(HolderSet.Named::key).map(TagKey::location).collect(Collectors.toSet());
        validKeys.removeIf(loadedKeys::contains);
        if (!validKeys.isEmpty()) {
            logDifference(validKeys);
        }

        tagkeyMap.keySet().stream().filter(l -> l.equals(tagkeyMap.get(l)))
                .forEach(RandomizationMapData::logMatchingKey);
    }

    private void generateItemMap() {
        List<Identifier> vanilla = ItemRandomizer.getKeys().collect(Collectors.toList());

        generateMap(vanilla, this::putItem);

        Set<Identifier> loadedKeys = itemMap.keySet();
        Set<Identifier> validKeys = ItemRandomizer.getKeys().collect(Collectors.toSet());
        validKeys.removeIf(loadedKeys::contains);
        if (!validKeys.isEmpty()) {
            logDifference(validKeys);
        }

        itemMap.keySet().stream().filter(l -> l.equals(itemMap.get(l)))
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
            LOGGER.warn("Invalid item mapping: [{}:{}]", vanilla, random);
            return;
        }
        itemMap.put(vanilla, random);
        reversedItemMap.put(random, vanilla);
    }

    private void putTag(Identifier vanilla, Identifier random) {
        if (isInvalid(vanilla) || isInvalid(random)) {
            LOGGER.warn("Invalid tag mapping: [{}:{}]", vanilla, random);
            return;
        }
        tagkeyMap.put(vanilla, random);
        reversedTagkeyMap.put(random, vanilla);
    }

    @Override
    public Identifier getItemIdFor(Identifier id) {
        return itemMap.getOrDefault(id, id);
    }

    @Override
    public Identifier getTagIdFor(Identifier tag) {
        return tagkeyMap.getOrDefault(tag, tag);
    }

    public Identifier getOriginalItem(Identifier random) {
        return reversedItemMap.get(random);
    }

    public TagKey<Item> getOriginalTagKey(TagKey<Item> random) {
        return TagKey.create(Registries.ITEM, getOriginalTagKey(random.location()));
    }

    public Identifier getOriginalTagKey(Identifier random) {
        return reversedTagkeyMap.get(random);
    }

    @Nullable
    public TagKey<Item> getRandomTag(Random rng) {
        int s = rng.nextInt(tagkeyMap.size());
        int i = 0;
        for (Identifier value : tagkeyMap.values()) {
            if (i++ == s) return KeyFactory.itemTag(value);
        }
        return null;
    }

    @Nullable
    public Item getRandomItem(Random rng) {
        int s = rng.nextInt(itemMap.size());
        int i = 0;
        for (Identifier value : itemMap.values()) {
            if (i++ == s) return ITEM_REGISTRY.get(value).orElseThrow().get();
        }
        return null;
    }

    public Set<Identifier> getItems() {
        return itemMap.keySet();
    }

    public Set<Identifier> getTags() {
        return tagkeyMap.keySet();
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
        public Identifier getItemIdFor(Identifier id) {
            return id;
        }

        @Override
        public Identifier getTagIdFor(Identifier tag) {
            return tag;
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
        public TagKey<Item> getRandomTag(Random rng) {
            return null;
        }

        @Override
        public Item getRandomItem(Random rng) {
            return null;
        }
    }
}
