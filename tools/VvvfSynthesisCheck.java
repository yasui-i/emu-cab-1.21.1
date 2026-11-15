import com.yasui_i.emucab.audio.VvvfSynthesizer;
import java.nio.*;
import java.nio.file.*;

/** Deterministic offline rendering of the exact in-game synthesizer. */
public final class VvvfSynthesisCheck {
    static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
    static double rms(short[] data){double sum=0;for(short s:data)sum+=(double)s*s;return Math.sqrt(sum/data.length);}
    static double state(VvvfSynthesizer synth,String name)throws Exception {
        var field=VvvfSynthesizer.class.getDeclaredField(name);field.setAccessible(true);
        return field.getDouble(synth);
    }
    static double dominantTone(short[] pcm,int from,int length) {
        double bestHz=0,bestEnergy=-1;
        for(int hz=430;hz<=910;hz+=2) {
            double real=0,imaginary=0;
            for(int i=0;i<length;i++) {
                double sample=pcm[from+i]*(.5-.5*Math.cos(2*Math.PI*i/(length-1)));
                double phase=2*Math.PI*hz*i/48000;
                real+=sample*Math.cos(phase);imaginary+=sample*Math.sin(phase);
            }
            double energy=real*real+imaginary*imaginary;
            if(energy>bestEnergy){bestEnergy=energy;bestHz=hz;}
        }
        return bestHz;
    }
    /**
     * A source can be created while the train is already passing the listener.
     * The first non-empty render must seed the audio-thread state from the most
     * recent packet. This catches the old zero-RPM launch that produced a false
     * low-to-high sweep every time a nearby bogey source was recreated.
     */
    static void initialTelemetryChecks()throws Exception {
        double passingRpm=350*60/(2*Math.PI*.46*3.6);
        for(int source=0;source<4;source++) {
            var passing=new VvvfSynthesizer(143+source*17011);
            passing.setMix(1,0,0);passing.setTarget(passingRpm,350,1,0,2.5,2);
            var audio=new short[4800];passing.render(audio,0,audio.length);
            // Inspect actual PCM during the first 20-80ms, before the old
            // zero-start follower could settle at the high-speed carrier.
            double tone=dominantTone(audio,960,2880);
            check(Math.abs(tone-867.2)<8,"Recreated 350km/h source swept up from low speed: "+tone+"Hz");
            check(state(passing,"outputGain")>.95,"New source fade never reached audible level");
        }
        var synth=new VvvfSynthesizer(143);
        synth.setTarget(1800,350,1,0,2.5,2);
        synth.render(new short[256],0,0);
        check(state(synth,"rpm")==0,"Zero-length render consumed initial telemetry");
        synth.setTarget(1800,350,1,0,2.5,2);
        var first=new short[256];synth.render(first,0,first.length);
        check(Math.abs(state(synth,"rpm")-1800)<1e-9,"First audible frame did not start at current wheel RPM");
        check(Math.abs(state(synth,"bodyKmh")-350)<1e-9,"First audible frame did not start at current body speed");
        check(state(synth,"traction")==1&&state(synth,"brake")==0,"New source invented a load ramp");
        check(state(synth,"outputGain")>0&&state(synth,"outputGain")<.25,"Direct RPM initialization bypassed gain fade-in");
        check(rms(first)>1,"High-speed source was silent after direct initialization");

        // Publishing several packets before the sound thread renders must use
        // the latest packet, while later changes still use the normal smooth
        // follower rather than jumping immediately.
        var latest=new VvvfSynthesizer(144);
        latest.setTarget(600,100,1,0,2.5,2);
        latest.setTarget(2400,350,1,0,2.5,2);
        latest.render(new short[1],0,1);
        check(Math.abs(state(latest,"rpm")-2400)<1e-9,"First frame used a stale telemetry packet");
        latest.setTarget(600,100,1,0,2.5,2);
        latest.render(new short[1],0,1);
        check(state(latest,"rpm")>600&&state(latest,"rpm")<2400,"Later RPM change stopped following smoothly");
        var stopped=new VvvfSynthesizer(145);stopped.render(new short[1],0,1);
        stopped.setTarget(1800,350,1,0,2.5,2);stopped.render(new short[1],0,1);
        check(state(stopped,"rpm")>0&&state(stopped,"rpm")<10,"Actual acceleration from rest jumped immediately");
        var braking=new VvvfSynthesizer(146);braking.setTarget(1800,350,0,1,2.5,2);
        braking.render(new short[1],0,1);
        check(state(braking,"traction")==0&&state(braking,"brake")==1,"New braking source started with the wrong load");
    }
    static short[] steady(double rpm,double traction,double brake,double e,double m,double w) {
        var synth=new VvvfSynthesizer(143);synth.setMix(e,m,w);synth.setTarget(rpm,traction,brake,2.5,2);
        synth.render(new short[48000],0,48000);var audio=new short[48000];synth.render(audio,0,audio.length);return audio;
    }
    static short[] bodyWind(double wheelRpm,double bodyKmh) {
        var synth=new VvvfSynthesizer(143);synth.setMix(0,0,1);synth.setTarget(wheelRpm,bodyKmh,0,0,2.5,2);
        synth.render(new short[48000],0,48000);var output=new short[48000];synth.render(output,0,output.length);return output;
    }
    static short[] steadyAtBodySpeed(double bodyKmh,double electrical,double mechanical,double wind) {
        double wheelRpm=bodyKmh/3.6*60/(2*Math.PI*.46);
        var synth=new VvvfSynthesizer(7000+(long)Math.round(bodyKmh));
        synth.setMix(electrical,mechanical,wind);
        synth.setTarget(wheelRpm,bodyKmh,1,0,2.5,2);
        synth.render(new short[48000*2],0,48000*2);
        var output=new short[48000];synth.render(output,0,output.length);return output;
    }
    static short[] sameWheelSpeedMotor(double bodyKmh) {
        var synth=new VvvfSynthesizer(7011);synth.setMix(1,0,0);synth.setTarget(1800,bodyKmh,1,0,2.5,2);
        synth.render(new short[96000],0,96000);var output=new short[48000];synth.render(output,0,output.length);return output;
    }
    /**
     * The outside balance is intentional: the train's broadband air stream
     * should become the foreground at line speed, while low-speed VVVF detail
     * remains audible. These assertions use the same fallback wind layer as a
     * missing optional CEE resource, so they are deterministic without assets.
     */
    static void exteriorBalanceChecks() {
        double motor80=rms(steadyAtBodySpeed(80,.8,0,0));
        double motor350=rms(steadyAtBodySpeed(350,.8,0,0));
        double wind80=rms(steadyAtBodySpeed(80,0,0,.25));
        double wind350=rms(steadyAtBodySpeed(350,0,0,.25));
        check(motor80>wind80*5,"Low-speed VVVF detail was lost under wind");
        check(motor80>motor350*1.35,"High-speed masking did not lower the inverter foreground");
        check(wind350>wind80*3,"Wind layer did not rise substantially with body speed");
        check(wind350>motor350,"High-speed wind was not the foreground layer");
        check(java.util.Arrays.equals(sameWheelSpeedMotor(0),sameWheelSpeedMotor(100)),"Low-speed inverter detail was attenuated");
        check(rms(sameWheelSpeedMotor(350))<rms(sameWheelSpeedMotor(0))*.3,"Motor masking followed wheel RPM instead of physical speed");

        // Wheel slip must not create wind or suppress the inverter: masking is
        // based on the physical body's speed, not wheel RPM.
        var slip=new VvvfSynthesizer(7011);slip.setMix(1,0,1);slip.setTarget(1800,0,1,0,2.5,2);
        slip.render(new short[96000],0,96000);var slipPcm=new short[48000];slip.render(slipPcm,0,slipPcm.length);
        var stationary=new VvvfSynthesizer(7011);stationary.setMix(1,0,0);stationary.setTarget(1800,0,1,0,2.5,2);
        stationary.render(new short[96000],0,96000);var stationaryPcm=new short[48000];stationary.render(stationaryPcm,0,stationaryPcm.length);
        check(java.util.Arrays.equals(slipPcm,stationaryPcm),"Wheel slip manufactured wind or changed stationary motor balance");
    }
    static void exteriorChecks() {
        var split=new VvvfSynthesizer(713);split.setMix(0,0,1);split.setExteriorLayersEnabled(false);
        split.setTarget(1800,350,0,0,2.5,2);var separated=new short[48000];split.render(separated,0,separated.length);
        check(rms(separated)==0,"Split VVVF stream still contains background wind");
        split.setMix(.8,0,0);split.setTarget(1800,120,1,0,2.5,2);
        split.render(separated,0,separated.length);split.render(separated,0,separated.length);
        check(rms(separated)>100,"Splitting background removed VVVF tone");
        check(rms(bodyWind(1800,0))==0,"Wheel slip manufactured wind with stationary body");
        check(rms(bodyWind(0,250))>100,"Moving body without wheel RPM lost wind");
        check(java.util.Arrays.equals(bodyWind(0,250),bodyWind(1800,250)),"Wind pitch/amplitude followed wheel RPM");
        var wave=new float[48000];for(int i=0;i<wave.length;i++)wave[i]=(float)(Math.sin(2*Math.PI*733*i/48000)*.141421356);
        var synth=new VvvfSynthesizer(143);synth.setMix(0,0,1);synth.setRecordedLayers(null,wave);synth.setTarget(0,200,0,0,2.5,2);
        var data=new short[48000];synth.render(data,0,data.length);synth.render(data,0,data.length);
        check(rms(data)>500,"Recorded wind layer was not mixed");
        // Once the gain target is zero the same continuing physical state must
        // retire without a hard waveform cut or an unending source.
        synth.setOutputGain(0);synth.render(data,0,data.length);synth.render(data,0,data.length);
        check(rms(data)<1,"Output-gain source fade failed");
        synth.setOutputGain(1);synth.render(data,0,data.length);check(rms(data)>500,"Source did not recover from fade");
        var fallback=new VvvfSynthesizer(143);fallback.setRecordedLayers(new float[]{Float.NaN,0},new float[]{0,Float.POSITIVE_INFINITY});
        fallback.setTarget(800,150,1,0,2.5,2);fallback.render(data,0,data.length);check(rms(data)>100,"Invalid recording did not fall back safely");
        double low=com.yasui_i.emucab.audio.VvvfProfile.carrierHz(0,0);
        double high=com.yasui_i.emucab.audio.VvvfProfile.carrierHz(168.2,0);
        check(Math.abs(low-451.2)<.01&&high>860&&high<870,"Exterior acoustic target map regressed");
        var loud=new VvvfSynthesizer(143);loud.setMix(2,2,2);loud.setTarget(20000,1000,1,1,20,8);loud.render(data,0,data.length);
        int peak=0;for(short value:data)peak=Math.max(peak,Math.abs((int)value));check(peak<32000,"Maximum supported gains clipped");
    }
    static void writeWav(Path path,short[] pcm)throws Exception {
        var b=ByteBuffer.allocate(44+pcm.length*2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36+pcm.length*2).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1)
            .putInt(48000).putInt(96000).putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(pcm.length*2);
        for(short s:pcm)b.putShort(s);Files.write(path,b.array());
    }
    public static void main(String[] args)throws Exception {
        initialTelemetryChecks();
        exteriorBalanceChecks();
        exteriorChecks();
        check(rms(steady(0,0,0,.8,.35,.25))==0,"Stationary neutral is not silent");
        var electric=steady(600,1,0,1,0,0);check(rms(electric)>200,"Electric layer silent");
        check(rms(steady(600,0,0,1,0,0))<2,"Coasting electric layer did not fade");
        check(rms(steady(600,0,1,1,0,0))>100,"Brake tone missing");
        check(rms(steady(600,1,0,0,1,0))>50,"Mechanical layer silent");
        check(rms(steady(1600,1,0,0,0,1))>100,"Wind layer silent");
        check(!java.util.Arrays.equals(electric,steady(1200,1,0,1,0,0)),"RPM did not change PCM");
        var bad=new VvvfSynthesizer(10);bad.setTarget(Double.NaN,Double.NaN,Double.POSITIVE_INFINITY,0,0);var invalid=new short[1024];bad.render(invalid,0,invalid.length);
        check(rms(invalid)==0,"Invalid input generated sound");
        final int frames=48000*24;var preview=new short[frames];var synth=new VvvfSynthesizer(143);
        long start=System.nanoTime();int max=0;double largestJump=0;
        for(int offset=0;offset<frames;offset+=480) {
            double seconds=offset/48000.0,rpm=seconds<10?seconds*180:seconds<13?1800:Math.max(0,1800-(seconds-13)*180);
            synth.setTarget(rpm,seconds<10?.85:0,seconds>=13&&seconds<23?.65:0,2.5,2);
            synth.render(preview,offset,Math.min(480,frames-offset));
        }
        double elapsed=(System.nanoTime()-start)/1e9;
        for(int i=0;i<preview.length;i++){max=Math.max(max,Math.abs((int)preview[i]));if(i>0)largestJump=Math.max(largestJump,Math.abs(preview[i]-preview[i-1]));}
        check(max<32000,"PCM clipped");
        var output=Path.of(args.length==0?"build/vvvf-exterior-runtime-check":args[0]);Files.createDirectories(output);
        writeWav(output.resolve("VVVF_牵引滑行制动_24秒.wav"),preview);
        String report="PASS approved exterior tone target; high-speed wind-dominant mix with low-speed motor detail; body-speed-only motor masking; zero/invalid silence; independent electromagnetic/mechanical/wind layers; body-speed wind independent of wheel slip; recorded textures; smooth source gain retirement/recovery; safe invalid-recording fallback; RPM-dependent waveform; coasting fade; brake tone; peak="+max+"/32767; largest sample step="+largestJump+"; 24 audio seconds generated in "+elapsed+" seconds.\n";
        Files.writeString(output.resolve("合成检查.txt"),report);System.out.print(report);
    }
}
