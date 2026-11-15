package com.yasui_i.emucab.control;

/** Radius in world blocks, independent of recording volume and Sable plot coordinates. */
public final class CabAtpAcoustics {
    /** Audible radius in world blocks. The previous 3-block radius was too small
     * for a moving cab at high speed, where the listener can cross the boundary
     * between two client ticks. */
    public static final double RADIUS=8;
    /** Delivery margin only; the client still enforces the exact audible radius. */
    public static final double DELIVERY_RADIUS=128;
    public static double gain(double distanceSquared) {
        if(!Double.isFinite(distanceSquared)||distanceSquared<0||distanceSquared>=RADIUS*RADIUS)return 0;
        return Math.min(1,(RADIUS-Math.sqrt(distanceSquared))/(RADIUS/2));
    }
    private CabAtpAcoustics() {}
}
