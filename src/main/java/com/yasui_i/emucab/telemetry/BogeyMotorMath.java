package com.yasui_i.emucab.telemetry;

/** Unit conversions and bounded acoustic loads; these never change traction or braking. */
public final class BogeyMotorMath {
    public static float wheelRpm(double wheelSurfaceMetresPerSecond,double wheelRadiusMetres) {
        if(!Double.isFinite(wheelSurfaceMetresPerSecond)||!Double.isFinite(wheelRadiusMetres)||wheelRadiusMetres<=0)return 0;
        return (float)Math.clamp(Math.abs(wheelSurfaceMetresPerSecond)*60/(2*Math.PI*wheelRadiusMetres),0,60000);
    }

    /** Normalize measured two-axle force against Simurail's configured maximum drive force. */
    public static float tractionLoad(double measuredNewtons,double targetSpeed,double ratedStress,double driveForceFactor) {
        if(!Double.isFinite(measuredNewtons)||measuredNewtons<=0||!Double.isFinite(targetSpeed)
                ||!Double.isFinite(ratedStress)||!Double.isFinite(driveForceFactor))return 0;
        double capacity=2*Math.abs(targetSpeed)*Math.abs(ratedStress)*Math.max(0,driveForceFactor);
        if(!Double.isFinite(capacity)||capacity<=0)return 0;
        return (float)Math.clamp(measuredNewtons/capacity,0,1);
    }

    public static float unit(double value){return Double.isFinite(value)?(float)Math.clamp(value,0,1):0;}
    private BogeyMotorMath(){}
}
