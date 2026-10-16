package com.ghzdude.randomizer.io;

import com.ghzdude.randomizer.RandomizerCore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings({"SameParameterValue"})
public class ConfigIO {
    public static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve(RandomizerCore.MODID);
    public static final Path BLACKLIST_DIR = CONFIG_DIR.resolve("blacklists");
    public static final Path VALUE_DIR = CONFIG_DIR.resolve("values");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String JSON_FILE = "%s.json";

    private static void tryWriteJson(JsonElement toWrite, File file) {
        try (Writer writer = Files.newBufferedWriter(file.toPath())) {
            if (file.canWrite() || file.createNewFile()) {
                GSON.toJson(toWrite, writer);
            }
        } catch (IOException | NullPointerException e) {
            LOGGER.warn("Failure to write JSON at {}", file);
        }
    }

    protected static void readFail(File file) {
        LOGGER.warn("Failure to read JSON at '{}'! Overwriting with defaults!", file.getAbsolutePath());
    }

    protected static Path createFileName(String file, Path dir) {
        if (Files.exists(dir) || dir.toFile().mkdirs()) {
            file = file.toLowerCase(Locale.ROOT).replace(" ", "");
            return dir.resolve(JSON_FILE.formatted(file));
        }
        throw new IllegalStateException("Failed to make file for \"%s\" in \"%s\"".formatted(file, dir));
    }

    protected static boolean shouldWriteDefaults(File pathFile) {
        return !pathFile.exists() && (pathFile.getParentFile().exists() || pathFile.getParentFile().mkdirs());
    }

    public static <T> ListHelper<T> ofList(Codec<T> codec) {
        return new ListHelper<>(codec);
    }

    public static <K, V> MapHelper<K, V> ofMap(Codec<K> keyCodec, Codec<V> valueCodec) {
        return new MapHelper<>(keyCodec, valueCodec);
    }

    public abstract static class AbstractHelper<T> {
        protected Path filePath;
        protected DataResult<T> result;

        public AbstractHelper<T> setPath(String file, Path path) {
            this.filePath = createFileName(file, path);
            return this;
        }

        protected void writeDefaults(T defaults, File pathFile) {
            encode(defaults).ifError(err -> LOGGER.error("Failed to write default values!: {}", err))
                    .ifSuccess(result -> tryWriteJson(result, pathFile));
            this.result = DataResult.success(defaults);
        }

        protected abstract DataResult<JsonElement> encode(T value);

        protected abstract DataResult<T> decode(JsonElement value);

        public AbstractHelper<T> read(T defaults) {
            File pathFile = filePath.toFile();
            try {
                if (shouldWriteDefaults(pathFile)) {
                    writeDefaults(defaults, pathFile);
                } else {
                    this.result = decode(GsonHelper.parseArray(Files.readString(filePath)));
                }
                return this;
            } catch (IOException e) {
                readFail(pathFile);
                writeDefaults(defaults, pathFile);
                return this;
            }
        }

        public DataResult<T> result() {
            return this.result;
        }
    }

    public static class ListHelper<E> extends AbstractHelper<List<E>> {

        private final Codec<E> elementCodec;

        public ListHelper(Codec<E> codec) {
            this.elementCodec = codec;
        }

        @Override
        public ListHelper<E> setPath(String file, Path path) {
            super.setPath(file, path);
            return this;
        }

        @Override
        protected DataResult<JsonElement> encode(List<E> value) {
            return Codec.list(elementCodec).encodeStart(JsonOps.INSTANCE, value);
        }

        @Override
        protected DataResult<List<E>> decode(JsonElement value) {
            return Codec.list(elementCodec).decode(JsonOps.INSTANCE, value)
                    .map(Pair::getFirst);
        }

        public ListHelper<E> read(List<E> defaults) {
            super.read(defaults);
            return this;
        }

        public ListHelper<E> validateElements(Predicate<E> validator) {
            this.result = this.result.flatMap(list -> {
                List<E> filtered = list.stream().filter(e -> {
                    if (!validator.test(e)) {
                        LOGGER.error("Invalid element in {}: {}", filePath, e);
                        return false;
                    }
                    return true;
                }).toList();
                return DataResult.success(filtered);
            });
            return this;
        }
    }

    public static class MapHelper<K, V> extends AbstractHelper<Map<K, V>> {

        private final Codec<K> keyCodec;
        private final Codec<V> valueCodec;

        private MapHelper(Codec<K> keyCodec, Codec<V> valueCodec) {
            this.keyCodec = keyCodec;
            this.valueCodec = valueCodec;
        }

        public MapHelper<K, V> setPath(String file, Path path) {
            super.setPath(file, path);
            return this;
        }

        @Override
        protected DataResult<JsonElement> encode(Map<K, V> value) {
            List<Pair<K, V>> list = value.entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue())).toList();
            return Codec.compoundList(this.keyCodec, this.valueCodec)
                    .encodeStart(JsonOps.INSTANCE, list);
        }

        @Override
        protected DataResult<Map<K, V>> decode(JsonElement value) {
            return Codec.compoundList(this.keyCodec, this.valueCodec)
                    .decode(JsonOps.INSTANCE, value)
                    .map(Pair::getFirst)
                    .map(list -> list.stream().collect(Collectors.toMap(Pair::getFirst, Pair::getSecond)));
        }

        public MapHelper<K, V> read(Map<K, V> defaults) {
            super.read(defaults);
            return this;
        }

        public MapHelper<K, V> validateElements(Predicate<Map.Entry<K, V>> validator) {
            this.result = this.result.flatMap(list -> {
                Map<K, V> filtered = list.entrySet().stream().filter(e -> {
                    if (!validator.test(e)) {
                        LOGGER.error("Invalid element in {}: {}", filePath, e);
                        return false;
                    }
                    return true;
                }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                return DataResult.success(filtered);
            });
            return this;
        }
    }
}
