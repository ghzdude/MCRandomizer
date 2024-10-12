package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.RecipeRandomizer;
import com.ghzdude.randomizer.api.AdvancementModify;
import com.google.common.collect.ImmutableMap;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(ServerAdvancementManager.class)
public abstract class AdvancementManagerMixin implements AdvancementModify {

    @Shadow
    private Map<ResourceLocation, AdvancementHolder> advancements;

    public void randomizer$randomizeRecipeAdvancements() {
        ImmutableMap.Builder<ResourceLocation, AdvancementHolder> toKeep = ImmutableMap.builder();
        this.advancements.forEach((loc, holder) -> {
            if (!loc.getPath().contains("recipes/")) {
                toKeep.put(loc, holder);
            }
        });

        RecipeRandomizer.buildAdvancements(toKeep);
        this.advancements = toKeep.build();
    }
}
