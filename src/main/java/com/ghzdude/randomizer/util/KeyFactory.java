package com.ghzdude.randomizer.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootTable;

public class KeyFactory {

    public static ResourceKey<Item> item(Identifier identifier) {
        return ResourceKey.create(Registries.ITEM, identifier);
    }

    public static TagKey<Item> itemTag(Identifier identifier) {
        return TagKey.create(Registries.ITEM, identifier);
    }

    public static ResourceKey<LootTable> table(Identifier identifier) {
        return ResourceKey.create(Registries.LOOT_TABLE, identifier);
    }
}
