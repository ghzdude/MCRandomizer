package com.ghzdude.randomizer;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITagManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

public class RandomizationMapData extends SavedData {
    private final Map<Item, Item> ITEM_MAP = new Object2ObjectOpenHashMap<>();
    private final Map<TagKey<Item>, TagKey<Item>> TAGKEY_MAP = new Object2ObjectOpenHashMap<>();

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

    @Override
    public @NotNull CompoundTag save(CompoundTag tag) {
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

    public static RandomizationMapData load(CompoundTag tag) {
        RandomizationMapData data = new RandomizationMapData();
        RandomizerCore.LOGGER.warn("Loading from disk!");

        CompoundTag itemMap = tag.getCompound("item_map");
        CompoundTag tagKeyMap = tag.getCompound("tag_key_map");

        ItemRandomizer.getValidItems().forEach(specialItem -> {
            Item vanilla = specialItem.item;
            Item random = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemMap.getString(vanilla.toString())));
            if (random == Items.AIR) return;

            data.put(vanilla, random);
        });

        ITagManager<Item> tagManager = ForgeRegistries.ITEMS.tags();
        if (tagManager != null) {
            tagManager.getTagNames().forEach(vanilla -> {
                String rand = tagKeyMap.getString(vanilla.location().toString());
                data.put(vanilla, tagManager.createTagKey(new ResourceLocation(rand)));
            });
        }
        data.setDirty();

        data.isLoaded = true;
        return data;
    }

    private void generateTagMap(Random rng) {
        ITagManager<Item> tagManager = ForgeRegistries.ITEMS.tags();
        if (tagManager == null) return;

        List<TagKey<Item>> vanilla = tagManager.getTagNames().toList();
        List<TagKey<Item>> randomized = Lists.newArrayList(vanilla);
        Collections.shuffle(randomized, rng);

        if (vanilla.size() != randomized.size()) {
            RandomizerCore.LOGGER.warn("Tagkey registry was modified during server start!");
            return;
        }

        for (int i = 0; i < vanilla.size(); i++) {
            if (TAGKEY_MAP.containsKey(vanilla.get(i))) continue;

            TAGKEY_MAP.put(vanilla.get(i), randomized.get(i));
        }
    }

    private void generateItemMap(Random rng) {
        List<Item> vanilla = Lists.newArrayList(ItemRandomizer.getValidItems().asItems());
        List<Item> randomized = Lists.newArrayList(vanilla);
        Collections.shuffle(randomized, rng);

        if (vanilla.size() != randomized.size()) {
            RandomizerCore.LOGGER.warn("Item registry was modified during server start!");
            return;
        }

        for (int i = 0; i < vanilla.size(); i++) {
            if (ITEM_MAP.containsKey(vanilla.get(i))) continue;

            put(vanilla.get(i), randomized.get(i));
        }
    }

    private void put(Item vanilla, Item random) {
        ITEM_MAP.put(vanilla, random);
    }

    private void put(TagKey<Item> vanilla, TagKey<Item> random) {
        TAGKEY_MAP.put(vanilla, random);
    }

    public ItemStack getStackFor(ItemStack stack) {
        return getStackFor(stack.getItem(), stack.getCount(), stack.getTag());
    }

    public ItemStack getStackFor(Item vanilla, int count, CompoundTag tag) {
        Item randomItem = ITEM_MAP.get(vanilla);
        if (randomItem == null || count < 1) return ItemStack.EMPTY;

        ItemStack random = new ItemStack(randomItem);
        random.setCount(Math.min(random.getMaxStackSize(), count));
        random.setTag(tag);
        return random;
    }

    public TagKey<Item> getTagKeyFor(TagKey<Item> vanilla) {
        return TAGKEY_MAP.get(vanilla);
    }

    public TagKey<Item> getRandomTag(Random rng) {
        List<TagKey<Item>> tags = streamTags().toList();
        return tags.get(rng.nextInt(tags.size()));
    }

    public Item getRandomItem(Random rng) {
        List<Item> items = streamItems().toList();
        return items.get(rng.nextInt(items.size()));
    }

    public Stream<TagKey<Item>> streamTags() {
        return TAGKEY_MAP.values().stream();
    }
    public Stream<Item> streamItems() {
        return ITEM_MAP.values().stream();
    }

    public boolean isLoaded() {
        return isLoaded;
    }
}
