package com.yasui_i.emucab.control;

import java.util.*;

/** Stable, localized wire IDs. Only assigned buttons are selectable. */
public final class CabWireChannels {
    public static final Map<String,String> SIGNALS;
    public static final List<String> CHANNELS;
    // Existing wires stay connected and read the same latch, but these IDs can
    // no longer be selected for new connections.
    public static final List<String> LEGACY_PARKING_CHANNELS=List.of("emucab.wire.button.B022","emucab.wire.button.B023");
    static {
        Map<String,String> channels=new LinkedHashMap<>();
        for(String name:List.of("traction","brake","direction_forward","direction_reverse","emergency","ready","parking_brake","door_left","door_right","pantograph_03","pantograph_06"))
            channels.put("emucab.wire."+name,name);
        Arrays.stream(CabGeometry.CONTROLS).filter(c->c.action()!=CabAction.UNASSIGNED)
                .filter(c->!isDoorControl(c.action()) && c.action()!=CabAction.MASTER
                        && c.action()!=CabAction.PARKING_APPLY && c.action()!=CabAction.PARKING_RELEASE)
                .sorted(Comparator.comparing(CabGeometry.Control::number))
                .forEach(c->channels.put("emucab.wire.button."+c.number(),"button/"+c.number()));
        SIGNALS=Collections.unmodifiableMap(channels);
        CHANNELS=List.copyOf(channels.keySet());
    }
    public static String next(String current,boolean forward) {
        int i=CHANNELS.indexOf(current);
        return i<0?CHANNELS.getFirst():CHANNELS.get(Math.floorMod(i+(forward?1:-1),CHANNELS.size()));
    }
    public static int value(String channel,Map<String,Integer> signals) {
        String signal=LEGACY_PARKING_CHANNELS.contains(channel)?"parking_brake":SIGNALS.get(channel);
        return signal==null?0:Math.clamp(signals.getOrDefault(signal,0),0,15);
    }
    private static boolean isDoorControl(CabAction action) {
        return switch(action) {
            case DOOR_RELEASE_LEFT, DOOR_OPEN_LEFT, DOOR_CLOSE_LEFT,
                 DOOR_RELEASE_RIGHT, DOOR_OPEN_RIGHT, DOOR_CLOSE_RIGHT -> true;
            default -> false;
        };
    }
    private CabWireChannels() {}
}
