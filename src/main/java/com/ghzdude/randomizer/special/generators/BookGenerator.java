package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.special.passages.Passage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

public class BookGenerator {

    private static final String FORMAT = "\"%s\"";
    private static final String PAGE_FORMAT = "{\"text\":" + FORMAT + "}";

    private static final ArrayList<Passage> PASSAGES = new ArrayList<>();
    public static void applyPassages(ItemStack stack) {

        CompoundTag tag = new CompoundTag();
        ListTag pages = new ListTag();
        int id = RandomizerCore.RANDOM.nextInt(PASSAGES.size());
        Passage passage = PASSAGES.get(id);

        StringTag title = StringTag.valueOf(String.format(FORMAT, passage.getTitle()));
        StringTag author = StringTag.valueOf(String.format(FORMAT, passage.getAuthor()));

        for (String page : passage.parseBody()) {

            pages.add(StringTag.valueOf(String.format(PAGE_FORMAT, page)));
        }

        tag.put("author", author);
        tag.put("title", title);
        tag.put("pages", pages);
        // tag.put("filtered_title", title);
        stack.setTag(tag);
    }
}
