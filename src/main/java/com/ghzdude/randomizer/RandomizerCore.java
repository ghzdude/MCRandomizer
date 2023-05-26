package com.ghzdude.randomizer;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(RandomizerCore.MODID)
public class RandomizerCore
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "randomizer";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    // Creates a new Block with the id "examplemod:example_block", combining the namespace and path
    // public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block", () -> new Block(BlockBehaviour.Properties.of(Material.STONE)));
    // Creates a new BlockItem with the id "examplemod:example_block", combining the namespace and path
    // public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block", () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties().tab(CreativeModeTab.TAB_BUILDING_BLOCKS)));

    private static int OFFSET = 0;
    private static final int COUNTER_MAX = 20;
    private static int STRUCTURE_PROBABILITY = 0;
    public static final RandomSource RANDOM = RandomSource.create();
    private static int points = 0;
    private static int pointMax = 1;
    private static int amtItemsGiven = 0;
    private static int cycle = 0;
    private static int cycleCounter = 3;

    public RandomizerCore()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RandomizerConfig.getSpec());

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        // BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        // ITEMS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(new RecipeRandomizer());
        MinecraftForge.EVENT_BUS.register(new LootRandomizer());
        MinecraftForge.EVENT_BUS.register(new MobRandomizer());
    }

    public static void incrementAmtItemsGiven() {
        amtItemsGiven++;
    }

    @SubscribeEvent
    public void update(TickEvent.PlayerTickEvent event) {
        if (event.side.isClient()) return;
        if (event.phase == TickEvent.Phase.END) return;
        if (OFFSET < 0) OFFSET = 0;
        OFFSET++;
        ServerPlayer player = (ServerPlayer) event.player;

        if (player.gameMode.isSurvival() && OFFSET % RandomizerConfig.getCooldown() == 0) {

            points = pointMax;

            int pointsToUse = RANDOM.nextIntBetweenInclusive(1, points);
            points -= pointsToUse;

            int selection = RANDOM.nextInt(100);
            if (RandomizerConfig.structureRandomizerEnabled() && selection < STRUCTURE_PROBABILITY) {

                pointsToUse = StructureRandomizer.placeStructure(pointsToUse, player.getLevel(), player);

            } else if (RandomizerConfig.itemRandomizerEnabled()) {
                player.displayClientMessage(Component.literal("Giving Item..."), true);
                pointsToUse = ItemRandomizer.giveRandomItem(pointsToUse, player);

                // make this per cycle instead of amount items given
                // the time between incrementing point max should increase slowly overtime
                cycle++;
                if (cycle == cycleCounter) {
                    cycle = 0;
                    if (cycleCounter < COUNTER_MAX) {
                        cycleCounter++;
                    }
                    pointMax++;
                    player.sendSystemMessage(Component.translatable("player.point_max.increased", pointMax));
                }
            }
            points += pointsToUse;
        }
    }

    @SubscribeEvent
    public void onLogin (PlayerEvent.PlayerLoggedInEvent player) {
        CompoundTag tag = player.getEntity().getPersistentData();
        points = tag.getInt("points");
        pointMax = tag.getInt("point_max");
        amtItemsGiven = tag.getInt("amount_items_given");
        cycle = tag.getInt("cycle");
        cycleCounter = tag.getInt("cycle_counter");
        STRUCTURE_PROBABILITY = RandomizerConfig.getStructureProbability();
        STRUCTURE_PROBABILITY = Math.max(1, Math.min(100, STRUCTURE_PROBABILITY));

        points = Math.max(points, 0);
        pointMax = Math.max(pointMax, 1);
        amtItemsGiven = Math.max(amtItemsGiven, 0);
        cycle = Math.max(cycle, 0);
        cycleCounter = Math.max(cycleCounter, RandomizerConfig.getCycleBase());
    }

    @SubscribeEvent
    public void onLogout (PlayerEvent.PlayerLoggedOutEvent player) {
        CompoundTag tag = player.getEntity().getPersistentData();
        tag.putInt("points", points);
        tag.putInt("point_max", pointMax);
        tag.putInt("amount_items_given", amtItemsGiven);
        tag.putInt("cycle", cycle);
        tag.putInt("cycle_counter", cycleCounter);
    }
}
