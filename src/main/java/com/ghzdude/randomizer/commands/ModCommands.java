package com.ghzdude.randomizer.commands;

import com.ghzdude.randomizer.io.ConfigIO;
import com.google.gson.JsonElement;
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
        event.getDispatcher().register(
                root().then(Commands.argument("query", EnumArgument.enumArgument(QueryType.class)))
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
                        .then(Commands.argument("advancements", ResourceKeyArgument.key(Registries.ADVANCEMENT))
                                .suggests((context, builder) -> {
                                    context.getSource().getServer().getAdvancements().getAllAdvancements()
                                            .stream().filter(h -> h.id().getPath().startsWith("recipes/"))
                                            .forEach(h -> builder.suggest(h.id().toString()));
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
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("randomizer");
    }

    private enum QueryType {
        ADVANCEMENT,
        RECIPE,
        LOOT
    }
}
