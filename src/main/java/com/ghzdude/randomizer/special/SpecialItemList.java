package com.ghzdude.randomizer.special;

import java.util.ArrayList;
import java.util.Collection;

public class SpecialItemList extends ArrayList<SpecialItem> {

    public SpecialItemList (Collection<SpecialItem> collection) {
        super(collection);
    }
    @Override
    public boolean contains(Object o) {
        if (!(o instanceof SpecialItem)) return false;
        for (SpecialItem item : this) {
            if (item.item.equals(((SpecialItem) o).item)) return true;
        }
        return false;
    }

    @Override
    public int indexOf(Object o) {
        for (int i = 0; i < this.size(); i++) {
            if (this.get(i).item == ((SpecialItem) o).item) return i;
        }
        return -1;
    }
}
