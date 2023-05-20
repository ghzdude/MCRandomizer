package com.ghzdude.randomizer;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class RandomizerConfig {

    public static final Pair<RandomizerConfig, ForgeConfigSpec> RandomizerConfigPair;
    public final ForgeConfigSpec.ConfigValue<Integer> itemCooldown;
    public final ForgeConfigSpec.ConfigValue<Boolean> randomizeRecipes;
    public final ForgeConfigSpec.ConfigValue<Boolean> giveRandomItems;
    public final ForgeConfigSpec.ConfigValue<Boolean> generateStructures;


    RandomizerConfig (ForgeConfigSpec.Builder builder) {

        // Item Randomizer
        builder.push("Item Randomizer");
        this.giveRandomItems = builder.comment("Should random items be given to the player? Defaults to true")
                .define("give_random_items", true);

        this.itemCooldown = builder.comment("Time between items given (measured in ticks, 20 ticks is one second) : Default value is 1200")
                .define("item_cooldown", 1200);
        builder.pop();

        // Recipe Randomizer
        builder.push("Recipe Randomizer");
        this.randomizeRecipes = builder.comment("Should recipes be randomized per world? Defaults to true")
                .define("randomize_recipes", true);
        builder.pop();

        // Structure Randomizer
        builder.push("Structure Randomizer");
        this.generateStructures = builder.comment("Should random structures be generated when able? Defaults to true")
                .define("generate_structures", true);
        builder.pop();
    }

    static {
        RandomizerConfigPair = new ForgeConfigSpec.Builder()
                .configure(RandomizerConfig::new);
    }

    public static RandomizerConfig getConfig(){
        return RandomizerConfigPair.getLeft();
    }

    public static int getCooldown() {
        return getConfig().itemCooldown.get();
    }

    public static boolean itemRandomizerEnabled() {
        return getConfig().giveRandomItems.get();
    }

    public static boolean structureRandomizerEnabled() {
        return getConfig().generateStructures.get();
    }

    public static boolean recipeRandomizerEnabled() {
        return getConfig().randomizeRecipes.get();
    }
}
