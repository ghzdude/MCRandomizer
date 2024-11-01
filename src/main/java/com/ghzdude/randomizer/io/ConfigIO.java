package com.ghzdude.randomizer.io;

import com.ghzdude.randomizer.RandomizerCore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings({"ResultOfMethodCallIgnored", "SameParameterValue"})
public class ConfigIO {
    private static final String BLACKLIST_DIR = "config\\" + RandomizerCore.MODID + "\\blacklists\\";
    private static final File directory = new File(FMLPaths.CONFIGDIR.get().toFile(), BLACKLIST_DIR);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void writeListToFile(File file, List<ResourceLocation> list) {
        JsonArray stringArray = new JsonArray();
        list.forEach(loc -> stringArray.add(loc.toString()));
        tryWriteJson(stringArray, file);
    }

    private static void tryWriteJson(JsonElement toWrite, File file) {
        try (Writer writer = Files.newBufferedWriter(file.toPath());) {
            GSON.toJson(toWrite, writer);

        } catch (IOException | NullPointerException e) {
            RandomizerCore.LOGGER.warn("Failure to write JSON at {}", file.getAbsolutePath());
        }
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
            RandomizerCore.LOGGER.warn("Failure to read JSON at {}", blacklistFile.getAbsolutePath());
        }
        return blacklist;
    }

    public static List<ResourceLocation> read(@NotNull String file, @NotNull List< @NotNull ResourceLocation> defaults) {
        return read(file, defaults, null);
    }

    private static File createFileName(String s) {
        if (!ConfigIO.directory.exists()) {
            ConfigIO.directory.mkdirs();
        }
        return new File(ConfigIO.directory, "\\" + s.toLowerCase(Locale.ROOT).replace(" ", "") + ".json");
    }
}
