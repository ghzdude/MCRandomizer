package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class PotionGenerator {
    public static final ArrayList<Potion> BLACKLISTED_POTIONS = new ArrayList<>(List.of(
            Potions.EMPTY,
            Potions.AWKWARD,
            Potions.THICK,
            Potions.WATER
    ));

    public static ArrayList<Map.Entry<ResourceKey<Potion>, Potion>> VALID_POTIONS = new ArrayList<>(
            ForgeRegistries.POTIONS.getEntries().stream()
                    .filter(entry -> !BLACKLISTED_POTIONS.contains(entry.getValue()))
                    .toList()
    );
    public static ArrayList<Map.Entry<ResourceKey<MobEffect>, MobEffect>> VALID_EFFECTS = new ArrayList<>(
            ForgeRegistries.MOB_EFFECTS.getEntries().stream()
                    .filter(entry -> entry.getValue().getCategory() != MobEffectCategory.NEUTRAL)
                    .toList()
    );

    public static void applyEffect(ItemStack stack) {

        final Random rng = RandomizerCore.seededRNG;

        int numOfEffects = rng.nextInt(3) + 1;

        CompoundTag baseTag = new CompoundTag();

        if (stack.getItem() == Items.SUSPICIOUS_STEW) {
            ListTag effects = new ListTag();

            for (int i = 1; i <= numOfEffects; i++) {
                int id = rng.nextInt(VALID_POTIONS.size());

                CompoundTag effect = new CompoundTag();
                effect.putInt("EffectDuration", rng.nextInt(100, 2001));
                effect.putString("forge:effect_id", VALID_POTIONS.get(id).getKey().location().toString());
                effects.add(effect);
            }
            baseTag.put("effects", effects);

            CompoundTag displayTag = new CompoundTag();

            ListTag lore = new ListTag();
            lore.add(StringTag.valueOf("\"A randomly generated stew from the Gods!\""));
            lore.add(StringTag.valueOf(String.format("\"Has [%d] effect(s)\"", numOfEffects)));
            displayTag.put("Lore", lore);
            baseTag.put("display", displayTag);

        } else {
            ListTag effects = new ListTag();

            for (int i = 1; i <= numOfEffects; i++) {
                int id = rng.nextInt(VALID_EFFECTS.size());

                CompoundTag effect = new CompoundTag();
                effect.putString("Id", VALID_EFFECTS.get(id).getKey().location().toString());
                effect.putInt("Duration",rng.nextInt(200, 2001));
                effect.putInt("Amplifier", rng.nextInt(4) + 1);
                effect.putBoolean("ShowIcon", true);
                effects.add(effect);
            }
            baseTag.put("CustomPotionEffects", effects);
            baseTag.putString("Potion", "minecraft:water");

            baseTag.putInt("CustomPotionColor", rng.nextInt(HexFormat.fromHexDigits("00FFFFFF")));

            CompoundTag displayTag = new CompoundTag();

            String itemType = stack.getItem() == Items.TIPPED_ARROW ? "Arrow" : "Potion";
            displayTag.putString("Name", String.format("\"Randomly Generated %s\"", itemType));

            ListTag lore = new ListTag();
            lore.add(StringTag.valueOf(String.format("\"A randomly generated %s from the Gods!\"", itemType)));
            displayTag.put("Lore", lore);
            baseTag.put("display", displayTag);

        }
        stack.setTag(baseTag);
    }
}
