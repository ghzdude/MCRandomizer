package com.ghzdude.randomizer;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.IConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class RandomizerConfig {

    private static final Pair<RandomizerConfig, ForgeConfigSpec> RandomizerConfigPair;
    private final ForgeConfigSpec.ConfigValue<Integer> itemCooldown;
    private final ForgeConfigSpec.ConfigValue<Boolean> pointsCarryover;
    private final ForgeConfigSpec.ConfigValue<Boolean> giveMultipleItems;
    private final ForgeConfigSpec.ConfigValue<Integer> cycleBase;
    private final ForgeConfigSpec.ConfigValue<Boolean> randomizeRecipes;
    private final ForgeConfigSpec.ConfigValue<Boolean> randomizeRecipeInputs;
    private final ForgeConfigSpec.ConfigValue<Boolean> giveRandomItems;
    private final ForgeConfigSpec.ConfigValue<Boolean> generateStructures;
    private final ForgeConfigSpec.ConfigValue<Integer> structureProbability;
    private final ForgeConfigSpec.ConfigValue<Boolean> randomizeLoot;
    private final ForgeConfigSpec.ConfigValue<Boolean> randomizeBlockLoot;
    private final ForgeConfigSpec.ConfigValue<Boolean> randomizeEntityLoot;
    private final ForgeConfigSpec.ConfigValue<Boolean> randomizeChestLoot;
    private final ForgeConfigSpec.ConfigValue<Boolean> randomizeMobs;

    RandomizerConfig (ForgeConfigSpec.Builder builder) {

        // Item Randomizer
        builder.push("Item Randomizer");
        this.giveRandomItems = builder.comment("Should random items be given to the player? Defaults to true.")
                .define("give_random_items", true);

        this.itemCooldown = builder.comment("Time between items given (measured in ticks, 20 ticks is one second). Default value is 600.")
                .define("item_cooldown", 600);

        this.pointsCarryover = builder.comment("Should unused points carry over after a cycle? : Default value is false.")
                .define("points_carryover", false);

        this.giveMultipleItems = builder.comment("Should the randomizer attempt to give the player more items if there are still points to use. Defaults to false.")
                .define("give_multiple_items", false);

        this.cycleBase = builder.comment("Amount of cycles needed to reach the first point max increment. Default value is 5.")
                        .define("cycle_base", 5);
        builder.pop();

        // Recipe Randomizer
        builder.push("Recipe Randomizer");
        this.randomizeRecipes = builder.comment("Should recipes be randomized? Defaults to true.")
                .define("randomize_recipes", true);
        this.randomizeRecipeInputs = builder.comment("Should recipe inputs also be randomized? Defaults to true.")
                .define("randomize_recipe_inputs", true);
        builder.pop();

        // Structure Randomizer
        builder.push("Structure Randomizer");
        this.generateStructures = builder.comment("Should random structures be generated when able? Defaults to true.").worldRestart()
                .define("generate_structures", true);

        this.structureProbability = builder.comment("Percent chance of how likely structure generation is picked over item generation. Defaults to 10.")
                        .defineInRange("structure_probability", 10, 0, 100);
        builder.pop();

        builder.push("Loot Randomizer");
        this.randomizeLoot = builder.comment("Should Loot Tables (block drops, entity drops, chest loot) be randomized? Defaults to true.")
                .define("randomize_loot", true);

        this.randomizeBlockLoot = builder.comment("Should block drops be randomized? Defaults to false.")
                .define("randomize_block_loot", false);

        this.randomizeEntityLoot = builder.comment("Should mob drops be randomized? Defaults to false.")
                .define("randomize_entity_loot", false);

        this.randomizeChestLoot = builder.comment("Should chest loot be randomized? Defaults to true.")
                        .define("randomize_chest_loot", true);
        builder.pop();

        builder.push("Mob Randomizer");

        this.randomizeMobs = builder.comment("Should mobs be randomized when spawning? Note that this completely overrides vanilla spawning logic. Ignores spawn egss. Defaults to false.")
                .define("randomize_mobs", false);
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

    public static boolean giveMultipleItems() {
        return getConfig().giveMultipleItems.get();
    }

    public static boolean pointsCarryover() {
        return getConfig().pointsCarryover.get();
    }

    public static int getCycleBase() {
        return getConfig().cycleBase.get();
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
    public static boolean randomizeInputs() {
        return getConfig().randomizeRecipeInputs.get();
    }

    public static boolean lootRandomizerEnabled() {
        return getConfig().randomizeLoot.get();
    }

    public static boolean randomizeBlockLoot() {
        return getConfig().randomizeBlockLoot.get();
    }

    public static boolean randomizeEntityLoot() {
        return getConfig().randomizeEntityLoot.get();
    }

    public static boolean randomizeChestLoot() {
        return getConfig().randomizeChestLoot.get();
    }

    public static boolean mobRandomizerEnabled() {
        return getConfig().randomizeMobs.get();
    }
}
