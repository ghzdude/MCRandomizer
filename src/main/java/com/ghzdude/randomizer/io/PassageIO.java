package com.ghzdude.randomizer.io;

import com.ghzdude.randomizer.RandomizerCore;
import com.ghzdude.randomizer.special.passages.Passage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PassageIO {
    private static final String PASSAGE_DIR = "config\\" + RandomizerCore.MODID + "\\passages\\";
    private static final File directory = new File(Minecraft.getInstance().gameDirectory, PASSAGE_DIR);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final ArrayList<Passage> EXAMPLES = new ArrayList<>(List.of(
            new Passage("Innerius and Susanin", "The Book of Sus - Part 1",
            "ξ. Innerius and Susanin, the blessed witnesses of Amogus. " +
                    "They helped build the Holy Temple, the one which brought us closer to the Holy Prophet. " +
                    "Without him, we would've been uncivilized nomads, like the primitive Imposters. " +
                    "Last night I was awoken by a bright light coming from outside around 3 AM. " +
                    "In my door, stood a large white and silver figure, but as my eyes adjusted, I identified it as an astronaut. " +
                    "He began to speak and told me his name, \\\"Amogus\\\", and that he was here to send an important warning. " +
                    "He saw I was interested in the once great Sus kingdom, and I would be one to make good use of his message. " +
                    "I was very concerned by what he said next: \\\"The Imposters never went away. " +
                    "They must have buried their ship to avoid detection and trick me into thinking they were gone. " +
                    "They slept peacefully in the cold mountains, only to emerge again one thousand years later. " +
                    "I was immediately informed when their ship was once again active. " +
                    "I think they are preparing a new attack.\\\" " +
                    "After saying this, he disappeared with the bright light quickly following. " +
                    "I must urgently inform S.U.S. Laboratories about this imminent attack.")
    ));

    public static void writeExamples() {
        for (Passage passage : EXAMPLES) {
            File passageFile = createFileName(directory, passage.title());

            try {
                if (passageFile.createNewFile()) {
                    Writer writer = Files.newBufferedWriter(passageFile.toPath());
                    GSON.toJson(passage, writer);
                    writer.close();
                }
            } catch (IOException | NullPointerException e) {
                RandomizerCore.LOGGER.warn("Failure to write JSON at " + passageFile.getAbsolutePath());
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static ArrayList<Passage> readPassages() {
        if (!directory.exists()) {
            directory.mkdirs();
        }

        writeExamples();
        String[] listPath = directory.list();
        ArrayList<Passage> passages = new ArrayList<>();

        if (listPath != null) {
            for (String passage : listPath) {
                File passageFile = new File(directory,  "\\" + passage);
                try {
                    JsonReader reader = GSON.newJsonReader(Files.newBufferedReader(passageFile.toPath()));
                    reader.beginObject();

                    reader.skipValue();
                    String author = reader.nextString();

                    reader.skipValue();
                    String title = reader.nextString();

                    reader.skipValue();
                    String body = reader.nextString();

                    reader.endObject();

                    if (passages.stream().noneMatch(pass -> pass.title().equals(title))) {
                        passages.add(new Passage(author, title, body));
                    } else {
                        RandomizerCore.LOGGER.warn("Passage \"" + passage + "\" already exists! Titles must be unique!");
                    }
                    reader.close();
                } catch (IOException | NullPointerException e) {
                    RandomizerCore.LOGGER.warn("Failure to read JSON at " + passageFile.getAbsolutePath() + " Overriding!");
                }
            }
        }
        return passages;
    }

    public static File createFileName(File directory, String s) {
        return new File(directory, "\\" + s.toLowerCase(Locale.ROOT).replace(" ", "") + ".json");
    }
}
