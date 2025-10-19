package com.ghzdude.randomizer;

import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class RandomizationMapData extends SavedData {

    public static final RandomizationMapData VANILLA = new DefaultedMapData();

    private static final Object2ObjectMap<String, SavedDataType<RandomizationMapData>> TYPE_MAP = new Object2ObjectOpenHashMap<>();

    private static final Random RNG = new Random();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Logger logger;

    private final Object2ObjectMap<ResourceLocation, ResourceLocation> ITEM_MAP = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<ResourceLocation, ResourceLocation> ITEM_MAP_REVERSE = new Object2ObjectOpenHashMap<>();

    private final Object2ObjectMap<ResourceLocation, ResourceLocation> TAGKEY_MAP = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<ResourceLocation, ResourceLocation> TAGKEY_MAP_REVERSE = new Object2ObjectOpenHashMap<>();

    private static Registry<Item> ITEM_REGISTRY;

    private boolean isLoaded = false;

    private RandomizationMapData(String name) {
        logger = LoggerFactory.getLogger("%s:%s".formatted(getClass().getName(), name));
    }

    static void init(RegistryAccess access) {
        ITEM_REGISTRY = access.lookupOrThrow(Registries.ITEM);
    }

    public static RandomizationMapData get(DimensionDataStorage storage, String prefix) {
        String name = RandomizerCore.MODID + "_" + prefix;
        SavedDataType<RandomizationMapData> type = TYPE_MAP.computeIfAbsent(name,
                (String k) -> new SavedDataType<>(k,
                        () -> new RandomizationMapData(prefix),
                        codec(prefix),
                        DataFixTypes.LEVEL));
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

    private void generateTagMap() {
        List<ResourceLocation> vanilla = ITEM_REGISTRY.getTags()
                .map(HolderSet.Named::key).map(TagKey::location).collect(Collectors.toList());

        generateMap(vanilla, true);

        Set<ResourceLocation> loadedKeys = TAGKEY_MAP.keySet();
        Set<ResourceLocation> validKeys = ITEM_REGISTRY.getTags()
                .map(HolderSet.Named::key).map(TagKey::location).collect(Collectors.toSet());
        validKeys.removeIf(loadedKeys::contains);
        if (!validKeys.isEmpty()) {
            logDifference(validKeys);
        }

        TAGKEY_MAP.keySet().stream().filter(l -> l.equals(TAGKEY_MAP.get(l)))
                .forEach(RandomizationMapData::logMatchingKey);
    }

    private void generateItemMap() {
        List<ResourceLocation> vanilla = ItemRandomizer.getKeys().collect(Collectors.toList());

        generateMap(vanilla, false);

        Set<ResourceLocation> loadedKeys = ITEM_MAP.keySet();
        Set<ResourceLocation> validKeys = ItemRandomizer.getKeys().collect(Collectors.toSet());
        validKeys.removeIf(loadedKeys::contains);
        if (!validKeys.isEmpty()) {
            logDifference(validKeys);
        }

        ITEM_MAP.keySet().stream().filter(l -> l.equals(ITEM_MAP.get(l)))
                .forEach(RandomizationMapData::logMatchingKey);
    }

    private static Codec<RandomizationMapData> codec(String prefix) {
        return CompoundTag.CODEC.xmap(tag -> load(tag, prefix), RandomizationMapData::save);
    }

    private static boolean isInvalid(ResourceLocation loc) {
        return ItemRandomizer.isBlacklisted(loc) || loc.getPath().isEmpty();
    }

    public @NotNull CompoundTag save() {
        return save(new CompoundTag());
    }

    public @NotNull CompoundTag save(CompoundTag tag) {
        logger.warn("Saving randomizations to disk!");
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

    public static RandomizationMapData load(CompoundTag tag, String prefix) {
        RandomizationMapData data = new RandomizationMapData(prefix);
        data.logger.warn("Loading from disk!");

        CompoundTag itemMap = tag.getCompoundOrEmpty("item_map");
        data.loadItems(itemMap);

        CompoundTag tagMap = tag.getCompoundOrEmpty("tag_key_map");
        data.loadTags(tagMap);

        data.setDirty();
        data.isLoaded = true;

        return data;
    }

    private void loadItems(CompoundTag compoundTag) {
        loadKeys(compoundTag, false);
    }

    private void loadTags(CompoundTag compoundTag) {
        loadKeys(compoundTag, true);
    }

    private void loadKeys(CompoundTag compoundTag, boolean isTag) {
        for (String item : compoundTag.keySet()) {
            ResourceLocation vanilla = ResourceLocation.parse(item);
            Optional<ResourceLocation> random = compoundTag.getString(item).map(ResourceLocation::tryParse);
            if (random.isEmpty() || isInvalid(vanilla) || isInvalid(random.get())) continue;

            if (isTag) putTag(vanilla, random.get());
            else putItem(vanilla, random.get());
        }

        Set<ResourceLocation> loadedKeys = ITEM_MAP.keySet();
        Set<ResourceLocation> validKeys = ItemRandomizer.getKeys().collect(Collectors.toSet());
        Sets.SetView<ResourceLocation> difference = Sets.difference(validKeys, loadedKeys);

        if (!difference.isEmpty()) {
            // randomize missing keys
            generateMap(difference, isTag);
            logDifference(difference);
        }
    }

    private void generateMap(Set<ResourceLocation> vanilla, boolean isTag) {
        generateMap(new ArrayList<>(vanilla), isTag);
    }

    private void generateMap(List<ResourceLocation> vanilla, boolean isTag) {
        if (vanilla.size() == 1) {
            // need to inject single element somehow
            // this is really ugly
            injectSingleElement(vanilla.getFirst(), isTag);
            return;
        }
        ResourceLocation key, value, tail = vanilla.get(RNG.nextInt(1, vanilla.size()));

        while (!vanilla.isEmpty()) {
            key = vanilla.removeFirst();
            value = vanilla.isEmpty() ? tail : RandomizerUtil.getRandom(vanilla, RNG);

            if (isTag) putTag(key, value);
            else putItem(key, value);
        }
    }

    private void injectSingleElement(ResourceLocation injected, boolean isTag) {
        if (isTag) {
            ResourceLocation randomTag = getRandomTag(RNG);
            ResourceLocation tagKeyFor = getTagKeyFor(randomTag);
            putTag(randomTag, injected);
            putTag(injected, tagKeyFor);
        } else {
            ResourceLocation randomItem = getRandomItem(RNG);
            ResourceLocation itemFor = getItemFor(randomItem);
            putItem(randomItem, injected);
            putItem(injected, itemFor);
        }
    }

    private void putItem(ResourceLocation vanilla, ResourceLocation random) {
        if (isInvalid(vanilla) || isInvalid(random)) {
            logger.warn("Invalid mapping: [{}:{}]", vanilla, random);
            return;
        }
        ITEM_MAP.put(vanilla, random);
        ITEM_MAP_REVERSE.put(random, vanilla);
    }

    private void putTag(ResourceLocation vanilla, ResourceLocation random) {
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
        ResourceLocation vanilla = Objects.requireNonNull(ITEM_REGISTRY.getKey(item));
        ResourceLocation random = getItemFor(vanilla);
        return ITEM_REGISTRY.get(random).map(Holder::get).orElseGet(() -> {
            logger.warn("failed to get item for {}", item);
            return item;
        });
    }

    public ResourceLocation getItemFor(ResourceLocation vanilla) {
        if (isInvalid(vanilla)) throw new IllegalArgumentException("Cannot randomize Air!");
        if (!ITEM_MAP.containsKey(vanilla)) {
            logger.warn("Item '{}' is not mapped to a random item!", vanilla);
            return vanilla;
        }
        return ITEM_MAP.get(vanilla);
    }

    public ResourceLocation getOriginalItem(ResourceLocation random) {
        return ITEM_MAP_REVERSE.get(random);
    }

    public TagKey<Item> getOriginalTagKey(TagKey<Item> random) {
        return TagKey.create(Registries.ITEM, getOriginalTagKey(random.location()));
    }

    public ResourceLocation getOriginalTagKey(ResourceLocation random) {
        return TAGKEY_MAP_REVERSE.get(random);
    }

    public TagKey<Item> getTagKeyFor(TagKey<Item> vanilla) {
        return ItemTags.create(getTagKeyFor(vanilla.location()));
    }

    public ResourceLocation getTagKeyFor(ResourceLocation vanilla) {
        return TAGKEY_MAP.get(vanilla);
    }

    @Nullable
    public ResourceLocation getRandomTag(Random rng) {
        ResourceLocation[] locs = getTags().toArray(ResourceLocation[]::new);
        return locs[rng.nextInt(locs.length)];
    }

    @Nullable
    public ResourceLocation getRandomItem(Random rng) {
        ResourceLocation[] locs = getItems().toArray(ResourceLocation[]::new);
        return locs[rng.nextInt(locs.length)];
    }

    public Set<ResourceLocation> getItems() {
        return ITEM_MAP.keySet();
    }

    public Set<ResourceLocation> getTags() {
        return TAGKEY_MAP.keySet();
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    private static void logDifference(Set<ResourceLocation> difference) {
        if (difference.isEmpty()) return;
        LOGGER.warn("Not all keys were associated with a random item/tag!");
        LOGGER.warn("Missed keys: {}", difference);
    }

    private static void logMatchingKey(ResourceLocation l) {
        LOGGER.warn("The key '{}' has been mapped to itself!", l);
    }

    private static class DefaultedMapData extends RandomizationMapData {

        private DefaultedMapData() {
            super("vanilla");
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
        public ResourceLocation getItemFor(ResourceLocation vanilla) {
            return vanilla;
        }

        @Override
        public ResourceLocation getOriginalItem(ResourceLocation random) {
            return random;
        }

        @Override
        public ResourceLocation getOriginalTagKey(ResourceLocation random) {
            return random;
        }

        @Override
        public TagKey<Item> getTagKeyFor(TagKey<Item> vanilla) {
            return vanilla;
        }

        @Override
        public @Nullable ResourceLocation getRandomTag(Random rng) {
            return null;
        }

        @Override
        public @Nullable ResourceLocation getRandomItem(Random rng) {
            return null;
        }
    }
}
