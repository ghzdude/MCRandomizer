package com.ghzdude.randomizer.special.structure;


import net.minecraft.world.level.levelgen.structure.Structure;
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
        if (o instanceof SpecialStructure) {
            return contains((SpecialStructure) o);
        } else if (o instanceof Structure) {
            return contains((Structure) o);
        }
        return false;
    }
    public boolean contains(SpecialStructure o) {
        return contains(o.structure);
    }
    public boolean contains(Structure o) {
        for (SpecialStructure structure : this) {
            if (structure.structure.equals(o)) return true;
        }
        return false;
    }

    @Override
    public int indexOf(Object o) {
        if (o instanceof SpecialStructure) {
            return indexOf((SpecialStructure) o);
        } else if (o instanceof Structure) {
            return indexOf((Structure) o);
        }
        return -1;
    }

    public int indexOf(SpecialStructure o) {
        return indexOf(o.structure);
    }

    public int indexOf(Structure o) {
        for (int i = 0; i < this.size(); i++) {
            if (this.get(i).structure.equals(o)) return i;
        }
        return -1;
    }
}
