package com.ghzdude.randomizer.special.modifiers;

import com.ghzdude.randomizer.RecipeRandomizer;
import com.mojang.logging.LogUtils;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;

public class AdvancementModifier implements ResourceManagerReloadListener {

    private final ServerAdvancementManager manager;
    private static final Logger LOGGER = LogUtils.getLogger();

    public AdvancementModifier(ServerAdvancementManager manager) {
        this.manager = manager;
    }

    @Override
    public void onResourceManagerReload(ResourceManager p_10758_) {
        // modify advancements here
        LOGGER.warn("Modifying advancements!");

        RecipeRandomizer.setAdvancements(this.manager);
    }
}
