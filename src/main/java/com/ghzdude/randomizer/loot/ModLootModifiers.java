package com.ghzdude.randomizer.loot;

import com.ghzdude.randomizer.RandomizerCore;
import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> RANDOMIZER_MODIFIERS = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, RandomizerCore.MODID);

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> RANDOMIZE_LOOT = RANDOMIZER_MODIFIERS.register("randomize_loot", LootRandomizeModifier.CODEC);

    public static void register(IEventBus bus) {
        RANDOMIZER_MODIFIERS.register(bus);
    }
}
