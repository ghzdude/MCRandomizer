package com.ghzdude.randomizer.util;

import com.ghzdude.randomizer.AdvancementModifier;
import com.ghzdude.randomizer.RecipeRandomizer;
import com.ghzdude.randomizer.loot.LootRandomizer;
import com.google.gson.JsonElement;
import com.mojang.serialization.*;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.function.Supplier;

/// TODO look into {@link net.minecraftforge.event.AddPackFindersEvent} and
///  {@link net.minecraft.server.packs.PackResources}
public class JsonMutationRegistry {

    private static final Reference2ObjectMap<Codec<?>, JsonMutator> registry = new Reference2ObjectOpenHashMap<>();

    static {
        registry.defaultReturnValue(JsonMutator.NO_OP);
        registry.put(LootTable.DIRECT_CODEC, LootRandomizer::run);
        registry.put(Recipe.CODEC, RecipeRandomizer::run);
        registry.put(Advancement.CODEC, AdvancementModifier::run);
    }

    public static <A, T> DataResult<A> handle(Codec<A> codec, DynamicOps<T> ops,
                                              T json, Supplier<DataResult<A>> original,
                                              Identifier key) {
        if (!registry.containsKey(codec)) return original.get();
        return new Dynamic<>(ops, json)
                .convert(JsonOps.INSTANCE)
                .map(e -> get(codec).mutate(key, e))
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
