package com.ghzdude.randomizer.special.item;

import net.minecraft.world.item.Item;

public class SpecialItem {
    public Item item;
    public int value;

    public SpecialItem(Item item, int value) {
        this.item = item;
        this.value = value;
    }

    public SpecialItem(Item item) {
        this(item, 1);
    }
}
