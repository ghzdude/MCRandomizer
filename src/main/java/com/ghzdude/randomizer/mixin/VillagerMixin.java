package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.RandomizerConfig;
import com.ghzdude.randomizer.VillagerRandomizer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AbstractVillager.class)
public abstract class VillagerMixin {

    @ModifyVariable(method = "addOffersFromItemListings", at = @At(value = "LOAD", ordinal = 1))
    public MerchantOffer randomizeOffer(MerchantOffer merchantoffer) {
        if (RandomizerConfig.randomizeVillagerTrades) {
            return VillagerRandomizer.randomizeOffer(merchantoffer);
        } else {
            return merchantoffer;
        }
    }
}
