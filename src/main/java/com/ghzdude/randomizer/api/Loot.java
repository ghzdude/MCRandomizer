package com.ghzdude.randomizer.api;

import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class Loot {

    public static LootParams createLootParams(ServerLevel level, @NotNull BlockItem block, @NotNull Item tool, boolean silk) {
        var stack = new ItemStack(tool);
        if (silk && !stack.isEmpty()) {
            stack.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        }

        Map<LootContextParam<?>, Object> lootContext = new Reference2ObjectArrayMap<>();
        lootContext.put(LootContextParams.BLOCK_STATE, block.getBlock().defaultBlockState());
        lootContext.put(LootContextParams.TOOL, stack);

        return new LootParams(level, lootContext, Map.of(), 1f);
    }

    public static LootParams createLootParams(MinecraftServer server, @NotNull BlockItem block, @NotNull Item tool, boolean silk) {
        return createLootParams(server.overworld(), block, tool, silk);
    }
}
