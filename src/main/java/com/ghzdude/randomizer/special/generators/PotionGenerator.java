package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class PotionGenerator {
    public static final ArrayList<Potion> BLACKLISTED_POTIONS = new ArrayList<>(List.of(
            Potions.EMPTY,
            Potions.AWKWARD,
            Potions.THICK,
            Potions.WATER
    ));

    public static ArrayList<Potion> VALID_POTIONS = new ArrayList<>(
            ForgeRegistries.POTIONS.getValues()
                    .stream()
                    .filter(PotionGenerator.BLACKLISTED_POTIONS::contains)
                    .toList()
    );
    public static ArrayList<MobEffect> VALID_EFFECTS = new ArrayList<>(
            ForgeRegistries.MOB_EFFECTS.getValues()
                    .stream()
                    .filter(mobEffect -> mobEffect.getCategory() != MobEffectCategory.NEUTRAL)
                    .toList()
    );

    public static void applyEffect(ItemStack stack) {

        final RandomSource random = RandomizerCore.RANDOM;

        int id = random.nextInt(VALID_POTIONS.size());
        int numOfEffects = random.nextInt(3) + 1;

        CompoundTag baseTag = new CompoundTag();

        if (stack.getItem() == Items.SUSPICIOUS_STEW) {
            ListTag effects = new ListTag();
            CompoundTag effect = new CompoundTag();

            for (int i = 1; i <= numOfEffects; i++) {
                effect.putInt("EffectDuration", random.nextIntBetweenInclusive(100, 2000));
                effect.putString("forge:effect_id", VALID_POTIONS.get(id).getName("minecraft:"));
                effects.add(effect);
            }

            baseTag.put("Effects", effects);

        } else {
            ListTag effects = new ListTag();

            for (int i = 1; i <= numOfEffects; i++) {
                CompoundTag effect = new CompoundTag();
                effect.putInt("Id", random.nextInt(VALID_EFFECTS.size()));
                effect.putInt("Amplifier", random.nextInt(4) + 1);
                effect.putInt("Duration",random.nextIntBetweenInclusive(200, 2000));
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
}
