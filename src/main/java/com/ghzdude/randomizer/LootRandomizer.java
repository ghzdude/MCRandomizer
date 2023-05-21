package com.ghzdude.randomizer;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.level.storage.loot.entries.CompositeEntryBase;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootTableReference;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
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
        setField(LootTable.class, lootTable, 7, false); // unfreeze loot table
        ResourceLocation tableId = lootTable.getLootTableId();
        // nothing to add, return
        if (pools.size() == 0) return;

        // we already have data for this loot table
        Object2ObjectOpenHashMap<String, NonNullList<Item>> poolsList = new Object2ObjectOpenHashMap<>();
        if (data.changedLootTables.containsKey(tableId)) {
            poolsList = data.changedLootTables.get(tableId);
        }

        for (LootPool pool : pools) {
            String poolId = pool.getName();
            // LootPool modified = lootTable.getPool(poolId);
            setField(LootPool.class, pool, 8, false); // unfreeze loot pool
            // lootTable.removePool(poolId);

            LootPoolEntryContainer[] entries = getField(LootPool.class, pool, 1);

            // can't do anything with this yet
            // no need to modify
            if (entries instanceof LootItem[]) {
                NonNullList<Item> newResults;
                if (poolsList.containsKey(poolId)) {
                    newResults = poolsList.get(poolId);
                } else {
                    newResults = generateRandomList(entries.length);
                    poolsList.put(poolId, newResults);
                }

                if (newResults.size() != entries.length) {
                    RandomizerCore.LOGGER.warn(String.format("pool entries from %s do not match items from data! Clearing data!", lootTable.getLootTableId()));
                    data.changedLootTables.clear();
                    data.setDirty();
                    return;
                }
                modifiyEntries(entries, newResults);

                // lootTable.addPool(modified);
            }
        }

        lootTable.freeze(); // refreeze table

        // if new table id, store it
        if (!data.changedLootTables.containsKey(tableId)){
            data.changedLootTables.put(tableId, poolsList);
        }
    }

    private void modifiyEntries (LootPoolEntryContainer[] entries, NonNullList<Item> newResults) {
        for (int j = 0; j < entries.length; j++) {
            LootPoolEntryContainer entry = entries[j];

            if (entry instanceof LootItem) {
                setField(LootItem.class, (LootItem) entry, 0, newResults.get(j));
            }

            if (entry instanceof CompositeEntryBase) {
                // RandomizerCore.LOGGER.warn("got composite entry: " + entry);
                // LootPoolEntryContainer[] entries2 = getField(CompositeEntryBase.class, (CompositeEntryBase) entry, 0);
                // for (LootPoolEntryContainer entry2 : entries2) {
                // setField(CompositeEntryBase.class, (CompositeEntryBase) entry, 0, newResult);
                // }
            }

            // LootTableReferences do not need a check, i think
        }
    }

    private NonNullList<Item> generateRandomList(int amtItems) {
        NonNullList<Item> itemList = NonNullList.withSize(amtItems, Items.AIR);
        itemList.replaceAll(item -> ItemRandomizer.getRandomItem().item);
        return itemList;
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

    private <T> void setField(Class<T> clazz, T instance, int index, Object newValue) {
        Field fld = clazz.getDeclaredFields()[index];
        fld.setAccessible(true);
        try {
            fld.set(instance, newValue);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SubscribeEvent
    public void start(ServerStartedEvent event) {
        data = get(event.getServer().overworld().getDataStorage());

        if (data.changedLootTables.size() > 0) {
            RandomizerCore.LOGGER.warn("Using existing randomized loot table data!");
        } else {
            RandomizerCore.LOGGER.warn("Unable to find or use existing data! Randomizing loot tables!");
        }

        LootTables lootTables = event.getServer().getLootTables();
        for (ResourceLocation location : lootTables.getIds()) {
            randomizeLootTables(lootTables.get(location));
        }
        data.setDirty();
    }

    public static LootData get(DimensionDataStorage storage){
        return storage.computeIfAbsent(LootData::load, LootData::create, RandomizerCore.MODID + "_loot");
    }

    protected static class LootData extends SavedData {

        // comical
        public Object2ObjectOpenHashMap<ResourceLocation, Object2ObjectOpenHashMap<String, NonNullList<Item>>> changedLootTables = new Object2ObjectOpenHashMap<>();
        private final Object2ObjectOpenHashMap<String, NonNullList<Item>> poolMap = new Object2ObjectOpenHashMap<>();
        private final Object2IntArrayMap<ResourceLocation> poolCount = new Object2IntArrayMap<>();
        private final Object2ObjectOpenHashMap<ResourceLocation, ArrayList<String>> tableMap = new Object2ObjectOpenHashMap<>();

        public static LootData create() {
            return new LootData();
        }

        @Override
        public @NotNull CompoundTag save(CompoundTag tag) {
            RandomizerCore.LOGGER.warn("Saving changed loot tables to world data!");
            ListTag changedLootTablesTag = new ListTag(); // list of loot tables

            // for each loot table
            changedLootTables.forEach((location, pools) -> {
                CompoundTag lootTableTag = new CompoundTag();
                ListTag poolsTag = new ListTag();

                // for each pool
                pools.forEach((poolId, items)  -> {
                    CompoundTag poolTag = new CompoundTag();
                    ListTag itemsTag = new ListTag(); // list of items

                    items.forEach(item -> itemsTag.add(StringTag.valueOf(item.toString())));

                    poolTag.putString("pool_name", poolId);
                    poolTag.put("items", itemsTag);
                    poolsTag.add(poolTag);
                });
                lootTableTag.putString("loot_entry_id", location.toString());
                lootTableTag.put("pools", poolsTag);
                changedLootTablesTag.add(lootTableTag);
            });

            tag.put("changed_loot_tables", changedLootTablesTag);
            return tag;
        }

        public static LootData load(CompoundTag tag) {
            RandomizerCore.LOGGER.warn("Loading changed loot tables to world data!");
            LootData data = create();

            // tag.getList("changed_loot_tables", Tag.TAG_COMPOUND).getCompound(0).get("loot_entry_id").toString()
            // tag.getList("changed_loot_tables", Tag.TAG_COMPOUND).getCompound(0).getList("pools", Tag.TAG_COMPOUND).getCompound(0).getString("pool_name").toString()
            // tag.getList("changed_loot_tables", Tag.TAG_COMPOUND).getCompound(0).getList("pools", Tag.TAG_COMPOUND).getCompound(0).getList("items", Tag.TAG_STRING).get(0).toString()

            Object2ObjectOpenHashMap<String, NonNullList<Item>> poolMap = new Object2ObjectOpenHashMap<>();
            NonNullList<Item> itemList;

            ListTag lootTablesTag = tag.getList("changed_loot_tables", Tag.TAG_COMPOUND);

            // for each loot table
            for (int i = 0; i < lootTablesTag.size(); i++) {
                CompoundTag lootTableTag = lootTablesTag.getCompound(i);
                ListTag poolsTag = lootTableTag.getList("pools", Tag.TAG_COMPOUND);
                ResourceLocation loc = new ResourceLocation(lootTableTag.getString("loot_entry_id"));
                // for each pool
                for (int j = 0; j < poolsTag.size(); j++) {
                    CompoundTag poolTag = poolsTag.getCompound(j);
                    ListTag itemsTag = poolTag.getList("items", Tag.TAG_STRING);

                    // for each item in pool
                    itemList = NonNullList.create();
                    for (int k = 0; k < itemsTag.size(); k++) { // for each item
                        itemList.add(ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemsTag.getString(k))));
                    }
                    poolMap.put(poolTag.getString("pool_name"), itemList);
                }
                data.changedLootTables.put(loc, poolMap);
            }
            return data;
        }

        public String getPoolId(ResourceLocation location) {
            return tableMap.get(location);
        }

        public int getPoolCountFromTable(ResourceLocation location) {
            return poolCount.getInt(location);
        }

        public NonNullList<Item> getPoolItems(String poolId) {
            return poolMap.get(poolId);
        }

        public void addPool (NonNullList<Item> items, String poolId) {
            poolMap.put(poolId, items);

        }

        public void addTable (ResourceLocation location, ArrayList<String> poolId) {
            tableMap.put(location, poolId);
            poolCount.put(location, poolId.size());
        }

        public Boolean containsTable (ResourceLocation key) {
                return tableMap.containsKey(key);
        }

        public Boolean containsPool (String key) {
                return poolMap.containsKey(key);
        }
    }
}
