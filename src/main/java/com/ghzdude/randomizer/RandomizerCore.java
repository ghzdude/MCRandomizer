package com.ghzdude.randomizer;

import com.ghzdude.randomizer.loot.LootRandomizer;
import com.ghzdude.randomizer.special.modifiers.AdvancementModifier;
import com.ghzdude.randomizer.special.modifiers.RecipeModifier;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.Random;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(RandomizerCore.MODID)
public class RandomizerCore
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "randomizer";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String POINT_KEY = "points";
    private static final String POINT_MAX_KEY = "point_max";
    private static final String CYCLE_KEY = "cycle";
    private static final String CYCLE_COUNTER_KEY = "cycle_counter";
    private static final String AMOUNT_KEY = "amount_items_given";
    public static Random seededRNG;
    public static Random unseededRNG;
    public static boolean serverStarted = false;

    private int OFFSET = 0;
    private static final int COUNTER_MAX = 50;

    public RandomizerCore(FMLJavaModLoadingContext context) {
//        var modEventBus = context.getModBusGroup();

        context.registerConfig(ModConfig.Type.COMMON, RandomizerConfig.Holder.getSpec());

        // Register the commonSetup method for modloading
//        modEventBus.addListener(this::commonSetup);
//        MinecraftForge.EVENT_BUS

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        EntityJoinLevelEvent.BUS.addListener(MobRandomizer::onEntityJoin);
//        MinecraftForge.EVENT_BUS.register(MobRandomizer::onEntityJoin);
    }

    public static void incrementAmtItemsGiven(Player player) {
        incrementAmtItemsGiven(player.getPersistentData());
    }

    public static void incrementAmtItemsGiven(CompoundTag data) {
        data.getInt(AMOUNT_KEY).ifPresent(integer -> data.putInt(AMOUNT_KEY, integer + 1));
    }

    @SubscribeEvent
    public void onStart(ServerStartedEvent event) {
        final var server = event.getServer();
        seededRNG = new Random(server.getWorldData().worldGenOptions().seed());
        unseededRNG = new Random();
        RandomizationMapData.init(server.registryAccess());
        ItemRandomizer.init(server);
        RecipeRandomizer.init(server);
        LootRandomizer.init(server);
        RandomizerUtil.init(server.registryAccess());
        if (RandomizerConfig.ensureCompletability) {
            CompletabilityVerifier.init(server);
            CompletabilityVerifier.ensureCompletability();
        }
        RandomizerConfig.update();
        serverStarted = true;
    }

    @SubscribeEvent
    public void onStop(ServerStoppingEvent event) {
        RandomizerUtil.dispose();
        LootRandomizer.dispose();
        CompletabilityVerifier.dispose();
        serverStarted = false;
    }

    @SubscribeEvent
    public void reload(AddReloadListenerEvent event) {
        if (!serverStarted) return;
        HolderLookup.Provider access = event.getRegistries();
        RecipeManager recipeManager = event.getServerResources().getRecipeManager();
        ServerAdvancementManager serverAdvancementManager = event.getServerResources().getAdvancements();

        if (RandomizerConfig.randomizeRecipes) {
            event.addListener(new RecipeModifier(access, recipeManager));
        }

        if (RandomizerConfig.randomizeRecipeInputs) {
            event.addListener(new AdvancementModifier(serverAdvancementManager));
        }
    }

    @SubscribeEvent
    public void playerTickPre(TickEvent.PlayerTickEvent.Pre event) {
        if (!shouldTick(event)) return;

        var player = (ServerPlayer) event.player;
        var data = player.getPersistentData();

        if (shouldUsePoints(player)) {
            if (!data.contains(POINT_MAX_KEY))
                data.putInt(POINT_MAX_KEY, 1);

            int pointMax = data.getInt(POINT_MAX_KEY).orElseThrow();

            int points = RandomizerConfig.pointsCarryover ?
                    data.getInt(POINT_KEY).orElseThrow() + pointMax : pointMax;

            int pointsToUse = seededRNG.nextInt(points) + 1;
            int remaining = pointsToUse;

            if (RandomizerConfig.generateStructures && seededRNG.nextInt(100) < RandomizerConfig.structureProbability) {
                remaining = StructureRandomizer.tryPlace(pointsToUse, player.level(), player);
            } else if (RandomizerConfig.giveRandomItems) {
                remaining = ItemRandomizer.giveRandomItem(pointsToUse, player.getInventory());
            }

            // we used points, so something succeeded
            if (remaining < pointsToUse) {
                increaseCycle(player, data);
            }

            data.putInt(POINT_KEY, remaining);
        }
    }

    private boolean shouldUsePoints(ServerPlayer player) {
        return player.gameMode.isSurvival();
    }

    private boolean shouldTick(TickEvent.PlayerTickEvent.Pre event) {
        if (event.side.isClient()) return false;
        if (OFFSET < 0) OFFSET = 0;
        return ++OFFSET % RandomizerConfig.itemCooldown == 0;
    }

    private void increaseCycle(Player player, CompoundTag data) {
        if (!data.contains(CYCLE_COUNTER_KEY))
            data.putInt(CYCLE_COUNTER_KEY, RandomizerConfig.cycleBase);

        int cycle = data.getInt(CYCLE_KEY).orElseThrow() + 1;
        int cycleCounter = data.getInt(CYCLE_COUNTER_KEY).orElseThrow();
        int pointMax = data.getInt(POINT_MAX_KEY).orElseThrow();

        if (cycle % cycleCounter == 0) {
            cycle = 0;
            int i = (cycleCounter / 2) + 1;
            cycleCounter = Math.min(cycleCounter + i, COUNTER_MAX);
            data.putInt(POINT_MAX_KEY, pointMax + 1);
            player.displayClientMessage(Component.translatable("randomizer.player.point_max.increased", pointMax), false);
        }
        data.putInt(CYCLE_KEY, cycle);
        data.putInt(CYCLE_COUNTER_KEY, cycleCounter);
    }
}
