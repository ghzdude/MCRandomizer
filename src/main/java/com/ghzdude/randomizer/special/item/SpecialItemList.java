package com.ghzdude.randomizer.special.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.Collection;

public class SpecialItemList extends ArrayList<SpecialItem> {

    public SpecialItemList (Collection<SpecialItem> collection) {
        super(collection);
    }
    @Override
    public boolean contains(Object o) {
        if (o instanceof SpecialItem) {
            return contains((SpecialItem) o);
        } else if (o instanceof Item) {
            return contains((Item) o);
        }
        return false;
    }

    public boolean contains(SpecialItem o) {
        return contains(o.item);
    }

    public boolean contains(Item o) {
        if (o == null) return false;
        for (SpecialItem item : this) {
            if (item.item.equals(o)) return true;
        }
        return false;
    }

    @Override
    public int indexOf(Object o) {
        if (o instanceof SpecialItem) {
            return indexOf((SpecialItem) o);
        } else if (o instanceof Item) {
            return indexOf((Item) o);
        }
        return -1;
    }

    public int indexOf(SpecialItem o) {
        return indexOf(o.item);
    }

    public int indexOf(Item o) {
        if (o == null) return -1;
        for (int i = 0; i < this.size(); i++) {
            if (this.get(i).item == o) return i;
        }
        return -1;
    }
}
