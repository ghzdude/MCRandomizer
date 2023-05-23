package com.ghzdude.randomizer.special;

import net.minecraft.data.worldgen.Structures;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SpecialStructures {

    public static final SpecialStructureList CONFIGURED_STRUCTURES = new SpecialStructureList(List.of(
            new SpecialStructure(Structures.NETHER_FOSSIL, 0),
            new SpecialStructure(Structures.ANCIENT_CITY, 2),
            new SpecialStructure(Structures.BASTION_REMNANT, 2),
            new SpecialStructure(Structures.FORTRESS, 2),
            new SpecialStructure(Structures.STRONGHOLD, 3),
            new SpecialStructure(Structures.END_CITY, 3),
            new SpecialStructure(Structures.WOODLAND_MANSION, 4)
    ));
    
    public static class SpecialStructureList extends ArrayList<SpecialStructure> {

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
                if (this.get(i).equals(o)) return i;
            }
            return -1;
        }
    }
}
