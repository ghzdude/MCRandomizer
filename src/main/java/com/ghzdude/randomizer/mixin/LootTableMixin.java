package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.api.Loot;
import net.minecraft.advancements.critereon.EnchantmentPredicate;
import net.minecraft.advancements.critereon.ItemEnchantmentsPredicate;
import net.minecraft.advancements.critereon.ItemSubPredicates;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.function.Predicate;

import static net.minecraft.world.level.storage.loot.predicates.LootItemConditions.MATCH_TOOL;

@Mixin(LootTable.class)
public class LootTableMixin implements Loot.TableInfo {

    @Shadow @Final private List<LootPool> pools;

    @Override
    public boolean randomizer$requiresSilkTouch(RegistryAccess access) {
        for (var p : pools) {
            if (((Loot.PoolInfo) p).randomizer$checkForSilkTouch(access.registryOrThrow(Registries.ENCHANTMENT))) {
                return true;
            }
        }
        return false;
    }

    @Mixin(LootPool.class)
    public static class LootPoolMixin implements Loot.PoolInfo {

        @Shadow @Final private List<LootPoolEntryContainer> entries;

        @Override
        public boolean randomizer$checkForSilkTouch(Registry<Enchantment> registry) {
            final var silkTouch = registry.getHolderOrThrow(Enchantments.SILK_TOUCH);

            for (var e : entries) {
                if (e instanceof Loot.EntryInfo info) {
                    return info.randomizer$hasCondition(MATCH_TOOL, condition -> {
                        var optional = ((MatchTool) condition).predicate();
                        if (optional.isEmpty()) return false;

                        var ench = optional.get().subPredicates().get(ItemSubPredicates.ENCHANTMENTS);
                        for (EnchantmentPredicate enchantmentPredicate : ((ItemEnchantAccessor) ench).invokeEnchantments()) {
                            if (enchantmentPredicate.enchantments().isPresent())
                                return enchantmentPredicate.enchantments().get().contains(silkTouch);
                        }
                        return false;
                    });
                }
            }
            return false;
        }
    }

    @Mixin(LootPoolEntryContainer.class)
    public static class EntryMixin implements Loot.EntryInfo {
        @Shadow @Final protected List<LootItemCondition> conditions;

        public boolean randomizer$hasCondition(LootItemConditionType type, Predicate<LootItemCondition> predicate) {
            for (var c : conditions) {
                if (c.getType() == type) {
                    return predicate.test(c);
                }
            }
            return false;
        }
    }

    @Mixin(ItemEnchantmentsPredicate.class)
    interface ItemEnchantAccessor {

        @Invoker
        List<EnchantmentPredicate> invokeEnchantments();
    }
}
