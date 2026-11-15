package com.yasui_i.emucab.control;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pure server-authoritative control logic, also usable by tests and wire adapters. */
public final class CabControlState {
    public static final int MAX_NOTCH = 7;
    public int notch;
    public boolean emergency;
    public boolean parkingBrake;
    public int direction; // -1 reverse, 0 neutral, +1 forward
    public int radioChannel = 1;
    public String radioInput = "";
    public final int[] pages = new int[5];
    public final EnumMap<CabAction, Integer> switches = new EnumMap<>(CabAction.class);
    private final Map<String, Integer> pulses = new HashMap<>();
    private String lastControl = "";
    private int feedbackTicks;

    public CabControlState() {
        // Internal supply/activation have no assigned physical switch. The master key
        // gates both, while all driver-operated switches start off.
        switches.put(CabAction.POWER,1);
        switches.put(CabAction.CAB_ACTIVE,1);
    }

    /** Remove a cab's key without replaying momentary outputs on the next activation. */
    public void releaseForTakeover() {
        boolean parked=parkingBrake;
        switches.clear();
        switches.put(CabAction.POWER,1);
        switches.put(CabAction.CAB_ACTIVE,1);
        pulses.clear();feedbackTicks=0;lastControl="";
        direction=0;notch=0;emergency=false;parkingBrake=parked;
    }

    public int value(CabAction a) { return switches.getOrDefault(a, 0); }
    public boolean on(CabAction a) { return value(a) != 0; }
    public boolean displaysActive() { return on(CabAction.POWER) && on(CabAction.MASTER) && on(CabAction.CAB_ACTIVE); }
    public boolean ready() {
        return on(CabAction.MASTER) && on(CabAction.POWER) && on(CabAction.CAB_ACTIVE)
                && on(CabAction.PANTOGRAPH) && on(CabAction.BREAKER)
                && !on(CabAction.DOOR_LEFT) && !on(CabAction.DOOR_RIGHT)
                && !on(CabAction.DOOR_RELEASE_LEFT) && !on(CabAction.DOOR_RELEASE_RIGHT)
                && !parkingBrake && !on(CabAction.HOLDING_BRAKE) && direction != 0 && !emergency;
    }

    /** Absolute drag target; all the same interlocks apply without synthesizing clicks. */
    public String setHandle(int target,boolean moving) {
        if(target < -8 || target > MAX_NOTCH)return "invalid_notch";
        if(emergency)return target==-8?null:"reset_first";
        if(target>0 && !ready())return "interlock";
        lastControl="master_handle";feedbackTicks=8;
        if(target==-8) { emergency=true;notch=0; }
        else notch=target;
        return null;
    }

    /** Returns a translation suffix on a rejected operation, otherwise null. */
    public String press(String id, CabAction action, int argument, boolean reverse, boolean moving) {
        if (action == CabAction.UNASSIGNED) return null;
        lastControl = id;
        feedbackTicks = 8;
        switch (action) {
            case UNASSIGNED -> {}
            case DOOR_RELEASE_LEFT, DOOR_RELEASE_RIGHT -> {
                if (moving || notch > 0) return "stop_first";
                boolean left=action==CabAction.DOOR_RELEASE_LEFT;
                CabAction door=left?CabAction.DOOR_LEFT:CabAction.DOOR_RIGHT;
                if (on(action) && on(door)) return "close_door_first";
                switches.put(action,on(action)?0:1);
            }
            case DOOR_OPEN_LEFT, DOOR_OPEN_RIGHT -> {
                if (moving || notch > 0) return "stop_first";
                boolean left=action==CabAction.DOOR_OPEN_LEFT;
                if (!on(left?CabAction.DOOR_RELEASE_LEFT:CabAction.DOOR_RELEASE_RIGHT)) return "release_door_first";
                switches.put(left?CabAction.DOOR_LEFT:CabAction.DOOR_RIGHT,1);
            }
            case DOOR_CLOSE_LEFT, DOOR_CLOSE_RIGHT -> {
                boolean left=action==CabAction.DOOR_CLOSE_LEFT;
                switches.put(left?CabAction.DOOR_LEFT:CabAction.DOOR_RIGHT,0);
                switches.put(left?CabAction.DOOR_RELEASE_LEFT:CabAction.DOOR_RELEASE_RIGHT,0);
                pulse(id,action);
            }
            case PARKING_APPLY -> {
                if (moving) return "stop_first";
                parkingBrake=true;
                if (notch>0) notch=0;
                pulse(id,action);
            }
            case PARKING_RELEASE -> { parkingBrake=false; pulse(id,action); }
            case HOLDING_BRAKE -> {
                if (!on(action) && moving) return "stop_first";
                switches.put(action,on(action)?0:1);
                if (notch>0) notch=0;
            }
            case MANUAL_NEUTRAL -> {
                // Manual request to negotiate a neutral section. Route detection,
                // timing and automatic reclosure belong to a future line adapter.
                switches.put(CabAction.BREAKER,0);
                if (notch>0) notch=0;
                pulse(id,action);
            }
            case GENERAL_RESET, CLEANING_BRAKE, DSD, OPERATING_MODE -> pulse(id,action);
            case EMERGENCY_RESET -> {
                if (moving) return "stop_first";
                if (notch!=0) return "neutral_first";
                emergency=false;
                pulse(id,action);
            }
            case SUNSHADE, PASSENGER_ALARM_BYPASS -> switches.put(action,on(action)?0:1);
            case HANDLE -> {
                if (emergency) return "reset_first";
                int next = notch + (reverse ? -1 : 1);
                if (next > 0 && !ready()) return "interlock";
                if (next < -MAX_NOTCH) { emergency = true; notch = 0; }
                else notch = Math.min(MAX_NOTCH, next);
            }
            case EMERGENCY -> { emergency = true; notch = 0; }
            case RESET -> {
                if (moving) return "stop_first";
                emergency = false; notch = 0;
            }
            case DIRECTION -> {
                if (moving || notch != 0) return "neutral_first";
                int[] sequence = {0, 1, -1};
                int index = direction == 0 ? 0 : direction == 1 ? 1 : 2;
                direction = sequence[Math.floorMod(index + (reverse ? -1 : 1), 3)];
            }
            case MASTER, POWER, CAB_ACTIVE, PANTOGRAPH, BREAKER -> {
                int next = on(action) ? 0 : 1;
                if (action == CabAction.BREAKER && next == 1 && !on(CabAction.PANTOGRAPH)) return "panto_first";
                switches.put(action, next);
                if (action == CabAction.PANTOGRAPH && next == 0) switches.put(CabAction.BREAKER, 0);
                if (!ready() && notch > 0) notch = 0;
            }
            case DOOR_LEFT, DOOR_RIGHT -> {
                if (moving || notch > 0) return "stop_first";
                switches.put(action, on(action) ? 0 : 1);
            }
            case DOOR_CLOSE -> {
                switches.put(CabAction.DOOR_LEFT, 0); switches.put(CabAction.DOOR_RIGHT, 0);
                pulse(id, action);
            }
            case WIPER, HEADLIGHT -> switches.put(action, Math.floorMod(value(action) + (reverse ? -1 : 1), 3));
            case CAB_LIGHT, AUXILIARY -> switches.put(action, on(action) ? 0 : 1);
            case BRAKE_TEST -> {
                if (moving) return "stop_first";
                notch = -MAX_NOTCH;
                pulse(id, action);
            }
            case ACK, SAND, HORN -> pulse(id, action);
            case SCREEN -> {
                // Display pages are fixed; retain independent softkey wire pulses.
                pulse(id, action);
            }
            case RADIO -> {
                if (argument < 10) radioInput = (radioInput + argument).substring(Math.max(0, radioInput.length()+1-3));
                else if (argument == 10) radioInput = "";
                else if (argument == 11 && !radioInput.isEmpty()) {
                    radioChannel = Math.clamp(Integer.parseInt(radioInput), 1, 999); radioInput = "";
                } else if (argument >= 12) radioChannel = Math.floorMod(radioChannel-1 + (reverse ? -1 : 1), 999)+1;
                pulse(id, action);
            }
        }
        return null;
    }

    private void pulse(String id, CabAction action) {
        pulses.put(id, 8);
        pulses.put("action/"+action.channel(), 8);
    }

    public boolean tick() {
        boolean changed = pulses.containsValue(1) || feedbackTicks == 1;
        pulses.replaceAll((key, ticks) -> ticks-1);
        pulses.values().removeIf(ticks -> ticks <= 0);
        if (feedbackTicks > 0) feedbackTicks--;
        return changed;
    }

    public boolean highlighted(String id) { return feedbackTicks > 0 && lastControl.equals(id); }
    public String lastControl() { return feedbackTicks > 0 ? lastControl : ""; }
    public void feedback(String id) { lastControl = id; feedbackTicks = id.isEmpty() ? 0 : 8; }

    public int signal(CabAction a) {
        if (a.momentary()) return pulses.containsKey("action/"+a.channel()) ? 15 : 0;
        return switch (a) {
            case UNASSIGNED -> 0;
            case DOOR_OPEN_LEFT -> on(CabAction.DOOR_LEFT)?15:0;
            case DOOR_OPEN_RIGHT -> on(CabAction.DOOR_RIGHT)?15:0;
            case DOOR_RELEASE_LEFT, DOOR_RELEASE_RIGHT, DOOR_CLOSE_LEFT, DOOR_CLOSE_RIGHT -> 0;
            case HANDLE -> emergency ? 0 : (int)Math.round(Math.max(0, notch)*15.0/MAX_NOTCH);
            case EMERGENCY -> emergency ? 15 : 0;
            case DIRECTION -> direction == 1 ? 15 : direction == -1 ? 7 : 0;
            case WIPER, HEADLIGHT -> value(a) * 7 + (value(a) == 2 ? 1 : 0);
            default -> on(a) ? 15 : 0;
        };
    }

    public Map<String, Integer> signals() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (CabAction a : CabAction.values()) out.put(a.channel(), signal(a));
        out.put("traction", signal(CabAction.HANDLE));
        out.put("brake", emergency ? 15 : (int)Math.round(Math.max(0,-notch)*15.0/MAX_NOTCH));
        out.put("direction_forward", direction == 1 ? 15 : 0);
        out.put("direction_reverse", direction == -1 ? 15 : 0);
        out.put("ready", ready() ? 15 : 0);
        out.put("parking_brake",parkingBrake?15:0);
        out.put("door_left",on(CabAction.DOOR_LEFT)?15:0);
        out.put("door_right",on(CabAction.DOOR_RIGHT)?15:0);
        for (var c : CabGeometry.CONTROLS) {
            int value = c.action().momentary() ? (pulses.containsKey(c.id())?15:0) : signal(c.action());
            out.put("control/"+c.id(), value);
            out.put("button/"+c.number(), value);
        }
        // A latched parking brake remains applied after the driver removes the key.
        // All other outputs retain the master-key interlock.
        if (!on(CabAction.MASTER)) out.replaceAll((key,value) -> key.equals("parking_brake") ? value : 0);
        return Map.copyOf(out);
    }
}
