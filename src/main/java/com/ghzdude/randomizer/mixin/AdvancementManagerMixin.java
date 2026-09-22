package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.AdvancementModifier;
import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.api.AdvancementModify;
import com.google.common.collect.ImmutableMap;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(value = ServerAdvancementManager.class, remap = false)
public abstract class AdvancementManagerMixin implements AdvancementModify {

    @Shadow
    private Map<Identifier, AdvancementHolder> advancements;

    public void randomizer$randomizeRecipeAdvancements() {
        ImmutableMap.Builder<Identifier, AdvancementHolder> toKeep = ImmutableMap.builder();
        this.advancements.forEach((loc, holder) -> {
            if (loc.getNamespace().equals(RandomizerCore.MODID)) return;
            //todo make this modify recipe advancements and use the same ids
            if (loc.getPath().startsWith("recipes/")) return;
            toKeep.put(loc, holder);
        });

        AdvancementModifier.buildAdvancements(toKeep);
        this.advancements = toKeep.build();
    }
}
