package com.yasui_i.emucab.audio;

/**
 * Real-time port of the approved exterior acoustic study. Reference-inspired
 * harmonics, RL-current force texture, casing resonances and recorded textures
 * remain separate; no upstream simulator code or reference recording is used.
 * This models sound, not regenerated power or a specific train's real inverter.
 */
public final class VvvfSynthesizer {
    private record Target(double rpm,double bodyKmh,double traction,double brake,double gear,int poles){}
    private record Mix(double electric,double mechanical,double wind){}
    private record Layers(float[] mechanical,float[] wind){}
    private volatile Target target=new Target(0,0,0,0,2.5,2);
    private volatile Mix mix=new Mix(.8,.35,.25);
    private volatile Layers layers=new Layers(null,null);
    private volatile double requestedGain=1;
    private volatile double motorGain=1.8;
    private volatile boolean exteriorLayersEnabled=true;
    private static final int TABLE_SIZE=16384;
    private static final double[] SIN=new double[TABLE_SIZE+1];
    static {for(int i=0;i<=TABLE_SIZE;i++)SIN[i]=Math.sin(2*Math.PI*i/TABLE_SIZE);}
    private static final double RPM_FOLLOW=1-Math.exp(-1/(48000*.075));
    private static final double LOAD_FOLLOW=1-Math.exp(-1/(48000*.12));
    private static final double GAIN_FOLLOW=1-Math.exp(-1/(48000*.025));
    private static final double CURRENT_FOLLOW=1-Math.exp(-1/(96000*.00075));
    private final double detune,initialOffset;
    private final double[] currents=new double[9];
    private final Biquad toneHigh=Biquad.high(300,.707,48000),toneLow=Biquad.low(4300,.707,48000);
    private final Biquad tone560=Biquad.peak(560,3.5,48000),tone1450=Biquad.peak(1450,2.8,48000);
    private final Biquad forceLowA=Biquad.low(4200,.707,96000),forceLowB=Biquad.low(4200,.707,96000);
    private final Biquad forceHigh=Biquad.high(240,.707,48000);
    private final Biquad force850=Biquad.peak(850,4,48000),force2150=Biquad.peak(2150,3,48000);
    private final Biquad gearHigh=Biquad.high(180,.707,48000),gearLow=Biquad.low(5000,.707,48000);
    private final Biquad outputHigh=Biquad.high(95,.707,48000),outputLow=Biquad.low(6500,.707,48000);
    private double rpm,bodyKmh,traction,brake,outputGain;
    private double electricalPhase,carrierPhase,tonePhase,gearPhase,gearOvertonePhase,breathPhase;
    private double mechanicalRead,windRead,noiseLow,windLow;
    private long random;
    private boolean initialized;
    public VvvfSynthesizer(){this(System.nanoTime());}
    public VvvfSynthesizer(long seed){
        random=seed==0?0x5a18f3e7L:seed;
        initialOffset=(seed&65535)/65536.0;
        detune=(((seed>>>16)&65535)/65535.0-.5)*.0013;
        electricalPhase=initialOffset;carrierPhase=initialOffset*.7;tonePhase=initialOffset;
        gearPhase=initialOffset*.3;gearOvertonePhase=initialOffset*.9;breathPhase=initialOffset;
        mechanicalRead=initialOffset*48000;windRead=initialOffset*240000;
    }
    /** Compatibility for callers that have not separated body and wheel speed. */
    public void setTarget(double wheelRpm,double traction01,double brake01,double gearRatio,int polePairs) {
        double speed=bound(Math.abs(wheelRpm),0,20000)*(2*Math.PI*.46)*3.6/60;
        setTarget(wheelRpm,speed,traction01,brake01,gearRatio,polePairs);
    }
    public void setTarget(double wheelRpm,double bodySpeedKmh,double traction01,double brake01,double gearRatio,int polePairs) {
        target=new Target(bound(Math.abs(wheelRpm),0,20000),bound(Math.abs(bodySpeedKmh),0,1000),
                bound(traction01,0,1),bound(brake01,0,1),bound(gearRatio,.1,20),Math.clamp(polePairs,1,8));
    }
    /** Shared immutable 48kHz mono loops, already conditioned to approximately .1 RMS. */
    public void setRecordedLayers(float[] mechanical,float[] wind){layers=new Layers(valid(mechanical),valid(wind));}
    private static float[] valid(float[] samples){
        if(samples==null||samples.length<2)return null;
        for(float sample:samples)if(!Float.isFinite(sample))return null;
        return samples;
    }
    /** Smooth audio-thread fade for source selection and retirement. */
    public void setOutputGain(double gain){requestedGain=bound(gain,0,2);}
    public void setMix(double electrical,double mechanical,double wind){mix=new Mix(bound(electrical,0,2),bound(mechanical,0,2),bound(wind,0,2));}
    public void setMotorGain(double gain){motorGain=bound(gain,0,4);}
    /** Persistent static bogey loops render rolling/wind separately in game. */
    public void setExteriorLayersEnabled(boolean enabled){exteriorLayersEnabled=enabled;}
    private static double bound(double v,double min,double max){return Double.isFinite(v)?Math.clamp(v,min,max):min;}
    private static double sin(double cycles) {
        cycles-=Math.floor(cycles);double index=cycles*TABLE_SIZE;int i=(int)index;
        return SIN[i]+(SIN[i+1]-SIN[i])*(index-i);
    }
    private static double wrap(double phase){return phase-Math.floor(phase);}
    private static double triangle(double phase){return 4*Math.abs(wrap(phase)-.5)-1;}
    private double noise(){random^=random<<13;random^=random>>>7;random^=random<<17;return (random>>>11)*0x1.0p-52-1;}
    private static double sample(float[] samples,double index) {
        double wrapped=index%samples.length;int i=(int)wrapped;int next=i+1==samples.length?0:i+1;
        return samples[i]+(samples[next]-samples[i])*(wrapped-i);
    }
    private double pwmForce(double tri,int base,double modulation) {
        double a=sin(electricalPhase)*modulation>tri?1:-1;
        double b=sin(electricalPhase+1.0/3)*modulation>tri?1:-1;
        double c=sin(electricalPhase+2.0/3)*modulation>tri?1:-1;
        double common=(a+b+c)/3;
        currents[base]+=(a-common-currents[base])*CURRENT_FOLLOW;
        currents[base+1]+=(b-common-currents[base+1])*CURRENT_FOLLOW;
        currents[base+2]+=(c-common-currents[base+2])*CURRENT_FOLLOW;
        return currents[base]*currents[base]-.5*currents[base+1]*currents[base+1]-.5*currents[base+2]*currents[base+2];
    }
    public void render(short[] pcm,int offset,int length) {
        java.util.Objects.checkFromIndexSize(offset,length,pcm.length);
        if(length==0)return;
        var t=target;var m=mix;var recordings=layers;double gain=requestedGain,electricGain=motorGain;
        boolean exterior=exteriorLayersEnabled;
        // A newly audible bogey may already be travelling at full speed. Seed
        // from its latest telemetry instead of replaying a zero-RPM launch on
        // every source switch/recreation. Only this audio thread owns the state;
        // later targets still follow smoothly and gain retains its fade-in.
        if(!initialized){
            rpm=t.rpm;bodyKmh=t.bodyKmh;traction=t.traction;brake=t.brake;
            initialized=true;
        }
        // Parameters publish atomically; all oscillator, filter and loop state
        // belongs to this audio stream. There is no allocation inside this loop.
        for(int n=offset;n<offset+length;n++) {
            rpm+=(t.rpm-rpm)*RPM_FOLLOW;bodyKmh+=(t.bodyKmh-bodyKmh)*RPM_FOLLOW;
            traction+=(t.traction-traction)*LOAD_FOLLOW;brake+=(t.brake-brake)*LOAD_FOLLOW;
            outputGain+=(gain-outputGain)*GAIN_FOLLOW;
            double motorHz=rpm*t.gear/60,electricalHz=Math.min(900,motorHz*t.poles);
            double studySpeed=VvvfProfile.studySpeed(electricalHz);
            double tone=VvvfProfile.carrierHz(electricalHz,brake)*(1+detune);
            double load=Math.max(traction,brake*.78),rootLoad=Math.sqrt(load);
            double running=VvvfProfile.smooth(studySpeed/4);
            tonePhase=wrap(tonePhase+tone/48000);breathPhase=wrap(breathPhase+6.3/48000);
            double tonal=0;
            for(int h=0;h<5;h++)tonal+=sin(tonePhase*(h+1)+initialOffset*.017*h)*VvvfProfile.harmonic(tone,h);
            tonal=toneLow.apply(toneHigh.apply(tonal));
            double shaped=.63*tonal+.24*tone560.apply(tonal)+.30*tone1450.apply(tonal);
            double sync=VvvfProfile.smooth((electricalHz-105)/20),five=VvvfProfile.smooth((electricalHz-145)/15);
            double modulation=Math.clamp(.18+electricalHz/110*.75,.18,.96),force=0;
            for(int over=0;over<2;over++) {
                electricalPhase=wrap(electricalPhase+electricalHz/96000);carrierPhase=wrap(carrierPhase+tone/96000);
                double asynchronous=pwmForce(triangle(carrierPhase),0,modulation);
                double nine=pwmForce(triangle(electricalPhase*9),3,modulation);
                double fivePulse=pwmForce(triangle(electricalPhase*5),6,modulation);
                double mixedForce=asynchronous*(1-sync)+nine*sync*(1-five)+fivePulse*sync*five;
                force+=forceLowB.apply(forceLowA.apply(mixedForce))*.5;
            }
            force=forceHigh.apply(force)*1.4;
            double forceTexture=.6*force+.35*force850.apply(force)+.25*force2150.apply(force);
            double electric=(shaped*.34+forceTexture*.35)*rootLoad*running*(1+.025*sin(breathPhase));
            gearPhase=wrap(gearPhase+Math.min(10000,motorHz*23)/48000);
            gearOvertonePhase=wrap(gearOvertonePhase+Math.min(15000,motorHz*46)/48000);
            double gear=(sin(gearPhase)*.010+sin(gearOvertonePhase)*.004)
                    *VvvfProfile.smooth(studySpeed/25)*(.28+.72*rootLoad);
            gear=gearLow.apply(gearHigh.apply(gear));
            double white=noise();noiseLow+=(white-noiseLow)*.035;windLow+=(white-windLow)*.24;
            double speed=Math.clamp(bodyKmh/350,0,2),wheelSpeed=Math.clamp(studySpeed/350,0,2);
            double rolling;
            if(recordings.mechanical!=null) {
                mechanicalRead+=.68+.38*Math.sqrt(wheelSpeed);
                if(mechanicalRead>=recordings.mechanical.length)mechanicalRead%=recordings.mechanical.length;
                rolling=sample(recordings.mechanical,mechanicalRead);
            }else rolling=noiseLow*1.3;
            rolling*= (.13+.54*Math.sqrt(speed))*VvvfProfile.smooth(Math.max(speed,wheelSpeed)/.012);
            double air;
            if(recordings.wind!=null) {
                windRead++;
                if(windRead>=recordings.wind.length)windRead%=recordings.wind.length;
                air=sample(recordings.wind,windRead);
            }else air=(windLow-noiseLow)*.75;
            // At line speed the outside listener is dominated by the air
            // stream around the bogie.  Keep the low-speed texture gentle,
            // then let the broadband wind rise faster than linearly so it
            // masks the inverter tone during a high-speed pass-by.  This is
            // an acoustic mix change only; the wheel/body telemetry and the
            // synthesized motor pitch are unchanged.
            air*=.82*Math.pow(speed,1.85);
            // Exterior high-speed masking follows actual body travel, not
            // wheel RPM: wheelspin at rest must still expose the motor tone.
            // Preserve acceleration detail below 140km/h, then blend toward
            // a wind/rolling-dominated pass-by at 350km/h without a hard mute.
            double motorPresence=1-.82*VvvfProfile.smooth((bodyKmh-140)/210);
            double sample=(electric*(m.electric/.8)*electricGain*motorPresence
                    +(gear+(exterior?rolling:0))*(m.mechanical/.35)+(exterior?air:0)*(m.wind/.25))*1.34307018;
            sample=outputLow.apply(outputHigh.apply(sample))*outputGain;
            // Transparent until the top .8 of headroom. Unlike constant tanh
            // this leaves normal study levels and harmonic ratios unchanged.
            double absolute=Math.abs(sample);
            if(absolute>.8)sample=Math.copySign(.8+.17*(absolute-.8)/(.17+absolute-.8),sample);
            pcm[n]=(short)Math.round(Math.clamp(sample,-.97,.97)*32767);
        }
    }
    /** Fixed-coefficient causal RBJ filters, one set per physical source. */
    private static final class Biquad {
        final double b0,b1,b2,a1,a2;double z1,z2;
        Biquad(double b0,double b1,double b2,double a0,double a1,double a2){this.b0=b0/a0;this.b1=b1/a0;this.b2=b2/a0;this.a1=a1/a0;this.a2=a2/a0;}
        double apply(double input){double output=b0*input+z1;z1=b1*input-a1*output+z2;z2=b2*input-a2*output;return output;}
        static Biquad low(double hz,double q,double rate){double w=2*Math.PI*hz/rate,c=Math.cos(w),a=Math.sin(w)/(2*q);return new Biquad((1-c)/2,1-c,(1-c)/2,1+a,-2*c,1-a);}
        static Biquad high(double hz,double q,double rate){double w=2*Math.PI*hz/rate,c=Math.cos(w),a=Math.sin(w)/(2*q);return new Biquad((1+c)/2,-1-c,(1+c)/2,1+a,-2*c,1-a);}
        static Biquad peak(double hz,double q,double rate){double w=2*Math.PI*hz/rate,c=Math.cos(w),a=Math.sin(w)/(2*q);return new Biquad(a,0,-a,1+a,-2*c,1-a);}
    }
}
