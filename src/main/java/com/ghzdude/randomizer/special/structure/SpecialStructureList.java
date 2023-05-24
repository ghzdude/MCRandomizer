package com.ghzdude.randomizer.special.structure;


import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class SpecialStructureList extends ArrayList<SpecialStructure> {

    public SpecialStructureList(@NotNull Collection<SpecialStructure> collection) {
        super(collection);
    }

    public SpecialStructureList() {
        super();
    }

    @Override
    public boolean contains(Object o) {
        for (SpecialStructure structure : this) {
            if (structure.structure.equals(o)) return true;
        }
        return false;
    }

    @Override
    public int indexOf(Object o) {
        for (int i = 0; i < this.size(); i++) {
            if (this.get(i).structure.equals(o)) return i;
        }
        return -1;
    }
}
