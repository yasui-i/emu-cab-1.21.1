package com.yasui_i.emucab.audio;

/** Acoustic targets from the approved exterior study, not a measured train map. */
public final class VvvfProfile {
    public static final int SAMPLE_RATE=48000;
    private static final double[] TONES={454.1,533.2,641.6,752.9,867.2};
    private static final double[][] DB={{0,-23,-19.6,-24.4,-18.7},{0,-8.2,-8.4,-8.5,-16.2},
            {0,-5.6,-2.3,-8.1,-18.6},{0,4.6,4.8,-10.9,-21.8},{0,-2.3,-14.6,-27.3,-32}};
    private static final int STEPS=2048;
    private static final double[][] HARMONICS=new double[5][STEPS+1];
    static {
        for(int i=0;i<=STEPS;i++) {
            double tone=440+i*460.0/STEPS;
            int k=0;while(k<TONES.length-2&&tone>TONES[k+1])k++;
            double fraction=Math.clamp((tone-TONES[k])/(TONES[k+1]-TONES[k]),0,1);
            for(int h=0;h<5;h++)HARMONICS[h][i]=Math.pow(10,(DB[k][h]+(DB[k+1][h]-DB[k][h])*fraction)/20)/2.8;
        }
    }
    /** Default .46m wheels, 2.5 gear, two pole pairs define the study's pitch map. */
    public static double studySpeed(double electricalHz){return electricalHz*60/5*(2*Math.PI*.46)*3.6/60;}
    public static double carrierHz(double electricalHz,double braking){return 451.2+416*smooth((studySpeed(electricalHz)-80)/270);}
    public static double harmonic(double tone,int order) {
        double index=Math.clamp((tone-440)/460*STEPS,0,STEPS);int i=(int)index;
        return i==STEPS?HARMONICS[order][i]:HARMONICS[order][i]+(HARMONICS[order][i+1]-HARMONICS[order][i])*(index-i);
    }
    public static double smooth(double v){v=Math.clamp(v,0,1);return v*v*(3-2*v);}
    private VvvfProfile(){}
}
