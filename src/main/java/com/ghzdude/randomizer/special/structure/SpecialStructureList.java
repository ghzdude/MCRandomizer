package com.ghzdude.randomizer.special.structure;


import net.minecraft.resources.ResourceKey;
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

    public boolean contains(SpecialStructure o) {
        return contains(o.key);
    }

    public boolean contains(ResourceKey<Structure> o) {
        for (SpecialStructure structure : this) {
            if (structure.key == o) return true;
        }
        return false;
    }

    public int indexOf(SpecialStructure o) {
        return indexOf(o.key);
    }

    public int indexOf(ResourceKey<Structure> o) {
        for (int i = 0; i < this.size(); i++) {
            if (this.get(i).key.equals(o)) return i;
        }
        return -1;
    }
}
