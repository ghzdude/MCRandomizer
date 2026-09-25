package com.ghzdude.randomizer.util;

import net.minecraft.nbt.CompoundTag;

import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public abstract class TagToken<T> {

    protected final String key;
    protected final Supplier<T> defaultValue;

    public TagToken(String key, T defaultValue) {
        this.key = key;
        this.defaultValue = () -> defaultValue;
    }

    public TagToken(String key, Supplier<T> defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public abstract T get(CompoundTag tag);

    public abstract Optional<T> getOptional(CompoundTag tag);

    public abstract void set(CompoundTag tag, T value);

    public static Int ofInt(String key, int defaultValue) {
        return new Int(key, defaultValue);
    }

    public static Int ofInt(String key, IntSupplier defaultValue) {
        return new Int(key, defaultValue);
    }

    public static TagToken<String> ofString(String key, String defaultValue) {
        return new TagToken<>(key, defaultValue) {
            @Override
            public String get(CompoundTag tag) {
                return tag.getStringOr(this.key, this.defaultValue.get());
            }

            @Override
            public Optional<String> getOptional(CompoundTag tag) {
                return tag.getString(this.key);
            }

            @Override
            public void set(CompoundTag tag, String value) {
                tag.putString(this.key, value);
            }
        };
    }

    public static final class Int extends TagToken<Integer> {

        private final IntSupplier defaultValue;

        public Int(String key, int defaultValue) {
            super(key, defaultValue);
            this.defaultValue = () -> defaultValue;
        }

        public Int(String key, IntSupplier defaultValue) {
            super(key, defaultValue::getAsInt);
            this.defaultValue = defaultValue;
        }

        @Override
        public Optional<Integer> getOptional(CompoundTag tag) {
            return tag.getInt(this.key);
        }

        @Override
        @Deprecated
        public Integer get(CompoundTag tag) {
            return this.getInt(tag);
        }

        public int getInt(CompoundTag tag) {
            return tag.getIntOr(this.key, this.defaultValue.getAsInt());
        }

        @Override
        @Deprecated
        public void set(CompoundTag tag, Integer value) {
            this.setInt(tag, value);
        }

        public void setInt(CompoundTag tag, int value) {
            tag.putInt(this.key, value);
        }

        public int incrementInt(CompoundTag tag) {
            return incrementInt(tag, 1);
        }

        public int incrementInt(CompoundTag tag, int by) {
            int i = getInt(tag) + by;
            setInt(tag, i);
            return i;
        }
    }
}
