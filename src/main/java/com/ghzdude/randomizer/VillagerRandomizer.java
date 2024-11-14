package com.ghzdude.randomizer;

import com.ghzdude.randomizer.util.RandomizerUtil;
import net.minecraft.world.item.trading.MerchantOffer;

public class VillagerRandomizer {
    public static MerchantOffer randomizeOffer(MerchantOffer vanilla) {
        int count = vanilla.getResult().getCount();
        var stack = RandomizerUtil.itemToStack(RandomizerUtil.getRandom(ItemRandomizer.getValidItems()), count);

        // MerchantOffer(ItemCost pBaseCostA, ItemStack pResult, int pMaxUses, int pXp, float pPriceMultiplier)
        return new MerchantOffer(
                vanilla.getItemCostA(),
                stack,
                vanilla.getMaxUses(),
                vanilla.getXp(),
                vanilla.getPriceMultiplier()
        );
    }
}
