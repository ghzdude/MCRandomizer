package com.ghzdude.randomizer.io;

import com.ghzdude.randomizer.RandomizerCore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

@SuppressWarnings({"ResultOfMethodCallIgnored", "SameParameterValue"})
public class ConfigIO {
    private static final String BLACKLIST_DIR = "config\\" + RandomizerCore.MODID + "\\blacklists\\";
    private static final File directory = new File(FMLPaths.CONFIGDIR.get().toFile(), BLACKLIST_DIR);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final List<String> BLACKLISTED_ITEMS = Stream.of(
            Items.AIR,
            Items.COMMAND_BLOCK,
            Items.COMMAND_BLOCK_MINECART,
            Items.CHAIN_COMMAND_BLOCK,
            Items.REPEATING_COMMAND_BLOCK,
            Items.BARRIER,
            Items.LIGHT,
            Items.STRUCTURE_BLOCK,
            Items.STRUCTURE_VOID,
            Items.KNOWLEDGE_BOOK,
            Items.JIGSAW,
            Items.DEBUG_STICK
    ).map(Item::toString).toList();

    private static final List<String> BLACKLISTED_ENTITIES = Stream.of(
            EntityType.ENDER_DRAGON,
            EntityType.WITHER,
            EntityType.WARDEN,
            EntityType.GIANT
    ).map(ForgeRegistries.ENTITY_TYPES::getKey)
            .filter(Objects::nonNull)
            .map(Objects::toString)
            .toList();

    private static final List<String> BLACKLISTED_STRUCTURES = List.of("namespace:structure_name_here");

    public static void writeListToFile(File file, List<String> list) {
        JsonArray stringArray = new JsonArray();
        list.forEach(stringArray::add);
        tryWriteJson(stringArray, file);
    }

    private static void tryWriteJson(JsonElement toWrite, File file) {
        try (Writer writer = Files.newBufferedWriter(file.toPath());) {
            GSON.toJson(toWrite, writer);

        } catch (IOException | NullPointerException e) {
            RandomizerCore.LOGGER.warn("Failure to write JSON at {}", file.getAbsolutePath());
        }
    }

    private static List<ResourceLocation> read(@NotNull String file, @NotNull List< @NotNull String> defaults, @Nullable IForgeRegistry<?> registry) {
        List<ResourceLocation> blacklist = new ArrayList<>();

        File blacklistFile = createFileName(file);
        try {
            if (blacklistFile.createNewFile()) {
                writeListToFile(blacklistFile, defaults);
            }

            JsonReader reader = GSON.newJsonReader(Files.newBufferedReader(blacklistFile.toPath()));
            reader.beginArray();

            while (reader.peek() == JsonToken.STRING) {
                ResourceLocation location = ResourceLocation.parse(reader.nextString());
                if (registry == null || registry.containsKey(location)) {
                    blacklist.add(location);
                } else {
                    RandomizerCore.LOGGER.warn("Location {} is not valid!", location);
                }
            }

            reader.endArray();
            reader.close();
        } catch (IOException | NullPointerException e) {
            RandomizerCore.LOGGER.warn("Failure to read JSON at {}", blacklistFile.getAbsolutePath());
        }
        return blacklist;
    }

    private static List<ResourceLocation> read(@NotNull String file, @NotNull List< @NotNull String> defaults) {
        return read(file, defaults, null);
    }

    public static List<ResourceLocation> readItemBlacklist() {
        return read("blacklisted_items", BLACKLISTED_ITEMS, ForgeRegistries.ITEMS);
    }

    public static List<ResourceLocation> readMobBlacklist() {
        return read("blacklisted_mobs", BLACKLISTED_ENTITIES, ForgeRegistries.ENTITY_TYPES);
    }

    public static List<ResourceLocation> readStructureBlacklist() {
        return read("blacklisted_structures", BLACKLISTED_STRUCTURES);
    }

    private static File createFileName(String s) {
        if (!ConfigIO.directory.exists()) {
            ConfigIO.directory.mkdirs();
        }
        return new File(ConfigIO.directory, "\\" + s.toLowerCase(Locale.ROOT).replace(" ", "") + ".json");
    }
}
