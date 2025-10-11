package com.ghzdude.randomizer.loot;

import com.ghzdude.randomizer.RandomizationMapData;
import com.ghzdude.randomizer.RandomizerConfig;
import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.compat.jei.ParsedLootTable;
import com.ghzdude.randomizer.special.item.SpecialItems;
import com.ghzdude.randomizer.util.RandomizerUtil;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LootRandomizer {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String MATCH_TOOL = "match_tool";
    public static final String CAN_TOOL_PERFORM_ACTION = "can_tool_perform_action";
    public static final String INVERTED = "inverted";
    private static RandomizationMapData INSTANCE = null;
    public static HolderLookup.RegistryLookup<LootTable> LOOT_REGISTRY;
    public static Registry<Item> ITEM_REGISTRY;
    public static Registry<Block> BLOCK_REGISTRY;

    /**
     * Set of all the valid tables
     */
    private static final ObjectOpenHashSet<ResourceLocation> TABLES = new ObjectOpenHashSet<>();

    /**
     * Maps a block's loot table to the block
     */
    private static final Object2ObjectMap<ResourceLocation, ResourceLocation> BLOCK_MAP = new Object2ObjectOpenHashMap<>();

    /**
     * Maps a loot table to its drops
     */
    private static final Object2ObjectMap<ResourceLocation, Set<LootData>> LOOT_MAP = new Object2ObjectOpenHashMap<>();

    /**
     * Maps a loot table with a map of the loot table drops to a different item required for completion
     */
    private static final Map<ResourceLocation, Map<ResourceLocation, ResourceLocation>> SPECIAL_MAP = new Object2ObjectOpenHashMap<>();

    private static final Map<ResourceLocation, ResourceLocation> ENTITY_EGG_MAP = new Object2ObjectOpenHashMap<>();


    public static ResourceLocation activeLocation;

    private static final Set<ResourceLocation> PICKAXE_MINABLE = new ObjectOpenHashSet<>();
    private static final Set<ResourceLocation> SHOVEL_MINABLE = new ObjectOpenHashSet<>();
    private static final Set<ResourceLocation> REQUIRES_STONE = new ObjectOpenHashSet<>();
    private static final Set<ResourceLocation> REQUIRES_IRON = new ObjectOpenHashSet<>();
    private static final Set<ResourceLocation> REQUIRES_DIAMOND = new ObjectOpenHashSet<>();
    private static RecipeManager RECIPE_MANAGER;
    private static RegistryAccess ACCESS;
    private static boolean appliesToAll;
    private static boolean requiresPick;
    private static boolean requiresShovel;
    private static boolean requiresSilk;
    private static boolean requiresShears;

    public static void init(MinecraftServer server) {
        INSTANCE = RandomizationMapData.get(server, "loot");
        ACCESS = server.registryAccess();
        LOOT_REGISTRY = server.reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE);
        ITEM_REGISTRY = ACCESS.lookupOrThrow(Registries.ITEM);
        BLOCK_REGISTRY = ACCESS.lookupOrThrow(Registries.BLOCK);
        RECIPE_MANAGER = server.getRecipeManager();

        TagKey<Block> pickaxeMineable = BlockTags.create(ResourceLocation.withDefaultNamespace("mineable/pickaxe"));
        TagKey<Block> shovelMineable = BlockTags.create(ResourceLocation.withDefaultNamespace("mineable/shovel"));

        TagKey<Block> needsStone = BlockTags.create(ResourceLocation.withDefaultNamespace("needs_stone_tool"));
        TagKey<Block> needsIron = BlockTags.create(ResourceLocation.withDefaultNamespace("needs_iron_tool"));
        TagKey<Block> needsDiamond = BlockTags.create(ResourceLocation.withDefaultNamespace("needs_diamond_tool"));

//        TagKey<Block> notWooden = BlockTags.create(ResourceLocation.withDefaultNamespace("incorrect_for_wooden_tool"));
//        TagKey<Block> notStone = BlockTags.create(ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool"));
//        TagKey<Block> notIron = BlockTags.create(ResourceLocation.withDefaultNamespace("incorrect_for_iron_tool"));
//        TagKey<Block> notGold = BlockTags.create(ResourceLocation.withDefaultNamespace("incorrect_for_gold_tool"));
//        TagKey<Block> notDiamond = BlockTags.create(ResourceLocation.withDefaultNamespace("incorrect_for_diamond_tool"));
//        TagKey<Block> notNetherite = BlockTags.create(ResourceLocation.withDefaultNamespace("incorrect_for_netherite_tool"));

        collectFromTag(pickaxeMineable, PICKAXE_MINABLE);
        collectFromTag(shovelMineable, SHOVEL_MINABLE);
        collectFromTag(needsStone, REQUIRES_STONE);
        collectFromTag(needsIron, REQUIRES_IRON);
        collectFromTag(needsDiamond, REQUIRES_DIAMOND);

        for (Block block : BLOCK_REGISTRY) {
            if (block == Blocks.AIR) continue;
            Optional<ResourceKey<LootTable>> lootTable = block.getLootTable();
            if (lootTable.isEmpty()) {
                LOGGER.debug("Block {} has no loot table", block);
                continue;
            }

            BLOCK_MAP.put(lootTable.get().location(), BLOCK_REGISTRY.getKey(block));
        }

        for (EntityType<?> type : server.registryAccess().lookupOrThrow(Registries.ENTITY_TYPE)) {
            SpawnEggItem egg = SpawnEggItem.byId(type);
            if (egg == null) continue;
            type.getDefaultLootTable()
                    .map(ResourceKey::location)
                    .ifPresent(key -> ENTITY_EGG_MAP.put(key, ITEM_REGISTRY.getKey(egg)));
        }

        Optional<DynamicOps<JsonElement>> registryOps = RandomizerCore.getOps();
        if (registryOps.isEmpty()) {
            return;
        }
        DynamicOps<JsonElement> ops = registryOps.get();

        LOGGER.info("Iterating through loot tables!");
        List<Holder.Reference<LootTable>> lootTables = LOOT_REGISTRY.listElements().toList();

        if (RandomizerConfig.enableDebug) {
            LOGGER.debug("Found {} loot tables", lootTables.size());
        }

        for (Holder.Reference<LootTable> table : lootTables) {
            // serialize loot table into JSON for easy lookup
            LootTable.DIRECT_CODEC.encodeStart(ops, table.get())
                    .ifError(e -> LOGGER.debug("error encoding table: {}", e.message()))
                    .result()
                    .filter(JsonElement::isJsonObject)
                    .map(JsonElement::getAsJsonObject)
                    .ifPresent(LootRandomizer::handleJson);
        }

        activeLocation = null;

        if (RandomizerConfig.enableDebug) {
            LOGGER.debug("loot map size: {}", LOOT_MAP.size());
        }

        for (ResourceLocation table : LOOT_MAP.keySet()) {
            Set<LootData> lootData = LOOT_MAP.get(table);

            ItemStack inputStack;
            if (isChestLoot(table)) {
                inputStack = new ItemStack(Items.CHEST);
            } else if (isEntityDrop(table)) {
                Optional<Holder.Reference<Item>> egg = ITEM_REGISTRY.get(Objects.requireNonNull(getEggForEntityTable(table)));
                inputStack = egg.map(ItemStack::new).orElse(ItemStack.EMPTY);
            } else if (table.getPath().startsWith("gameplay/fishing")) {
                inputStack = new ItemStack(Items.FISHING_ROD);
            } else if (table.getPath().startsWith("spawners")) {
                inputStack = new ItemStack(Items.SPAWNER);
            } else if (table.getPath().startsWith("gameplay/hero")) {
                inputStack = new ItemStack(Items.EMERALD);
            } else if (isBlock(table)) {
                Item blockItem = getItemFromBlock(getBlockFor(table));
                if (blockItem == null) continue;
                inputStack = new ItemStack(blockItem);
            } else if (table.getPath().startsWith("dispensers/")) {
                inputStack = new ItemStack(Items.DISPENSER);
            } else if (table.getPath().startsWith("pots/")) {
                inputStack = new ItemStack(Items.DECORATED_POT);
            } else if (table.getPath().startsWith("archaeology/")) {
                inputStack = new ItemStack(Items.BRUSH);
            } else if (table.getPath().equals("gameplay/piglin_bartering")) {
                inputStack = new ItemStack(Items.GOLD_INGOT);
            } else if (table.getPath().equals("gameplay/cat_morning_gift")) {
                inputStack = new ItemStack(Items.CAT_SPAWN_EGG);
            } else if (table.getPath().equals("gameplay/sniffer_digging")) {
                inputStack = new ItemStack(Items.SNIFFER_SPAWN_EGG);
            } else if (table.getPath().startsWith("shearing/")) {
                inputStack = new ItemStack(Items.SHEARS);
            } else if (table.getPath().equals("gameplay/panda_sneeze")) {
                inputStack = new ItemStack(Items.PANDA_SPAWN_EGG);
            } else if (table.getPath().equals("gameplay/chicken_lay")) {
                inputStack = new ItemStack(Items.EGG);
            } else {
                if (RandomizerConfig.enableDebug)
                    LOGGER.debug("Unhandled Table: '{}'", table);
                continue;
            }

            if (inputStack.isEmpty()) {
                LOGGER.warn("Input cannot be air for table '{}'!", table);
                return;
            }

            List<Component> lines = RandomizerUtil.getOrCreateLines(inputStack);
            lines.add(Component.translatable("randomizer.compat.jei.table.id", table)
                    .withStyle(ChatFormatting.DARK_GRAY));
            inputStack.set(DataComponents.LORE, new ItemLore(lines));

            List<ItemStack> drops = new ArrayList<>();
            for (LootData data : lootData) {
                ParsedLootTable.Type type = data.getType();
                expandData(data).map(ITEM_REGISTRY::get)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(Holder::get)
                        .map(Item::getDefaultInstance)
                        .filter(stack -> !stack.isEmpty())
                        .forEach(stack -> {
                            configureOutputStack(table, data, stack, type);
                            drops.add(stack);
                        });
            }

            if (!drops.isEmpty())
                ParsedLootTable.registerRecipe(inputStack, drops, table);
        }

        if (RandomizerConfig.enableDebug) {
            LOGGER.debug("Parsed {} loot tables", ParsedLootTable.getKeys().size());
        }
    }

    private static void configureOutputStack(ResourceLocation table, LootData data, ItemStack stack, ParsedLootTable.Type type) {
        List<Component> additional = new ArrayList<>();
        if (isBlock(table)) {
            additional.add(type.getName());
        }
        if (data.silk() && data.shears()) {
            additional.add(ParsedLootTable.Type.SHEARS_OR_SILK.getName());
        } else if (data.silk()) {
            additional.add(ParsedLootTable.Type.SILK.getName());
        } else if (data.shears()) {
            additional.add(ParsedLootTable.Type.SHEARS.getName());
        }
        if (SpecialItems.EFFECT_ITEMS.contains(stack.getItem())) {
            additional.add(Component.literal("May have random effects!"));
        }
        if (SpecialItems.ENCHANTABLE.contains(stack.getItem())) {
            additional.add(Component.literal("May have random enchantments!"));
        }
        if (!additional.isEmpty()) {
            List<Component> existing = RandomizerUtil.getOrCreateLines(stack);
            existing.addAll(additional);
            stack.set(DataComponents.LORE, new ItemLore(existing));
        }
    }

    private static @Nullable Item getItemFromBlock(ResourceLocation block) {
        return switch (BLOCK_REGISTRY.get(block).orElseThrow().get()) {
            case CandleCakeBlock candleCakeBlock -> {
                DataResult<JsonElement> result = CandleCakeBlock.CODEC.encoder().encodeStart(JsonOps.INSTANCE, candleCakeBlock);
                if (result.isError()) yield null;
                yield result.result()
                        .map(JsonElement::getAsJsonObject)
                        .map(object -> object.get("candle").getAsString())
                        .map(ResourceLocation::parse)
                        .map(ITEM_REGISTRY::get)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(Holder::get)
                        .orElse(null);
            }
            case AttachedStemBlock stemBlock -> {
                DataResult<JsonElement> result = AttachedStemBlock.CODEC.encoder().encodeStart(JsonOps.INSTANCE, stemBlock);
                if (result.isError()) yield null;
                yield result.result()
                        .map(JsonElement::getAsJsonObject)
                        .map(object -> object.get("seed").getAsString())
                        .map(ResourceLocation::parse)
                        .map(ITEM_REGISTRY::get)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(Holder::get)
                        .orElse(null);
            }
            case WeepingVinesPlantBlock ignored -> Blocks.WEEPING_VINES.asItem();
            case KelpPlantBlock ignored -> Blocks.KELP.asItem();
            case TwistingVinesPlantBlock ignored -> Blocks.TWISTING_VINES.asItem();
            case CaveVinesPlantBlock ignored -> Blocks.CAVE_VINES.asItem();
            case FlowerPotBlock flowerPotBlock -> flowerPotBlock.getEmptyPot().asItem();
            case BambooSaplingBlock ignored -> Blocks.BAMBOO.asItem();
            case TallSeagrassBlock ignored -> Blocks.SEAGRASS.asItem();
            default -> Optional.of(BLOCK_REGISTRY.get(block).orElseThrow().value()).map(Block::asItem).orElse(null);
        };
    }

    public static boolean hasTable(ResourceLocation table) {
        return LOOT_MAP.containsKey(table);
    }

    public static Set<ResourceLocation> getItems(ResourceLocation table) {
        return LOOT_MAP.get(table).stream().flatMap(LootRandomizer::expandData).collect(Collectors.toUnmodifiableSet());
    }

    @Nullable
    public static ResourceLocation getEggForEntityTable(ResourceLocation table) {
        if (!ENTITY_EGG_MAP.containsKey(table) && table.getPath().startsWith("entities/sheep/")) {
            return ITEM_REGISTRY.getKey(Items.SHEEP_SPAWN_EGG);
        }
        return ENTITY_EGG_MAP.get(table);
    }

    private static Stream<ResourceLocation> expandData(LootData data) {
        if (data.tag()) {
            return ITEM_REGISTRY.get(data.makeTagKey())
                    .map(holders -> holders.stream().map(Holder::get).map(ITEM_REGISTRY::getKey))
                    .orElseThrow();
        } else if (data.reference()) {
            return getItems(data.location()).stream();
        } else {
            return Stream.of(data.location());
        }
    }

    public static Set<ResourceLocation> getDrops(ResourceLocation table) {
        return LOOT_MAP.get(table).stream().map(LootData::location).collect(Collectors.toUnmodifiableSet());
    }

    public static Set<ResourceLocation> getKnownTables() {
        return ImmutableSet.copyOf(LOOT_MAP.keySet());
    }

    @Nullable
    public static ResourceLocation getBlockFor(ResourceLocation table) {
        if (BLOCK_MAP.containsKey(table)) return BLOCK_MAP.get(table);
        LOGGER.warn("Table '{}' is not a block table!", table);
        return null;
    }

    public static void registerSpecialDrop(ResourceLocation table, ResourceLocation drop, ResourceLocation replace) {
        SPECIAL_MAP.computeIfAbsent(table, k -> new Object2ObjectOpenHashMap<>())
                .put(drop, replace);

        ParsedLootTable parsedLootTable = ParsedLootTable.get(table);
        if (parsedLootTable == null) {
            throw new NullPointerException("Parsed LootTable \"" + table + "\" does not exist!");
        }

        List<ItemStack> drops = new ArrayList<>();
        for (LootData data : LOOT_MAP.get(table)) {
            ParsedLootTable.Type type = data.getType();
            expandData(data).map(l -> drop.equals(l) ? replace : l)
                    .map(ITEM_REGISTRY::get)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(Holder::get)
                    .map(Item::getDefaultInstance)
                    .filter(stack -> !stack.isEmpty())
                    .forEach(stack -> {
                        configureOutputStack(table, data, stack, type);
                        drops.add(stack);
                    });
        }

        if (!drops.isEmpty())
            ParsedLootTable.registerRecipe(parsedLootTable.input(), drops, parsedLootTable.lootTable());
    }

    private static void collectFromTag(TagKey<Block> key, Set<ResourceLocation> collection) {
        BLOCK_REGISTRY.get(key).ifPresent(blocks -> blocks.stream()
                .map(holder -> BLOCK_REGISTRY.getKey(holder.get()))
                .forEach(collection::add));
    }

    private static void handleJson(JsonObject table) {
        if (!table.has("random_sequence") || !table.has("pools"))
            return;

        ResourceLocation id = activeLocation = ResourceLocation.parse(table.get("random_sequence").getAsString());
        appliesToAll = false;

        if (!isBlacklisted(id)) TABLES.add(id);

        Set<LootData> items = LOOT_MAP.computeIfAbsent(id, k -> new ObjectOpenHashSet<>());

        // todo handling table json needs to be improved
        // certain tables do not have proper output information
        // trying to fix it is a pain in the ass, so im leaving it for now
        requiresPick = isBlock(id) && PICKAXE_MINABLE.contains(BLOCK_MAP.get(id));
        requiresShovel = isBlock(id) && SHOVEL_MINABLE.contains(BLOCK_MAP.get(id));

        handleJsonRaw(table, items);
    }

    private static void handleJsonRaw(JsonObject table, Set<LootData> items) {
        if (!table.has("pools"))
            return;

        if (!appliesToAll) {
            requiresShears = false;
            requiresSilk = false;
        }

        List<JsonObject> pools = table.getAsJsonArray("pools")
                .asList().stream().map(JsonElement::getAsJsonObject).toList();

        for (JsonObject pool : pools) {
            List<JsonObject> entries = pool.getAsJsonArray("entries")
                    .asList().stream().map(JsonElement::getAsJsonObject).toList();

            if (pool.has("conditions") && !appliesToAll) {
                requiresSilk = handleConditions(pool, MATCH_TOOL, LootRandomizer::handleMatchTool);
                requiresShears = handleConditions(pool, CAN_TOOL_PERFORM_ACTION, LootRandomizer::handleShears);
                appliesToAll = requiresSilk || requiresShears;
            }

            for (JsonObject entry : entries) {
                handleEntry(entry, items);
            }
        }
    }

    private static void handleEntry(JsonObject entry, Set<LootData> items) {
        if (isType(entry, "alternatives")) {
            handleAlternatives(entry, items);
        } else if (isType(entry, "item")) {
            handleItem(entry, items);
        } else if (isType(entry, "empty")) {
            // no-op
        } else if (isType(entry, "loot_table")) {
            JsonElement value = entry.get("value");
            if (value.isJsonObject()) {
                // table within table
                handleJsonRaw(value.getAsJsonObject(), items);
            } else {
                // table location
                ResourceLocation reference = ResourceLocation.parse(value.getAsString());
                addEntry(LootData.table(reference), items);
            }
        } else if (isType(entry, "dynamic")) {
            ResourceLocation name = getName(entry);
            List<ResourceLocation> list = ITEM_REGISTRY.getTags()
                    // this isn't really a great solution, but it should work for sherds
                    .filter(named -> named.key().location().getPath().contains(name.getPath()))
                    .flatMap(HolderSet.ListBacked::stream)
                    .map(holder -> ITEM_REGISTRY.getKey(holder.get()))
                    .toList();

            for (ResourceLocation item : list) {
                addEntry(LootData.standard(getRandomized(item)), items);
            }

        } else if (isType(entry, "tag")) {
            ResourceLocation vanilla = getName(entry);
            ResourceLocation randomized = getRandomized(vanilla);
            addEntry(LootData.tag(randomized), items);
        } else if (RandomizerConfig.enableDebug) {
            LOGGER.debug("unhandled entry: {}", entry);
        }
    }

    private static void addEntry(LootData data, Set<LootData> items) {
        items.add(data);
    }

    private static ResourceLocation getName(JsonObject entry) {
        if (entry.has("name")) {
            return ResourceLocation.parse(entry.get("name").getAsString());
        }
        throw new IllegalArgumentException(String.format("Cannot get item from Entry '%s'", entry));
    }

    private static boolean isType(JsonObject object, String type) {
        if (!object.has("type")) return false;
        return object.get("type").getAsString().contains(type);
    }

    private static boolean isCondition(JsonObject object, String type) {
        if (!object.has("condition")) return false;
        return object.get("condition").getAsString().contains(type);
    }

    private static void handleAlternatives(JsonObject entry, Set<LootData> items) {
        List<JsonObject> children = entry.getAsJsonArray("children")
                .asList().stream().map(JsonElement::getAsJsonObject).toList();

        for (JsonObject child : children) {
            handleEntry(child, items);
        }
    }

    private static void handleItem(JsonObject entry, Set<LootData> items) {
        ResourceLocation vanilla = getName(entry);
        ResourceLocation random = getRandomized(vanilla);
        LootData data = LootData.standard(random).pick(requiresPick).shovel(requiresShovel);

        if (!appliesToAll) {
            requiresSilk = handleConditions(entry, MATCH_TOOL, LootRandomizer::handleMatchTool);
            requiresShears = handleConditions(entry, CAN_TOOL_PERFORM_ACTION, LootRandomizer::handleShears);

            if (entry.has("functions")) {
                List<JsonObject> functions = entry.getAsJsonArray("functions").asList().stream()
                        .map(JsonElement::getAsJsonObject)
                        .toList();

                for (JsonObject function : functions) {
                    Optional<ResourceLocation> location = canSmelt(function, vanilla);
                    if (location.isPresent()) {
                        addEntry(LootData.standard(getRandomized(location.get())).smelt(true), items);
                        break;
                    }
                }
            }
        }

        addEntry(data.silk(requiresSilk).shears(requiresShears), items);
    }

    private static ResourceLocation getRandomized(ResourceLocation vanilla) {
        RandomizationMapData mapData = getMapData(activeLocation);
        if (mapData.getItems().contains(vanilla))
            return mapData.getItemFor(vanilla);
        if (mapData.getTags().contains(vanilla))
            return mapData.getTagKeyFor(vanilla);
        throw new IllegalArgumentException("'%s' must be an item or tag!".formatted(vanilla));
    }

    private static boolean hasCondition(JsonObject object, String type) {
        if (!object.has("conditions")) return false;
        List<JsonObject> conditions = object.getAsJsonArray("conditions")
                .asList().stream().map(JsonElement::getAsJsonObject).toList();

        for (JsonObject condition : conditions) {
            if (isCondition(condition, type)) return true;
        }
        return false;
    }

    private static boolean handleConditions(JsonObject object, String type, Predicate<JsonObject> predicate) {
        if (!object.has("conditions")) return false;
        List<JsonObject> conditions = object.getAsJsonArray("conditions")
                .asList().stream().map(JsonElement::getAsJsonObject).toList();

        for (JsonObject condition : conditions) {
            if (handleCondition(type, predicate, condition)) return true;
        }
        return false;
    }

    private static boolean handleCondition(String type, Predicate<JsonObject> predicate, JsonObject condition) {
        if (isCondition(condition, type) && predicate.test(condition)) {
            return true;
        } else if (isCondition(condition, "any_of")) {
            return handleTerms(type, predicate, condition.getAsJsonArray("terms"));
        } else if (isCondition(condition, INVERTED)) {
            JsonObject term = condition.getAsJsonObject("term");
            return !handleCondition(type, predicate, term);
        }
        return false;
    }

    private static boolean handleTerms(String type, Predicate<JsonObject> predicate, JsonArray condition) {
        List<JsonObject> terms = condition.asList().stream()
                .map(JsonElement::getAsJsonObject).toList();

        for (JsonObject term : terms) {
            if (isCondition(term, type) && predicate.test(term)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handleMatchTool(JsonObject object) {
        if (!object.has("predicate")) return false;

        JsonObject predicate = object.getAsJsonObject("predicate");
        if (!predicate.has("predicates")) return false;

        JsonObject predicates = predicate.getAsJsonObject("predicates");
        if (!predicates.has("minecraft:enchantments")) return false;

        return hasEnchantment(predicates, "silk_touch");
    }

    private static boolean hasEnchantment(JsonObject predicate, String enchantment) {
        return predicate.getAsJsonArray("minecraft:enchantments")
                .asList().stream().map(JsonElement::getAsJsonObject)
                .filter(object -> object.has("enchantments"))
                .map(object -> object.get("enchantments").getAsString())
                .anyMatch(s -> s.contains(enchantment));
    }

    private static boolean handleShears(JsonObject object) {
        if (!object.has("action")) return false;
        return object.get("action").getAsString().contains("shears");
    }

    private static Optional<ResourceLocation> canSmelt(JsonObject function, ResourceLocation currentItem) {
        if (function.has("function") && function.get("function").getAsString().contains("furnace_smelt")) {
            //noinspection unchecked
            List<RecipeHolder<SmeltingRecipe>> recipes = RECIPE_MANAGER.getRecipes().stream()
                    .filter(recipeHolder -> recipeHolder.value().getType().equals(RecipeType.SMELTING))
                    .map(recipeHolder -> (RecipeHolder<SmeltingRecipe>) recipeHolder)
                    .toList();
            Optional<Holder.Reference<Item>> item = ITEM_REGISTRY.get(currentItem);
            if (item.isEmpty()) return Optional.empty();
            ItemStack stack = new ItemStack(item.get());
            return recipes.stream()
                    .filter(holder -> holder.value().input().test(stack))
                    .map(holder -> RandomizerCore.getOps()
                            .flatMap(ops -> Recipe.CODEC.encodeStart(ops, holder.value())
                                    .result()
                                    .map(JsonElement::getAsJsonObject)
                                    .map(object -> object.get("result"))
                                    .flatMap(object -> ItemStack.CODEC.decode(ops, object).result())
                                    .map(Pair::getFirst)))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(is -> Objects.requireNonNull(ITEM_REGISTRY.getKey(is.getItem())))
                    .findAny();
        }
        return Optional.empty();
    }

    public static RandomizationMapData getMapData(ResourceLocation table) {
        if (RandomizerConfig.randomizeLoot && TABLES.contains(table))
            return INSTANCE;
        return RandomizationMapData.VANILLA;
    }

    public static void dispose() {
        ParsedLootTable.clearRegistry();
        TABLES.clear();
        BLOCK_MAP.clear();
        LOOT_MAP.clear();
    }

    private static boolean isBlacklisted(ResourceLocation location) {
        return !RandomizerConfig.randomizeBlockLoot && isBlock(location) ||
                !RandomizerConfig.randomizeEntityLoot && isEntityDrop(location) ||
                !RandomizerConfig.randomizeChestLoot && isChestLoot(location);
    }

    public static boolean isBlock(ResourceLocation location) {
        return location.getPath().startsWith("blocks/");
    }

    public static boolean isEntityDrop(ResourceLocation location) {
        return location.getPath().startsWith("entities/");
    }

    public static boolean isChestLoot(ResourceLocation location) {
        return location.getPath().startsWith("chests/");
    }

    public static @NotNull ObjectArrayList<ItemStack> randomizeLoot(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ResourceLocation queriedLootTableId = context.getQueriedLootTableId();
        if (!TABLES.contains(queriedLootTableId)) return generatedLoot;

        RandomizationMapData mapData = getMapData(queriedLootTableId);

        if (RandomizerConfig.enableDebug) {
            LOGGER.debug("Table '{}' is being queried, randomizing", queriedLootTableId);
        }

        Map<ResourceLocation, ResourceLocation> replacementMap = SPECIAL_MAP.getOrDefault(queriedLootTableId, Collections.emptyMap());

        ObjectArrayList<ItemStack> ret = new ObjectArrayList<>();
        for (ItemStack stack : generatedLoot) {
            if (!stack.isEmpty()) {
                var random = mapData.getItemFor(stack.getItem());
                ResourceLocation key = ITEM_REGISTRY.getKey(random);
                if (replacementMap.containsKey(key)) {
                    random = ITEM_REGISTRY.get(replacementMap.get(key)).orElseThrow().get();
                }
                ret.add(RandomizerUtil.itemToStack(random, stack.getCount()));
            } else {
                ret.add(ItemStack.EMPTY);
            }
        }

        return ret;
    }

    public static final class LootData {
        public static final int REQUIRES_SILK = 0;
        public static final int REQUIRES_SHEARS = 1;
        public static final int REQUIRES_SMELT = 2;
        public static final int REQUIRES_PICK = 3;
        public static final int TABLE_REFERENCE = 4;
        public static final int TAG_REFERENCE = 5;
        public static final int REQUIRES_SHOVEL = 6;

        public static LootData standard(ResourceLocation item) {
            return new LootData(item);
        }

        public static LootData tag(ResourceLocation item) {
            return new LootData(item).tag(true);
        }

        public static LootData table(ResourceLocation item) {
            return new LootData(item).reference(true);
        }

        private final ResourceLocation location;
        private final BitSet data = new BitSet();

        public LootData(@NotNull ResourceLocation location) {
            this.location = Objects.requireNonNull(location);
        }

        public boolean silk() {
            return data.get(REQUIRES_SILK);
        }

        public boolean shears() {
            return data.get(REQUIRES_SHEARS);
        }

        public boolean smelt() {
            return data.get(REQUIRES_SMELT);
        }

        public boolean pick() {
            return data.get(REQUIRES_PICK);
        }

        public boolean shovel() {
            return data.get(REQUIRES_SHOVEL);
        }

        public boolean reference() {
            return data.get(TABLE_REFERENCE);
        }

        public boolean tag() {
            return data.get(TAG_REFERENCE);
        }

        public LootData silk(boolean b) {
            data.set(REQUIRES_SILK, b);
            return this;
        }

        public LootData shears(boolean b) {
            data.set(REQUIRES_SHEARS, b);
            return this;
        }

        public LootData smelt(boolean b) {
            data.set(REQUIRES_SMELT, b);
            return this;
        }

        public LootData pick(boolean b) {
            data.set(REQUIRES_PICK, b);
            return this;
        }

        public LootData shovel(boolean b) {
            data.set(REQUIRES_SHOVEL, b);
            return this;
        }

        public LootData reference(boolean b) {
            data.set(TABLE_REFERENCE, b);
            return this;
        }

        public LootData tag(boolean b) {
            data.set(TAG_REFERENCE, b);
            return this;
        }

        public ParsedLootTable.Type getType() {
            if (!shovel() && !pick()) return ParsedLootTable.Type.HAND;
            return shovel() ? ParsedLootTable.Type.SHOVEL : ParsedLootTable.Type.PICK;
        }

        public TagKey<Item> makeTagKey() {
            return TagKey.create(Registries.ITEM, this.location());
        }

        public ResourceLocation location() {
            return location;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (LootData) obj;
            return Objects.equals(this.location, that.location) &&
                    Objects.equals(this.data, that.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(location, data);
        }

        @Override
        public String toString() {
            return "LootData[" +
                    "location=" + location +
                    ", silk=" + silk() +
                    ", shears=" + shears() +
                    ", pick=" + pick() +
                    ", smelt=" + smelt() +
                    ", table=" + reference() +
                    ", tag=" + tag() +
                    ']';
        }
    }
}
