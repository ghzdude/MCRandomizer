package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.AdvancementModifier;
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
        ImmutableMap.Builder<Identifier, AdvancementHolder> modified = ImmutableMap.builder();
        this.advancements.forEach((loc, holder) -> {
            AdvancementHolder modify;
            if (loc.getPath().startsWith("recipes/")) {
                modify = AdvancementModifier.modify(holder.value()).build(loc);
            } else {
                modify = holder;
            }
            modified.put(loc, modify);
        });

//        AdvancementModifier.buildAdvancements(modified);
        this.advancements = modified.build();
    }
}
