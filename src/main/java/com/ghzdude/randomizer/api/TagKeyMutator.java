package com.ghzdude.randomizer.api;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public interface TagKeyMutator {

    Identifier getTagIdFor(Identifier tag);

    default TagKey<Item> getItemKeyFor(TagKey<Item> tag) {
        return TagKey.create(tag.registry(), getTagIdFor(tag.location()));
    };
}
