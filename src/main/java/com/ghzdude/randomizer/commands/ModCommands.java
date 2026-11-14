package com.ghzdude.randomizer.commands;

import com.ghzdude.randomizer.RecipeRandomizer;
import com.ghzdude.randomizer.io.ConfigIO;
import com.ghzdude.randomizer.loot.LootRandomizer;
import com.google.gson.JsonElement;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

/// See {@link net.minecraftforge.common.ForgeHooks#onJsonDataParsed(Codec, Identifier, Object)}
public class ModCommands {

    public static final DynamicCommandExceptionType MISSING_TABLE = new DynamicCommandExceptionType(v -> Component.literal("Missing table: " + v));

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(root()
                .then(Commands.literal("query")
                        .then(advancementCommand())
                        .then(recipeCommand())
                        .then(lootCommand())
                ).then(Commands.literal("reset_player")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ServerPlayer player = source.getPlayer();
                            if (player != null) {
                                source.sendSystemMessage(Component.literal("Clearing player advancements and recipe book!"));
                                String playerName = player.getPlainTextName();
                                try {
                                    source.dispatcher().execute("advancement revoke " + playerName + " everything", source);
                                    source.dispatcher().execute("recipe take " + playerName + " *", source);
                                } catch (CommandSyntaxException e) {
                                    source.sendFailure(Component.literal(e.getMessage()));
                                }
                                return 1;
                            }
                            return 0;
                        }))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("randomizer");
    }

    private static <T> RegistryOps<T> createOps(CommandSourceStack source, DynamicOps<T> ops) {
        return source.getServer().registryAccess().createSerializationContext(ops);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> advancementCommand() {
        final String advancementKey = "advancement";
        return Commands.literal(advancementKey)
                .then(Commands.argument(advancementKey, ResourceKeyArgument.key(Registries.ADVANCEMENT))
                        .suggests((context, builder) -> {
                            Stream<AdvancementHolder> stream = context.getSource().getServer()
                                    .getAdvancements().getAllAdvancements()
                                    .stream().filter(h -> h.id().getPath().startsWith("recipes/"));
                            return SharedSuggestionProvider.suggestResource(stream, builder,
                                    AdvancementHolder::id,
                                    advancementHolder -> Component.literal(advancementHolder.id().toString()));
                        })
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            RegistryOps<JsonElement> ops = createOps(source, JsonOps.INSTANCE);
                            AdvancementHolder holder = ResourceKeyArgument.getAdvancement(context, advancementKey);
                            DataResult<JsonElement> result = Advancement.CODEC.encodeStart(ops, holder.value());
                            result.map(ConfigIO.GSON::toJson).map(Component::literal)
                                    .ifError(err -> source.sendFailure(Component.literal(err.message())))
                                    .ifSuccess(component -> source.sendSuccess(() -> Component.literal(holder.id().toString())
                                            .append(" -> ")
                                            .append(component), false));
                            return 1;
                        }));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> recipeCommand() {
        final String recipeKey = "recipe";
        return Commands.literal(recipeKey)
                .then(Commands.argument(recipeKey, ResourceKeyArgument.key(Registries.RECIPE))
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(RecipeRandomizer.getKnownRecipes(), builder,
                                ResourceKey::identifier, k -> Component.literal(k.identifier().toString())))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            ResourceKey<Recipe<?>> r = ResourceKeyArgument.getRegistryKey(context, recipeKey, Registries.RECIPE, MISSING_TABLE);
                            Collection<Either<TagKey<Item>, ResourceKey<Item>>> ingredients = RecipeRandomizer.getIngredients(r);
                            ResourceKey<Item> output = RecipeRandomizer.getResultFor(r);
                            MutableComponent root = Component.literal(r.identifier().toString()).append(" -> [");
                            for (Either<TagKey<Item>, ResourceKey<Item>> ingredient : ingredients) {
                                String mapped = ingredient.map(key -> "#" + key.location(), key -> key.identifier().toString());
                                root.append(Component.literal("\n  input -> ").append(mapped));
                            }
                            root.append("\n] outputs -> ").append(output.identifier().toString());
                            source.sendSystemMessage(root);
                            return 1;
                        }));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> lootCommand() {
        final String lootKey = "loot";
        return Commands.literal(lootKey)
                .then(Commands.argument(lootKey, ResourceKeyArgument.key(Registries.LOOT_TABLE))
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(LootRandomizer.getKnownTables(), builder))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            Holder.Reference<LootTable> lootTable = getTable(context, lootKey);
                            Set<Identifier> drops = LootRandomizer.getDrops(lootTable.key().identifier());
                            MutableComponent root = Component.literal(lootTable.key().identifier().toString());
                            drops.forEach(id -> root.append("drops").append(" -> ").append(id.toString()));
                            source.sendSystemMessage(root);
                            return 1;
                        }));
    }

    private static Holder.Reference<LootTable> getTable(CommandContext<CommandSourceStack> context, String key) throws CommandSyntaxException {
        ResourceKey<LootTable> table = ResourceKeyArgument.getRegistryKey(context, key, Registries.LOOT_TABLE, MISSING_TABLE);
        return context.getSource().getServer().reloadableRegistries().lookup().getOrThrow(table);
    }
}
