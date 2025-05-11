package com.ghzdude.randomizer;

import com.google.common.collect.Lists;
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
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class RandomizationMapData extends SavedData {

    public static final RandomizationMapData VANILLA = new DefaultedMapData();

    private static final ResourceLocation AIR = ResourceLocation.parse("minecraft:air");
    private static final Random RNG = new Random();

    private final Object2ObjectMap<ResourceLocation, ResourceLocation> ITEM_MAP = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<ResourceLocation, ResourceLocation> ITEM_MAP_REVERSE = new Object2ObjectOpenHashMap<>();

    private final Object2ObjectMap<ResourceLocation, ResourceLocation> TAGKEY_MAP = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<ResourceLocation, ResourceLocation> TAGKEY_MAP_REVERSE = new Object2ObjectOpenHashMap<>();

    @Nullable
    private final Registry<Item> ITEM_REGISTRY;

    private boolean isLoaded = false;

    public RandomizationMapData(@Nullable RegistryAccess access) {
        ITEM_REGISTRY = access != null ? access.registryOrThrow(Registries.ITEM) : null;

        ITEM_MAP.defaultReturnValue(AIR);
        ITEM_MAP_REVERSE.defaultReturnValue(AIR);

        TAGKEY_MAP.defaultReturnValue(AIR);
        TAGKEY_MAP_REVERSE.defaultReturnValue(AIR);
    }

    public static Factory<RandomizationMapData> factory(RegistryAccess access) {
        return new Factory<>(() -> new RandomizationMapData(access), RandomizationMapData::load, DataFixTypes.LEVEL);
    }

    public static RandomizationMapData get(DimensionDataStorage storage, RegistryAccess access, String prefix) {
        RandomizationMapData data = storage.computeIfAbsent(RandomizationMapData.factory(access), RandomizerCore.MODID + "_" + prefix);
        if (!data.isLoaded()) {
            data.generateItemMap();
            data.generateTagMap();
            data.setDirty();
            data.isLoaded = true;
        }
        return data;
    }

    public static RandomizationMapData get(MinecraftServer server, String prefix) {
        return get(server.overworld().getDataStorage(), server.registryAccess(),  prefix);
    }

    public static RandomizationMapData get(ServerLevel serverLevel, String prefix) {
        return get(serverLevel.getServer(), prefix);
    }

    private static boolean isAir(ResourceLocation loc) {
        return AIR.equals(loc);
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        RandomizerCore.LOGGER.warn("Saving randomizations to disk!");
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

    public static RandomizationMapData load(CompoundTag tag, HolderLookup.Provider provider) {
        if (!(provider instanceof RegistryAccess access)) return VANILLA;
        Registry<Item> itemRegistry = access.registryOrThrow(Registries.ITEM);
        RandomizationMapData data = new RandomizationMapData(access);
        RandomizerCore.LOGGER.warn("Loading from disk!");

        CompoundTag itemMap = tag.getCompound("item_map");
        CompoundTag tagMap = tag.getCompound("tag_key_map");

        ItemRandomizer.getValidItems().forEach(item -> {
            ResourceLocation vanilla = Objects.requireNonNull(itemRegistry.getKey(item));
            ResourceLocation random = ResourceLocation.parse(itemMap.getString(vanilla.toString()));
            if (isAir(random)) return;
            data.putItem(vanilla, random);
        });

        itemRegistry.getTagNames().map(TagKey::location).forEach(vanilla -> {
            ResourceLocation rand = ResourceLocation.parse(tagMap.getString(vanilla.toString()));
            if (isAir(rand)) return;
            data.putTag(vanilla, rand);
        });

        data.setDirty();
        data.isLoaded = true;

        return data;
    }

    private void generateTagMap() {
        List<ResourceLocation> vanilla = ITEM_REGISTRY.getTagNames().map(TagKey::location).collect(Collectors.toList());
        List<ResourceLocation> copy = Lists.newArrayList(vanilla);

        for (ResourceLocation key : vanilla) {
            int selection = RNG.nextInt(copy.size());

            ResourceLocation value = copy.remove(selection);
            vanilla.remove(value);

            TAGKEY_MAP.put(key, value);
            TAGKEY_MAP_REVERSE.put(value, key);
        }
    }

    private void generateItemMap() {
        List<ResourceLocation> vanilla = Lists.newArrayList(ItemRandomizer.getValidItems().stream().map(ITEM_REGISTRY::getKey).toList());
        List<ResourceLocation> copy = Lists.newArrayList(vanilla);

        for (ResourceLocation key : vanilla) {
            int selection = RNG.nextInt(copy.size());

            ResourceLocation value = copy.remove(selection);
            vanilla.remove(value);

            ITEM_MAP.put(key, value);
            ITEM_MAP_REVERSE.put(value, key);
        }
    }

    private void putItem(ResourceLocation vanilla, ResourceLocation random) {
        ITEM_MAP.put(vanilla, random);
    }

    private void putTag(ResourceLocation vanilla, ResourceLocation random) {
        TAGKEY_MAP.put(vanilla, random);
    }

    public ItemStack getStackFor(ItemStack stack) {
        return getStackFor(stack.getItem(), stack.getCount());
    }

    public ItemStack getStackFor(Item vanilla, int count) {
        Item randomItem = getItemFor(vanilla);
        if (randomItem == null || count < 1) {
            // cannot return empty
            return new ItemStack(vanilla, Math.max(count, 1));
        }

        ItemStack random = new ItemStack(randomItem);
        random.setCount(Math.min(random.getMaxStackSize(), count));
        return random;
    }

    public Item getItemFor(Item item) {
        ResourceLocation vanilla = ForgeRegistries.ITEMS.getKey(item);
        if (vanilla == null || isAir(vanilla)) throw new IllegalArgumentException("Cannot randomize Air!");
        return ForgeRegistries.ITEMS.getValue(ITEM_MAP.get(vanilla));
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

    private static class DefaultedMapData extends RandomizationMapData {

        public DefaultedMapData() {
            super(null);
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
