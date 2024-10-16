package com.ghzdude.randomizer.api;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

import java.util.function.Predicate;

public final class Loot {
    public interface TableInfo {
        boolean randomizer$hasSilkTouch(RegistryAccess access);
        ItemStack randomizer$getSilkDrop(RegistryAccess access);
        ItemStack randomizer$getDrop(RegistryAccess access);
    }
    public interface PoolInfo {
        boolean randomizer$checkForSilkTouch(Registry<Enchantment> registry);
        Holder<Item> randomizer$getSilkDrop(Registry<Enchantment> registry);
        Holder<Item> randomizer$getDrop(Registry<Enchantment> registry);
    }
    public interface EntryInfo {
        boolean randomizer$hasCondition(LootItemConditionType type, Predicate<LootItemCondition> predicate);
    }
}
