package com.ghzdude.randomizer;

import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class AdvancementModifier {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static Advancement.Builder modify(Advancement advancement) {
        List<ItemPredicate> predicates = new ArrayList<>();
        Registry<Item> itemRegistry = RandomizerCore.getRegistry(Registries.ITEM).orElseThrow();
        Advancement.Builder builder = new Advancement.Builder();
        AdvancementRewards.Builder rewards = new AdvancementRewards.Builder();
        advancement.rewards().recipes().forEach(recipeKey -> {
            ItemPredicate.Builder itemBuilder = ItemPredicate.Builder.item();
            RecipeRandomizer.getIngredients(recipeKey).forEach(e -> e
                    .mapRight(itemRegistry::get)
                    .ifLeft(tag -> itemBuilder.of(itemRegistry, tag))
                    .ifRight(item -> item.ifPresent(i -> itemBuilder.of(itemRegistry, i.get())))
            );
            predicates.add(itemBuilder.build());
            rewards.addRecipe(recipeKey);
        });
        builder.rewards(rewards);

        builder.addCriterion("has_item", InventoryChangeTrigger.TriggerInstance.hasItems(predicates.toArray(ItemPredicate[]::new)));
        return builder;
    }
}
