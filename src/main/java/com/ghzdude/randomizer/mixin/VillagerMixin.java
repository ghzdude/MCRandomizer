package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.RandomizerConfig;
import com.ghzdude.randomizer.VillagerRandomizer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractVillager.class)
public abstract class VillagerMixin {

    @WrapOperation(method = "addOffersFromItemListings",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/MerchantOffers;add(Ljava/lang/Object;)Z"))
    private static boolean randomizeOffer(MerchantOffers instance, Object o, Operation<Boolean> original) {
        MerchantOffer offer = (MerchantOffer) o;
        if (RandomizerConfig.randomizeVillagerTrades) {
            return original.call(instance, VillagerRandomizer.randomizeOffer(offer));
        } else {
            return original.call(instance, offer);
        }
    }
}
