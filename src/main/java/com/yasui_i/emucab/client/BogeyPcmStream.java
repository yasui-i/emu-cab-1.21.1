package com.yasui_i.emucab.client;

import com.yasui_i.emucab.audio.VvvfSynthesizer;
import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** A continuous live stream: four 100 ms buffers tolerate slow client frames. */
public final class BogeyPcmStream implements AudioStream {
    public static final int BLOCK_SAMPLES=4800;
    private static final AudioFormat FORMAT=new AudioFormat(48000,16,1,true,false);
    private final VvvfSynthesizer synth;
    private final short[] scratch=new short[BLOCK_SAMPLES];
    private final ByteBuffer buffer=ByteBuffer.allocateDirect(BLOCK_SAMPLES*2).order(ByteOrder.LITTLE_ENDIAN);
    private volatile boolean closed,recoveryDisabled;

    public BogeyPcmStream(VvvfSynthesizer synth){this.synth=synth;}
    @Override public AudioFormat getFormat(){return FORMAT;}
    @Override public ByteBuffer read(int requested) {
        if(closed)return null;
        int samples=Math.min(scratch.length,Math.max(0,requested/2));
        if(samples==0)return null;
        synth.render(scratch,0,samples);
        buffer.clear();
        for(int i=0;i<samples;i++)buffer.putShort(scratch[i]);
        buffer.flip();return buffer;
    }
    public boolean canRecover(){return !closed&&!recoveryDisabled;}
    /** Explicit stop must win over automatic underrun recovery. */
    public void disableRecovery(){recoveryDisabled=true;}
    @Override public void close(){closed=true;}
}
