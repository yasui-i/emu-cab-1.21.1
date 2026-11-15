package com.yasui_i.emucab.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.yasui_i.emucab.client.BogeyExteriorBuffers;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** The normal sound-buffer cache also owns reload/disposal of our shared PCM. */
@Mixin(SoundBufferLibrary.class)
public abstract class BogeyExteriorBufferMixin {
    @Shadow @Final private Map<ResourceLocation,CompletableFuture<SoundBuffer>> cache;

    @Inject(method="getCompleteBuffer",at=@At("HEAD"),cancellable=true)
    private void emucab$exteriorLoop(ResourceLocation id,CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
        if(BogeyExteriorBuffers.owns(id))cir.setReturnValue(cache.computeIfAbsent(id,BogeyExteriorBuffers::load));
    }
}
