package com.ghzdude.randomizer.api;

import com.ghzdude.randomizer.AdvancementModifier;
import com.ghzdude.randomizer.mixin.AdvancementManagerMixin;
import net.minecraft.advancements.Advancement;

public interface AdvancementModify {

    /// implemented by {@link AdvancementManagerMixin} <br />
    /// see {@link AdvancementModifier#modify(Advancement)}
    void randomizer$randomizeRecipeAdvancements();
}
