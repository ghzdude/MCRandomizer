package com.ghzdude.randomizer;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public class RandomizerConfig {

    public static final Pair<RandomizerConfig, ForgeConfigSpec> RandomizerConfigPair;
    public final ForgeConfigSpec.ConfigValue<Integer> itemCooldown;
    // public final ForgeConfigSpec.ConfigValue<List<? extends Item>> blacklistedItems;

    RandomizerConfig (ForgeConfigSpec.Builder builder) {

        itemCooldown = builder.comment("time between items given (measured in ticks, 20 ticks is one second)")
                .define("item_cooldown", 1200);

        // shit don't work
        /*blacklistedItems = builder.comment("List of Items/Blocks that shouldn't be given to the player")
                .defineList("item_blacklist", new BlacklistedItems().get(), o -> o instanceof Item);
        */
    }

    static {
        RandomizerConfigPair = new ForgeConfigSpec.Builder()
                .configure(RandomizerConfig::new);
    }

    public static int getCooldown(){
        return RandomizerConfigPair.getLeft().itemCooldown.get();
    }
}
