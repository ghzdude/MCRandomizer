package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.api.RandomizerContext;
import com.ghzdude.randomizer.loot.LootRandomizer;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Mixin(value = LootContext.class, remap = false)
public class LootContextMixin implements RandomizerContext {

    @Unique
    private final Set<Identifier> randomizer$specialTables = new HashSet<>();

    @Inject(method = "pushVisitedElement", at = @At("HEAD"))
    public void checkPush(LootContext.VisitedEntry<?> element, CallbackInfoReturnable<Boolean> cir) {
        if (element.value() instanceof LootTable table && LootRandomizer.SPECIAL_MAP.containsKey(table.getLootTableId())) {
            randomizer$specialTables.add(table.getLootTableId());
        }
    }

    @Override
    public Map<Identifier, Identifier> randomizer$getMap() {
        ImmutableMap.Builder<Identifier, Identifier> builder = ImmutableMap.builder();
        for (Identifier table : randomizer$specialTables) {
            if (LootRandomizer.SPECIAL_MAP.containsKey(table)) {
                builder.putAll(LootRandomizer.SPECIAL_MAP.get(table));
            }
        }
        return builder.build();
    }
}
