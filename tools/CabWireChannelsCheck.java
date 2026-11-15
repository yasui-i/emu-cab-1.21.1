import com.yasui_i.emucab.control.*;
import java.util.*;

public class CabWireChannelsCheck {
    public static void main(String[] args) {
        assert CabWireChannels.CHANNELS.size()==27;
        assert CabWireChannels.next("world",true).equals("emucab.wire.traction");
        String c=CabWireChannels.CHANNELS.getFirst();
        for(int i=0;i<CabWireChannels.CHANNELS.size();i++)c=CabWireChannels.next(c,true);
        assert c.equals(CabWireChannels.CHANNELS.getFirst());
        assert CabWireChannels.next(c,false).equals(CabWireChannels.CHANNELS.getLast());
        assert CabWireChannels.value("unknown",Map.of())==0;
        var state=new CabControlState();
        CabControlStateCheck.start(state);
        state.switches.put(CabAction.DOOR_RELEASE_LEFT,1);
        assert state.press("door",CabAction.DOOR_OPEN_LEFT,0,false,false)==null;
        assert CabWireChannels.value("emucab.wire.door_left",state.signals())==15;
        assert state.press("close",CabAction.DOOR_CLOSE_LEFT,0,false,false)==null;
        assert CabWireChannels.value("emucab.wire.door_left",state.signals())==0;
        assert CabWireChannels.value("emucab.wire.direction_forward",state.signals())==15;
        assert CabWireChannels.value("emucab.wire.direction_reverse",state.signals())==0;
        state.switches.put(CabAction.DOOR_RELEASE_RIGHT,1);
        assert state.press("door",CabAction.DOOR_OPEN_RIGHT,0,false,false)==null;
        assert CabWireChannels.value("emucab.wire.door_right",state.signals())==15;
        assert CabWireChannels.value("emucab.wire.door_left",state.signals())==0;
        assert state.press("close",CabAction.DOOR_CLOSE_RIGHT,0,false,false)==null;
        for(int notch=-7;notch<=7;notch++) {
            assert state.setHandle(notch,false)==null;
            assert CabWireChannels.value("emucab.wire.traction",state.signals())==(int)Math.round(Math.max(0,notch)*15.0/7);
            assert CabWireChannels.value("emucab.wire.brake",state.signals())==(int)Math.round(Math.max(0,-notch)*15.0/7);
        }
        state.setHandle(-8,false);
        assert CabWireChannels.value("emucab.wire.emergency",state.signals())==15;
        assert CabWireChannels.value("emucab.wire.brake",state.signals())==15;
        for(var button:CabGeometry.CONTROLS) {
            String channel="emucab.wire.button."+button.number();
            boolean door=button.action()==CabAction.DOOR_RELEASE_LEFT || button.action()==CabAction.DOOR_OPEN_LEFT || button.action()==CabAction.DOOR_CLOSE_LEFT
                    || button.action()==CabAction.DOOR_RELEASE_RIGHT || button.action()==CabAction.DOOR_OPEN_RIGHT || button.action()==CabAction.DOOR_CLOSE_RIGHT;
            boolean parking=button.action()==CabAction.PARKING_APPLY || button.action()==CabAction.PARKING_RELEASE;
            assert CabWireChannels.CHANNELS.contains(channel)==(button.action()!=CabAction.UNASSIGNED && !door && !parking && button.action()!=CabAction.MASTER):button.number();
            if(button.action().momentary() && !door && !parking) {
                var s=new CabControlState();
                s.switches.put(CabAction.MASTER,1);
                if(button.action()==CabAction.DOOR_OPEN_LEFT)s.switches.put(CabAction.DOOR_RELEASE_LEFT,1);
                if(button.action()==CabAction.DOOR_OPEN_RIGHT)s.switches.put(CabAction.DOOR_RELEASE_RIGHT,1);
                assert s.press(button.id(),button.action(),button.argument(),false,false)==null;
                assert CabWireChannels.value(channel,s.signals())==15:channel;
                for(int tick=0;tick<8;tick++)s.tick();
                assert CabWireChannels.value(channel,s.signals())==0:channel;
            }
        }
        var locked=new CabControlState();
        locked.switches.put(CabAction.MASTER,1);
        locked.direction=1; locked.notch=7; locked.switches.put(CabAction.DOOR_LEFT,1);
        locked.switches.put(CabAction.MASTER,0);
        assert locked.signals().values().stream().allMatch(v->v==0);
        var parking=new CabControlState();
        CabControlStateCheck.start(parking);
        assert parking.press("apply",CabAction.PARKING_APPLY,0,false,true).equals("stop_first");
        assert CabWireChannels.value("emucab.wire.parking_brake",parking.signals())==0;
        assert parking.press("apply",CabAction.PARKING_APPLY,0,false,false)==null;
        for(int tick=0;tick<200;tick++)parking.tick();
        assert CabWireChannels.value("emucab.wire.parking_brake",parking.signals())==15;
        assert parking.press("key",CabAction.MASTER,0,false,false)==null;
        for(int tick=0;tick<200;tick++)parking.tick();
        assert parking.parkingBrake && !parking.on(CabAction.MASTER);
        assert parking.signals().entrySet().stream().allMatch(e->e.getValue()==(e.getKey().equals("parking_brake")?15:0));
        for(String legacy:CabWireChannels.LEGACY_PARKING_CHANNELS) {
            assert !CabWireChannels.CHANNELS.contains(legacy);
            assert CabWireChannels.value(legacy,parking.signals())==15;
        }
        assert parking.press("release",CabAction.PARKING_RELEASE,0,false,false)==null;
        assert parking.signals().values().stream().allMatch(v->v==0);
        for(String legacy:CabWireChannels.LEGACY_PARKING_CHANNELS)assert CabWireChannels.value(legacy,parking.signals())==0;
        System.out.println("Wire channels: 27 selectable outputs; merged parking latch survives key off, releases with key off, and updates legacy wires; other outputs remain interlocked");
    }
}
