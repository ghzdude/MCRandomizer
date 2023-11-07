package com.ghzdude.randomizer.special.modifiers;

import com.ghzdude.randomizer.ItemRandomizer;
import com.ghzdude.randomizer.RandomizerConfig;
import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.RecipeRandomizer;
import com.ghzdude.randomizer.reflection.ReflectionUtils;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class RecipeModifier implements ResourceManagerReloadListener {

    private final RegistryAccess access;
    private final RecipeManager manager;
    public RecipeModifier(RegistryAccess access, RecipeManager manager) {
        this.access = access;
        this.manager = manager;
    }

    @Override
    public void onResourceManagerReload(ResourceManager p_10758_) {
        RecipeRandomizer.randomizeRecipes(manager, access);
    }
}
