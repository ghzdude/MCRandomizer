package com.ghzdude.randomizer.io;

import com.ghzdude.randomizer.RandomizerCore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.client.Minecraft;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.data.worldgen.Structures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class ConfigIO {
    private static final String BLACKLIST_DIR = "config\\" + RandomizerCore.MODID + "\\blacklists\\";
    private static final File directory = new File(Minecraft.getInstance().gameDirectory, BLACKLIST_DIR);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final ArrayList<Item> BLACKLISTED_ITEMS = new ArrayList<>(Arrays.asList(
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

    private static final ArrayList<EntityType<?>> BLACKLISTED_ENTITIES = new ArrayList<>(List.of(
            EntityType.ENDER_DRAGON,
            EntityType.WITHER,
            EntityType.WARDEN,
            EntityType.GIANT
    ));

    private static final ArrayList<Structure> BLACKLISTED_STRUCTURES = new ArrayList<>(List.of(
            Structures.NETHER_FOSSIL.value()
    ));

    public static void writeItemBlacklist(File file) {
        JsonArray itemArray = new JsonArray();
        for (Item item : BLACKLISTED_ITEMS) {
            ResourceLocation location = ForgeRegistries.ITEMS.getKey(item);
            if (location != null) {
                itemArray.add(location.toString());
            } else {
                RandomizerCore.LOGGER.warn("Resource Location for " + item + " is null!");
            }
        }

        tryWriteJson(itemArray, file);
    }

    public static void writeMobBlacklist(File file) {
        JsonArray entities = new JsonArray();
        for (EntityType<?> entityType : BLACKLISTED_ENTITIES) {
            ResourceLocation location = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
            if (location != null) {
                entities.add(location.toString());
            } else {
                RandomizerCore.LOGGER.warn("Resource Location for " + entityType + " is null!");
            }
        }

        tryWriteJson(entities, file);
    }

    public static void writeStructureBlacklist(File file) {
        JsonArray structures = new JsonArray();
        for (Structure structure : BLACKLISTED_STRUCTURES) {
            ResourceLocation location = BuiltinRegistries.STRUCTURES.getKey(structure);
            if (location != null) {
                structures.add(location.toString());
            } else {
                RandomizerCore.LOGGER.warn("Resource Location is null!");
            }
        }

        tryWriteJson(structures, file);
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
                writeItemBlacklist(blacklistedItems);
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
                writeMobBlacklist(blacklistedMobs);
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

    public static ArrayList<Structure> readStructureBlacklist() {
        ArrayList<Structure> blacklist = new ArrayList<>();

        File blacklistFile = createFileName("blacklisted_structures");
        try {
            if (blacklistFile.createNewFile()) {
                writeStructureBlacklist(blacklistFile);
            }

            JsonReader reader = GSON.newJsonReader(Files.newBufferedReader(blacklistFile.toPath()));
            reader.beginArray();

            while (reader.peek() == JsonToken.STRING) {
                ResourceLocation location = new ResourceLocation(reader.nextString());
                if (BuiltinRegistries.STRUCTURES.containsKey(location)) {
                    blacklist.add(BuiltinRegistries.STRUCTURES.get(location));
                } else {
                    RandomizerCore.LOGGER.warn("Location " + location + "is not a valid structure!");
                }
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
