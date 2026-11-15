import com.yasui_i.emucab.audio.VvvfSynthesizer;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Offline balance probe for the formal exterior VVVF renderer. It does not replace a game run. */
public final class VvvfWindPreview {
    static final int RATE=48000;
    static double rpm(double kmh){return kmh/3.6*60/(2*Math.PI*.46);}
    static float[] readFloats(Path path)throws IOException {
        byte[] bytes=Files.readAllBytes(path);
        if((bytes.length&3)!=0)throw new IOException("float32 cache has partial sample: "+path);
        var out=new float[bytes.length/4];var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<out.length;i++)out[i]=b.getFloat();
        return out;
    }
    static double rms(short[] pcm){double sum=0;for(short s:pcm)sum+=(double)s*s;return Math.sqrt(sum/pcm.length)/32767;}
    static int peak(short[] pcm){int p=0;for(short s:pcm)p=Math.max(p,Math.abs((int)s));return p;}
    static short[] stable(float[] mechanical,float[] wind,double kmh,double traction,double brake,double e,double m,double w,double motorGain){
        var synth=new VvvfSynthesizer(3107+(long)Math.round(kmh*17));
        synth.setRecordedLayers(mechanical,wind);synth.setMix(e,m,w);synth.setMotorGain(motorGain);
        synth.setTarget(rpm(kmh),kmh,traction,brake,2.5,2);
        var warm=new short[RATE*2];synth.render(warm,0,warm.length);
        var pcm=new short[RATE*4];synth.render(pcm,0,pcm.length);return pcm;
    }
    static void writeWav(Path path,short[] pcm)throws IOException {
        var b=ByteBuffer.allocate(44+pcm.length*2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36+pcm.length*2).put("WAVEfmt ".getBytes()).putInt(16)
         .putShort((short)1).putShort((short)1).putInt(RATE).putInt(RATE*2).putShort((short)2).putShort((short)16)
         .put("data".getBytes()).putInt(pcm.length*2);
        for(short s:pcm)b.putShort(s);Files.write(path,b.array());
    }
    static short[] passing(float[] mechanical,float[] wind,double radius){
        final int seconds=6,frames=seconds*RATE;var out=new short[frames];var synth=new VvvfSynthesizer(7719);
        synth.setRecordedLayers(mechanical,wind);synth.setMix(.8,.35,.25);synth.setMotorGain(1.8);synth.setTarget(rpm(350),350,.5,0,2.5,2);
        for(int offset=0;offset<frames;offset+=480){
            double t=offset/(double)RATE;
            // Constant350km/h point source passing8m from a listener. The
            // envelope approximates the configured game distance fade, not Doppler.
            double distance=Math.hypot((t-3)*350/3.6,8);
            synth.setOutputGain(.65*Math.clamp((radius-distance)/(radius*.8),0,1));
            synth.render(out,offset,Math.min(480,frames-offset));
        }
        return out;
    }
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args.length==0?".":args[0]);Path output=Path.of(args.length<2?"build/wind-balance-0.5.21":args[1]);
        double radius=args.length<3?64:Double.parseDouble(args[2]);
        Files.createDirectories(output);
        float[] mechanical=readFloats(root.resolve("build/vvvf-timbre-study-cache/mechanical-loop.f32"));
        float[] wind=readFloats(root.resolve("build/vvvf-timbre-study-cache/wind-loop.f32"));
        double[] speeds={80,160,250,300,350};var report=new StringBuilder("{\"rate\":48000,\"note\":\"Formal VvvfSynthesizer offline probe; not a real-train SPL measurement.\",\"cache\":{\"mechanicalSamples\":").append(mechanical.length).append(",\"windSamples\":").append(wind.length).append("},\"speeds\":[");
        for(int i=0;i<speeds.length;i++){
            double speed=speeds[i];short[] motor=stable(mechanical,wind,speed,1,0,.8,0,0,1.8);short[] windOnly=stable(mechanical,wind,speed,0,0,0,0,.25,1.8);short[] mixed=stable(mechanical,wind,speed,1,0,.8,.35,.25,1.8);
            if(i>0)report.append(',');report.append(String.format(Locale.ROOT,"{\"kmh\":%.1f,\"defaultMotorRms\":%.7f,\"defaultWindRms\":%.7f,\"mixedRms\":%.7f,\"motorPeak\":%d,\"windPeak\":%d,\"mixedPeak\":%d}",speed,rms(motor),rms(windOnly),rms(mixed),peak(motor),peak(windOnly),peak(mixed)));
        }
        report.append("]}");Files.writeString(output.resolve("高速风噪平衡测量.json"),report);
        short[] preview=passing(mechanical,wind,radius);writeWav(output.resolve("350kmh经过混音试听.wav"),preview);
        Files.writeString(output.resolve("说明.txt"),"正式 VvvfSynthesizer + 已解码机械/风噪 float32 循环的离线检查。试听采用单个350 km/h、50%牵引负载点源，距听者最近8m、"+radius+"m距离衰减，不加入多普勒或多转向架；不是实车录音，也不代表游戏内最终声压。\n");
        System.out.println(report);
    }
}
