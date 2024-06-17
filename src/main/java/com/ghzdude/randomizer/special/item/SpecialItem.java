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

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o instanceof Item other) {
            return this.item == other;
        } else if (o instanceof SpecialItem other) {
            return this.item == other.item;
        }
        return false;
    }
}
