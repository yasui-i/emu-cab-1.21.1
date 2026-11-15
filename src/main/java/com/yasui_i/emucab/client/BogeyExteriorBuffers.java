package com.yasui_i.emucab.client;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.yasui_i.emucab.EMUCab;
import net.minecraft.resources.ResourceLocation;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;

/** Two shared, conditioned PCM loops in the ordinary static sound pool. */
public final class BogeyExteriorBuffers {
    private static final ResourceLocation ROLLING=EMUCab.id("sounds/bogey_rolling_loop.ogg");
    private static final ResourceLocation WIND=EMUCab.id("sounds/bogey_wind_loop.ogg");
    private static final AudioFormat FORMAT=new AudioFormat(48000,16,1,true,false);
    public static final int LOOP_SAMPLES=48000*8;
    public static final float LOOP_SECONDS=8;

    public static boolean owns(ResourceLocation id){return ROLLING.equals(id)||WIND.equals(id);}

    public static CompletableFuture<SoundBuffer> load(ResourceLocation id) {
        return BogeyRecordedLayers.load().thenApplyAsync(layers->buffer(WIND.equals(id)?layers.wind():layers.mechanical(),WIND.equals(id)),
                net.minecraft.Util.nonCriticalIoPool());
    }

    private static SoundBuffer buffer(float[] samples,boolean wind) {
        var pcm=ByteBuffer.allocateDirect(LOOP_SAMPLES*2).order(ByteOrder.LITTLE_ENDIAN);
        // Read a cyclic section and crossfade its last 100 ms back into its
        // beginning. The source starts at a UUID-dependent offset, then loops
        // on OpenAL without Java refill work or channel-selection restarts.
        int crossfade=4800;long random=wind?871312:129518;double low=0;
        for(int i=0;i<LOOP_SAMPLES;i++) {
            double value;
            if(samples.length>1) {
                value=samples[i%samples.length];
                if(i>=LOOP_SAMPLES-crossfade) {
                    double t=(i-(LOOP_SAMPLES-crossfade))/(double)(crossfade-1);
                    value=value*(1-t)+samples[Math.floorMod(i-LOOP_SAMPLES,samples.length)]*t;
                }
            }else {
                random^=random<<13;random^=random>>>7;random^=random<<17;
                double white=(random>>>11)*0x1.0p-52-1;low+=(white-low)*(wind?.24:.035);
                value=low*(wind?.55:1.3);
                // Smooth to zero at either side of the fallback loop seam.
                value*=Math.min(1,Math.min(i,LOOP_SAMPLES-1-i)/480.0);
            }
            pcm.putShort((short)Math.round(Math.clamp(value,-.95,.95)*32767));
        }
        pcm.flip();return new SoundBuffer(pcm,FORMAT);
    }

    private BogeyExteriorBuffers(){}
}
