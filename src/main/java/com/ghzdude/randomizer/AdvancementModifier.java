package com.ghzdude.randomizer;

import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdvancementModifier {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void buildAdvancements(ImmutableMap.Builder<Identifier, AdvancementHolder> map) {
        Optional<Registry<Item>> optionalRegistry = RandomizerCore.getRegistry(Registries.ITEM);
        if (optionalRegistry.isEmpty()) {
            LOGGER.error("Failed to randomize advancements");
            return;
        }
        Registry<Item> itemRegistry = optionalRegistry.get();

        Map<Identifier, List<Identifier>> modified = RecipeRandomizer.getModified();
        for (Identifier ing : modified.keySet()) {
            Item[] changedItems;
            Optional<Item> item = itemRegistry.getOptional(ing);
            var tag = itemRegistry.getTags()
                    .map(HolderSet.Named::key)
                    .filter(key -> key.location().equals(ing))
                    .findFirst();

            if (item.isPresent()) {
                changedItems = new Item[]{ item.get() };
            } else if (tag.isPresent()) {
                changedItems = itemRegistry.get(tag.get()).orElseThrow()
                        .stream().map(Holder::get).toArray(Item[]::new);
            } else {
                LOGGER.warn("{} is not a valid item or tag!", ing);
                continue;
            }

            Advancement.Builder builder = new Advancement.Builder();
            AdvancementRewards.Builder rewards = new AdvancementRewards.Builder();
            for (Identifier recipe : modified.get(ing)) {
                rewards.addRecipe(ResourceKey.create(Registries.RECIPE, recipe));
            }
            builder.rewards(rewards);
            builder.addCriterion("has_item", InventoryChangeTrigger.TriggerInstance.hasItems(changedItems));
            String path = "%s-%s_gives_recipes".formatted(ing.getNamespace(), ing.getPath());
            AdvancementHolder toAdd = builder.build(RandomizerUtil.location(path));
            map.put(toAdd.id(), toAdd);
        }
    }

    public static Advancement.Builder modify(Advancement advancement) {
        Map<Identifier, List<Identifier>> modified = RecipeRandomizer.getModified();
        List<ItemPredicate> predicates = new ArrayList<>();
        Registry<Item> itemRegistry = RandomizerCore.getRegistry(Registries.ITEM).orElseThrow();
        Advancement.Builder builder = new Advancement.Builder();
        AdvancementRewards.Builder rewards = new AdvancementRewards.Builder();
        advancement.rewards().recipes().forEach(recipeKey -> {
            predicates.add(ItemPredicate.Builder.item()
                    .of(itemRegistry, modified.get(recipeKey.identifier()).stream()
                            .map(itemRegistry::get)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .map(Holder::get)
                            .toArray(ItemLike[]::new))
                    .build());
            rewards.addRecipe(recipeKey);
        });
        builder.rewards(rewards);

        builder.addCriterion("has_item", InventoryChangeTrigger.TriggerInstance.hasItems(predicates.toArray(new ItemPredicate[0])));
        return builder;
    }
}
