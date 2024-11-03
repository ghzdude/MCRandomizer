package com.ghzdude.randomizer;

import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.common.collect.Lists;
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

import java.util.*;

public class RandomizationMapData extends SavedData {
    private static final String KEY_PREFIX = RandomizerCore.MODID + "_%s";
    private static Registry<Item> ITEM_REGISTRY;

    public static void init(RegistryAccess access) {
        ITEM_REGISTRY = access.registryOrThrow(Registries.ITEM);
    }

    public static Factory<RandomizationMapData> factory() {
        return new Factory<>(RandomizationMapData::new, RandomizationMapData::load, DataFixTypes.LEVEL);
    }

    public static RandomizationMapData get(DimensionDataStorage storage, String name) {
        RandomizationMapData data = storage.computeIfAbsent(RandomizationMapData.factory(), KEY_PREFIX.formatted(name));
        if (!data.isLoaded()) {
            data.generateItemMap();
            data.generateTagMap();
            data.setDirty();
            data.isLoaded = true;
        }
        return data;
    }

    public static RandomizationMapData get(MinecraftServer server, String prefix) {
        return get(server.overworld().getDataStorage(), prefix);
    }

    public static RandomizationMapData get(ServerLevel serverLevel, String prefix) {
        return get(serverLevel.getServer(), prefix);
    }

    private final Map<ResourceLocation, ResourceLocation> ITEM_MAP = new Object2ObjectOpenHashMap<>();
    private final Map<ResourceLocation, ResourceLocation> TAGKEY_MAP = new Object2ObjectOpenHashMap<>();
    private final List<ResourceLocation> ITEM_LIST = new ArrayList<>();
    private final List<ResourceLocation> TAGKEY_LIST = new ArrayList<>();

    private boolean isLoaded = false;

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        RandomizerCore.LOGGER.warn("Saving randomizations to disk!");
        final ResourceLocation AIR = ITEM_REGISTRY.getKey(Items.AIR);

        CompoundTag itemMap = new CompoundTag();
        CompoundTag tagKeyMap = new CompoundTag();

        ITEM_MAP.forEach((vanilla, random) -> {
            if (random.equals(AIR)) return;

            itemMap.putString(vanilla.toString(), random.toString());
        });

        TAGKEY_MAP.forEach((vanilla, random) -> {
            tagKeyMap.putString(vanilla.toString(), random.toString());
        });

        tag.put("item_map", itemMap);
        tag.put("tag_key_map", tagKeyMap);
        return tag;
    }

    public static RandomizationMapData load(CompoundTag tag, HolderLookup.Provider provider) {
        RandomizationMapData data = new RandomizationMapData();
        RandomizerCore.LOGGER.warn("Loading from disk!");
        data.load(tag);
        return data;
    }

    public void load(CompoundTag tag) {
        final ResourceLocation AIR = ITEM_REGISTRY.getKey(Items.AIR);

        CompoundTag itemMap = tag.getCompound("item_map");
        CompoundTag tagKeyMap = tag.getCompound("tag_key_map");

        for (var vanilla : itemMap.getAllKeys()) {
            var random = ResourceLocation.parse(itemMap.getString(vanilla));
            if (random.equals(AIR)) continue;

            ITEM_MAP.put(ResourceLocation.parse(vanilla), random);
        }

        for (var vanilla : tagKeyMap.getAllKeys()) {
            var random = ResourceLocation.parse(tagKeyMap.getString(vanilla));
            TAGKEY_MAP.put(ResourceLocation.parse(vanilla), random);
        }

        ITEM_LIST.addAll(ITEM_MAP.keySet());
        TAGKEY_LIST.addAll(TAGKEY_MAP.keySet());

        setDirty();
        isLoaded = true;
    }

    private void generateTagMap() {
        List<ResourceLocation> vanilla = ITEM_REGISTRY.getTagNames().map(TagKey::location).distinct().toList();
        List<ResourceLocation> randomized = Lists.newArrayList(vanilla);
        Collections.shuffle(randomized, RandomizerCore.unseededRNG);

        if (vanilla.size() != randomized.size()) {
            RandomizerCore.LOGGER.warn("Tagkey registry was modified during server start!");
            return;
        }

        for (int i = 0; i < vanilla.size(); i++) {
            TAGKEY_MAP.put(vanilla.get(i), randomized.get(i));
        }
        TAGKEY_LIST.addAll(TAGKEY_MAP.keySet());
    }

    private void generateItemMap() {
        List<ResourceLocation> vanilla = Lists.newArrayList(ItemRandomizer.getValidItems());
        List<ResourceLocation> copy = Lists.newArrayList(vanilla);

        for (ResourceLocation key : vanilla) {
            int avoid = copy.indexOf(key);
            int selection;
            do {
                selection = RandomizerCore.unseededRNG.nextInt(copy.size());
            } while (selection == avoid && copy.size() > 1);

            ResourceLocation value = copy.get(selection);
            copy.remove(selection);

            ITEM_MAP.put(key, value);
        }
        ITEM_LIST.addAll(ITEM_MAP.keySet());
    }

    public ItemStack getStackFor(ItemStack stack) {
        return getStackFor(stack.getItem(), stack.getCount());
    }

    public ItemStack getStackFor(Item vanilla, int count) {
        Item randomItem = getItemFor(vanilla);
        if (randomItem == null || count < 1 || vanilla == Items.AIR) return ItemStack.EMPTY;

        ItemStack random = new ItemStack(randomItem);
        random.setCount(Math.min(random.getMaxStackSize(), count));
        return random;
    }

    public Item getItemFor(Item item) {
        return ITEM_REGISTRY.get(ITEM_MAP.get(ITEM_REGISTRY.getKey(item)));
    }

    public TagKey<Item> getTagKeyFor(TagKey<Item> vanilla) {
        var k = TAGKEY_MAP.get(vanilla.location());
        return TagKey.create(ITEM_REGISTRY.key(), k);
    }

    public TagKey<Item> getRandomTag(Random rng) {
        return TagKey.create(ITEM_REGISTRY.key(), RandomizerUtil.getRandom(TAGKEY_LIST, rng));
    }

    public Item getRandomItem(Random rng) {
        return ITEM_REGISTRY.get(RandomizerUtil.getRandom(ITEM_LIST, rng));
    }

    public List<ResourceLocation> getItems() {
        return ITEM_LIST;
    }

    public List<Item> getAsItems() {
        return getItems().stream()
                .map(ITEM_REGISTRY::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<ResourceLocation> getTags() {
        return TAGKEY_LIST;
    }

    public boolean isLoaded() {
        return isLoaded;
    }
}
