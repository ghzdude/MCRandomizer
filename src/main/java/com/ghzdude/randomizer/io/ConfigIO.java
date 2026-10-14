package com.ghzdude.randomizer.io;

import com.ghzdude.randomizer.RandomizerCore;
import com.google.gson.*;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

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
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String JSON_FILE = "%s.json";

    public static void writeListToFile(File file, List<Identifier> list) {
        JsonArray stringArray = new JsonArray();
        list.forEach(loc -> stringArray.add(loc.toString()));
        tryWriteJson(stringArray, file);
    }

    public static void writeValues(File valueFile, Object2IntMap<Identifier> valueMap) {
        JsonObject map = new JsonObject();
        for (var location : valueMap.keySet()) {
            map.addProperty(location.toString(), valueMap.getInt(location));
        }
        tryWriteJson(map, valueFile);
    }

    private static void tryWriteJson(JsonElement toWrite, File file) {
        try (Writer writer = Files.newBufferedWriter(file.toPath())) {
            if (file.canWrite() || file.createNewFile()) {
                GSON.toJson(toWrite, writer);
            }
        } catch (IOException | NullPointerException e) {
            LOGGER.warn("Failure to write JSON at {}", file);
        }
    }

    public static <T> Object2IntMap<Identifier> readValues(String file, Object2IntMap<Identifier> defaults, Registry<T> registry) {
        final Object2IntMap<Identifier> map = new Object2IntArrayMap<>();

        File valueFile = VALUE_DIR.resolve(JSON_FILE.formatted(file)).toFile();
        if (!valueFile.exists() && (valueFile.getParentFile().exists() || valueFile.getParentFile().mkdirs())) {
            writeValues(valueFile, defaults);
            return defaults;
        }
        try {

            JsonObject object = JsonParser.parseString(Files.readString(valueFile.toPath())).getAsJsonObject();

            for (String vanilla : object.keySet()) {
                var loc = Identifier.parse(vanilla);
                int i = object.get(vanilla).getAsInt();
                if (registry.containsKey(loc)) {
                    map.put(loc, i);
                    continue;
                }

                LOGGER.warn("Value \"{}\" does not exist in {} or is invalid!", loc, registry.key());
            }
        } catch (IOException e) {
            readFail(valueFile);
        }

        return map;
    }

    public static List<Identifier> read(String file, List<Identifier> defaults, @Nullable Registry<?> registry) {
        List<Identifier> blacklist = new ArrayList<>();

        File blacklistFile = createFileName(file);
        try {
            if (blacklistFile.createNewFile()) {
                // first time, write defaults
                writeListToFile(blacklistFile, defaults);
                return defaults;
            }

            JsonArray elements = JsonParser.parseString(Files.readString(blacklistFile.toPath())).getAsJsonArray();

            for (JsonElement vanilla : elements) {
                Identifier location = Identifier.parse(vanilla.getAsString());
                if (registry == null || registry.containsKey(location)) {
                    blacklist.add(location);
                } else {
                    LOGGER.warn("Location {} is not valid!", location);
                }
            }
        } catch (IOException | NullPointerException e) {
            readFail(blacklistFile);
            writeListToFile(blacklistFile, defaults);
            return defaults;
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
        LOGGER.warn("Failure to read JSON at '{}'! Overwriting with defaults!", file.getAbsolutePath());
    }

    public static <T> Helper<T> ofList(Codec<T> codec) {
        return new Helper<>(codec);
    }

    public static class Helper<T> {

        private final Codec<T> codec;
        private Path filePath;
        private T defaults;
        private DataResult<T> result;

        private Helper(Codec<T> codec) {
            this.codec = codec;
        }

        public Helper<T> config(String file) {
            filePath = createFileName(file, CONFIG_DIR);
            return this;
        }

        public Helper<T> values(String file) {
            filePath = createFileName(file, VALUE_DIR);
            return this;
        }

        private Path createFileName(String file, Path dir) {
            if (Files.exists(dir) || dir.toFile().mkdirs()) {
                file = file.toLowerCase(Locale.ROOT).replace(" ", "");
                return dir.resolve(JSON_FILE.formatted(file));
            }
            throw new IllegalStateException("Failed to make file for \"%s\" in \"%s\"".formatted(file, dir));
        }

        public Helper<T> defaults(T defaults) {
            this.defaults = defaults;
            return this;
        }

        // TODO fix validation somehow
//        public <R> Helper<T> validate(Function<T, Stream<ResourceKey<R>>> toKeys,
//                                      Function<Collection<ResourceKey<R>>, T> toResult,
//                                      Registry<R> registry) {
//            this.result.ifError(err -> LOGGER.info("All element(s) invalid! {}", err))
//                    .map(toKeys)
//                    .flatMap(keys -> {
//                        List<ResourceKey<R>> validKeys = keys.filter(key -> {
//                            if (registry.containsKey(key)) {
//                                return true;
//                            }
//                            LOGGER.warn("Value \"{}\" does not exist in {} or is invalid!", key.identifier(), registry.key());
//                            return false;
//                        }).toList();
//                        if (validKeys.isEmpty()) return DataResult.error(() -> "All element(s) invalid!");
//                        return DataResult.success(toResult.apply(validKeys));
//                    });
//            return this;
//        }

        public DataResult<T> read() {
            File pathFile = filePath.toFile();
            try {
                if (shouldWriteDefaults(pathFile)) {
                    this.codec.encodeStart(JsonOps.INSTANCE, defaults)
                            .ifError(err -> LOGGER.error("Failed to write default values!: {}", err))
                            .ifSuccess(result -> tryWriteJson(result, pathFile));
                    return DataResult.success(defaults);
                } else {
                    JsonElement object = JsonParser.parseString(Files.readString(filePath));
                    return this.codec.decode(JsonOps.INSTANCE, object).map(Pair::getFirst);
                }
            } catch (IOException e) {
                readFail(pathFile);
                return DataResult.error(() -> "Failed to read: " + e);
            }
        }

        private static boolean shouldWriteDefaults(File pathFile) {
            return !pathFile.exists() && (pathFile.getParentFile().exists() || pathFile.getParentFile().mkdirs());
        }
    }
}
