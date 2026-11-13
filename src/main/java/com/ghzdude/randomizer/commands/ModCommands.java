package com.ghzdude.randomizer.commands;

import com.ghzdude.randomizer.io.ConfigIO;
import com.google.gson.JsonElement;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.event.RegisterCommandsEvent;

public class ModCommands {
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(root()
                .then(Commands.literal("query")
                        .then(Commands.literal("advancement")
                                .then(advancementCommand()))
                        .then(Commands.literal("recipe")
                                .then(recipeCommand()))
                        .then(Commands.literal("loot")
                                .then(lootCommand())
                        )
                )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("randomizer");
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceKey<Advancement>> advancementCommand() {
        return Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT))
                .suggests((context, builder) -> {
                    context.getSource().getServer().getAdvancements().getAllAdvancements()
                            .stream().filter(h -> h.id().getPath().startsWith("recipes/"))
                            .forEach(h -> builder.suggest(h.id().toString()));
                    return builder.buildFuture();
                })
                .executes(context -> {
                    RegistryOps<JsonElement> ops = context.getSource().getServer().registryAccess().createSerializationContext(JsonOps.INSTANCE);
                    AdvancementHolder holder = ResourceKeyArgument.getAdvancement(context, "advancement");
                    DataResult<JsonElement> result = Advancement.CODEC.encodeStart(ops, holder.value());
                    result.map(ConfigIO.GSON::toJson).map(Component::literal)
                            .ifSuccess(s -> context.getSource().sendSuccess(() -> s, false));
                    return 1;
                });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceKey<Recipe<?>>> recipeCommand() {
        return Commands.argument("recipe", ResourceKeyArgument.key(Registries.RECIPE))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("you ran the recipe command"), false);
                    return 1;
                });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceKey<LootTable>> lootCommand() {
        return Commands.argument("loot", ResourceKeyArgument.key(Registries.LOOT_TABLE))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("you ran the loot command"), false);
                    return 1;
                });
    }
}
