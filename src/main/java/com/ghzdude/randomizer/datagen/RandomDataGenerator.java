package com.ghzdude.randomizer.datagen;

import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.RecipeRandomizer;
import net.minecraft.data.DataGenerator;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RandomizerCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class RandomDataGenerator {
    @SubscribeEvent
    public static void dataGen(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        ExistingFileHelper fileHelper = event.getExistingFileHelper();

        generator.addProvider(true, new RecipeRandomizer.RandomRecipeProvider(generator));
    }
}
