package com.yasui_i.emucab.control;

/**
 * Client-side speed display smoothing. Samples are authoritative server values;
 * the display never differentiates the Sable render pose, which can contain
 * network correction steps while a physical body is moving.
 */
public final class CabSpeedometer {
    private double value;
    private double previous;
    private double target;
    private long latestSample=Long.MIN_VALUE;
    private boolean initialized;

    public boolean accept(long sequence,double speedKmh) {
        if(sequence<=latestSample || !Double.isFinite(speedKmh) || speedKmh<0)return false;
        latestSample=sequence;
        target=speedKmh;
        if(!initialized) { previous=value=target; initialized=true; }
        return true;
    }

    /** Advance one client tick. The factor damps packet-to-packet physics noise. */
    public void tick() {
        previous=value;
        value += (target-value)*.24;
        if(Math.abs(target-value)<.02)value=target;
    }

    /** Render-frame interpolation toward the next authoritative sample. */
    public double value(float partialTick) {
        if(!initialized)return 0;
        double t=Math.clamp(partialTick,0,1);
        return previous+(value-previous)*t;
    }

    public double latest() { return value; }
    public double target() { return target; }
    public long latestSample() { return latestSample; }
}
