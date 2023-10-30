package com.ghzdude.randomizer;

import com.ghzdude.randomizer.special.generators.*;
import com.ghzdude.randomizer.special.item.SpecialItem;
import com.ghzdude.randomizer.special.item.SpecialItemList;
import com.ghzdude.randomizer.special.item.SpecialItems;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/* Item Randomizer Description
 * Goal is to give the player a random item every so often DONE
 * Every so often, points are added to a counter DONE
 * over time, the amount of points gained each cycle is increased
 * those points are then used to give the player an item DONE
 * more points give bigger stacksize of item at once DONE
 * items have a defined value, otherwise stacksize is used
 */
public class ItemRandomizer {
    private static final SpecialItemList VALID_ITEMS = new SpecialItemList(configureValidItem());
    private static final SpecialItemList SIMPLE_ITEMS = new SpecialItemList(VALID_ITEMS.stream()
            .filter(specialItem ->
                    !SpecialItems.EFFECT_ITEMS.contains(specialItem) &&
                    !SpecialItems.ENCHANTABLE.contains(specialItem.item))
            .toList()
    );

    private static Collection<SpecialItem> configureValidItem() {
        int lastMatch;
        ArrayList<SpecialItem> validItems = new ArrayList<>();

        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (SpecialItems.BLACKLISTED_ITEMS.contains(item)) continue;
            SpecialItem toUpdate;

            if (SpecialItems.SPECIAL_ITEMS.contains(item)) {
                toUpdate = SpecialItems.SPECIAL_ITEMS.get(SpecialItems.SPECIAL_ITEMS.indexOf(item));
            } else if (SpecialItems.EFFECT_ITEMS.contains(item)) {
                toUpdate = SpecialItems.EFFECT_ITEMS.get(SpecialItems.EFFECT_ITEMS.indexOf(item));
            } else {
                toUpdate = new SpecialItem(item);
            }

            if (toUpdate.item.toString().contains("shulker_box")) {
                lastMatch = SpecialItems.SPECIAL_ITEMS.indexOf(Items.SHULKER_BOX);
                toUpdate.value = SpecialItems.SPECIAL_ITEMS.get(lastMatch).value;
            }

            validItems.add(toUpdate);
        }
        return validItems;
    }

    public static int giveRandomItem(int pointsToUse, Inventory inventory){
        if (RandomizerConfig.giveMultipleItems()) {
            pointsToUse = giveMultiple(pointsToUse, inventory);
        } else {
            pointsToUse = giveOnce(pointsToUse, inventory);
        }
        return pointsToUse;
    }

    private static int giveMultiple(int pointsToUse, Inventory playerInventory) {
        int tries = 0;
        while (tries < 5 && pointsToUse > 0) {
            pointsToUse = giveOnce(pointsToUse, playerInventory);
            tries++;
        }
        return pointsToUse;
    }

    private static int giveOnce(int pointsToUse, Inventory playerInventory) {
        SpecialItem selectedItem = getRandomSpecialItem(pointsToUse);

        int amtToGive = Math.floorDiv(pointsToUse, selectedItem.value);
        ItemStack stack = specialItemToStack(selectedItem);

        stack.setCount(Math.min(amtToGive, stack.getMaxStackSize()));

        pointsToUse -= stack.getCount() * selectedItem.value;
        addStackToPlayer(stack, playerInventory);
        return pointsToUse;
    }

    public static SpecialItem getRandomSpecialItem() {
        return VALID_ITEMS.getRandomSpecialItem(RandomizerCore.rng);
    }

    public static ItemStack getRandomItemStack() {
        return specialItemToStack(getRandomSpecialItem());
    }

    public static SpecialItem getRandomSpecialItem(int points) {
        SpecialItem toReturn;
        do {
            toReturn = VALID_ITEMS.getRandomSpecialItem(RandomizerCore.rng);
        } while (toReturn.value > points);
        return toReturn;
    }

    public static SpecialItem getRandomSimpleItem() {
        return SIMPLE_ITEMS.getRandomSpecialItem(RandomizerCore.rng);
    }

    public static ItemStack specialItemToStack (SpecialItem item) {
        ItemStack stack = new ItemStack(item.item);

        if (SpecialItems.ENCHANTABLE.contains(item.item)) {
            EnchantmentGenerator.applyEnchantment(stack);
        } else if (SpecialItems.EFFECT_ITEMS.contains(item)) {
            PotionGenerator.applyEffect(stack);
        } else if (item.item == Items.WRITTEN_BOOK) {
            BookGenerator.applyPassages(stack);
        } else if (item.item == Items.FIREWORK_ROCKET) {
            FireworkGenerator.applyFirework(stack);
        } else if (item.item == Items.FIREWORK_STAR) {
            FireworkGenerator.applyFireworkStar(stack);
        } else if (item.item == Items.GOAT_HORN) {
            GoatHornGenerator.applyGoatHornSound(stack);
        }

        return stack;
    }

    private static void addStackToPlayer(ItemStack stack, Inventory inventory) {
        RandomizerCore.LOGGER.warn(String.format("Given %s to %s.",  stack.copy(), inventory.player.getDisplayName().getString()));
        if (inventory.getFreeSlot() == -1) {
            Entity itemEnt = stack.getEntityRepresentation();
            if (itemEnt != null) {
                itemEnt.setPos(inventory.player.position());
                inventory.player.level().addFreshEntity(itemEnt);
            }
        } else {
            inventory.add(stack);
        }
        RandomizerCore.incrementAmtItemsGiven();
    }

    public static ItemRandomMapData get(DimensionDataStorage storage){
        return storage.computeIfAbsent(ItemRandomMapData.factory(), RandomizerCore.MODID + "_loot");
    }

    public static class ItemRandomMapData extends SavedData {

        public static final Map<Item, Item> ITEM_MAP = new Object2ObjectOpenHashMap<>();
        public static ItemRandomMapData INSTANCE;

        public static SavedData.Factory<ItemRandomMapData> factory() {
            return new Factory<>(ItemRandomMapData::new, ItemRandomMapData::load, DataFixTypes.LEVEL);
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

        public static ItemRandomMapData load(CompoundTag tag) {
            RandomizerCore.LOGGER.warn("Loading changed loot tables to world data!");
            CompoundTag map = tag.getCompound("item_map");
            ForgeRegistries.ITEMS.forEach((item) -> {
                Item random = ForgeRegistries.ITEMS.getValue(new ResourceLocation(map.getString(item.toString())));
                ITEM_MAP.put(item, random);
            });
            return INSTANCE;
        }

        public void configure() {
            List<Item> registry = Lists.newArrayList(ItemRandomizer.VALID_ITEMS.asItems());

            List<Item> blocks = Lists.newArrayList(registry);
            Collections.shuffle(blocks, RandomizerCore.rng);

            if (registry.size() != blocks.size()) {
                RandomizerCore.LOGGER.warn("Registry was modified during server start!");
                return;
            }

            for (int i = 0; i < registry.size(); i++) {
                if (ITEM_MAP.containsKey(registry.get(i))) continue;

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
