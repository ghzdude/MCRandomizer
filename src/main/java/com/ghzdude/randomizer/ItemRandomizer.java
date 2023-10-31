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
import net.minecraft.tags.TagKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITagManager;
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
        ItemStack stack = itemToStack(selectedItem.item);

        stack.setCount(Math.min(amtToGive, stack.getMaxStackSize()));

        pointsToUse -= stack.getCount() * selectedItem.value;
        addStackToPlayer(stack, playerInventory);
        return pointsToUse;
    }

    public static SpecialItem getRandomSpecialItem() {
        return VALID_ITEMS.getRandomSpecialItem(RandomizerCore.rng);
    }

    public static ItemStack getRandomItemStack() {
        return itemToStack(getRandomSpecialItem().item);
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
        return itemToStack(item.item);
    }

    public static ItemStack itemToStack(Item item) {
        ItemStack stack = new ItemStack(item);
        if (SpecialItems.ENCHANTABLE.contains(item)) {
            EnchantmentGenerator.applyEnchantment(stack);
        } else if (SpecialItems.EFFECT_ITEMS.contains(item)) {
            PotionGenerator.applyEffect(stack);
        } else if (item == Items.WRITTEN_BOOK) {
            BookGenerator.applyPassages(stack);
        } else if (item == Items.FIREWORK_ROCKET) {
            FireworkGenerator.applyFirework(stack);
        } else if (item == Items.FIREWORK_STAR) {
            FireworkGenerator.applyFireworkStar(stack);
        } else if (item == Items.GOAT_HORN) {
            GoatHornGenerator.applyGoatHornSound(stack);
        }
        return stack;
    }

    public static ItemStack getStackFor(ItemStack stack) {
        return getStackFor(stack.getItem(), stack.getCount(), stack.getTag());
    }

    public static ItemStack getStackFor(Item vanilla, int count, CompoundTag tag) {
        return ItemRandomMapData.getStackFor(vanilla, count, tag);
    }

    public static TagKey<Item> getTagKeyFor(TagKey<Item> vanilla) {
        return ItemRandomMapData.getTagKeyFor(vanilla);
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


        private final Map<Item, Item> ITEM_MAP = new Object2ObjectOpenHashMap<>();
        private final Map<TagKey<Item>, TagKey<Item>> TAGKEY_MAP = new Object2ObjectOpenHashMap<>();
        public static final ItemRandomMapData INSTANCE = new ItemRandomMapData();

        public static SavedData.Factory<ItemRandomMapData> factory() {
            return new Factory<>(ItemRandomMapData::new, ItemRandomMapData::load, DataFixTypes.LEVEL);
        }

        public void configure() {
            generateItemMap();
            generateTagMap();
            setDirty();
        }

        @Override
        public @NotNull CompoundTag save(CompoundTag tag) {
            RandomizerCore.LOGGER.warn("Saving changed loot tables to world data!");
            ListTag map = new ListTag();
            this.ITEM_MAP.forEach((vanilla, random) -> {
                CompoundTag itemPair = new CompoundTag();
                if (random == Items.AIR) return;

                itemPair.putString(vanilla.toString(), random.toString());
                map.add(itemPair);
            });
            tag.put("item_map", map);
            return tag;
        }

        public static ItemRandomMapData load(CompoundTag tag) {
            RandomizerCore.LOGGER.warn("Loading changed loot tables to world data!");
            CompoundTag map = tag.getCompound("item_map");
            VALID_ITEMS.forEach((specialItem) -> {
                Item vanilla = specialItem.item;
                Item random = ForgeRegistries.ITEMS.getValue(new ResourceLocation(map.getString(vanilla.toString())));
                if (random == Items.AIR) return;

                INSTANCE.ITEM_MAP.put(vanilla, random);
            });
            INSTANCE.setDirty();
            return INSTANCE;
        }

        private void generateTagMap() {
            ITagManager<Item> tagManager = ForgeRegistries.ITEMS.tags();
            if (tagManager == null) return;

            List<TagKey<Item>> vanilla = tagManager.getTagNames().toList();
            List<TagKey<Item>> randomized = Lists.newArrayList(vanilla);
            Collections.shuffle(randomized, RandomizerCore.rng);

            if (vanilla.size() != randomized.size()) {
                RandomizerCore.LOGGER.warn("Tagkey registry was modified during server start!");
                return;
            }

            for (int i = 0; i < vanilla.size(); i++) {
                if (TAGKEY_MAP.containsKey(vanilla.get(i))) continue;

                TAGKEY_MAP.put(vanilla.get(i), randomized.get(i));
            }
        }

        private void generateItemMap() {
            List<Item> vanilla = Lists.newArrayList(VALID_ITEMS.asItems());
            List<Item> randomized = Lists.newArrayList(vanilla);
            Collections.shuffle(randomized, RandomizerCore.rng);

            if (vanilla.size() != randomized.size()) {
                RandomizerCore.LOGGER.warn("Item registry was modified during server start!");
                return;
            }

            for (int i = 0; i < vanilla.size(); i++) {
                if (ITEM_MAP.containsKey(vanilla.get(i))) continue;

                ITEM_MAP.put(vanilla.get(i), randomized.get(i));
            }
        }

        public static ItemStack getStackFor(ItemStack stack) {
            return getStackFor(stack.getItem(), stack.getCount(), stack.getTag());
        }

        public static ItemStack getStackFor(Item vanilla, int count, CompoundTag tag) {
            Item randomItem = INSTANCE.ITEM_MAP.get(vanilla);
            if (randomItem == null) return ItemStack.EMPTY;

            ItemStack random = new ItemStack(randomItem);
            random.setCount(Math.min(random.getMaxStackSize(), count));
            random.setTag(tag);
            return random;
        }

        public static TagKey<Item> getTagKeyFor(TagKey<Item> vanilla) {
            return INSTANCE.TAGKEY_MAP.get(vanilla);
        }
    }
}
