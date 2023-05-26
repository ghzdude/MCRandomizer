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
    private static int POINTS = 0;
    private static int POINT_MAX = 1;
    private static int AMT_ITEMS_GIVEN = 0;
    private static int CYCLE = 0;
    private static int CYCLE_COUNTER = 3;
    private static final int COUNTER_MAX = 20;
    private static int STRUCTURE_PROBABILITY = 0;
    public static final RandomSource RANDOM = RandomSource.create();

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
        AMT_ITEMS_GIVEN++;
    }

    @SubscribeEvent
    public void update(TickEvent.PlayerTickEvent event) {
        if (event.side.isClient()) return;
        if (event.phase == TickEvent.Phase.END) return;
        if (OFFSET < 0) OFFSET = 0;
        OFFSET++;
        ServerPlayer player = (ServerPlayer) event.player;

        if (player.gameMode.isSurvival() && OFFSET % RandomizerConfig.getCooldown() == 0) {

            POINTS = POINT_MAX;

            int pointsToUse = RANDOM.nextIntBetweenInclusive(1, POINTS);
            POINTS -= pointsToUse;

            int selection = RANDOM.nextInt(100);
            if (RandomizerConfig.structureRandomizerEnabled() && selection < STRUCTURE_PROBABILITY) {

                pointsToUse = StructureRandomizer.placeStructure(pointsToUse, player.getLevel(), player);

            } else if (RandomizerConfig.itemRandomizerEnabled()) {
                player.displayClientMessage(Component.literal("Giving Item..."), true);
                pointsToUse = ItemRandomizer.giveRandomItem(pointsToUse, player);

                // make this per cycle instead of amount items given
                // the time between incrementing point max should increase slowly overtime
                CYCLE++;
                if (CYCLE == CYCLE_COUNTER) {
                    CYCLE = 0;
                    if (CYCLE_COUNTER < COUNTER_MAX) {
                        CYCLE_COUNTER++;
                    }
                    POINT_MAX++;
                    player.sendSystemMessage(Component.translatable("player.point_max.increased", POINT_MAX));
                }
            }
            POINTS += pointsToUse;
        }
    }

    @SubscribeEvent
    public void onLogin (PlayerEvent.PlayerLoggedInEvent player) {
        CompoundTag tag = player.getEntity().getPersistentData();
        POINTS = tag.getInt("points");
        POINT_MAX = tag.getInt("point_max");
        AMT_ITEMS_GIVEN = tag.getInt("amount_items_given");
        CYCLE = tag.getInt("cycle");
        CYCLE_COUNTER = tag.getInt("cycle_counter");
        STRUCTURE_PROBABILITY = RandomizerConfig.getStructureProbability();
        STRUCTURE_PROBABILITY = Math.max(1, Math.min(100, STRUCTURE_PROBABILITY));

        POINTS = Math.max(POINTS, 0);
        POINT_MAX = Math.max(POINT_MAX, 1);
        AMT_ITEMS_GIVEN = Math.max(AMT_ITEMS_GIVEN, 0);
        CYCLE = Math.max(CYCLE, 0);
        CYCLE_COUNTER = Math.max(CYCLE_COUNTER, RandomizerConfig.getCycleBase());
    }

    @SubscribeEvent
    public void onLogout (PlayerEvent.PlayerLoggedOutEvent player) {
        CompoundTag tag = player.getEntity().getPersistentData();
        tag.putInt("points", POINTS);
        tag.putInt("point_max", POINT_MAX);
        tag.putInt("amount_items_given", AMT_ITEMS_GIVEN);
        tag.putInt("cycle", CYCLE);
        tag.putInt("cycle_counter", CYCLE_COUNTER);
    }
}
