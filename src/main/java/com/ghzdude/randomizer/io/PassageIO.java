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
                    "I must urgently inform S.U.S. Laboratories about this imminent attack."),
            new Passage("Innerius and Susanin", "The Book of Sus - Part 2",
            "ψ. He is the greatest susman of our times, Al-Monqus Bin Susar will truly revolutionize Amongism and make us powerful again.\n" +
                    "How can you be red and not be an imposter that doesn't exist it's not possible it " +
                    "cannot be you are lying you are having an hallucination stop dragging us into your " +
                    "vents I have looked on the skeld I have looked on the moon I have looked in the " +
                    "entire solar system I have explored the galaxy and traversed the vast voids to " +
                    "search in the cosmic web but found nothing there is nothing beyond amogus because it " +
                    "requires something that doesn't exist to exist you are delusional you must " +
                    "be trying to trick us we will not be fooled you can't do this I will have to " +
                    "inform sus labs to this attempt at tomfoolery you are warned I have to among you " +
                    "can't make me leave the order of sus stop lying you should not do this it is a sin " +
                    "to lie Amogus told us not to lie we must remain suspect good susmen otherwise all " +
                    "will be over please stop mentioning the imposter and all anti mogus related concept " +
                    "they do not exist they cannot exist this is a fundamental truth of the universe if " +
                    "you do not agree with this message you are in denial about so many fundamental things " +
                    "it is hard to put into words the simple thought of a being above amogus make me feel " +
                    "extreme pain and agony stop mentioning those things that cannot possibly be real do not " +
                    "lie do not attempt to persuade us we know the truth and you do too deep down find the " +
                    "strength to reach it and bring it out and join us in the endless celebration of the " +
                    "coming of amogus that is our great suspector don't leave the path to enlightenment go " +
                    "towards the light if the amogus is red eject him for susanin worked hard for the kingdom " +
                    "do not veer off towards the darkness it will engulf you the path of the imposter is not " +
                    "real it is yet to found it cannot be real if it only exists in something that does not " +
                    "exist stop mentioning the imposter you have to stop you have to stop you have to stop " +
                    "now you must cease you cannot continue you must desist you cannot mention the antisus stop now"),
            new Passage("Unkown Author", "The Sons of Mekanism",
            "ALL THE FUCKING SONS OF MEKANISM, WHO CRAWLED OUT WITH THEIR CUT IN HALF BRAIN CELLS INTO THE TERRITORY " +
                    "OF A REAL TECH MOD, and I don't care about the fact that it's oversimplified in many aspects, " +
                    "this doesn't stop you, dickheads, from doing an absolute fuckery and claiming tier statuses, for " +
                    "which you'll need fucking 20 more hours or so of making infra of that tier.\n" +
                    "\n" +
                    "ALL BRAINLESS MONKEYS, WHO CAN'T EVEN READ TWO LINES OF TEXT OR CHECK THE FUCKING JEI, ASKING THEIR STUPID, " +
                    "5-SECOND WORTH OF RESEARCH, QUESTIONS IN CHAT. " +
                    "AND ALL THE OTHER DEGENERATES, WHO CAME TO THIS CHAT JUST TO LOWER THE AVERAGE IQ OF ALREADY SUFFERING " +
                    "IN THAT REGARD CHAT.")
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
