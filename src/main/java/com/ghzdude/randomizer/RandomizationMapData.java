package com.ghzdude.randomizer;

import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
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
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITagManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RandomizationMapData extends SavedData {
    private final Map<Item, Item> ITEM_MAP = new Object2ObjectOpenHashMap<>();
    private final Map<TagKey<Item>, TagKey<Item>> TAGKEY_MAP = new Object2ObjectOpenHashMap<>();
    private List<Item> ITEM_LIST = List.of();
    private List<TagKey<Item>> TAGKEY_LIST = List.of();

    private boolean isLoaded = false;

    public static Factory<RandomizationMapData> factory() {
        return new Factory<>(RandomizationMapData::new, RandomizationMapData::load, DataFixTypes.LEVEL);
    }

    public static RandomizationMapData get(DimensionDataStorage storage, String prefix) {
        RandomizationMapData data = storage.computeIfAbsent(RandomizationMapData.factory(), RandomizerCore.MODID + "_" + prefix);
        if (!data.isLoaded()) {
            Random rng = new Random();
            data.generateItemMap(rng);
            data.generateTagMap(rng);
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

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        RandomizerCore.LOGGER.warn("Saving randomizations to disk!");
        CompoundTag itemMap = new CompoundTag();
        CompoundTag tagKeyMap = new CompoundTag();

        ITEM_MAP.forEach((vanilla, random) -> {
            if (random == Items.AIR) return;

            itemMap.putString(vanilla.toString(), random.toString());
        });

        TAGKEY_MAP.forEach((vanilla, random) -> {
            tagKeyMap.putString(vanilla.location().toString(), random.location().toString());
        });

        tag.put("item_map", itemMap);
        tag.put("tag_key_map", tagKeyMap);
        return tag;
    }

    public static RandomizationMapData load(CompoundTag tag, HolderLookup.Provider provider) {
        RandomizationMapData data = new RandomizationMapData();
        RandomizerCore.LOGGER.warn("Loading from disk!");

        CompoundTag itemMap = tag.getCompound("item_map");
        CompoundTag tagKeyMap = tag.getCompound("tag_key_map");

        ItemRandomizer.getValidItems().forEach(item -> {
            Item random = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(itemMap.getString(item.toString())));
            if (random == Items.AIR) return;

            data.put(item, random);
        });

        ITagManager<Item> tagManager = ForgeRegistries.ITEMS.tags();
        if (tagManager != null) {
            tagManager.getTagNames().forEach(vanilla -> {
                String rand = tagKeyMap.getString(vanilla.location().toString());
                data.put(vanilla, tagManager.createTagKey(ResourceLocation.parse(rand)));
            });
        }
        data.setDirty();

        data.isLoaded = true;
        return data;
    }

    private void generateTagMap(Random rng) {
        ITagManager<Item> tagManager = ForgeRegistries.ITEMS.tags();
        if (tagManager == null) return;

        List<TagKey<Item>> vanilla = tagManager.getTagNames().distinct().toList();
        List<TagKey<Item>> randomized = Lists.newArrayList(vanilla);
        Collections.shuffle(randomized, rng);

        if (vanilla.size() != randomized.size()) {
            RandomizerCore.LOGGER.warn("Tagkey registry was modified during server start!");
            return;
        }

        for (int i = 0; i < vanilla.size(); i++) {
            TAGKEY_MAP.put(vanilla.get(i), randomized.get(i));
        }
        TAGKEY_LIST = List.copyOf(TAGKEY_MAP.keySet());
    }

    private void generateItemMap(Random rng) {
        List<Item> vanilla = Lists.newArrayList(ItemRandomizer.getValidItems());
        List<Item> copy = Lists.newArrayList(vanilla);

        for (Item key : vanilla) {
            int avoid = copy.indexOf(key);
            int selection;
            do {
                selection = rng.nextInt(copy.size());
            } while (selection == avoid && copy.size() > 1);

            Item value = copy.get(selection);
            copy.remove(selection);

            ITEM_MAP.put(key, value);
        }
        ITEM_LIST = List.copyOf(ITEM_MAP.keySet());
    }

    private void put(Item vanilla, Item random) {
        ITEM_MAP.put(vanilla, random);
    }

    private void put(TagKey<Item> vanilla, TagKey<Item> random) {
        TAGKEY_MAP.put(vanilla, random);
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
        return ITEM_MAP.get(item);
    }

    public TagKey<Item> getTagKeyFor(TagKey<Item> vanilla) {
        return TAGKEY_MAP.get(vanilla);
    }

    public TagKey<Item> getRandomTag(Random rng) {
        return RandomizerUtil.getRandom(TAGKEY_LIST, rng);
    }

    public Item getRandomItem(Random rng) {
        return RandomizerUtil.getRandom(ITEM_LIST, rng);
    }

    public List<Item> getItems() {
        return ITEM_LIST;
    }

    public List<TagKey<Item>> getTags() {
        return TAGKEY_LIST;
    }

    public boolean isLoaded() {
        return isLoaded;
    }
}
