package com.yasui_i.emucab.control;

/** Display-only gauge ranges and tick/frame smoothing. Never feeds electrical simulation. */
public final class CabElectricalDisplay {
    public static final double VOLTAGE_MIN=17_500,VOLTAGE_MAX=32_500,CURRENT_MAX=600;
    private double voltage,current,previousVoltage,previousCurrent;
    private boolean initialized;

    public static double voltageFraction(double volts){return fraction(volts,VOLTAGE_MIN,VOLTAGE_MAX);}
    public static double currentFraction(double amps){return fraction(amps,0,CURRENT_MAX);}
    private static double fraction(double value,double min,double max){
        return Double.isFinite(value)?Math.clamp((value-min)/(max-min),0,1):0;
    }
    public void tick(double volts,double amps){
        volts=clean(volts);amps=clean(amps);
        if(!initialized){previousVoltage=voltage=volts;previousCurrent=current=amps;initialized=true;return;}
        previousVoltage=voltage;previousCurrent=current;
        voltage=follow(voltage,volts);current=follow(current,amps);
    }
    private static double clean(double value){return Double.isFinite(value)?Math.max(0,value):0;}
    private static double follow(double shown,double target){
        // Disconnection must visibly reach zero immediately rather than imply live power.
        return target==0||Math.abs(shown-target)<.01?target:shown+(target-shown)*.3;
    }
    public double voltage(float partial){return interpolate(previousVoltage,voltage,partial);}
    public double current(float partial){return interpolate(previousCurrent,current,partial);}
    private static double interpolate(double old,double value,float partial){
        return old+(value-old)*Math.clamp(partial,0,1);
    }
}
