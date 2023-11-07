package com.ghzdude.randomizer;

import com.ghzdude.randomizer.reflection.ReflectionUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.tags.ITag;
import net.minecraftforge.registries.tags.ITagManager;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Stream;

/* Recipe Randomizer Description.
 * on resource re/load, randomize every recipe.
 * each world would have a unique set of randomized recipes.
 *
 * for advancements, picking up item A gives you recipes whose input is also item A.
 * to be more precise, picking up item A gives you a recipe by resource location
 * the inputs and output can change, but the resource location does not
 * if the recipe inputs are modified, the advancement rewards needs to change.
 * if recipe A with item A as input becomes item B, then picking item B should unlock recipe A.
 * an item can unlock multiple recipes.
 * for a given advancement for an item, i need to find all recipes whose inputs match.
 * then update the rewards for the advancement
 *
 */
public class RecipeRandomizer {

    private static RecipeData INSTANCE;

    @SubscribeEvent
    public void start(ServerStartedEvent event) {
        if (RandomizerConfig.recipeRandomizerEnabled()) {

            RandomizerCore.LOGGER.warn("Recipe Randomizer Running!");
            INSTANCE = get(event.getServer().overworld().getDataStorage());
            randomizeRecipes(
                    event.getServer().getRecipeManager(),
                    event.getServer().registryAccess()
            );

            setAdvancements(event.getServer().getAdvancements());
        }
    }

    @SubscribeEvent
    public void stop(ServerStoppingEvent event) {
        INSTANCE.getMap().clear();
    }

    public static void setAdvancements(ServerAdvancementManager manager) {
        Map<ResourceLocation, AdvancementHolder> holders = ReflectionUtils.getField(ServerAdvancementManager.class, manager, 2);
        Map<ResourceLocation, AdvancementHolder> toKeep = new Object2ObjectOpenHashMap<>();
        holders.forEach(((resourceLocation, holder) -> {
            if (!holder.id().getPath().contains("recipes/")) toKeep.put(resourceLocation, holder);
        }));

        buildAdvancements(toKeep);
        ReflectionUtils.setField(ServerAdvancementManager.class, manager, 2, toKeep);
    }

    public static void randomizeRecipes(RecipeManager manager, RegistryAccess access) {
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            ItemStack newResult = ItemRandomizer.getStackFor(recipe.getResultItem(access));

            modifyRecipeOutputs(recipe, newResult);

            // if inputs are not to be randomized, move on to the next recipe
            if (RandomizerConfig.randomizeInputs()) {
                modifyRecipeInputs(
                        recipe.getIngredients().stream()
                        .distinct().filter(ingredient -> !ingredient.isEmpty()).toList(), holder.id()
                );
            }
            INSTANCE.setDirty();
        }
    }

    private static void modifyRecipeOutputs(Recipe<?> recipe, ItemStack newResult) {
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            ReflectionUtils.setField(ShapedRecipe.class, shapedRecipe, 5, newResult);
        } else if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            ReflectionUtils.setField(ShapelessRecipe.class, shapelessRecipe, 2, newResult);
        } else if (recipe instanceof AbstractCookingRecipe abstractCookingRecipe) {
            ReflectionUtils.setField(AbstractCookingRecipe.class, abstractCookingRecipe, 4, newResult);
        } else if (recipe instanceof SingleItemRecipe singleItemRecipe) {
            ReflectionUtils.setField(SingleItemRecipe.class, singleItemRecipe, 1, newResult);
        }
    }

    private static void modifyRecipeInputs(List<Ingredient> ingredients, ResourceLocation recipe) {
        for (int k = 0; k < ingredients.size(); k++) {
            Ingredient.Value[] values = ReflectionUtils.getField(Ingredient.class, ingredients.get(k), 2);
            if (values.length == 0 || Arrays.stream(values).allMatch(Objects::isNull)) continue;
            // each slot of a recipe has an ingredient
            // each ingredient can have multiple values
            Ingredient toReplace;


            // if an ingredient has only one item, it's probably an item value
            if (ingredients.get(k).getItems().length == 1) {

            } else {// otherwise it's probably a tag value

            }

            for (int j = 0; j < values.length; j++) {
                values[j] = getValue(recipe);
            }
        }
    }

    private static Ingredient.Value getValue(ResourceLocation recipe) {
        IForgeRegistry<Item> registry = ForgeRegistries.ITEMS;
        Ingredient.Value value;
        ResourceLocation ingredient;
        if (INSTANCE.mapContains(recipe)) {
            ingredient = INSTANCE.getIngredient(recipe);
            var holder = registry.getHolder(ingredient);
            if (holder.isPresent()) {
                ItemStack stack = ItemRandomizer.itemToStack(registry.getValue(ingredient));
                value = new Ingredient.ItemValue(stack);
            } else {
                TagKey<Item> key = registry.tags().createTagKey(ingredient);
                value = new Ingredient.TagValue(key);
            }
            return value;
        }

        if (RandomizerCore.seededRNG.nextBoolean()) {
            ItemStack stack = ItemRandomizer.getRandomItemStack(RandomizerCore.seededRNG);
            ingredient = registry.getKey(stack.getItem());
            value = new Ingredient.ItemValue(stack);
        } else {
            TagKey<Item> key = ItemRandomizer.getRandomTag(RandomizerCore.seededRNG);
            ingredient = key.location();
            value = new Ingredient.TagValue(key);
        }

        if (ingredient == null) return null;
        INSTANCE.addToMap(recipe, ingredient);
        return value;
    }


    private static void buildAdvancements(Map<ResourceLocation, AdvancementHolder> map) {
        ITagManager<Item> tagManager = ForgeRegistries.ITEMS.tags();
        if (tagManager == null) return;

        INSTANCE.getMap().forEach((ing, recipes) -> {
            Item[] changedItems;
            Item item = ForgeRegistries.ITEMS.getValue(ing);
            Optional<ITag<Item>> tag = tagManager.getTagNames()
                    .filter(key -> key.location().equals(ing))
                    .map(tagManager::getTag)
                    .findFirst();

            if (item != Items.AIR) {
                changedItems = new Item[]{item};
            } else if (tag.isPresent()) {
                changedItems = tag.get().stream().toArray(Item[]::new);
            } else {
                RandomizerCore.LOGGER.warn("{} is not a valid item or tag!", ing);
                return;
            }

            Advancement.Builder builder = new Advancement.Builder();
            for (ResourceLocation recipe : recipes) {
                builder.rewards(AdvancementRewards.Builder.recipe(recipe));
            }
            builder.addCriterion("has_item", InventoryChangeTrigger.TriggerInstance.hasItems(changedItems));
            AdvancementHolder toAdd = builder.build(new ResourceLocation(RandomizerCore.MODID, ing.getNamespace() + "-" + ing.getPath() + "_gives_recipes"));
            map.put(toAdd.id(), toAdd);
        });
    }

    public static RecipeData get(DimensionDataStorage storage) {
        INSTANCE = storage.computeIfAbsent(RecipeData.factory(), RandomizerCore.MODID + "_recipes");
        return INSTANCE;
    }

    private static class RecipeData extends SavedData {
        private final Map<ResourceLocation, List<ResourceLocation>> MODIFIED = new Object2ObjectOpenHashMap<>();

        public static SavedData.Factory<RecipeData> factory() {
            return new Factory<>(RecipeData::new, RecipeData::load, DataFixTypes.LEVEL);
        }
        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag data = new CompoundTag();
            MODIFIED.forEach((ingredient, recipes) -> {
                var recipesTag = new ListTag();
                recipes.forEach(recipe -> recipesTag.add(StringTag.valueOf(recipe.toString())));
                data.put(ingredient.toString(), recipesTag);
            });
            tag.put("recipe_data", data);
            return tag;
        }

        public static RecipeData load(CompoundTag tag) {
            INSTANCE = new RecipeData();
            CompoundTag data = tag.getCompound("recipe_data");
            data.getAllKeys().forEach(s -> {
                ListTag recipesTag = data.getList(s, Tag.TAG_STRING);
                recipesTag.forEach(tag1 -> {
                    StringTag recipe = (StringTag) tag1;
                    ResourceLocation recipeLocation = new ResourceLocation(recipe.getAsString());
                    INSTANCE.addToMap(new ResourceLocation(s), recipeLocation);
                });
            });
            INSTANCE.setDirty();
            return INSTANCE;
        }

        public void addToMap(@Nonnull ResourceLocation recipe, @Nonnull ResourceLocation ingredient) {
            if (!MODIFIED.containsKey(ingredient)) {
                List<ResourceLocation> list = new ArrayList<>();
                list.add(recipe);
                MODIFIED.put(ingredient, list);
            } else if (!getMap().get(ingredient).contains(ingredient)){
                MODIFIED.get(ingredient).add(recipe);
            }
        }
        public Map<ResourceLocation, List<ResourceLocation>> getMap() {
            return MODIFIED;
        }
        public Stream<Map.Entry<ResourceLocation, List<ResourceLocation>>> stream() {
            return MODIFIED.entrySet().stream();
        }

        public boolean mapContains(ResourceLocation recipe) {
            return stream().anyMatch(entry -> entry.getValue().contains(recipe));
        }

        public ResourceLocation getIngredient(ResourceLocation recipe) {
            return stream().filter(entry -> entry.getValue().contains(recipe)).findFirst().get().getKey();
        }
    }
}
