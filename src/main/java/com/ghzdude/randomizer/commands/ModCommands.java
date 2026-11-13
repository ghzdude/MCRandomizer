package com.ghzdude.randomizer.commands;

import com.ghzdude.randomizer.io.ConfigIO;
import com.google.gson.JsonElement;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.server.command.EnumArgument;

public class ModCommands {
    public static void registerCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = root();

        ArgumentBuilder<CommandSourceStack, ?> query2 = Commands.literal("query"// ,
//                EnumArgument.enumArgument(QueryType.class)
                /*reader -> {
            String s = reader.readUnquotedString();
            try {
                return Enum.valueOf(QueryType.class, s.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new Dynamic2CommandExceptionType((found, constants) -> {
                    return Component.translatable("commands.forge.arguments.enum.invalid", constants, found);
                }).createWithContext(reader, s, Arrays.stream(QueryType.values()).map(Enum::name).toArray());
            }
        }*/)/*.suggests((context, builder) -> {
            builder.suggest(QueryType.ADVANCEMENT.name().toLowerCase());
            builder.suggest(QueryType.RECIPE.name().toLowerCase());
            builder.suggest(QueryType.LOOT.name().toLowerCase());
            return builder.buildFuture();
        })*/;

//        query2.then(QueryType.ADVANCEMENT.command());
        for (QueryType query : QueryType.values()) {
            query2.then(query.command());
        }

        event.getDispatcher().register(root.then(query2));

        if (true) return;

        event.getDispatcher().register(
                root().then(Commands.argument("query", EnumArgument.enumArgument(QueryType.class))
                        .executes(context -> {
                            QueryType query = context.getArgument("query", QueryType.class);
                            switch (query) {
                                case ADVANCEMENT -> {
                                }
                                case RECIPE -> {
                                }
                                case LOOT -> {
                                }
                            }
                            return 1;
                        })
//                        .then(Commands.literal("advancement"))
                        .then(Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT))
                        .suggests((context, builder) -> {
                            QueryType query = context.getArgument("query", QueryType.class);
                            if (query == QueryType.ADVANCEMENT) {
                                context.getSource().getServer().getAdvancements().getAllAdvancements()
                                                .stream().filter(h -> h.id().getPath().startsWith("recipes/"))
                                                .forEach(h -> builder.suggest(h.id().toString()));
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            RegistryOps<JsonElement> ops = context.getSource().getServer().registryAccess().createSerializationContext(JsonOps.INSTANCE);
                            AdvancementHolder holder = ResourceKeyArgument.getAdvancement(context, "advancements");
                            DataResult<JsonElement> result = Advancement.CODEC.encodeStart(ops, holder.value());
                            result.map(ConfigIO.GSON::toJson).map(Component::literal)
                                            .ifSuccess(s -> context.getSource().sendSuccess(() -> s, false));
                            return 1;
                        }))
                )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("randomizer");
    }

    private enum QueryType {
        ADVANCEMENT,
        RECIPE,
        LOOT;


        public ArgumentBuilder<CommandSourceStack, ?> command() {
            return switch (this) {
                case ADVANCEMENT -> Commands.literal("advancement")
                        .then(Commands.argument("advancement", ResourceKeyArgument.key(Registries.ADVANCEMENT))
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
                                }));
                case RECIPE -> Commands.literal("recipe")
                        .then(Commands.argument("recipe", ResourceKeyArgument.key(Registries.RECIPE))
                                .executes(context -> {
                                    context.getSource().sendSuccess(() -> Component.literal("you ran the recipe command"), false);
                                    return 1;
                                }));
                case LOOT -> Commands.literal("loot")
                        .then(Commands.argument("loot", ResourceKeyArgument.key(Registries.LOOT_TABLE))
                                .executes(context -> {
                                    context.getSource().sendSuccess(() -> Component.literal("you ran the loot command"), false);
                                    return 1;
                                }));
            };
        }
    }
}
