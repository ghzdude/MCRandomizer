package com.ghzdude.randomizer;

import com.ghzdude.randomizer.compat.jei.BlockDropRecipe;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/* Description
 * This is to ensure that eyes of ender are always obtainable regardless of randomization
 * Iterate through known recipes and create a path to the most common blocks found in the overworld
 * if an item from the nether is selected, then make sure that obsidian is obtainable
 */

public class CompletabilityVerifier {

    // item -> what the item makes
    private static final Object2ObjectMap<ResourceLocation, Set<ResourceLocation>> INGREDIENTS = new Object2ObjectOpenHashMap<>();

    // item -> recipe that makes this item
    private static final Object2ObjectMap<ResourceLocation, Set<ResourceLocation>> RECIPES = new Object2ObjectOpenHashMap<>();

    public static final AtomicReference<ResourceLocation> active_recipe = new AtomicReference<>();
    public static final AtomicReference<RecipeManager> recipe_manager = new AtomicReference<>();

    public static final ResourceLocation ENDER_EYE = ResourceLocation.withDefaultNamespace("ender_eye");
    private static ResourceLocation[] RECIPE_PATH = { ENDER_EYE };

    private static Registry<Item> REGISTRY;

    // overworld
    private static final List<Item> OVERWORLD = List.of(
            Items.GRASS_BLOCK,
            Items.DIRT,
            Items.STONE
    );

    // nether
    private static final List<Item> NETHER = List.of(
            Items.NETHERRACK,
            Items.SOUL_SAND,
            Items.SOUL_SOIL
    );

    public static void init(MinecraftServer server) {
        REGISTRY = server.registryAccess().registryOrThrow(Registries.ITEM);
    }

    public static void addRecipe(NonNullList<Ingredient> ingredients, ItemStack result, ResourceLocation id) {
        if (!RandomizerConfig.ensureCompletability) return;
        ingredients.stream()
                .distinct()
                .map(Ingredient::getItems)
                .flatMap(Arrays::stream)
                .map(ItemStack::getItem)
                .distinct()
                .map(REGISTRY::getKey)
                .forEach(key -> addPath(key, REGISTRY.getKey(result.getItem())));
    }

    public static void addBlockDrop(BlockDropRecipe recipe, Registry<Item> registry) {
        if (!RandomizerConfig.ensureCompletability) return;
        ResourceLocation loc = registry.getKey(recipe.input().getItem());
        addPath(loc, registry.getKey(recipe.output().getItem()));
    }

    private static void addPath(ResourceLocation loc, ResourceLocation end) {
        INGREDIENTS.computeIfAbsent(loc, k -> new ObjectArraySet<>())
                .add(end);
        RECIPES.computeIfAbsent(end, k -> new ObjectArraySet<>())
                .add(loc);
    }

    public static boolean canModifyOutputs(RecipeHolder<?> recipeHolder) {
        if (!RandomizerConfig.ensureCompletability) return true;
        return recipeHolder.id() != active_recipe.get();
    }

    public static boolean ensureCompletability() {
        return ensureCompletability(REGISTRY.getKey(Items.ENDER_EYE));
    }

    private static boolean ensureCompletability(ResourceLocation primary) {
        ResourceLocation head = primary;
        while (true) {
            // for each recipe that makes this item
            for (ResourceLocation key : RECIPES.getOrDefault(head, Collections.emptySet())) {
                if (key.equals(primary)) continue;

                // is this item a common item
                if (NETHER.contains(REGISTRY.get(key))) {
                    return ensureCompletability(REGISTRY.getKey(Items.OBSIDIAN));
                    // ensure obsidian is obtainable
                } else if (OVERWORLD.contains(REGISTRY.get(key))) {
                    // we can obtain this item in the overworld
                    return true;
                } else {
                    // not a common item, find a new recipe
                    head = INGREDIENTS.get(key).iterator().next();
                    break;
                }
            }
            // no recipe works, we need to make a new one
            // not sure how to actually do this
        }
    }
}
