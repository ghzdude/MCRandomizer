package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.RandomizerConfig;
import com.ghzdude.randomizer.loot.LootRandomizer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("UnstableApiUsage")
@Mixin(ForgeHooks.class)
public abstract class RandomizeLootMixin {

    @Inject(method = "modifyLoot", at = @At("TAIL"), cancellable = true, remap = false)
    private static void InjectLootRandomizer(ResourceLocation lootTableId,
                             ObjectArrayList<ItemStack> generatedLoot,
                             LootContext context, CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir) {
        if (RandomizerConfig.randomizeLoot.get()) {
            var list = LootRandomizer.randomizeLoot(generatedLoot, context);
            cir.setReturnValue(list);
        }
    }
}
