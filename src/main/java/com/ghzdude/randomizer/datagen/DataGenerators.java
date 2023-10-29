package com.ghzdude.randomizer.datagen;

import com.ghzdude.randomizer.RandomizerCore;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RandomizerCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DataGenerators {
    @SubscribeEvent
    public static void runData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(), new GlobalLootProvider(event.getGenerator().getPackOutput()));
    }
}
