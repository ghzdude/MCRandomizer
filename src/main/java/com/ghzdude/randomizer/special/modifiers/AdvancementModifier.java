package com.ghzdude.randomizer.special.modifiers;

import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.RecipeRandomizer;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public class AdvancementModifier implements ResourceManagerReloadListener {

    private final ServerAdvancementManager manager;

    public AdvancementModifier(ServerAdvancementManager manager) {
        this.manager = manager;
    }

    @Override
    public void onResourceManagerReload(ResourceManager p_10758_) {
        // modify advancements here
        RandomizerCore.LOGGER.warn("Modifying advancements!");

        RecipeRandomizer.setAdvancements(this.manager);
    }
}
