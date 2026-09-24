package com.ghzdude.randomizer.api;

import com.ghzdude.randomizer.util.RandomizerUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public interface StackMutator {

    Identifier getItemIdFor(Identifier id);

    default ResourceKey<Item> getResourceKeyFor(ResourceKey<Item> item) {
        return ResourceKey.create(item.registryKey(), getItemIdFor(item.identifier()));
    }

    default Holder<Item> getHolderFor(Holder<Item> item, Registry<Item> registry) {
        return item.unwrap()
                .mapLeft(this::getResourceKeyFor)
                .mapRight(i -> getItemFor(i, registry))
                .map(id -> registry.get(id).map(r -> (Holder<Item>) r),
                        i -> Optional.of(registry.wrapAsHolder(i)))
                .orElse(item);
    }

    default Item getItemFor(Item item, Registry<Item> registry) {
        return registry.getResourceKey(item)
                .map(this::getResourceKeyFor)
                .flatMap(registry::get)
                .map(Holder::get)
                .orElse(item);
    }

    default ItemStack getStackFor(ItemStack itemStack, Registry<Item> registry) {
        return registry.getResourceKey(itemStack.getItem())
                .map(this::getResourceKeyFor)
                .flatMap(registry::get)
                .map(ref -> new ItemStack(ref, itemStack.getCount(), itemStack.getComponentsPatch()))
                .map(RandomizerUtil::applyStackEffects)
                .orElse(itemStack);
    }
}
