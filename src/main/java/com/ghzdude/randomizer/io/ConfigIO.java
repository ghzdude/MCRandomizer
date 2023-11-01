package com.ghzdude.randomizer.io;

import com.ghzdude.randomizer.RandomizerCore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class ConfigIO {
    private static final String BLACKLIST_DIR = "config\\" + RandomizerCore.MODID + "\\blacklists\\";
    private static final File directory = new File(Minecraft.getInstance().gameDirectory, BLACKLIST_DIR);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final ArrayList<String> BLACKLISTED_ITEMS = new ArrayList<>(Stream.of(
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
    ).map(Item::toString).toList());

    private static final ArrayList<String> BLACKLISTED_ENTITIES = new ArrayList<>(Stream.of(
            EntityType.ENDER_DRAGON,
            EntityType.WITHER,
            EntityType.WARDEN,
            EntityType.GIANT
    ).map(EntityType::toString).toList());

    private static final ArrayList<String> BLACKLISTED_STRUCTURES =  new ArrayList<>(List.of("namespace:structure_name_here"));

    public static void writeListToFile(File file, List<String> list) {
        JsonArray stringArray = new JsonArray();
        for (String s : list) {
                stringArray.add(s);
        }

        tryWriteJson(stringArray, file);
    }

    private static void tryWriteJson(JsonElement toWrite, File file) {
        try {
            Writer writer = Files.newBufferedWriter(file.toPath());

            GSON.toJson(toWrite, writer);
            writer.close();
        } catch (IOException | NullPointerException e) {
            RandomizerCore.LOGGER.warn("Failure to write JSON at " + file.getAbsolutePath());
        }
    }

    public static ArrayList<Item> readItemBlacklist() {
        ArrayList<Item> blacklist = new ArrayList<>();

        File blacklistedItems = createFileName("blacklisted_items");
        try {
            if (blacklistedItems.createNewFile()) {
                writeListToFile(blacklistedItems, BLACKLISTED_ITEMS);
            }

            JsonReader reader = GSON.newJsonReader(Files.newBufferedReader(blacklistedItems.toPath()));
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
            RandomizerCore.LOGGER.warn("Failure to read JSON at " + blacklistedItems.getAbsolutePath());
        }
        return blacklist;
    }

    public static ArrayList<EntityType<?>> readMobBlacklist() {
        ArrayList<EntityType<?>> blacklist = new ArrayList<>();

        File blacklistedMobs = createFileName("blacklisted_mobs");
        try {
            if (blacklistedMobs.createNewFile()) {
                writeListToFile(blacklistedMobs, BLACKLISTED_ENTITIES);
            }

            JsonReader reader = GSON.newJsonReader(Files.newBufferedReader(blacklistedMobs.toPath()));
            reader.beginArray();

            while (reader.peek() == JsonToken.STRING) {
                ResourceLocation location = new ResourceLocation(reader.nextString());
                if (ForgeRegistries.ENTITY_TYPES.containsKey(location)) {
                    blacklist.add(ForgeRegistries.ENTITY_TYPES.getValue(location));
                } else {
                    RandomizerCore.LOGGER.warn("Location " + location + "is not a proper entity type!");
                }
            }

            reader.endArray();
            reader.close();
        } catch (IOException | NullPointerException e) {
            RandomizerCore.LOGGER.warn("Failure to read JSON at " + blacklistedMobs.getAbsolutePath());
        }
        return blacklist;
    }

    public static ArrayList<ResourceLocation> readStructureBlacklist() {
        ArrayList<ResourceLocation> blacklist = new ArrayList<>();

        File blacklistFile = createFileName("blacklisted_structures");
        try {
            if (blacklistFile.createNewFile()) {
                writeListToFile(blacklistFile, BLACKLISTED_STRUCTURES);
            }

            JsonReader reader = GSON.newJsonReader(Files.newBufferedReader(blacklistFile.toPath()));
            reader.beginArray();

            while (reader.peek() == JsonToken.STRING) {
                ResourceLocation location = new ResourceLocation(reader.nextString());
                blacklist.add(location);
            }

            reader.endArray();
            reader.close();
        } catch (IOException | NullPointerException e) {
            RandomizerCore.LOGGER.warn("Failure to read JSON at " + blacklistFile.getAbsolutePath());
        }
        return blacklist;
    }

    private static File createFileName(String s) {
        if (!ConfigIO.directory.exists()) {
            ConfigIO.directory.mkdirs();
        }
        return new File(ConfigIO.directory, "\\" + s.toLowerCase(Locale.ROOT).replace(" ", "") + ".json");
    }
}
