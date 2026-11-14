package com.ghzdude.randomizer.mixin;

import com.ghzdude.randomizer.util.JsonMutationRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
@Mixin(SimpleJsonResourceReloadListener.class)
public class JsonParseMixin {

    /// Injects logic into {@link SimpleJsonResourceReloadListener#scanDirectory(ResourceManager, FileToIdConverter, DynamicOps, Codec, Map)}
    /// and runs registered randomizer logic on the json before being sent to {@link ForgeHooks#onJsonDataParsed(Codec, Identifier, Object)}
    /// @param original Function that takes in {@code (Codec<A>, DynamicOps<T>, T)}
    @WrapOperation(method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;parse(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"))
    private static <A, T> DataResult<A> injectJsonParse(Codec<A> codec, DynamicOps<T> ops,
                                                        T json, Operation<DataResult<A>> original,
                                                        @Local(name = "id") Identifier id) {
        return JsonMutationRegistry.handle(codec, ops, json, () -> original.call(codec, ops, json), id);
    }
}
