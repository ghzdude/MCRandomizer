package com.ghzdude.randomizer;

import java.util.ArrayList;
import java.util.Collection;

public class SpecialItemList extends ArrayList<ItemRandomizer.SpecialItem> {

    SpecialItemList (Collection<ItemRandomizer.SpecialItem> collection) {
        super(collection);
    }
    @Override
    public boolean contains(Object o) {
        if (!(o instanceof ItemRandomizer.SpecialItem)) return false;
        for (ItemRandomizer.SpecialItem item : this) {
            if (item.item.equals(((ItemRandomizer.SpecialItem) o).item)) return true;
        }
        return false;
    }

    @Override
    public int indexOf(Object o) {
        for (int i = 0; i < this.size(); i++) {
            if (this.get(i).item == ((ItemRandomizer.SpecialItem) o).item) return i;
        }
        return -1;
    }
}
