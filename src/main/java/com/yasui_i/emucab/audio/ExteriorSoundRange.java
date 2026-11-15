package com.yasui_i.emucab.audio;

/** Shared world-space range and gain rules for exterior sounds. */
public final class ExteriorSoundRange {
    public static final double MIN=64, DEFAULT=128, MAX=512;
    // Covers the maximum client radius plus moving-source/network margin.
    public static final double DELIVERY=MAX+128;

    public static double gain(double distance,double radius,double fadeFraction) {
        if(!Double.isFinite(distance)||distance<0||!Double.isFinite(radius)||radius<=0
                ||!Double.isFinite(fadeFraction)||fadeFraction<=0)return 0;
        return Math.sqrt(Math.clamp((radius-distance)/(radius*fadeFraction),0,1));
    }

    public static double balance(double gainPower) {
        return Double.isFinite(gainPower)?1/Math.sqrt(Math.max(1,gainPower)):1;
    }

    private ExteriorSoundRange() {}
}
