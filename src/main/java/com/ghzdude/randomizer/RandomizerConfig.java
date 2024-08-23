package com.ghzdude.randomizer;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.IConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class RandomizerConfig {

    public static int itemCooldown;
    public static boolean pointsCarryover;
    public static boolean giveMultipleItems;
    public static int cycleBase;
    public static boolean randomizeRecipes;
    public static boolean randomizeRecipeInputs;
    public static boolean giveRandomItems;
    public static boolean generateStructures;
    public static int structureProbability;
    public static boolean randomizeLoot;
    public static boolean randomizeBlockLoot;
    public static boolean randomizeEntityLoot;
    public static boolean randomizeChestLoot;
    public static boolean randomizeMobs;

    static {
        update();
    }

    public static void update() {
        itemCooldown = Holder.itemCooldown.get();
        pointsCarryover = Holder.pointsCarryover.get();
        giveMultipleItems = Holder.giveMultipleItems.get();
        cycleBase = Holder.cycleBase.get();
        randomizeRecipes = Holder.randomizeRecipes.get();
        randomizeRecipeInputs = Holder.randomizeRecipeInputs.get();
        giveRandomItems = Holder.giveRandomItems.get();
        generateStructures = Holder.generateStructures.get();
        structureProbability = Holder.structureProbability.get();
        randomizeLoot = Holder.randomizeLoot.get();
        randomizeBlockLoot = Holder.randomizeBlockLoot.get();
        randomizeEntityLoot = Holder.randomizeEntityLoot.get();
        randomizeChestLoot = Holder.randomizeChestLoot.get();
        randomizeMobs = Holder.randomizeMobs.get();
    }

    public static class Holder {

        private static final Pair<Holder, ForgeConfigSpec> RandomizerConfigPair;

        static {
            RandomizerConfigPair = new ForgeConfigSpec.Builder()
                    .configure(Holder::new);
        }

        public static ForgeConfigSpec.ConfigValue<Integer> itemCooldown;
        public static ForgeConfigSpec.ConfigValue<Boolean> pointsCarryover;
        public static ForgeConfigSpec.ConfigValue<Boolean> giveMultipleItems;
        public static ForgeConfigSpec.ConfigValue<Integer> cycleBase;
        public static ForgeConfigSpec.ConfigValue<Boolean> randomizeRecipes;
        public static ForgeConfigSpec.ConfigValue<Boolean> randomizeRecipeInputs;
        public static ForgeConfigSpec.ConfigValue<Boolean> giveRandomItems;
        public static ForgeConfigSpec.ConfigValue<Boolean> generateStructures;
        public static ForgeConfigSpec.ConfigValue<Integer> structureProbability;
        public static ForgeConfigSpec.ConfigValue<Boolean> randomizeLoot;
        public static ForgeConfigSpec.ConfigValue<Boolean> randomizeBlockLoot;
        public static ForgeConfigSpec.ConfigValue<Boolean> randomizeEntityLoot;
        public static ForgeConfigSpec.ConfigValue<Boolean> randomizeChestLoot;
        public static ForgeConfigSpec.ConfigValue<Boolean> randomizeMobs;

        public Holder(ForgeConfigSpec.Builder builder) {

            // Item Randomizer
            builder.push("Item Randomizer");
            giveRandomItems = builder.comment("Should random items be given to the player? Defaults to true.")
                    .define("give_random_items", true);

            itemCooldown = builder.comment("Time between items given (measured in ticks, 20 ticks is one second). Default value is 800.")
                    .define("item_cooldown", 800);

            pointsCarryover = builder.comment("Should unused points carry over after a cycle? : Default value is false.")
                    .define("points_carryover", false);

            giveMultipleItems = builder.comment("Should the randomizer attempt to give the player more items if there are still points to use. Defaults to false.")
                    .define("give_multiple_items", false);

            cycleBase = builder.comment("Amount of cycles needed to reach the first point max increment. Default value is 10.")
                    .define("cycle_base", 10);
            builder.pop();

            // Recipe Randomizer
            builder.push("Recipe Randomizer");
            randomizeRecipes = builder.comment("Should recipes be randomized? Defaults to true.")
                    .define("randomize_recipes", true);
            randomizeRecipeInputs = builder.comment("Should recipe inputs also be randomized? This also attempts to change advancements for unlocking recipes. Defaults to true.")
                    .define("randomize_recipe_inputs", true);
            builder.pop();

            // Structure Randomizer
            builder.push("Structure Randomizer");
            generateStructures = builder.comment("Should random structures be generated when able? Defaults to true.").worldRestart()
                    .define("generate_structures", true);

            structureProbability = builder.comment("Percent chance of how likely structure generation is picked over item generation. Defaults to 10.")
                    .defineInRange("structure_probability", 10, 0, 100);
            builder.pop();

            builder.push("Loot Randomizer");
            randomizeLoot = builder.comment("Should Loot Tables (block drops, entity drops, chest loot) be randomized? Defaults to true.")
                    .define("randomize_loot", true);

            randomizeBlockLoot = builder.comment("Should block drops be randomized? Defaults to true.")
                    .define("randomize_block_loot", true);

            randomizeEntityLoot = builder.comment("Should mob drops be randomized? Defaults to true.")
                    .define("randomize_entity_loot", true);

            randomizeChestLoot = builder.comment("Should chest loot be randomized? Defaults to true.")
                    .define("randomize_chest_loot", true);
            builder.pop();

            builder.push("Mob Randomizer");

            randomizeMobs = builder.comment("Should mobs be randomized when spawning? Attempts to mimic vanilla spawning logic and checks. Ignores spawn egss. Defaults to false.")
                    .define("randomize_mobs", false);
            builder.pop();
        }

        public static IConfigSpec<?> getSpec() {
            return RandomizerConfigPair.getRight();
        }

    }
}
