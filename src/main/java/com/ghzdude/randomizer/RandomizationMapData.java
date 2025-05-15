package com.ghzdude.randomizer;

import com.ghzdude.randomizer.util.RandomizerUtil;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class RandomizationMapData extends SavedData {

    public static final RandomizationMapData VANILLA = new DefaultedMapData();


    private static final ResourceLocation AIR = ResourceLocation.parse("minecraft:air");
    private static final Random RNG = new Random();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Object2ObjectMap<ResourceLocation, ResourceLocation> ITEM_MAP = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<ResourceLocation, ResourceLocation> ITEM_MAP_REVERSE = new Object2ObjectOpenHashMap<>();

    private final Object2ObjectMap<ResourceLocation, ResourceLocation> TAGKEY_MAP = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<ResourceLocation, ResourceLocation> TAGKEY_MAP_REVERSE = new Object2ObjectOpenHashMap<>();

    private static Registry<Item> ITEM_REGISTRY;

    private boolean isLoaded = false;

    public RandomizationMapData() {
        ITEM_MAP.defaultReturnValue(AIR);
        ITEM_MAP_REVERSE.defaultReturnValue(AIR);
        TAGKEY_MAP.defaultReturnValue(AIR);
        TAGKEY_MAP_REVERSE.defaultReturnValue(AIR);
    }

    public static void init(RegistryAccess access) {
        ITEM_REGISTRY = access.registryOrThrow(Registries.ITEM);
    }

    public static Factory<RandomizationMapData> factory() {
        return new Factory<>(RandomizationMapData::new, RandomizationMapData::load, DataFixTypes.LEVEL);
    }

    public static RandomizationMapData get(DimensionDataStorage storage, String prefix) {
        RandomizationMapData data = storage.computeIfAbsent(RandomizationMapData.factory(), RandomizerCore.MODID + "_" + prefix);
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

    private static boolean isAir(ResourceLocation loc) {
        return AIR.equals(loc) || loc.getPath().isEmpty();
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        LOGGER.warn("Saving randomizations to disk!");
        CompoundTag itemMap = new CompoundTag();
        CompoundTag tagKeyMap = new CompoundTag();

        ITEM_MAP.forEach((vanilla, random) -> {
            if (isAir(vanilla) || isAir(random)) return;
            itemMap.putString(vanilla.toString(), random.toString());
        });

        TAGKEY_MAP.forEach((vanilla, random) -> {
            if (isAir(vanilla) || isAir(random)) return;
            tagKeyMap.putString(vanilla.toString(), random.toString());
        });

        tag.put("item_map", itemMap);
        tag.put("tag_key_map", tagKeyMap);
        return tag;
    }

    public static RandomizationMapData load(CompoundTag tag, HolderLookup.Provider ignored) {
        RandomizationMapData data = new RandomizationMapData();
        LOGGER.warn("Loading from disk!");

        CompoundTag itemMap = tag.getCompound("item_map");
        CompoundTag tagMap = tag.getCompound("tag_key_map");

        for (String item : itemMap.getAllKeys()) {
            ResourceLocation vanilla = ResourceLocation.parse(item);
            ResourceLocation random = ResourceLocation.parse(itemMap.getString(item));
            if (isAir(vanilla) || isAir(random)) continue;
            data.putItem(vanilla, random);
        }

        Set<ResourceLocation> loadedKeys = data.ITEM_MAP.keySet();
        Set<ResourceLocation> validKeys = ItemRandomizer.getKeys().collect(Collectors.toSet());
        validKeys.removeIf(loadedKeys::contains);
        if (!validKeys.isEmpty()) {
            logDifference(validKeys);
        }

        for (String tagKey : tagMap.getAllKeys()) {
            ResourceLocation vanilla = ResourceLocation.parse(tagKey);
            ResourceLocation random = ResourceLocation.parse(tagMap.getString(tagKey));
            if (isAir(vanilla) || isAir(random)) continue;
            data.putTag(vanilla, random);
        }

        data.getItems().stream().filter(l -> l.equals(data.getItemFor(l)))
                .forEach(RandomizationMapData::logMatchingKey);

        loadedKeys = data.TAGKEY_MAP.keySet();
        validKeys = ITEM_REGISTRY.getTagNames().map(TagKey::location).collect(Collectors.toSet());
        validKeys.removeIf(loadedKeys::contains);
        if (!validKeys.isEmpty()) {
            logDifference(validKeys);
        }

        data.getTags().stream().filter(l -> l.equals(data.getTagKeyFor(l)))
                .forEach(RandomizationMapData::logMatchingKey);

        data.setDirty();
        data.isLoaded = true;

        return data;
    }

    private void generateTagMap() {
        List<ResourceLocation> vanilla = ITEM_REGISTRY.getTagNames().map(TagKey::location).collect(Collectors.toList());

        ResourceLocation key, value, tail = vanilla.get(RNG.nextInt(1, vanilla.size()));

        while (!vanilla.isEmpty()) {
            key = vanilla.removeFirst();
            value = vanilla.isEmpty() ? tail : RandomizerUtil.getRandom(vanilla, RNG);

            putTag(key, value);
        }

        Set<ResourceLocation> loadedKeys = TAGKEY_MAP.keySet();
        Set<ResourceLocation> validKeys = ITEM_REGISTRY.getTagNames().map(TagKey::location).collect(Collectors.toSet());
        validKeys.removeIf(loadedKeys::contains);
        if (!validKeys.isEmpty()) {
            logDifference(validKeys);
        }

        TAGKEY_MAP.keySet().stream().filter(l -> l.equals(TAGKEY_MAP.get(l)))
                .forEach(RandomizationMapData::logMatchingKey);
    }

    private void generateItemMap() {
        List<ResourceLocation> vanilla = ItemRandomizer.getKeys().collect(Collectors.toList());

        ResourceLocation key, value, tail = vanilla.get(RNG.nextInt(1, vanilla.size()));

        while (!vanilla.isEmpty()) {
            key = vanilla.removeFirst();
            value = vanilla.isEmpty() ? tail : RandomizerUtil.getRandom(vanilla, RNG);

            putItem(key, value);
        }

        Set<ResourceLocation> loadedKeys = ITEM_MAP.keySet();
        Set<ResourceLocation> validKeys = ItemRandomizer.getKeys().collect(Collectors.toSet());
        validKeys.removeIf(loadedKeys::contains);
        if (!validKeys.isEmpty()) {
            logDifference(validKeys);
        }

        ITEM_MAP.keySet().stream().filter(l -> l.equals(ITEM_MAP.get(l)))
                .forEach(RandomizationMapData::logMatchingKey);
    }

    private void putItem(ResourceLocation vanilla, ResourceLocation random) {
        if (isAir(vanilla) || isAir(random)) {
            throw new IllegalArgumentException("Items cannot be air!");
        }
        ITEM_MAP.put(vanilla, random);
        ITEM_MAP_REVERSE.put(random, vanilla);
    }

    private void putTag(ResourceLocation vanilla, ResourceLocation random) {
        if (isAir(vanilla) || isAir(random)) {
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
        return ITEM_REGISTRY.get(random);
    }

    public ResourceLocation getItemFor(ResourceLocation vanilla) {
        if (isAir(vanilla)) throw new IllegalArgumentException("Cannot randomize Air!");
        if (!ITEM_MAP.containsKey(vanilla)) {
            LOGGER.warn("Item '{}' is not mapped to a random item!", vanilla);
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
        return TagKey.create(Registries.ITEM, getTagKeyFor(vanilla.location()));
    }

    public ResourceLocation getTagKeyFor(ResourceLocation vanilla) {
        return TAGKEY_MAP.get(vanilla);
    }

    @Nullable
    public TagKey<Item> getRandomTag(Random rng) {
        int s = rng.nextInt(TAGKEY_MAP.size());
        int i = 0;
        for (ResourceLocation value : TAGKEY_MAP.values()) {
            if (i++ == s) return TagKey.create(ITEM_REGISTRY.key(), value);
        }
        return null;
    }

    @Nullable
    public Item getRandomItem(Random rng) {
        int s = rng.nextInt(ITEM_MAP.size());
        int i = 0;
        for (ResourceLocation value : ITEM_MAP.values()) {
            if (i++ == s) return ITEM_REGISTRY.get(value);
        }
        return null;
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
        LOGGER.warn("Not all keys were associated with a random item/tag! Usually this means I suck at randomizing!");
        LOGGER.warn("Missed keys: {}", difference);
    }

    private static void logMatchingKey(ResourceLocation l) {
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
        public TagKey<Item> getRandomTag(Random rng) {
            return null;
        }

        @Override
        public Item getRandomItem(Random rng) {
            return null;
        }
    }
}
