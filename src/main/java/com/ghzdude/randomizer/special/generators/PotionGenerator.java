package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class PotionGenerator {
    public static final ArrayList<Potion> BLACKLISTED_POTIONS = new ArrayList<>(List.of(
            Potions.AWKWARD.get(),
            Potions.THICK.get(),
            Potions.WATER.get(),
            Potions.MUNDANE.get()
    ));

    public static Map<ResourceLocation, Potion> VALID_POTIONS = new HashMap<>();
    public static List<ResourceLocation> POTION_NAMES = new ArrayList<>();

//    public static ArrayList<Map.Entry<ResourceKey<Potion>, Potion>> VALID_POTIONS = new ArrayList<>(
//            ForgeRegistries.POTIONS.getEntries().stream()
//                    .filter(entry -> !BLACKLISTED_POTIONS.contains(entry.getValue()))
//                    .toList()
//    );
//    public static ArrayList<Map.Entry<ResourceKey<MobEffect>, MobEffect>> VALID_EFFECTS = new ArrayList<>(
//            ForgeRegistries.MOB_EFFECTS.getEntries().stream()
//                    .filter(entry -> entry.getValue().getCategory() != MobEffectCategory.NEUTRAL)
//                    .toList()
//    );

    public static final Map<ResourceLocation, MobEffect> VALID_EFFECTS = new HashMap<>();
    public static List<ResourceLocation> EFFECT_NAMES = new ArrayList<>();

    public static void initPotions(Registry<Potion> potions) {
        potions.stream()
                .filter(potion -> !BLACKLISTED_POTIONS.contains(potion))
                .forEach(potion -> {
                    VALID_POTIONS.put(potions.getKey(potion), potion);
                    POTION_NAMES.add(potions.getKey(potion));
                });
    }

    public static void initEffects(Registry<MobEffect> effects) {
        effects.stream().forEach(mobEffect -> {
            var loc = effects.getKey(mobEffect);
            VALID_EFFECTS.put(loc, mobEffect);
            EFFECT_NAMES.add(loc);
        });
    }

    public static void applyEffect(ItemStack stack) {

        final Random rng = RandomizerCore.seededRNG;

        int numOfEffects = rng.nextInt(3) + 1;

        if (stack.getItem() == Items.SUSPICIOUS_STEW) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("\"A randomly generated stew from the Gods!\""));
            lore.add(Component.literal(String.format("\"Has [%d] effect(s)\"", numOfEffects)));
            stack.set(DataComponents.LORE, new ItemLore(lore));

            List<ResourceLocation> list = new ArrayList<>();
            for (int i = 0; i < numOfEffects; i++) {
                int id = rng.nextInt(VALID_EFFECTS.size());
                if (list.contains(EFFECT_NAMES.get(id))) {
                    --i;
                    continue;
                }
                list.add(EFFECT_NAMES.get(id));
            }

            var effects = list.stream()
                    .map(loc -> Holder.direct(VALID_EFFECTS.get(loc)))
                    .map(holder -> new SuspiciousStewEffects.Entry(holder, rng.nextInt(100, 2001)))
                    .toList();

            stack.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, new SuspiciousStewEffects(effects));

        } else {
            List<ResourceLocation> list = new ArrayList<>(numOfEffects);

            for (int i = 1; i <= numOfEffects; i++) {
                int id = rng.nextInt(EFFECT_NAMES.size());
                var loc = EFFECT_NAMES.get(id);
                if (list.contains(loc)) {
                    --i;
                    continue;
                }
                list.add(loc);
//                CompoundTag effect = new CompoundTag();
//                effect.putString("id", VALID_EFFECTS.get(id).getKey().location().toString());
//                effect.putInt("duration",rng.nextInt(200, 2001));
//                effect.putInt("amplifier", rng.nextInt(4) + 1);
//                effect.putBoolean("show_icon", true);
//                effects.add(effect);
            }

            var effects = list.stream()
                    .map(loc -> Holder.direct(VALID_EFFECTS.get(loc)))
                    .map(holder -> new MobEffectInstance(holder, rng.nextInt(200, 2001), rng.nextInt(4) + 1))
                    .toList();

            int color = rng.nextInt(HexFormat.fromHexDigits("00FFFFFF"));
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.of(Potions.WATER), Optional.of(color), effects));

            String itemType = stack.getItem() == Items.TIPPED_ARROW ? "Arrow" : "Potion";
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(String.format("\"Randomly Generated %s\"", itemType)));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal(String.format("\"A randomly generated %s from the Gods!\"", itemType)));
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
    }
}
