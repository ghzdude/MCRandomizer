package com.ghzdude.randomizer.api;

import net.minecraft.world.item.ItemStack;

import java.util.concurrent.atomic.AtomicInteger;

public interface EntryAccessor {

    AtomicInteger counter = new AtomicInteger();

    ItemStack[] randomizer$getStacks();

    default boolean needsRecalculate() {
        return randomizer$getId() != counter.get();
    }

    int randomizer$getId();
}
