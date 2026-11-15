package com.yasui_i.emucab.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.yasui_i.emucab.client.BogeyPcmStream;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Resume only our continuous PCM after starvation, preserving its oscillator state. */
@Mixin(Channel.class)
public abstract class BogeyStreamingChannelMixin {
    @Shadow private AudioStream stream;
    @Shadow @Final private int source;

    @Inject(method="updateStream",at=@At("TAIL"))
    private void emucab$recoverUnderrun(CallbackInfo ci) {
        if(stream instanceof BogeyPcmStream pcm&&pcm.canRecover()
                &&AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE)==AL10.AL_STOPPED
                &&AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED)>0)
            AL10.alSourcePlay(source);
    }

    @Inject(method="stop",at=@At("HEAD"))
    private void emucab$explicitStop(CallbackInfo ci) {
        if(stream instanceof BogeyPcmStream pcm)pcm.disableRecovery();
    }
}
