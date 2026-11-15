package emucabmotortest;

import com.yasui_i.emucab.audio.VvvfSynthesizer;
import com.yasui_i.emucab.client.BogeyRecordedLayers;
import java.nio.*;
import java.nio.file.*;

/** Renders the exact game DSP with the actual resource-manager decoded loops. */
final class ExteriorRuntimePreview {
    static void render(BogeyRecordedLayers.Layers layers)throws Exception {
        final int rate=48000,frames=rate*54;
        var pcm=new short[frames];var synth=new VvvfSynthesizer(8143);
        synth.setRecordedLayers(layers.mechanical(),layers.wind());
        for(int offset=0;offset<frames;offset+=480) {
            double t=offset/(double)rate,speed=0,traction=0,brake=0;
            if(t>=1.5&&t<24){speed=350*(t-1.5)/22.5;traction=.86-.22*smooth((speed-180)/170);}
            else if(t>=24&&t<30)speed=350-(t-24)*2;
            else if(t>=30&&t<52){speed=338*(1-(t-30)/22);brake=.72*smooth(speed/12);}
            double rpm=speed/3.6*60/(2*Math.PI*.46);
            synth.setTarget(rpm,speed,traction,brake,2.5,2);
            synth.render(pcm,offset,Math.min(480,frames-offset));
        }
        int peak=0;double sum=0;
        for(short v:pcm){peak=Math.max(peak,Math.abs((int)v));sum+=(double)v*v;}
        if(peak>=32000||sum<1e7)throw new AssertionError("Invalid exterior runtime preview peak="+peak);
        var output=Path.of("..","vvvf-exterior-runtime-preview.wav");
        var data=ByteBuffer.allocate(44+frames*2).order(ByteOrder.LITTLE_ENDIAN);
        data.put("RIFF".getBytes()).putInt(36+frames*2).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1)
                .putInt(rate).putInt(rate*2).putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(frames*2);
        for(short v:pcm)data.putShort(v);Files.write(output,data.array());
        Files.writeString(Path.of("..","vvvf-exterior-runtime-preview.txt"),"PASS exact runtime DSP + resource-manager loops; 54sec mono; peak="+peak+"/32767; RMS="+Math.sqrt(sum/frames)/32767+"; compressed test speed profile, not driven train capture\n");
    }
    static double smooth(double value){value=Math.clamp(value,0,1);return value*value*(3-2*value);}
}
