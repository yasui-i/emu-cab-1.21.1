package com.yasui_i.emucab.control;

/** Indicators show cab commands until real per-car feedback is connected. */
public final class CabVehicleDisplay {
    public static boolean motorCar(int car){return car==2||car==4||car==5||car==7;}
    public static boolean traction(CabControlState state,int car){
        return motorCar(car)&&state.notch>=1&&!state.emergency;
    }
    public static boolean braking(CabControlState state){
        return state.emergency||state.notch<0||state.parkingBrake||state.on(CabAction.HOLDING_BRAKE);
    }
    public static float carCenter(int car){return 63+(car-1)*19;}
    public static double brakeDemand(CabControlState state){return state.emergency?1:Math.clamp(-state.notch/7.,0,1);}
    /** Display-only allocation model. Simurail has no separate regenerative-energy feedback. */
    public static boolean electricBrake(CabControlState state,double speed,int car){
        return motorCar(car)&&state.notch<0&&!state.emergency&&speed>=8
            &&state.on(CabAction.PANTOGRAPH)&&state.on(CabAction.BREAKER)&&!state.parkingBrake;
    }
    public static boolean airPrecontrol(CabControlState state,double speed){
        return state.emergency||state.parkingBrake||state.on(CabAction.HOLDING_BRAKE)
            ||state.notch<0&&(state.notch<=-4||speed<8||!state.on(CabAction.PANTOGRAPH)||!state.on(CabAction.BREAKER));
    }
    private CabVehicleDisplay(){}
}
