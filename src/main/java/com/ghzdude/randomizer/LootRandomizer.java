package com.ghzdude.randomizer;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.level.storage.loot.entries.CompositeEntryBase;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootTableReference;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/* Loot Randomizer
 * Randomizes Loot dropped from mobs, blocks, etc
 * should only be randomized once per level/world
 */
public class LootRandomizer {

    private LootData data;

    public void randomizeLootTables(LootTable lootTable) {
        List<LootPool> pools = getField(LootTable.class, lootTable, 4);
        ResourceLocation tableId = lootTable.getLootTableId();


        for (int i = 0; i < pools.size(); i++) {
            LootPool pool = pools.get(i);
            String poolId = pool.getName();
            LootPoolEntryContainer[] entries = getField(LootPool.class, pool, 1);

            configure(tableId, poolId, pools.size(), entries.length);

            for (int j = 0; j < entries.length; j++) {
                LootPoolEntryContainer entry = entries[i];

                Item newResult = data.changedLootTables.get(tableId).get(i).get(poolId).get(j);

                if (entry instanceof LootItem) {
                    setField(LootItem.class, (LootItem) entry, 0, newResult);
                }
                if (entry instanceof CompositeEntryBase) {
                    RandomizerCore.LOGGER.warn("got composite entry: " + entry);
                    // LootPoolEntryContainer[] entries2 = getField(CompositeEntryBase.class, (CompositeEntryBase) entry, 0);
                    // for (LootPoolEntryContainer entry2 : entries2) {
                        // setField(CompositeEntryBase.class, (CompositeEntryBase) entry, 0, newResult);
                    // }
                }
                if (entry instanceof LootTableReference) {
                    RandomizerCore.LOGGER.warn("got reference: " + entry);
                }
            }
        }
        // Classes to check LootItem AlternativesEntry LootTableReference
    }

    private void configure(ResourceLocation tableId, String poolId, int amtPools, int amtItems) {
        ArrayList<Object2ObjectOpenHashMap<String, NonNullList<Item>>> poolsList = new ArrayList<>();

        if (data.changedLootTables.get(tableId) != null) {
            poolsList = data.changedLootTables.get(tableId);
        }

        if (poolsList.isEmpty()) {
            NonNullList<Item> poolItems;
            for (int i = 0; i < amtPools; i++) {
                Object2ObjectOpenHashMap<String, NonNullList<Item>> datapool = new Object2ObjectOpenHashMap<>();

                poolItems = NonNullList.withSize(amtItems, Items.AIR);
                for (int j = 0; j < poolItems.size(); j++) {
                    if (poolItems.get(j) == Items.AIR) {
                        poolItems.set(j, ItemRandomizer.getRandomItem().item);
                    }
                }
                datapool.put(poolId, poolItems);
                poolsList.add(datapool);
            }
            data.changedLootTables.put(tableId, poolsList);
        }
    }

    @SuppressWarnings("unchecked")
    private <T, R> R getField(Class<T> clazz, T instance, int index) {
        Field field = clazz.getDeclaredFields()[index];
        field.setAccessible(true);
        try {
            return (R) field.get(instance);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> void setField(Class<T> clazz, T instance, int index, Item newResult) {
        Field fld = clazz.getDeclaredFields()[index];
        fld.setAccessible(true);
        try {
            fld.set(instance, newResult);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SubscribeEvent
    public void start(ServerStartedEvent event) {
            data = get(event.getServer().overworld().getDataStorage());

            LootTables lootTables = event.getServer().getLootTables();
            for (ResourceLocation location : lootTables.getIds()) {
                randomizeLootTables(lootTables.get(location));
            }
    }

    public static LootData get(DimensionDataStorage storage){
        return storage.computeIfAbsent(LootData::load, LootData::create, RandomizerCore.MODID + "_loot");
    }

    protected static class LootData extends SavedData {

        // comical
        public final Object2ObjectOpenHashMap<ResourceLocation, ArrayList<Object2ObjectOpenHashMap<String, NonNullList<Item>>>> changedLootTables = new Object2ObjectOpenHashMap<>();
        @Override
        public @NotNull CompoundTag save(CompoundTag tag) {
            RandomizerCore.LOGGER.warn("Saving changed loot tables to world data!");
            ListTag changedLootTablesTag = new ListTag(); // list of loot tables

            // list of items
            // map pool to list
            // list of pools
            // map loot table to list
            // list of loot tables
            // god save me
            changedLootTables.forEach((location, pools) -> {
                CompoundTag lootTableTag = new CompoundTag(); // per loot table
                lootTableTag.putString("loot_entry_id", location.toString());
                ListTag poolsTag = new ListTag(); // list of pools
                pools.forEach((pool)  -> {
                    CompoundTag poolTag = new CompoundTag(); // per pool
                    pool.forEach((poolId, items) -> {
                        poolTag.putString("pool_name", poolId);
                        ListTag itemsTag = new ListTag(); // list of items
                        items.forEach(item -> itemsTag.add(StringTag.valueOf(item.getDescriptionId())));
                        poolTag.put("items", itemsTag);
                    });
                    poolsTag.add(poolTag);
                });
                changedLootTablesTag.add(poolsTag);
            });

            tag.put("changed_loot_tables", changedLootTablesTag);
            return tag;
        }

        public static LootData create() {
            return new LootData();
        }

        public static LootData load(CompoundTag tag) {
            RandomizerCore.LOGGER.warn("Loading changed loot tables to world data!");
            LootData data = create();
            // list of loot tables
            // map loot table to list
            // list of pools
            // map pool to list
            // list of items

            ListTag lootTablesTag = tag.getList("changed_loot_tables", Tag.TAG_COMPOUND);

            ArrayList<Object2ObjectOpenHashMap<String, NonNullList<Item>>> poolsMap = new ArrayList<>();
            for (int i = 0; i < lootTablesTag.size(); i++) {
                CompoundTag lootTableTag = lootTablesTag.getCompound(i);
                ListTag poolsTag = lootTableTag.getList("loot_items", Tag.TAG_COMPOUND);
                Object2ObjectOpenHashMap<String, NonNullList<Item>> poolMap = new Object2ObjectOpenHashMap<>();
                for (int j = 0; j < poolsTag.size(); j++) {
                    CompoundTag poolTag = poolsTag.getCompound(i);
                    ListTag itemsTag = poolTag.getList("items", Tag.TAG_COMPOUND);
                    NonNullList<Item> itemList = NonNullList.withSize(itemsTag.size(), Items.AIR);
                    for (int k = 0; k < itemsTag.size(); k++) {
                        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemsTag.getString(i)));
                        itemList.set(i, item == null ? Items.AIR : item);
                    }
                    poolMap.put(poolTag.getString("pool_name"), itemList);
                    poolsMap.add(poolMap);
                }
                data.changedLootTables.put(new ResourceLocation(lootTableTag.getString("loot_entry_id")) ,poolsMap);
            }
            return data;
        }
    }
}
