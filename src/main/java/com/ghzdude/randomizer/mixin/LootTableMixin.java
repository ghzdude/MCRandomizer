package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.api.Loot;
import net.minecraft.advancements.critereon.EnchantmentPredicate;
import net.minecraft.advancements.critereon.ItemEnchantmentsPredicate;
import net.minecraft.advancements.critereon.ItemSubPredicates;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.CompositeEntryBase;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.function.Predicate;

import static net.minecraft.world.level.storage.loot.predicates.LootItemConditions.MATCH_TOOL;

@Mixin(LootTable.class)
public class LootTableMixin implements Loot.TableInfo {

    @Shadow @Final private List<LootPool> pools;
    @Unique
    private ItemStack randomizer$silkDrop = null;
    @Unique
    private ItemStack randomizer$drop = null;

    @Override
    public boolean randomizer$hasSilkTouch(RegistryAccess access) {
        var registry = access.registryOrThrow(Registries.ENCHANTMENT);
        for (var p : pools) {
            if (randomizer$drop == null) {
                var a = ((Loot.PoolInfo) p).randomizer$getDrop(registry);
                randomizer$drop = new ItemStack(a.get());
            }
            if (((Loot.PoolInfo) p).randomizer$checkForSilkTouch(registry)) {
                if (randomizer$silkDrop == null) {
                    var a = ((Loot.PoolInfo) p).randomizer$getSilkDrop(registry);
                    randomizer$silkDrop = new ItemStack(a.get());
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack randomizer$getSilkDrop(RegistryAccess access) {
        return randomizer$silkDrop == null ? ItemStack.EMPTY : randomizer$silkDrop;
    }

    @Override
    public ItemStack randomizer$getDrop(RegistryAccess access) {
        return randomizer$drop == null ? ItemStack.EMPTY : randomizer$drop;
    }

    @Mixin(LootPool.class)
    public static class LootPoolMixin implements Loot.PoolInfo {

        @Shadow @Final private List<LootPoolEntryContainer> entries;

        @Shadow @Final private List<LootItemCondition> conditions;

        @Override
        public boolean randomizer$checkForSilkTouch(Registry<Enchantment> registry) {
            final var silkTouch = registry.getHolderOrThrow(Enchantments.SILK_TOUCH);

            for (var c : conditions) {
                if (c.getType() == MATCH_TOOL) {
                    if (test(c, silkTouch)) {
                        return true;
                    }
                }
            }

            for (var e : entries) {
                if (e instanceof CompositeAccessor accessor) {
                    for (var extra : accessor.getChildren()) {
                        if (((Loot.EntryInfo) extra).randomizer$hasCondition(MATCH_TOOL, condition -> test(condition, silkTouch))) {
                            return true;
                        }
                    }
                } else {
                    boolean hasCondition = ((Loot.EntryInfo) e).randomizer$hasCondition(MATCH_TOOL, condition -> test(condition, silkTouch));
                    if (hasCondition) return true;
                }
            }
            return false;
        }

        @Override
        public Holder<Item> randomizer$getSilkDrop(Registry<Enchantment> registry) {
            final var silkTouch = registry.getHolderOrThrow(Enchantments.SILK_TOUCH);

            for (var c : conditions) {
                if (c.getType() == MATCH_TOOL) {
                    if (test(c, silkTouch) && entries.getFirst() instanceof ItemAccessor accessor) {
                        return accessor.getItem();
                    }
                }
            }

            for (var e : entries) {
                if (e instanceof CompositeAccessor accessor) {
                    for (var extra : accessor.getChildren()) {
                        if (((Loot.EntryInfo) extra).randomizer$hasCondition(MATCH_TOOL, condition -> test(condition, silkTouch))) {
                            if (extra instanceof ItemAccessor accessor1)
                                return accessor1.getItem();
                        }
                    }
                } else {
                    boolean hasCondition = ((Loot.EntryInfo) e).randomizer$hasCondition(MATCH_TOOL, condition -> test(condition, silkTouch));
                    if (hasCondition && e instanceof ItemAccessor accessor)
                        return accessor.getItem();
                }
            }
            return Holder.direct(Items.AIR);
        }

        @Override
        public Holder<Item> randomizer$getDrop(Registry<Enchantment> registry) {
            final var silkTouch = registry.getHolderOrThrow(Enchantments.SILK_TOUCH);

            for (var c : conditions) {
                if (c.getType() == MATCH_TOOL) {
                    if (!test(c, silkTouch) && entries.getFirst() instanceof ItemAccessor accessor) {
                        return accessor.getItem();
                    }
                }
            }

            for (var e : entries) {
                if (e instanceof CompositeAccessor accessor) {
                    for (var extra : accessor.getChildren()) {
                        if (!((Loot.EntryInfo) extra).randomizer$hasCondition(MATCH_TOOL, condition -> test(condition, silkTouch))) {
                            if (extra instanceof ItemAccessor accessor1)
                                return accessor1.getItem();
                        }
                    }
                } else {
                    boolean hasCondition = ((Loot.EntryInfo) e).randomizer$hasCondition(MATCH_TOOL, condition -> test(condition, silkTouch));
                    if (!hasCondition && e instanceof ItemAccessor accessor)
                        return accessor.getItem();
                }
            }
            return Holder.direct(Items.AIR);
        }

        private boolean test(LootItemCondition condition, Holder<Enchantment> holder) {
            var optional = ((MatchTool) condition).predicate();
            if (optional.isEmpty()) return false;

            var ench = optional.get().subPredicates().get(ItemSubPredicates.ENCHANTMENTS);
            for (EnchantmentPredicate enchantmentPredicate : ((ItemEnchantAccessor) ench).invokeEnchantments()) {
                if (enchantmentPredicate.enchantments().isPresent())
                    return enchantmentPredicate.enchantments().get().contains(holder);
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

    @Mixin(CompositeEntryBase.class)
    interface CompositeAccessor {
        @Accessor
        List<LootPoolEntryContainer> getChildren();
    }

    @Mixin(LootItem.class)
    interface ItemAccessor {
        @Accessor
        Holder<Item> getItem();
    }
}
