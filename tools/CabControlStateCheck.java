import com.yasui_i.emucab.control.*;

/** Dependency-free checks of real control behaviour, run with java -ea. */
public class CabControlStateCheck {
    static void start(CabControlState s) {
        for(var a:new CabAction[]{CabAction.MASTER,CabAction.PANTOGRAPH,CabAction.BREAKER})
            assert s.press(a.channel(),a,0,false,false)==null;
        assert s.press("direction",CabAction.DIRECTION,0,false,false)==null;
    }
    public static void main(String[] args) {
        var s = new CabControlState();
        assert !s.ready() && !s.displaysActive() && s.direction==0;
        assert !s.on(CabAction.MASTER) && !s.on(CabAction.PANTOGRAPH) && !s.on(CabAction.BREAKER);
        assert s.signals().get("traction")==0 && s.signals().get("ready")==0;
        start(s);
        assert s.ready();
        assert s.displaysActive();
        s.press("cab",CabAction.CAB_ACTIVE,0,false,false);
        assert !s.displaysActive();
        s.press("cab",CabAction.CAB_ACTIVE,0,false,false);
        s.press("power",CabAction.POWER,0,false,false);
        assert !s.displaysActive();
        s.press("power",CabAction.POWER,0,false,false);
        s.press("key",CabAction.MASTER,0,false,false);
        assert !s.displaysActive();
        s.press("key",CabAction.MASTER,0,false,false);
        for(int screen=0;screen<5;screen++) {
            s.press("softkey",CabAction.SCREEN,screen*16,false,false);
            assert s.pages[screen]==0;
        }
        assert s.displaysActive();
        assert s.press("handle",CabAction.HANDLE,0,false,false)==null && s.notch==1;
        assert s.press("door",CabAction.DOOR_LEFT,0,false,true)!=null;
        s.press("emergency",CabAction.EMERGENCY,0,false,true);
        assert s.emergency && s.signals().get("brake")==15;
        assert s.press("reset",CabAction.RESET,0,false,true)!=null;
        assert s.press("reset",CabAction.RESET,0,false,false)==null;
        assert !s.emergency && s.notch==0;
        s.press("door",CabAction.DOOR_LEFT,0,false,false);
        assert !s.ready() && s.press("handle",CabAction.HANDLE,0,false,false)!=null;
        s.press("close",CabAction.DOOR_CLOSE,0,false,false);
        assert s.ready();
        s.press("horn",CabAction.HORN,0,false,false);
        assert s.signals().get("horn")==15;
        for(int i=0;i<8;i++)s.tick();
        assert s.signals().get("horn")==0;
        s.press("panto",CabAction.PANTOGRAPH,0,false,false);
        assert !s.on(CabAction.BREAKER) && !s.ready();
        assert s.press("breaker",CabAction.BREAKER,0,false,false)!=null;
        s.press("radio",CabAction.RADIO,8,false,false);
        s.press("radio",CabAction.RADIO,11,false,false);
        assert s.radioChannel==8;
        for(int value:s.signals().values())assert value>=0 && value<=15;
        var drag=new CabControlState();
        start(drag);
        assert drag.setHandle(7,false)==null && drag.notch==7;
        assert drag.setHandle(-7,true)==null && drag.notch==-7;
        assert drag.setHandle(0,true)==null && drag.notch==0;
        drag.press("door",CabAction.DOOR_LEFT,0,false,false);
        assert drag.setHandle(4,false).equals("interlock") && drag.notch==0;
        assert drag.setHandle(-3,true)==null && drag.notch==-3;
        assert drag.setHandle(-8,true)==null && drag.emergency;
        assert drag.setHandle(0,false).equals("reset_first") && drag.emergency;
        assert drag.setHandle(8,false).equals("invalid_notch");
        assert drag.press("reset",CabAction.RESET,0,false,true).equals("stop_first");
        assert drag.press("reset",CabAction.RESET,0,false,false)==null && drag.notch==0;
        var oldCab=new CabControlState();start(oldCab);oldCab.parkingBrake=true;
        oldCab.press("horn",CabAction.HORN,0,false,false);oldCab.notch=7;
        oldCab.switches.put(CabAction.DOOR_LEFT,1);oldCab.releaseForTakeover();
        assert !oldCab.on(CabAction.MASTER)&&oldCab.direction==0&&oldCab.notch==0;
        assert oldCab.signals().entrySet().stream().allMatch(e->e.getKey().equals("parking_brake")?e.getValue()==15:e.getValue()==0);
        oldCab.press("key",CabAction.MASTER,0,false,false);
        assert oldCab.signals().get("horn")==0&&!oldCab.on(CabAction.DOOR_LEFT)&&!oldCab.on(CabAction.PANTOGRAPH);
        System.out.println("Control state: interlocks, reset, pulse release, radio, signal ranges passed");
    }
}
