package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.util.RandomizerUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.SuspiciousStewEffects;

import java.util.*;

public class PotionGenerator {
    public static final List<Potion> BLACKLISTED_POTIONS = List.of(
            Potions.AWKWARD.get(),
            Potions.THICK.get(),
            Potions.WATER.get(),
            Potions.MUNDANE.get()
    );

    public static Map<ResourceLocation, Potion> VALID_POTIONS = new HashMap<>();
    public static List<ResourceLocation> POTION_NAMES = new ArrayList<>();

    public static final Map<ResourceLocation, MobEffect> VALID_EFFECTS = new HashMap<>();
    public static List<ResourceLocation> EFFECT_NAMES = new ArrayList<>();

    private static Registry<MobEffect> EFFECT_REGISTRY;

    public static void init(RegistryAccess access) {
        initEffects(access.registryOrThrow(Registries.MOB_EFFECT));
        initPotions(access.registryOrThrow(Registries.POTION));
    }

    private static void initPotions(Registry<Potion> potions) {
        potions.stream()
                .filter(potion -> !BLACKLISTED_POTIONS.contains(potion))
                .forEach(potion -> {
                    VALID_POTIONS.put(potions.getKey(potion), potion);
                    POTION_NAMES.add(potions.getKey(potion));
                });
    }

    private static void initEffects(Registry<MobEffect> effects) {
        effects.stream().forEach(mobEffect -> {
            var loc = effects.getKey(mobEffect);
            VALID_EFFECTS.put(loc, mobEffect);
            EFFECT_NAMES.add(loc);
        });
        EFFECT_REGISTRY = effects;
    }

    public static void applyEffect(ItemStack stack) {

        final Random rng = RandomizerCore.seededRNG;

        int numOfEffects = rng.nextInt(3) + 1;

        if (stack.getItem() == Items.SUSPICIOUS_STEW) {
            makeStew(stack, rng, numOfEffects);
        } else {
            makePotion(stack, rng, numOfEffects);
        }
    }

    private static void makeStew(ItemStack stack, Random rng, int numOfEffects) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("randomizer.stew.lore.1"));
        lore.add(Component.translatable("randomizer.stew.lore.2", numOfEffects));
        stack.set(DataComponents.LORE, new ItemLore(lore));

        List<ResourceLocation> list = new ArrayList<>(numOfEffects);

        addEffects(list, numOfEffects);

        var effects = list.stream()
                .map(EFFECT_REGISTRY::get)
                .filter(Objects::nonNull)
                .map(EFFECT_REGISTRY::wrapAsHolder)
                .map(holder -> new SuspiciousStewEffects.Entry(holder, rng.nextInt(100, 2001)))
                .toList();

        stack.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, new SuspiciousStewEffects(effects));
    }

    private static void makePotion(ItemStack stack, Random rng, int numOfEffects) {
        List<ResourceLocation> list = new ArrayList<>(numOfEffects);

        addEffects(list, numOfEffects);

        var effects = list.stream()
                .map(loc -> Holder.direct(VALID_EFFECTS.get(loc)))
                .map(holder -> new MobEffectInstance(holder, rng.nextInt(200, 2001), rng.nextInt(4) + 1))
                .toList();

        int color = rng.nextInt(0x00FFFFFF);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.of(Potions.WATER), Optional.of(color), effects));

        Component itemType = Component.translatable(stack.getItem().getDescriptionId());
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("randomizer.potion_title", itemType));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("randomizer.potion_lore", itemType));
        stack.set(DataComponents.LORE, new ItemLore(lore));
    }

    private static void addEffects(List<ResourceLocation> list, int amount) {
        for (int i = 0; i < amount; i++) {
            var loc = RandomizerUtil.getRandom(EFFECT_NAMES, RandomizerCore.unseededRNG);
            if (list.contains(loc)) {
                --i;
                continue;
            }
            list.add(loc);
        }
    }
}
