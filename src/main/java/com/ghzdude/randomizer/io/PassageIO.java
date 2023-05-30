package com.ghzdude.randomizer.io;

import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.special.passages.Passage;
import com.ghzdude.randomizer.special.passages.Passages;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Locale;

public class PassageIO {
    private static final String PASSAGE_DIR = "config\\" + RandomizerCore.MODID + "\\passages\\";
    private static final File directory = new File(Minecraft.getInstance().gameDirectory, PASSAGE_DIR);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void writeExample() {

        if (!directory.exists()) {
            directory.mkdir();
        }

        Passage passage = Passages.EXAMPLE;
        File passageFile = createFileName(directory, passage.title());

        try {
            Writer writer = Files.newBufferedWriter(passageFile.toPath());
            if (!passageFile.exists()) {
                GSON.toJson(passage, writer);
                writer.close();
            }
        } catch (IOException | NullPointerException e) {
            RandomizerCore.LOGGER.warn("Failure to write JSON at " + passageFile.getAbsolutePath());
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void readPassagesFromDisk() {
        if (!directory.exists()) {
            directory.mkdirs();
        }

        String[] listPath = directory.list();

        if (listPath != null) {
            for (String passage : listPath) {
                File passageFile = new File(directory,  "\\" + passage);
                try {
                    if (passageFile.createNewFile()) {
                        writeExample();
                    }
                    JsonReader reader = GSON.newJsonReader(Files.newBufferedReader(passageFile.toPath()));
                    reader.beginObject();

                    reader.skipValue();
                    String author = reader.nextString();

                    reader.skipValue();
                    String title = reader.nextString();

                    reader.skipValue();
                    String body = reader.nextString();

                    reader.endObject();

                    if (Passages.PASSAGES.stream().noneMatch(pass -> pass.title().equals(title))) {
                        Passages.PASSAGES.add(new Passage(author, title, body));
                    } else {
                        RandomizerCore.LOGGER.warn("Passage \"" + title + "\" already exists! Titles must be unique!");
                    }
                    reader.close();
                } catch (IOException | NullPointerException e) {
                    RandomizerCore.LOGGER.warn("Failure to read JSON at " + passageFile.getAbsolutePath() + " Overriding!");
                }
            }
        }
    }

    public static File createFileName(File directory, String s) {
        return new File(directory, "\\" + s.toLowerCase(Locale.ROOT).replace(" ", "") + ".json");
    }
}
