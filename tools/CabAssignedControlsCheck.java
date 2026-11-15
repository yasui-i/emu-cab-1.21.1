import com.yasui_i.emucab.control.*;
import java.util.*;

/** Exercise the user's numbered controls, including commands that share equipment. */
public class CabAssignedControlsCheck {
    private static CabGeometry.Control control(String number) {
        return Arrays.stream(CabGeometry.CONTROLS).filter(c->c.number().equals(number)).findFirst().orElseThrow();
    }
    private static String press(CabControlState s,String number,boolean moving) {
        var c=control(number);
        return s.press(c.id(),c.action(),c.argument(),false,moving);
    }
    public static void main(String[] args) {
        var expected=Map.ofEntries(
            Map.entry("B016",CabAction.MANUAL_NEUTRAL),Map.entry("B017",CabAction.DOOR_OPEN_LEFT),
            Map.entry("B018",CabAction.SUNSHADE),Map.entry("B019",CabAction.DOOR_RELEASE_LEFT),
            Map.entry("B020",CabAction.WIPER),Map.entry("B021",CabAction.DOOR_CLOSE_LEFT),
            Map.entry("B022",CabAction.PARKING_APPLY),Map.entry("B023",CabAction.PARKING_RELEASE),
            Map.entry("B024",CabAction.CLEANING_BRAKE),Map.entry("B026",CabAction.HOLDING_BRAKE),
            Map.entry("B028",CabAction.DSD),Map.entry("B029",CabAction.BREAKER),
            Map.entry("B032",CabAction.PANTOGRAPH),Map.entry("B033",CabAction.SAND),
            Map.entry("B034",CabAction.HORN),Map.entry("B035",CabAction.HANDLE),
            Map.entry("B036",CabAction.GENERAL_RESET),Map.entry("B037",CabAction.EMERGENCY_RESET),
            Map.entry("B038",CabAction.DOOR_CLOSE_RIGHT),Map.entry("B039",CabAction.DOOR_RELEASE_RIGHT),
            Map.entry("B040",CabAction.DOOR_OPEN_RIGHT),Map.entry("B041",CabAction.PASSENGER_ALARM_BYPASS),
            Map.entry("B042",CabAction.DIRECTION),Map.entry("B172",CabAction.MASTER),
            Map.entry("B173",CabAction.OPERATING_MODE));
        for(var c:CabGeometry.CONTROLS)
            assert c.action()==expected.getOrDefault(c.number(),CabAction.UNASSIGNED):c.number();
        var s=new CabControlState();
        assert !s.displaysActive() && !s.ready() && s.direction==0;
        assert press(s,"B032",false)==null;assert press(s,"B029",false)==null;
        assert press(s,"B042",false)==null;
        assert press(s,"B172",false)==null && s.displaysActive() && s.ready();
        assert press(s,"B172",false)==null && !s.on(CabAction.MASTER) && !s.displaysActive() && !s.ready();
        assert s.setHandle(1,false).equals("interlock");
        press(s,"B172",false);assert s.displaysActive() && s.ready();
        s.setHandle(2,false);press(s,"B172",false);
        assert s.notch==0 && !s.ready();
        press(s,"B172",false);
        assert press(s,"B042",true).equals("neutral_first") && s.direction==1;
        s.setHandle(-2,false);
        assert press(s,"B042",false).equals("neutral_first") && s.direction==1;
        s.setHandle(0,false);
        press(s,"B042",false);assert s.direction==-1;
        press(s,"B042",false);assert s.direction==0 && !s.ready();
        press(s,"B042",false);assert s.direction==1 && s.ready();
        var selector=control("B042");
        s.press(selector.id(),selector.action(),0,true,false);assert s.direction==0;
        s.press(selector.id(),selector.action(),0,true,false);assert s.direction==-1;
        s.press(selector.id(),selector.action(),0,true,false);assert s.direction==1;
        for(var c:CabGeometry.CONTROLS) if(c.action()==CabAction.UNASSIGNED) {
            var before=s.signals();String feedback=s.lastControl();
            s.press(c.id(),c.action(),c.argument(),false,false);
            assert s.signals().equals(before) && s.lastControl().equals(feedback):c.number();
        }
        assert press(s,"B017",false).equals("release_door_first");
        assert press(s,"B019",true).equals("stop_first");
        assert press(s,"B019",false)==null && !s.on(CabAction.DOOR_LEFT) && !s.ready();
        assert s.setHandle(1,false).equals("interlock");
        assert press(s,"B017",false)==null && s.on(CabAction.DOOR_LEFT);
        assert press(s,"B017",false)==null && s.on(CabAction.DOOR_LEFT); // open is not a toggle
        assert press(s,"B039",false)==null && press(s,"B040",false)==null;
        assert press(s,"B019",false).equals("close_door_first");
        for(int tick=0;tick<80;tick++)s.tick();
        assert s.signals().get("button/B017")==15 && s.signals().get("button/B040")==15;
        press(s,"B021",false);
        assert s.signals().get("button/B017")==0 && s.signals().get("button/B040")==15;
        assert !s.on(CabAction.DOOR_LEFT) && !s.on(CabAction.DOOR_RELEASE_LEFT);
        assert s.on(CabAction.DOOR_RIGHT) && s.on(CabAction.DOOR_RELEASE_RIGHT);
        press(s,"B038",false);assert s.ready() && s.signals().get("button/B040")==0;
        assert press(s,"B022",true).equals("stop_first");
        s.setHandle(3,false);press(s,"B022",false);
        assert s.parkingBrake && s.notch==0 && !s.ready() && s.signals().get("parking_brake")==15;
        press(s,"B022",false);assert s.parkingBrake;
        press(s,"B023",false);assert !s.parkingBrake && s.ready();
        press(s,"B026",false);assert !s.ready() && s.on(CabAction.HOLDING_BRAKE);
        press(s,"B023",false);assert !s.ready(); // parking release is independent
        press(s,"B026",false);assert s.ready();
        s.setHandle(2,false);press(s,"B024",true);assert s.notch==2; // cleaning is not B7
        press(s,"B016",true);assert !s.on(CabAction.BREAKER) && s.on(CabAction.PANTOGRAPH) && s.notch==0;
        press(s,"B029",false);assert s.ready();
        press(s,"B032",false);assert !s.on(CabAction.BREAKER);
        assert press(s,"B029",false).equals("panto_first");
        press(s,"B032",false);press(s,"B029",false);
        s.setHandle(-8,true);assert s.emergency;
        press(s,"B036",false);press(s,"B028",false);press(s,"B041",false);
        assert s.emergency; // ordinary reset, DSD and passenger bypass cannot reset EB
        assert press(s,"B037",true).equals("stop_first");
        assert press(s,"B037",false)==null && !s.emergency;
        s.setHandle(-2,false);assert press(s,"B037",false).equals("neutral_first");
        s.setHandle(0,false);
        press(s,"B173",false);assert s.signals().get("button/B173")==15;
        press(s,"B033",false);press(s,"B034",false);
        assert s.signal(CabAction.SAND)==15 && s.signal(CabAction.HORN)==15;
        for(int i=0;i<8;i++)s.tick();
        assert s.signal(CabAction.OPERATING_MODE)==0 && s.signal(CabAction.HORN)==0;
        assert s.signal(CabAction.SAND)==0 && s.signal(CabAction.DSD)==0;
        assert s.on(CabAction.PASSENGER_ALARM_BYPASS);
        press(s,"B018",false);assert s.on(CabAction.SUNSHADE);
        for(int n:new int[]{1,2,0}) { press(s,"B020",false);assert s.value(CabAction.WIPER)==n; }
        for(int value:s.signals().values()) assert value>=0 && value<=15;
        assert expected.size()==25;
        System.out.println("Assigned controls: 25 bindings, master key, direction interlocks, inert blanks, door sequences, independent brakes, VCB, DSD, resets and signal expiry passed");
    }
}
