package com.ghzdude.randomizer;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.IConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.function.Predicate;

public class RandomizerConfig {

    private static final Pair<RandomizerConfig, ForgeConfigSpec> RandomizerConfigPair;
    private final ForgeConfigSpec.ConfigValue<Integer> itemCooldown;
    private final ForgeConfigSpec.ConfigValue<Integer> cycleBase;
    private final ForgeConfigSpec.ConfigValue<Boolean> randomizeRecipes;
    private final ForgeConfigSpec.ConfigValue<Boolean> giveRandomItems;
    private final ForgeConfigSpec.ConfigValue<Boolean> generateStructures;
    private final ForgeConfigSpec.ConfigValue<Integer> structureProbability;
    private final ForgeConfigSpec.ConfigValue<Boolean> randomizeLoot;
    private final ForgeConfigSpec.ConfigValue<Boolean> randomizeBloockLoot;
    private final ForgeConfigSpec.ConfigValue<Boolean> randomizeEntityLoot;
    private final ForgeConfigSpec.ConfigValue<Boolean> randomizeChestLoot;

    RandomizerConfig (ForgeConfigSpec.Builder builder) {

        // Item Randomizer
        builder.push("Item Randomizer");
        this.giveRandomItems = builder.comment("Should random items be given to the player? Defaults to true.")
                .define("give_random_items", true);

        this.itemCooldown = builder.comment("Time between items given (measured in ticks, 20 ticks is one second) : Default value is 1200.")
                .define("item_cooldown", 1200);
        builder.pop();

        // Recipe Randomizer
        builder.push("Recipe Randomizer");
        this.randomizeRecipes = builder.comment("Should recipes be randomized per world? Defaults to true.")
                .define("randomize_recipes", true);
        builder.pop();

        // Structure Randomizer
        builder.push("Structure Randomizer");
        this.generateStructures = builder.comment("Should random structures be generated when able? Defaults to true.")
                .define("generate_structures", true);

        Predicate<Object> validateProbability = o -> Integer.parseInt(o.toString()) > 0 || Integer.parseInt(o.toString()) < 100;
        this.structureProbability = builder.comment("Probability of how likely structure generation is picked over item generation. Defaults to 10, value must be between 0 and 100 ")
                        .define("structure_probability", 10, validateProbability);

        builder.pop();

        builder.push("Loot Randomizer");
        this.randomizeLoot = builder.comment("Should Loot Randomizer be enabled at all? Defaults to true.")
                .define("randomize_loot", true);

        this.randomizeBloockLoot = builder.comment("Should block drops be randomized? Defaults to false.")
                .define("randomize_block_loot", false);

        this.randomizeEntityLoot = builder.comment("Should mob drops be randomized? Defaults to false.")
                .define("randomize_entity_loot", false);

        this.randomizeChestLoot = builder.comment("Should chest loot be randomized? Defaults to true.")
                        .define("randomize_chest_loot", true);

        builder.pop();
    }

    static {
        RandomizerConfigPair = new ForgeConfigSpec.Builder()
                .configure(RandomizerConfig::new);
    }

    public static RandomizerConfig getConfig(){
        return RandomizerConfigPair.getLeft();
    }

    public static IConfigSpec<?> getSpec() {
        return RandomizerConfigPair.getRight();
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

    public static int getStructureProbability() {
        return getConfig().structureProbability.get();
    }

    public static boolean recipeRandomizerEnabled() {
        return getConfig().randomizeRecipes.get();
    }

    public static boolean randomizeLoot() {
        return getConfig().randomizeLoot.get();
    }

    public static boolean randomizeBlockLoot() {
        return getConfig().randomizeBloockLoot.get();
    }

    public static boolean randomizeEntityLoot() {
        return getConfig().randomizeEntityLoot.get();
    }

    public static boolean randomizeChestLoot() {
        return getConfig().randomizeChestLoot.get();
    }
}
