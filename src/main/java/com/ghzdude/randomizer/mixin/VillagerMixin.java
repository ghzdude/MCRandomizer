package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.VillagerRandomizer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayList;

@Mixin(AbstractVillager.class)
public abstract class VillagerMixin extends AgeableMob implements InventoryCarrier, Npc, Merchant {

    protected VillagerMixin(EntityType<? extends AgeableMob> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Inject(method = "addOffersFromItemListings", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/trading/MerchantOffers;add(Ljava/lang/Object;)Z"),
            locals = LocalCapture.CAPTURE_FAILHARD)
    public void randomizeOffer(MerchantOffers pGivenMerchantOffers, VillagerTrades.ItemListing[] pNewTrades, int pMaxNumbers,
                               CallbackInfo ci, ArrayList<Object> arraylist, int i, MerchantOffer merchantoffer) {
        if (randomizer$getThis() instanceof WanderingTrader) {
            arraylist.add(VillagerRandomizer.randomizeOffer(merchantoffer));
        }
    }

    @Unique
    private AbstractVillager randomizer$getThis() {
        return (AbstractVillager) (Object) this;
    }
}
