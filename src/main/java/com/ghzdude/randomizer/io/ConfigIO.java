package com.ghzdude.randomizer.io;

import com.ghzdude.randomizer.RandomizerCore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class ConfigIO {
    private static final String BLACKLIST_DIR = "config\\" + RandomizerCore.MODID + "\\blacklists\\";
    private static final File directory = new File(Minecraft.getInstance().gameDirectory, BLACKLIST_DIR);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final ArrayList<Item> BLACKLISTED_ITEMS = new ArrayList<>(Arrays.asList(
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
            Items.JIGSAW
    ));

    public static void writeDefaults() {
        File passageFile = createFileName(directory, "blacklisted_items");
        JsonArray itemArray = new JsonArray();
        for (Item item : BLACKLISTED_ITEMS) {
            ResourceLocation location = ForgeRegistries.ITEMS.getKey(item);
            if (location != null) {
                itemArray.add(location.toString());
            } else {
                RandomizerCore.LOGGER.warn("Invalid item " + item + " in JSON");
            }
        }

        try {
            Writer writer = Files.newBufferedWriter(passageFile.toPath());
            passageFile.createNewFile();
            GSON.toJson(itemArray, writer);
            writer.close();
        } catch (IOException | NullPointerException e) {
            RandomizerCore.LOGGER.warn("Failure to write JSON at " + passageFile.getAbsolutePath());
        }
    }

    public static ArrayList<Item> readBlacklist() {
        ArrayList<Item> blacklist = new ArrayList<>();

        if (!directory.exists()) {
            directory.mkdirs();
        }

        File passageFile = createFileName(directory, "blacklisted_items");
        try {
            if (passageFile.createNewFile()) {
                writeDefaults();
            }
            JsonReader reader = GSON.newJsonReader(Files.newBufferedReader(passageFile.toPath()));
            reader.beginArray();

            while (reader.peek() == JsonToken.STRING) {
                ResourceLocation location = new ResourceLocation(reader.nextString());
                if (ForgeRegistries.ITEMS.containsKey(location)) {
                    blacklist.add(ForgeRegistries.ITEMS.getValue(location));
                } else {
                    RandomizerCore.LOGGER.warn("Location " + location + "is not a proper item!");
                }
            }

            reader.endArray();
            reader.close();
        } catch (IOException | NullPointerException e) {
            RandomizerCore.LOGGER.warn("Failure to read JSON at " + passageFile.getAbsolutePath());
        }
        return blacklist;
    }

    private static File createFileName(File directory, String s) {
        return new File(directory, "\\" + s.toLowerCase(Locale.ROOT).replace(" ", "") + ".json");
    }
}
