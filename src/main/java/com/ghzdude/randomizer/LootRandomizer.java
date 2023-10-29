package com.ghzdude.randomizer;

import com.ghzdude.randomizer.special.item.SpecialItems;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/* Loot Randomizer
 * Randomizes Loot dropped from mobs, blocks, etc
 * should only be randomized once per level/world
 */
public class LootRandomizer {

    @SubscribeEvent
    public void start(ServerStartingEvent event) {
        if (RandomizerConfig.lootRandomizerEnabled()) {
            LootData.INSTANCE = get(event.getServer().overworld().getDataStorage());
            LootData.INSTANCE.configure(event.getServer().getWorldData().worldGenOptions().seed());
        }
    }

    public static LootData get(DimensionDataStorage storage){
        return storage.computeIfAbsent(LootData.factory(), RandomizerCore.MODID + "_loot");
    }

    public static class LootData extends SavedData {

        public static final Map<Item, Item> ITEM_MAP = new Object2ObjectOpenHashMap<>();
        public static LootData INSTANCE;

        public static SavedData.Factory<LootData> factory() {
            return new Factory<>(LootData::new, LootData::load, DataFixTypes.LEVEL);
        }

        @Override
        public @NotNull CompoundTag save(CompoundTag tag) {
            RandomizerCore.LOGGER.warn("Saving changed loot tables to world data!");
            ListTag map = new ListTag();
            ITEM_MAP.forEach((vanilla, random) -> {
                CompoundTag itemPair = new CompoundTag();
                itemPair.putString(vanilla.toString(), random.toString());
                map.add(itemPair);
            });
            tag.put("item_map", map);
            return tag;
        }

        public static LootData load(CompoundTag tag) {
            RandomizerCore.LOGGER.warn("Loading changed loot tables to world data!");
            INSTANCE = factory().constructor().get();
            CompoundTag map = tag.getCompound("item_map");
            ForgeRegistries.ITEMS.forEach((item) -> {
                Item random = ForgeRegistries.ITEMS.getValue(new ResourceLocation(map.getString(item.toString())));
                ITEM_MAP.put(item, random);
            });
            return INSTANCE;
        }

        public void configure(long seed) {
            Random rng = new Random(seed);
            List<Item> registry = Lists.newArrayList(ForgeRegistries.ITEMS.getValues()
                .stream()
                .filter(item -> !SpecialItems.BLACKLISTED_ITEMS.contains(item)).toList());

            List<Item> blocks = Lists.newArrayList(registry);
            Collections.shuffle(blocks, rng);


            if (registry.size() != blocks.size()) {
                RandomizerCore.LOGGER.warn("Registry was modified during server start!");
                return;
            }

            for (int i = 0; i < registry.size(); i++) {
                if (ITEM_MAP.containsKey(registry.get(i)) && SpecialItems.BLACKLISTED_ITEMS.contains(blocks.get(i))) continue;

                ITEM_MAP.put(registry.get(i), blocks.get(i));
            }
            setDirty();
        }

        public ItemStack getStack(ItemStack vanilla) {
            ItemStack random = new ItemStack(ITEM_MAP.get(vanilla.getItem()));
            random.setCount(vanilla.getCount());
            random.setTag(vanilla.getTag());
            return random;
        }
    }
}
