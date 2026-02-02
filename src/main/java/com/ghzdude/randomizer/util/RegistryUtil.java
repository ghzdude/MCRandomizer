package com.ghzdude.randomizer.util;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.event.server.ServerAboutToStartEvent;

import java.util.Optional;

public class RegistryUtil {
    private static RegistryAccess access;

    public static void init(ServerAboutToStartEvent event) {
        access = event.getServer().registryAccess();
    }

    public static void dispose() {
        access = null;
    }

    public static Optional<RegistryAccess> getAccess() {
        return Optional.ofNullable(access);
    }

    public static <T> Registry<T> getRegistry(ResourceKey<Registry<T>> key) {
        return access.lookupOrThrow(key);
    }
}
