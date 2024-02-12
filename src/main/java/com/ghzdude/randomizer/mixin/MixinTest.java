package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.loot.LootRandomizeModifier;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.loot.LootModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("UnstableApiUsage")
@Mixin(ForgeHooks.class)
public abstract class MixinTest {

    @Unique
    private static final LootModifier randomizer$modifier = new LootRandomizeModifier();
    @Inject(method = "modifyLoot", at = @At("TAIL"), cancellable = true)
    private static void test(ResourceLocation lootTableId,
                             ObjectArrayList<ItemStack> generatedLoot,
                             LootContext context, CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir) {
        var list = randomizer$modifier.apply(generatedLoot, context);
        cir.setReturnValue(list);
    }
}
