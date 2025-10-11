package com.ghzdude.randomizer;

import com.ghzdude.randomizer.loot.LootRandomizer;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.Random;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(RandomizerCore.MODID)
public class RandomizerCore
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "randomizer";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    public static Random seededRNG;
    public static Random unseededRNG;
    public static boolean serverStarted = false;
    private static DynamicOps<JsonElement> OPS;

    public RandomizerCore(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, RandomizerConfig.Holder.getSpec());

        MobSpawnEvent.FinalizeSpawn.BUS.addListener(MobRandomizer::randomizeSpawn);
        // todo improve loot randomizer with load event
//        LootTableLoadEvent.BUS.addListener(LootRandomizer::test);
        ServerStartingEvent.BUS.addListener(event -> {
            final var server = event.getServer();
            OPS = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            seededRNG = new Random(server.getWorldData().worldGenOptions().seed());
            unseededRNG = new Random();
        });
        TickEvent.PlayerTickEvent.Pre.BUS.addListener(ItemRandomizer::playerTickPre);
        ServerStartedEvent.BUS.addListener(RandomizerCore::onStart);
        ServerStoppingEvent.BUS.addListener(RandomizerCore::onStop);
        AddReloadListenerEvent.BUS.addListener(event -> event.addListener(simple(RecipeRandomizer::reload)));
    }

    static void onStart(ServerStartedEvent event) {
        final var server = event.getServer();
        RandomizationMapData.init(server.registryAccess());
        ItemRandomizer.init(server);
        RecipeRandomizer.init(server);
        LootRandomizer.init(server);
        MobRandomizer.init(server.registryAccess());
        RandomizerUtil.init(server.registryAccess());
        if (RandomizerConfig.ensureCompletability) {
            CompletabilityVerifier.init(server);
            CompletabilityVerifier.ensureCompletability();
        }
        RandomizerConfig.update();
        serverStarted = true;
    }

    static void onStop(ServerStoppingEvent event) {
        RandomizerUtil.dispose();
        LootRandomizer.dispose();
        CompletabilityVerifier.dispose();
        serverStarted = false;
    }

    public static Optional<DynamicOps<JsonElement>> getOps() {
        return Optional.ofNullable(OPS);
    }

    private static ResourceManagerReloadListener simple(Runnable runnable) {
        return m -> runnable.run();
    }
}
