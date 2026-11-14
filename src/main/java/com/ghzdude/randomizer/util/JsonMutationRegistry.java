package com.ghzdude.randomizer.util;

import com.google.gson.JsonElement;
import com.mojang.serialization.*;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.function.Supplier;

public class JsonMutationRegistry {

    private static final Reference2ObjectMap<Codec<?>, JsonMutator> registry = new Reference2ObjectOpenHashMap<>();

    static {
        registry.defaultReturnValue(JsonMutator.NO_OP);
        registry.put(LootTable.CODEC, (key, original) -> {
            // todo delegate to loot randomizer
            return original;
        });

        registry.put(Recipe.CODEC, (key, original) -> {
            // todo delegate to recipe randomizer
            return original;
        });

        registry.put(Advancement.CODEC, (key, original) -> {
            // todo delegate to advancement modifier
            return original;
        });
    }

    public static <A, T> DataResult<A> handle(Codec<A> codec, DynamicOps<T> ops,
                                              T json, Supplier<DataResult<A>> original,
                                              Identifier key) {
        if (!registry.containsKey(codec)) return original.get();
        return new Dynamic<>(ops, json)
                .convert(JsonOps.INSTANCE)
                .map(e -> get(codec).mutate(key, e))
                .convert(ops)
                .read(codec);
    }

    public static JsonMutator get(Codec<?> codec) {
        return registry.get(codec);
    }

    public interface JsonMutator {

        JsonMutator NO_OP = (key, original) -> original;

        JsonElement mutate(Identifier key, JsonElement original);
    }
}
