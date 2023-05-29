package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.special.passages.Passage;
import com.ghzdude.randomizer.special.passages.Passages;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

public class BookGenerator {

    private static final String FORMAT = "%s";
    private static final String PAGE_FORMAT = "\"%s\"";

    public static void applyPassages(ItemStack stack) {

        CompoundTag tag = new CompoundTag();
        ListTag pages = new ListTag();
        int id = RandomizerCore.RANDOM.nextInt(Passages.PASSAGES.size());
        Passage passage = Passages.PASSAGES.get(id);

        StringTag title = StringTag.valueOf(String.format(FORMAT, passage.title()));
        StringTag author = StringTag.valueOf(String.format(FORMAT, passage.author()));

        for (String page : passage.parseBody()) {
            pages.add(StringTag.valueOf(String.format(PAGE_FORMAT, page)));
        }

        tag.put("author", author);
        tag.put("title", title);
        tag.put("pages", pages);
        stack.setTag(tag);
    }
}
