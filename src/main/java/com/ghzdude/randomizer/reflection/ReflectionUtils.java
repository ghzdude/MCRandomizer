package com.ghzdude.randomizer.reflection;

import java.lang.reflect.Field;

public class ReflectionUtils {
    @SuppressWarnings("unchecked")
    public static  <T, R> R getField(Class<T> clazz, T instance, int index) {
        Field field = clazz.getDeclaredFields()[index];
        field.setAccessible(true);
        try {
            return (R) field.get(instance);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static  <T> void setField(Class<T> clazz, T instance, int index, Object newValue) {
        Field fld = clazz.getDeclaredFields()[index];
        fld.setAccessible(true);
        try {
            fld.set(instance, newValue);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
