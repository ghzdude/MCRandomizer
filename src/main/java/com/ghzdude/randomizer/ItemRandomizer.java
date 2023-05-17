package com.ghzdude.randomizer;

import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.registries.ForgeRegistries;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;

/* Item Randomizer Description
 * Goal is to give the player a random item every so often DONE
 * Every so often, points are added to a counter DONE
 * over time, the amount of points gained each cycle is increased
 * those points are then used to give the player an item DONE
 * more points give bigger stacksize of item at once DONE
 * items have a defined value, otherwise stacksize is used
 */
public class ItemRandomizer {
    private int points;
    private int pointMax;
    private int amtItemsGiven;
    private int offset = 0;
    private static final RandomSource random = RandomSource.create();
    protected final SpecialItemList validItems;

    ItemRandomizer (ArrayList<SpecialItem> validItems) {

        int lastMatch = -1;
        validItems.removeIf(specialItem -> SpecialItems.BLACKLISTED_ITEMS.contains(specialItem.item));
        for (int i = 0; i < validItems.size(); i++) {
            SpecialItem toUpdate = validItems.get(i);
            int match;

            if (SpecialItems.SPECIAL_ITEMS.contains(toUpdate)) {
                match = SpecialItems.SPECIAL_ITEMS.indexOf(toUpdate);
                lastMatch = match;
                validItems.set(i, SpecialItems.SPECIAL_ITEMS.get(match));
            } else if (SpecialItems.EFFECT_ITEMS.contains(toUpdate)) {
                match = SpecialItems.EFFECT_ITEMS.indexOf(toUpdate);
                lastMatch = match;
                validItems.set(i, SpecialItems.EFFECT_ITEMS.get(match));
            }

            if (toUpdate.item.toString().contains("shulker_box")){
                validItems.get(i).value = SpecialItems.SPECIAL_ITEMS.get(lastMatch).value;
            }
        }

        this.validItems = new SpecialItemList(validItems);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event){
        if (event.side == LogicalSide.CLIENT) return;
        if (event.phase == TickEvent.Phase.END) return;
        if (offset < 0) offset = 0;
        offset++;

        if (offset % RandomizerConfig.getCooldown() == 0) {
            points = pointMax;

            Player player = event.player;
            if (player.getInventory().getFreeSlot() == -1) return;

            int tries = 0;

            // use a random amount of points
            int pointsToUse = random.nextIntBetweenInclusive(1, points);
            points -= pointsToUse;

            // try to give up to five items while there are points to use
            while (tries < 5 && pointsToUse > 0) {
                SpecialItem selectedItem = getRandomItem();

                if (selectedItem.value > pointsToUse) continue;

                int amtToGive = Math.floorDiv(pointsToUse, selectedItem.value);
                ItemStack stack = new ItemStack(selectedItem.item);

                stack.setCount(Math.min(amtToGive, stack.getMaxStackSize()));

                if (selectedItem.item == Items.WRITTEN_BOOK){
                    getRandomBook(stack);
                }

                if (SpecialItems.EFFECT_ITEMS.contains(selectedItem)) {
                    applyEffect(stack);
                }
                int pointsUsed = stack.getCount() * selectedItem.value;

                pointsToUse -= pointsUsed;
                addStackToPlayer(stack, player.getInventory(), pointsUsed);
                tries++;
            }
        }
    }

    private SpecialItem getRandomItem() {
        int id = random.nextInt(validItems.size());
        return validItems.get(id);
    }

    private void getRandomBook(ItemStack stack) {
        // pages -> {ListTag@23688}  size = 1
        //      '{"text":"proper book"}'
        // author -> {StringTag@23690} ""Dev""
        // filtered_title -> {StringTag@23692} ""krk""
        // title -> {StringTag@23694} ""krk""

        // player.getInventory().getItem(5).getTag().put("pages", new ListTag(){{add(StringTag.valueOf("{\"text\":\"improper book\"}"));}})

        CompoundTag tag = new CompoundTag();
        ListTag pages = new ListTag();
        StringTag author = StringTag.valueOf("\"Amonga\"");
        StringTag filteredTitle = StringTag.valueOf("\"The Book of Sus\"");
        StringTag title = StringTag.valueOf("\"The Book of Sus\"");

        // each page has a max of 798 characters
        // each book has a max of 100 pages in JE
        pages.add(0, StringTag.valueOf("{\"text\":\"they are among us\"}"));
        tag.put("pages", pages);
        tag.put("author", author);
        tag.put("filtered_title", filteredTitle);
        tag.put("title", title);
        stack.setTag(tag);
    }

    private void applyEffect(ItemStack stack) {
        // Potion -> {StringTag@24140} ""minecraft:swiftness""

        // Effects -> {ListTag@24241}  size = 1
        //      Compound Tag ->
        //          "EffectDuration" -> {IntTag@24252} "120"
        //          "forge:effect_id" -> {StringTag@24254} ""minecraft:jump_boost""
        //          "EffectId" -> {IntTag@24256} "8"

        ArrayList<Potion> potions = new ArrayList<>(ForgeRegistries.POTIONS.getValues());
        ArrayList<MobEffect> mobEffects = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
        potions.removeIf(potion -> potion == Potions.EMPTY);

        int id = random.nextInt(potions.size());
        int numOfEffects = random.nextInt(3) + 1;

        CompoundTag baseTag = new CompoundTag();

        if (stack.getItem() == Items.SUSPICIOUS_STEW) {
            ListTag effects = new ListTag();
            CompoundTag effect = new CompoundTag();

            for (int i = 1; i <= numOfEffects; i++) {
                effect.putInt("EffectDuration", random.nextIntBetweenInclusive(100, 2000));
                effect.putString("forge:effect_id", potions.get(id).getName("minecraft:"));
                effects.add(effect);
            }

            baseTag.put("Effects", effects);

        } else {
            ListTag effects = new ListTag();

            for (int i = 1; i <= numOfEffects; i++) {
                CompoundTag effect = new CompoundTag();
                effect.putInt("Id", random.nextInt(mobEffects.size()));
                effect.putInt("Amplifier", random.nextInt(4) + 1);
                effect.putInt("Duration",random.nextIntBetweenInclusive(100, 2000));
                effect.putBoolean("ShowIcon", true);
                effects.add(effect);
            }
            baseTag.put("CustomPotionEffects", effects);
            baseTag.putString("Potion", "minecraft:water");

            baseTag.putInt("CustomPotionColor", random.nextInt(HexFormat.fromHexDigits("00FFFFFF")));

            CompoundTag displayTag = new CompoundTag();

            String itemType = stack.getItem() == Items.TIPPED_ARROW ? "Arrow" : "Potion";
            displayTag.putString("Name", String.format("\"Randomly Generated %s\"", itemType));

            ListTag lore = new ListTag();
            lore.add(StringTag.valueOf(String.format("\"A randomly generated %s from the Gods\"", itemType)));
            displayTag.put("Lore", lore);
            baseTag.put("display", displayTag);

        }
        stack.setTag(baseTag);
    }

    private void addStackToPlayer(ItemStack stack, Inventory inventory, int pointsUsed) {
        inventory.player.sendSystemMessage(Component.translatable("player.recieved.item",  stack.copy(), inventory.player.getDisplayName().getString(), pointsUsed));
        inventory.add(stack);
        amtItemsGiven++;
        if (amtItemsGiven % 20 == 0) {
            inventory.player.sendSystemMessage(Component.translatable("player.points.increased", amtItemsGiven));
            pointMax++;
        }
    }

    @SubscribeEvent
    public void onLogin (PlayerEvent.PlayerLoggedInEvent player) {
        CompoundTag tag = player.getEntity().getPersistentData();
        this.points = tag.getInt("points");
        this.pointMax = tag.getInt("point_max");
        this.amtItemsGiven = tag.getInt("amount_items_given");

        this.points = Math.max(this.points, 0);
        this.pointMax = Math.max(this.pointMax, 1);
        this.amtItemsGiven = Math.max(this.amtItemsGiven, 0);
    }
    
    @SubscribeEvent
    public void onLogout (PlayerEvent.PlayerLoggedOutEvent player) {
        CompoundTag tag = player.getEntity().getPersistentData();
        tag.putInt("points", this.points);
        tag.putInt("point_max", this.pointMax);
        tag.putInt("amount_items_given", this.amtItemsGiven);
    }

    protected static class SpecialItems {
        protected static final ArrayList<Item> BLACKLISTED_ITEMS = new ArrayList<>(Arrays.asList(
                Items.AIR,
                Items.COMMAND_BLOCK,
                Items.COMMAND_BLOCK_MINECART,
                Items.CHAIN_COMMAND_BLOCK,
                Items.REPEATING_COMMAND_BLOCK,
                Items.BARRIER,
                Items.LIGHT,
                Items.STRUCTURE_BLOCK,
                Items.STRUCTURE_VOID,
                Items.WRITTEN_BOOK,
                Items.KNOWLEDGE_BOOK,
                Items.JIGSAW
        ));

        /* create special logic for...
         * Written Book
         * Tipped Arrow
         * potions
         * sus stew
         */

        /* give point values to
         * wodden tools - 1 point
         * stone tools - 2 point
         * iron tools - 3 point
         * diamond tools - 5 point
         * netherite tools - 9 point
         * nether star - 15 points
         * shulker boxes 6 points
         * chest 3 points
         * bundle 6 points
         * written book 4 points
         * potions 4 points
         * splash/lingering potions 6 points
         * tipped arrows 6 points
         * villager stations 6-15
         * furnace 2 points
         * blast furnace 5 points
         * cooker 4 points
         * campfire 2 points
         * im fucking tired lol
         */

        protected static final SpecialItemList EFFECT_ITEMS = new SpecialItemList(Arrays.asList(
                new SpecialItem(Items.POTION, 4),
                new SpecialItem(Items.SPLASH_POTION, 6),
                new SpecialItem(Items.LINGERING_POTION, 6),
                new SpecialItem(Items.TIPPED_ARROW, 6),
                new SpecialItem(Items.SUSPICIOUS_STEW, 4)
        ));

        protected static final SpecialItemList SPECIAL_ITEMS = new SpecialItemList(Arrays.asList(
            new SpecialItem(Items.WOODEN_PICKAXE),
            new SpecialItem(Items.WOODEN_AXE),
            new SpecialItem(Items.WOODEN_HOE),
            new SpecialItem(Items.WOODEN_SHOVEL),
            new SpecialItem(Items.WOODEN_SWORD),

            new SpecialItem(Items.GOLDEN_PICKAXE, 2),
            new SpecialItem(Items.GOLDEN_AXE, 2),
            new SpecialItem(Items.GOLDEN_HOE, 2),
            new SpecialItem(Items.GOLDEN_SHOVEL, 2),
            new SpecialItem(Items.GOLDEN_SWORD, 2),

            new SpecialItem(Items.STONE_PICKAXE, 2),
            new SpecialItem(Items.STONE_AXE, 2),
            new SpecialItem(Items.STONE_HOE, 2),
            new SpecialItem(Items.STONE_SHOVEL, 2),
            new SpecialItem(Items.STONE_SWORD, 2),

            new SpecialItem(Items.IRON_PICKAXE, 3),
            new SpecialItem(Items.IRON_AXE, 3),
            new SpecialItem(Items.IRON_HOE, 3),
            new SpecialItem(Items.IRON_SHOVEL, 3),
            new SpecialItem(Items.IRON_SWORD, 3),

            new SpecialItem(Items.DIAMOND_PICKAXE, 5),
            new SpecialItem(Items.DIAMOND_AXE, 5),
            new SpecialItem(Items.DIAMOND_HOE, 5),
            new SpecialItem(Items.DIAMOND_SHOVEL, 5),
            new SpecialItem(Items.DIAMOND_SWORD, 5),

            new SpecialItem(Items.NETHERITE_PICKAXE, 9),
            new SpecialItem(Items.NETHERITE_AXE, 9),
            new SpecialItem(Items.NETHERITE_HOE, 9),
            new SpecialItem(Items.NETHERITE_SHOVEL, 9),
            new SpecialItem(Items.NETHERITE_SWORD, 9),

            new SpecialItem(Items.NETHER_STAR, 15),
            new SpecialItem(Items.CHEST, 3),
            new SpecialItem(Items.TRAPPED_CHEST, 3),
            new SpecialItem(Items.SHULKER_BOX, 6),
            new SpecialItem(Items.BUNDLE, 6),
            new SpecialItem(Items.WRITTEN_BOOK, 4),

            // villager stations
            new SpecialItem(Items.GRINDSTONE, 6),
            new SpecialItem(Items.FLETCHING_TABLE, 3),
            new SpecialItem(Items.ANVIL, 6),
            new SpecialItem(Items.CHIPPED_ANVIL, 4),
            new SpecialItem(Items.DAMAGED_ANVIL, 2),
            new SpecialItem(Items.COMPOSTER, 8),
            new SpecialItem(Items.STONECUTTER, 6),
            new SpecialItem(Items.BLAST_FURNACE, 3),
            new SpecialItem(Items.SMOKER, 3),

            new SpecialItem(Items.CAMPFIRE, 2),
            new SpecialItem(Items.SOUL_CAMPFIRE, 2)
        ));
    }

    protected static class SpecialItem {
        public Item item;
        public int value;

        SpecialItem(Item item, int value) {
            this.item = item;
            this.value = value;
        }

        SpecialItem(Item item) {
            this(item, 1);
        }
    }
}
