package com.ghzdude.randomizer;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
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
    private static final ItemRandomizer ITEM_RANDOMIZER = new ItemRandomizer();
    public static final RandomSource RANDOM = RandomSource.create();

    public RandomizerCore()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RandomizerConfig.RandomizerConfigPair.getRight());

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

    }

    public static void incrementAmtItemsGiven() {
        AMT_ITEMS_GIVEN++;
    }

    @SubscribeEvent
    public void update(TickEvent.PlayerTickEvent event) {
        if (event.side == LogicalSide.CLIENT) return;
        if (event.phase == TickEvent.Phase.END) return;
        if (OFFSET < 0) OFFSET = 0;
        OFFSET++;

        if (OFFSET % RandomizerConfig.getCooldown() == 0) {

            POINTS = POINT_MAX;

            Player player = event.player;
            if (player.getInventory().getFreeSlot() == -1) return;

            int pointsToUse = RANDOM.nextIntBetweenInclusive(1, POINTS);
            POINTS -= pointsToUse;

            int selection = RANDOM.nextInt(100);
            if (selection < 100) {
                MinecraftServer server = event.player.getServer();
                if (server == null) return;
                ServerLevel level = server.getLevel(event.player.getLevel().dimension());
                if (level == null) return;
                pointsToUse -= StructureRandomizer.placeStructure(pointsToUse, level);
            } else {
                pointsToUse -= ITEM_RANDOMIZER.GiveRandomItem(pointsToUse, player.getInventory());
            }

            if (AMT_ITEMS_GIVEN % 20 == 0) {
                player.sendSystemMessage(Component.translatable("player.point_max.increased", POINT_MAX));
                POINT_MAX++;
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

        POINTS = Math.max(POINTS, 0);
        POINT_MAX = Math.max(POINT_MAX, 1);
        AMT_ITEMS_GIVEN = Math.max(AMT_ITEMS_GIVEN, 0);
    }

    @SubscribeEvent
    public void onLogout (PlayerEvent.PlayerLoggedOutEvent player) {
        CompoundTag tag = player.getEntity().getPersistentData();
        tag.putInt("points", POINTS);
        tag.putInt("point_max", POINT_MAX);
        tag.putInt("amount_items_given", AMT_ITEMS_GIVEN);
    }
}
