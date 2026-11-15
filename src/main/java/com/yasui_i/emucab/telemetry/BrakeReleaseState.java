package com.yasui_i.emucab.telemetry;

/** Debounced command edges; wheel force alone is zero at rest even with brakes applied. */
public final class BrakeReleaseState {
    /** The 4.481 s recording must finish before this bogey can play it again. */
    public static final int MIN_COOLDOWN_TICKS=100;
    private static final int STABLE_TICKS=3;
    private int appliedTicks,releasedTicks,cooldown;
    private boolean armed;

    /** Called once per server tick. Loading a released bogey never makes a sound. */
    public boolean update(float effectiveBrake) {
        return update(effectiveBrake,MIN_COOLDOWN_TICKS);
    }

    /** Repeated release edges during the quiet period are discarded, never queued. */
    public boolean update(float effectiveBrake,int cooldownTicks) {
        if(cooldown>0)cooldown--;
        if(!Float.isFinite(effectiveBrake)) {appliedTicks=releasedTicks=0;return false;}
        // Simurail squares the redstone fraction: even signal 1 is about 0.0044.
        if(effectiveBrake>.001f) {
            releasedTicks=0;
            if(++appliedTicks>=STABLE_TICKS) {appliedTicks=STABLE_TICKS;armed=true;}
            return false;
        }
        appliedTicks=0;
        if(effectiveBrake>.00001f) {releasedTicks=0;return false;}
        if(++releasedTicks<STABLE_TICKS)return false;
        releasedTicks=STABLE_TICKS;
        if(!armed)return false;
        armed=false;
        if(cooldown>0)return false;
        cooldown=Math.clamp(cooldownTicks,MIN_COOLDOWN_TICKS,20*300);
        return true;
    }
}
