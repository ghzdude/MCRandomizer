package com.ghzdude.randomizer;

import com.ghzdude.randomizer.special.SpecialItem;
import com.ghzdude.randomizer.special.SpecialItemList;
import com.ghzdude.randomizer.special.SpecialItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
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
    private static final SpecialItemList VALID_ITEMS = new SpecialItemList(configureValidItem());

    @SuppressWarnings("SuspiciousMethodCalls")
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

    public static int GiveRandomItem(int pointsToUse, Player player){
        int tries = 0;
        Inventory playerInventory = player.getInventory();

        // try to give up to five items while there are points to use
        while (tries < 5 && pointsToUse > 0) {
            SpecialItem selectedItem = getRandomItem();

            if (selectedItem.value > pointsToUse) continue;

            int amtToGive = Math.floorDiv(pointsToUse, selectedItem.value);
            ItemStack stack = specialItemToStack(selectedItem);

            stack.setCount(Math.min(amtToGive, stack.getMaxStackSize()));

            int pointsUsed = stack.getCount() * selectedItem.value;

            pointsToUse -= pointsUsed;
            if (playerInventory.getFreeSlot() == -1) {
                Entity itemEnt = stack.getEntityRepresentation();
                if (itemEnt != null) {
                    itemEnt.setPos(playerInventory.player.position());
                    playerInventory.player.getLevel().addFreshEntity(itemEnt);
                }
            } else {
                addStackToPlayer(stack, playerInventory, pointsUsed);
            }
            tries++;
        }
        return pointsToUse;
    }

    public static SpecialItem getRandomItem() {
        int id = RandomizerCore.RANDOM.nextInt(VALID_ITEMS.size());
        return VALID_ITEMS.get(id);
    }

    public static ItemStack specialItemToStack (SpecialItem item) {
        ItemStack stack = new ItemStack(item.item);

        // do something about goat horns and fireworks
        if (item.item == Items.WRITTEN_BOOK){
            getRandomBook(stack);
        } else if (SpecialItems.EFFECT_ITEMS.contains(item)) {
            applyEffect(stack);
        }

        return stack;
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    public static SpecialItem getRandomSimpleItem() {
        SpecialItem item;
        do {
            item = getRandomItem();
        } while (SpecialItems.BLACKLISTED_ITEMS.contains(item) || SpecialItems.EFFECT_ITEMS.contains(item));
        return item;
    }

    private static void getRandomBook(ItemStack stack) {
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

    private static void applyEffect(ItemStack stack) {
        // Potion -> {StringTag@24140} ""minecraft:swiftness""

        // Effects -> {ListTag@24241}  size = 1
        //      Compound Tag ->
        //          "EffectDuration" -> {IntTag@24252} "120"
        //          "forge:effect_id" -> {StringTag@24254} ""minecraft:jump_boost""
        //          "EffectId" -> {IntTag@24256} "8"

        ArrayList<Potion> potions = new ArrayList<>(ForgeRegistries.POTIONS.getValues());
        ArrayList<MobEffect> mobEffects = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
        potions.removeIf(potion -> potion == Potions.EMPTY);

        final RandomSource random = RandomizerCore.RANDOM;

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

    private static void addStackToPlayer(ItemStack stack, Inventory inventory, int pointsUsed) {
        RandomizerCore.LOGGER.warn(String.format("Given %s to %s, %d points used.",  stack.copy(), inventory.player.getDisplayName().getString(), pointsUsed));
        // deal with inventory being full, maybe drop item to ground?

        inventory.add(stack);
        RandomizerCore.incrementAmtItemsGiven();
    }
}
