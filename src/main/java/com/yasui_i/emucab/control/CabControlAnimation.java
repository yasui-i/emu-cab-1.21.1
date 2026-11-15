package com.yasui_i.emucab.control;

import java.util.HashMap;
import java.util.Map;

/** Transient visual motion only; never contributes to controls, interlocks or wire signals. */
public final class CabControlAnimation {
    private static final double PRESS_DOWN = 1.6;
    private static final double PRESS_HOLD = 2.4;
    private static final double PRESS_UP = 2.4;
    private static final double ROTATE_TICKS = 4;
    private final Map<String, Press> presses = new HashMap<>();
    private final Map<String, Rotation> rotations = new HashMap<>();
    private long lastSequence = -1;

    private record Press(double started,double from) {}

    private record Rotation(double from, double target, double started) {
        double value(double time) {
            return from + (target-from)*smooth((time-started)/ROTATE_TICKS);
        }
    }

    /** Every accepted packet is a new actuation, including a repeated press on the same control. */
    public void press(String id,long sequence,double time) {
        if(id==null || id.isEmpty() || sequence<=lastSequence || !Double.isFinite(time))return;
        lastSequence=sequence;
        double from=pressAmount(id,time);
        presses.put(id,new Press(time,from));
        // Only real controls are accepted by the receiver; expire old entries on input, not per frame.
        presses.entrySet().removeIf(entry -> time-entry.getValue().started()>PRESS_DOWN+PRESS_HOLD+PRESS_UP);
    }

    /** 0 = released, 1 = fully depressed. Runs in game ticks, independent of frame rate. */
    public double pressAmount(String id,double time) {
        Press press=presses.get(id);
        if(press==null || !Double.isFinite(time))return 0;
        double age=time-press.started();
        if(age<0)return 0;
        if(age<PRESS_DOWN)return press.from()+(1-press.from())*smooth(age/PRESS_DOWN);
        if(age<PRESS_DOWN+PRESS_HOLD)return 1;
        return 1-smooth((age-PRESS_DOWN-PRESS_HOLD)/PRESS_UP);
    }

    /** Stateful knobs settle into their authoritative detent; momentary rotary controls spring back. */
    public double rotationDegrees(CabGeometry.Control control,CabControlState state,double time) {
        if(control.action().momentary())return 45*pressAmount(control.id(),time);
        double target=rotationTargetDegrees(control,state);
        Rotation motion=rotations.get(control.id());
        if(motion==null) {
            rotations.put(control.id(),new Rotation(target,target,time));
            return target; // Joining/loading must show the saved detent without replaying a turn.
        }
        if(motion.target()!=target) {
            motion=new Rotation(motion.value(time),target,time);
            rotations.put(control.id(),motion);
        }
        return motion.value(time);
    }

    public static double rotationTargetDegrees(CabGeometry.Control control,CabControlState state) {
        return switch(control.action()) {
            case MASTER -> state.on(CabAction.MASTER)?90:0;
            case DIRECTION -> Math.clamp(state.direction,-1,1)*45;
            case WIPER, HEADLIGHT -> Math.clamp(state.value(control.action()),0,2)*45;
            case PANTOGRAPH, BREAKER, SUNSHADE, PASSENGER_ALARM_BYPASS,
                    POWER, CAB_ACTIVE, CAB_LIGHT, AUXILIARY, HOLDING_BRAKE -> state.on(control.action())?45:0;
            default -> 0;
        };
    }

    private static double smooth(double value) {
        double t=Math.clamp(value,0,1);
        return t*t*(3-2*t);
    }
}
