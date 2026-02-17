package com.ghzdude.randomizer.special.generators;

import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.io.PassageIO;
import com.ghzdude.randomizer.special.passages.Passage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

public class BookGenerator {

    private static final String FORMAT = "%s";

    public static final ArrayList<Passage> PASSAGES = PassageIO.readPassages();

    public static void applyPassages(ItemStack stack) {
        int id = RandomizerCore.unseededRNG.nextInt(PASSAGES.size());
        Passage passage = PASSAGES.get(id);

        List<Filterable<Component>> pages = new ArrayList<>();
        for (String page : passage.parseBody()) {
            pages.add(Filterable.passThrough(Component.literal(page)));
        }

        var bookContent = new WrittenBookContent(Filterable.passThrough(String.format(FORMAT, passage.title())), passage.author(), 0, pages, true);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, bookContent);
        stack.setCount(1);
    }
}
