package com.ghzdude.randomizer.io;

import com.ghzdude.randomizer.RandomizerCore;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings({"SameParameterValue"})
public class ConfigIO {
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve(RandomizerCore.MODID);
    private static final Path BLACKLIST_DIR = CONFIG_DIR.resolve("blacklists");
    private static final Path VALUE_DIR = CONFIG_DIR.resolve("values");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String JSON_FILE = "%s.json";

    public static void writeListToFile(File file, List<ResourceLocation> list) {
        JsonArray stringArray = new JsonArray();
        list.forEach(loc -> stringArray.add(loc.toString()));
        tryWriteJson(stringArray, file);
    }

    public static void writeValues(File valueFile, Object2IntMap<ResourceLocation> valueMap) {
        JsonObject map = new JsonObject();
        for (var location : valueMap.keySet()) {
            map.addProperty(location.toString(), valueMap.getInt(location));
        }
        tryWriteJson(map, valueFile);
    }

    private static void tryWriteJson(JsonElement toWrite, File file) {
        try {
            if (!file.createNewFile()) return;
            Writer writer = Files.newBufferedWriter(file.toPath());
            GSON.toJson(toWrite, writer);
            writer.close();

        } catch (IOException | NullPointerException e) {
            RandomizerCore.LOGGER.warn("Failure to write JSON at {}", file);
        }
    }

    public static <T> Object2IntMap<ResourceLocation> readValues(String file, Object2IntMap<ResourceLocation> defaults, Registry<T> registry) {
        final Object2IntMap<ResourceLocation> map = new Object2IntArrayMap<>();

        File valueFile = VALUE_DIR.resolve(JSON_FILE.formatted(file)).toFile();
        if (!valueFile.exists() && (valueFile.getParentFile().exists() || valueFile.getParentFile().mkdirs())) {
            writeValues(valueFile, defaults);
            return defaults;
        }
        try {
            var reader = GSON.newJsonReader(Files.newBufferedReader(valueFile.toPath()));
            reader.beginObject();
            while (reader.peek() != JsonToken.END_OBJECT) {
                var loc = ResourceLocation.parse(reader.nextName());
                int i = reader.nextInt();
                if (registry.containsKey(loc)) {
                    map.put(loc, i);
                    continue;
                }

                RandomizerCore.LOGGER.warn("Value \"{}\" does not exist in {} or is invalid!", loc, registry.key());
            }
            reader.endObject();
            reader.close();

        } catch (IOException e) {
            readFail(valueFile);
        }

        return map;
    }

    public static List<ResourceLocation> read(@NotNull String file, @NotNull List< @NotNull ResourceLocation> defaults, @Nullable Registry<?> registry) {
        List<ResourceLocation> blacklist = new ArrayList<>();

        File blacklistFile = createFileName(file);
        try {
            if (blacklistFile.createNewFile()) {
                writeListToFile(blacklistFile, defaults);
                return defaults;
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
            readFail(blacklistFile);
        }
        return blacklist;
    }

    private static File createFileName(String file) {
        if (Files.exists(BLACKLIST_DIR) || BLACKLIST_DIR.toFile().mkdirs()) {
            file = file.toLowerCase(Locale.ROOT).replace(" ", "");
            return BLACKLIST_DIR.resolve(JSON_FILE.formatted(file)).toFile();
        }
        throw new IllegalStateException("Failed to make file for \"%s\" in \"%s\"".formatted(file, BLACKLIST_DIR));
    }

    private static void readFail(File file) {
        RandomizerCore.LOGGER.warn("Failure to read JSON at {}", file.getAbsolutePath());
    }
}
