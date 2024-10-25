package com.ghzdude.randomizer;

import com.ghzdude.randomizer.loot.LootRandomizer;
import com.ghzdude.randomizer.special.modifiers.AdvancementModifier;
import com.ghzdude.randomizer.special.modifiers.RecipeModifier;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
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
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final String POINT_KEY = "points";
    private static final String POINT_MAX_KEY = "point_max";
    private static final String CYCLE_KEY = "cycle";
    private static final String CYCLE_COUNTER_KEY = "cycle_counter";
    private static final String AMOUNT_KEY = "amount_items_given";
    public static Random seededRNG;
    public static Random unseededRNG;
    public static boolean serverStarted = false;

    // Creates a new Block with the id "examplemod:example_block", combining the namespace and path
    // public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block", () -> new Block(BlockBehaviour.Properties.of(Material.STONE)));
    // Creates a new BlockItem with the id "examplemod:example_block", combining the namespace and path
    // public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block", () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties().tab(CreativeModeTab.TAB_BUILDING_BLOCKS)));

    private int OFFSET = 0;
    private static final int COUNTER_MAX = 50;

    public RandomizerCore() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RandomizerConfig.Holder.getSpec());

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new RecipeRandomizer());
        MinecraftForge.EVENT_BUS.register(new MobRandomizer());
        MinecraftForge.EVENT_BUS.register(RandomizerConfig.class);
    }

    public static void incrementAmtItemsGiven(Player player) {
        incrementAmtItemsGiven(player.getPersistentData());
    }

    public static void incrementAmtItemsGiven(CompoundTag data) {
        data.putInt(AMOUNT_KEY, data.getInt(AMOUNT_KEY) + 1);
    }

    @SubscribeEvent
    public void onStart(ServerStartedEvent event) {
        var server = event.getServer();
        seededRNG = new Random(server.getWorldData().worldGenOptions().seed());
        unseededRNG = new Random();
        ItemRandomizer.init(server);
        LootRandomizer.init(server);
        RandomizerUtil.init(server.registryAccess());
        serverStarted = true;
    }

    @SubscribeEvent
    public void onStop(ServerStoppingEvent event) {
        RandomizerUtil.dispose();
        serverStarted = false;
    }

    @SubscribeEvent
    public void reload(AddReloadListenerEvent event) {
        if (!serverStarted) return;
        RegistryAccess access = event.getRegistryAccess();
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
    public void update(TickEvent.PlayerTickEvent event) {
        if (!shouldTick(event)) return;

        var player = (ServerPlayer) event.player;
        var data = player.getPersistentData();

        if (shouldUsePoints(player)) {
            int points = data.getInt(POINT_KEY);
            int pointMax = data.getInt(POINT_MAX_KEY);

            if (RandomizerConfig.pointsCarryover) {
                points += pointMax;
            }

            int pointsToUse = seededRNG.nextInt(points) + 1;
            int remaining = pointsToUse;
            points -= pointsToUse;

            int selection = seededRNG.nextInt(100);
            if (RandomizerConfig.generateStructures && selection < RandomizerConfig.structureProbability) {
                remaining = StructureRandomizer.placeStructure(pointsToUse, player.serverLevel(), player);
            } else if (RandomizerConfig.giveRandomItems) {
                player.displayClientMessage(Component.literal("Giving Item..."), true);
                remaining = ItemRandomizer.giveRandomItem(pointsToUse, player.getInventory());
            }

            // we used points, so something succeeded
            if (remaining < pointsToUse) {
                increaseCycle(player, data);
            }

            points += pointsToUse - remaining;
            player.getPersistentData().putInt(POINT_KEY, points);
        }
    }

    private boolean shouldUsePoints(ServerPlayer player) {
        return player.gameMode.isSurvival();
    }

    private boolean shouldTick(TickEvent.PlayerTickEvent event) {
        if (event.side.isClient()) return false;
        if (event.phase == TickEvent.Phase.END) return false;
        if (OFFSET < 0) OFFSET = 0;
        return ++OFFSET % RandomizerConfig.itemCooldown == 0;
    }

    private void increaseCycle(Player player, CompoundTag data) {
        int cycle = data.getInt(CYCLE_KEY) + 1;
        int cycleCounter = data.getInt(CYCLE_COUNTER_KEY);
        int pointMax = data.getInt(POINT_MAX_KEY);

        if (cycle % cycleCounter == 0) {
            cycle = 0;
            int i = (cycleCounter / 2) + 1;
            cycleCounter = Math.min(cycleCounter + i, COUNTER_MAX);
            data.putInt(POINT_MAX_KEY, pointMax + 1);
            player.sendSystemMessage(Component.translatable("player.point_max.increased", pointMax));
        }
        data.putInt(CYCLE_KEY, cycle);
        data.putInt(CYCLE_COUNTER_KEY, cycleCounter);
    }
}
