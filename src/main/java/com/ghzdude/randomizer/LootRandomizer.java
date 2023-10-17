package com.ghzdude.randomizer;

import com.ghzdude.randomizer.reflection.ReflectionUtils;
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
import net.minecraft.world.level.storage.loot.entries.*;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/* Loot Randomizer
 * Randomizes Loot dropped from mobs, blocks, etc
 * should only be randomized once per level/world
 */
public class LootRandomizer {

    private LootData data;

    private final int MAX_DEPTH = 10;

    public void randomizeLootTables(LootTables lootTables) {
        for (ResourceLocation tableId : lootTables.getIds()) {
            if (RandomizerConfig.randomizeBlockLoot() && tableId.getPath().contains("blocks/")) {
                randomizeLoot(lootTables.get(tableId));
            }

            if (RandomizerConfig.randomizeEntityLoot() && tableId.getPath().contains("entities/")) {
                randomizeLoot(lootTables.get(tableId));
            }

            if (RandomizerConfig.randomizeChestLoot() && tableId.getPath().contains("chests/")) {
                randomizeLoot(lootTables.get(tableId));
            }
        }
    }

    private void randomizeLoot(LootTable table) {
        ResourceLocation tableId = table.getLootTableId();
        ReflectionUtils.setField(LootTable.class, table, 7, false); // unfreeze loot table
        List<LootPool> pools = ReflectionUtils.getField(LootTable.class, table, 4);

        for (int i = 0; i < pools.size(); i++) {
            LootPool pool = pools.get(i);
            String poolName = pool.getName();

            ReflectionUtils.setField(LootPool.class, pool, 8, false); // unfreeze loot pool
            LootPoolEntryContainer[] entries = ReflectionUtils.getField(LootPool.class, pool, 1);

            NonNullList<Item> newEntries;
            if (data.containsPool(tableId, poolName)) {
                newEntries = data.getPoolItems(tableId, poolName);
            } else {
                newEntries = generateRandomList(calculateNewResults(entries, 0));
                if (newEntries.size() == 0) return;
                data.addPool(tableId, poolName, newEntries);
                RandomizerCore.LOGGER.warn("No data for " + tableId + ", generating new data!");
            }

            modifyEntries(entries, newEntries);
        }
    }

    public int calculateNewResults (LootPoolEntryContainer[] entries, int depth) {
        int size = 0;

        if (depth > MAX_DEPTH) {
            RandomizerCore.LOGGER.warn("Loot Pool entries exceeded max depth! Stopping randomization for this pool.");
            return size;
        }

        for (LootPoolEntryContainer entry : entries) {
            LootPoolEntryType type = entry.getType();

            if (type == LootPoolEntries.ITEM) {
                size++;
                continue;
            }

            if (type == LootPoolEntries.ALTERNATIVES) {
                LootPoolEntryContainer[] extraEntries = ReflectionUtils.getField(CompositeEntryBase.class, (CompositeEntryBase) entry, 0);
                size += calculateNewResults(extraEntries, depth++);
            }

//            if (type == LootPoolEntries.GROUP) {
//                RandomizerCore.LOGGER.warn("Group Entry");
//                continue;
//            }
//
//            if (type == LootPoolEntries.SEQUENCE) {
//                RandomizerCore.LOGGER.warn("Sequence Entry");
//                continue;
//            }
//
//            if (type == LootPoolEntries.DYNAMIC) {
//                RandomizerCore.LOGGER.warn("Dynamic Entry");
//            }
        }
        return size;
    }

    public void modifyEntries (LootPoolEntryContainer[] entries, List<Item> toReplace ) {
        if (entries.length == 0) {
            RandomizerCore.LOGGER.warn("Pool Entries ran out!");
            return;
        }

        for (int i = 0; i < entries.length; i++) {
            LootPoolEntryType type = entries[i].getType();

            if (type == LootPoolEntries.EMPTY) {
                continue;
            }

            if (i == toReplace.size()) {
                RandomizerCore.LOGGER.warn("Items to replace is empty!");
                return;
            }

            if (type == LootPoolEntries.ITEM) {
                ReflectionUtils.setField(LootItem.class, (LootItem) entries[i], 0, toReplace.get(i));
                continue;
            }

            if (type == LootPoolEntries.ALTERNATIVES) {
                LootPoolEntryContainer[] extraEntries = ReflectionUtils.getField(CompositeEntryBase.class, (CompositeEntryBase) entries[i], 0);
                modifyEntries(extraEntries, toReplace.subList(i, toReplace.size()));
            }
        }
    }

    // debug only remove later
    public void printTableDebug (LootTable table, List<LootPool> pools) {
        StringBuilder builder = new StringBuilder(table.getLootTableId().toString());
        builder.append(" has pools -> [");
        for (LootPool pool : pools) {
            builder.append(pool.getName());
            builder.append(",");
        }
        if (builder.lastIndexOf(",") != -1) {
            builder.deleteCharAt(builder.lastIndexOf(","));
        }
        builder.append("]");

        RandomizerCore.LOGGER.debug(builder.toString());
    }

    private NonNullList<Item> generateRandomList(int amtItems) {
        NonNullList<Item> itemList = NonNullList.withSize(amtItems, Items.AIR);
        itemList.replaceAll(item -> ItemRandomizer.getRandomSimpleItem().item);
        return itemList;
    }

    @SubscribeEvent
    public void start(ServerStartedEvent event) {
        if (RandomizerConfig.lootRandomizerEnabled()) {
            data = get(event.getServer().overworld().getDataStorage());

            RandomizerCore.LOGGER.warn("Loot Table Randomizer running!");

            randomizeLootTables(event.getServer().getLootTables());
            data.setDirty();
        }
    }

    public static LootData get(DimensionDataStorage storage){
        return storage.computeIfAbsent(LootData::load, LootData::create, RandomizerCore.MODID + "_loot");
    }

    protected static class LootData extends SavedData {

        // comical
        private final Object2ObjectOpenHashMap<ResourceLocation, Object2ObjectOpenHashMap<String, NonNullList<Item>>> lootTablePoolsMap = new Object2ObjectOpenHashMap<>();
        public static LootData create() {
            return new LootData();
        }

        @Override
        public @NotNull CompoundTag save(CompoundTag tag) {
            RandomizerCore.LOGGER.warn("Saving changed loot tables to world data!");
            // list of loot tables
            ListTag changedLootTablesTag = new ListTag();

            // for each loot table
            lootTablePoolsMap.forEach((location, pools) -> {
                CompoundTag lootTableTag = new CompoundTag(); // each table has an id and a list of pools
                ListTag poolsTag = new ListTag(); // list of pools

                // for each pool
                pools.forEach((poolId, items) -> {
                    CompoundTag poolTag = new CompoundTag(); // each pool has a name and a list of items
                    ListTag itemsTag = new ListTag(); // list of items

                    items.forEach( // add each item
                            item -> itemsTag.add(StringTag.valueOf(item.toString()))
                    );

                    if (items.size() > 0) {
                        poolTag.putString("pool_name", poolId);
                        poolTag.put("items", itemsTag);
                        poolsTag.add(poolTag);
                    }
                });
                lootTableTag.putString("loot_entry_id", location.toString());
                lootTableTag.put("pools", poolsTag);
                changedLootTablesTag.add(lootTableTag);
            });

            tag.put("changed_loot_tables", changedLootTablesTag);
            return tag;
        }

        public static LootData load(CompoundTag tag) {
            LootData data = create();
            RandomizerCore.LOGGER.warn("Loading changed loot tables to world data!");

            ListTag lootTablesTag = tag.getList("changed_loot_tables", Tag.TAG_COMPOUND);

            // for each loot table
            for (int i = 0; i < lootTablesTag.size(); i++) {
                CompoundTag lootTableTag = lootTablesTag.getCompound(i);
                CompoundTag poolTag = null;
                NonNullList<Item> itemList = null;
                ListTag poolsTag = lootTableTag.getList("pools", Tag.TAG_COMPOUND);
                ResourceLocation loc = new ResourceLocation(lootTableTag.getString("loot_entry_id"));
                // for each pool
                for (int j = 0; j < poolsTag.size(); j++) {
                    poolTag = poolsTag.getCompound(j);
                    ListTag itemsTag = poolTag.getList("items", Tag.TAG_STRING);
                    if (itemsTag.size() > 0) {
                        itemList = NonNullList.withSize(itemsTag.size(), Items.AIR);

                        // for each item in pool
                        for (int k = 0; k < itemsTag.size(); k++) { // for each item
                            itemList.set(k, ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemsTag.getString(k))));
                        }
                        data.addPool(loc, poolTag.getString("pool_name"), itemList);
                    }
                }
            }
            return data;
        }

        public NonNullList<Item> getPoolItems(ResourceLocation location, String poolId) {
            return lootTablePoolsMap.get(location).get(poolId);
        }

        public void addPool(ResourceLocation tableId, String poolId, NonNullList<Item> items) {
            if (containsPool(tableId, poolId)){
                lootTablePoolsMap.get(tableId).put(poolId, items);
            } else if (poolId != null){
                Object2ObjectOpenHashMap<String, NonNullList<Item>> poolMap = new Object2ObjectOpenHashMap<>();
                poolMap.put(poolId, items);
                lootTablePoolsMap.put(tableId, poolMap);
            } else {
                RandomizerCore.LOGGER.warn("A pool name in {} was null! Randomization will not be saved.", tableId);
            }
        }

        public Boolean containsPool(ResourceLocation tableId, String poolId) {
            if (tableId == null || poolId == null) return false;

            if (lootTablePoolsMap.containsKey(tableId)) {
                return lootTablePoolsMap.get(tableId).containsKey(poolId);
            }
            return false;
        }

        public int getTablesCount() {
            return lootTablePoolsMap.size();
        }
    }
}
