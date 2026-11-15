import com.yasui_i.emucab.control.*;

public class CabControlAnimationStateCheck {
    public static void main(String[] args) {
        var animation=new CabControlAnimation();
        animation.press("door",1,100);
        near(animation.pressAmount("door",100),0);
        near(animation.pressAmount("door",100.8),.5);
        near(animation.pressAmount("door",102),1);
        near(animation.pressAmount("door",105.2),.5);
        near(animation.pressAmount("door",107),0);
        animation.press("other",2,101);
        near(animation.pressAmount("door",102),1);
        near(animation.pressAmount("other",101.8),.5);
        animation.press("door",3,105.2);
        near(animation.pressAmount("door",105.2),.5); // A rapid repeat does not pop up before pressing again.
        near(animation.pressAmount("door",106),.75);
        animation.press("door",3,106); // Duplicate packet must not restart motion.
        animation.press("door",2,106); // A stale event must not restart another control either.
        near(animation.pressAmount("door",106.8),1);
        near(animation.pressAmount("absent",106),0);

        var state=new CabControlState();
        var master=control(CabAction.MASTER);
        near(CabControlAnimation.rotationTargetDegrees(master,state),0);
        state.switches.put(CabAction.MASTER,1);
        near(CabControlAnimation.rotationTargetDegrees(master,state),90);
        near(animation.rotationDegrees(master,state,200),90); // Loading a saved knob position does not replay it.
        state.switches.put(CabAction.MASTER,0);
        var signals=state.signals();
        near(animation.rotationDegrees(master,state,201),90);
        near(animation.rotationDegrees(master,state,203),45);
        // More/fewer render frames must sample the same game-time motion.
        animation.rotationDegrees(master,state,203.4);
        near(animation.rotationDegrees(master,state,203),45);
        near(animation.rotationDegrees(master,state,205),0);
        assert state.signals().equals(signals):"Visual animation changed output signals";

        var direction=control(CabAction.DIRECTION);
        for(int value=-1;value<=1;value++) {
            state.direction=value;
            near(CabControlAnimation.rotationTargetDegrees(direction,state),value*45);
        }
        var wiper=control(CabAction.WIPER);
        for(int value=0;value<3;value++) {
            state.switches.put(CabAction.WIPER,value);
            near(CabControlAnimation.rotationTargetDegrees(wiper,state),value*45);
        }
        for(var action:new CabAction[]{CabAction.PANTOGRAPH,CabAction.BREAKER}) {
            var knob=control(action);near(CabControlAnimation.rotationTargetDegrees(knob,state),0);
            state.switches.put(action,1);near(CabControlAnimation.rotationTargetDegrees(knob,state),45);
        }
        near(animation.pressAmount("door",Double.NaN),0);
        animation.press("door",5,Double.NaN);
        var newlyJoined=new CabControlAnimation();near(newlyJoined.pressAmount("door",302),0);
        var horn=control(CabAction.HORN);
        animation.press(horn.id(),4,300);
        near(animation.rotationDegrees(horn,state,302),45);
        near(animation.rotationDegrees(horn,state,307),0);
        System.out.println("Control animation: independent/repeated presses, duplicate rejection, time-based easing, saved detents, rotary spring return and signal isolation passed");
    }
    private static CabGeometry.Control control(CabAction action) {
        return new CabGeometry.Control(action.name(),"test",action,0,false,0,0,0,1,1,1);
    }
    private static void near(double actual,double expected) {
        assert Math.abs(actual-expected)<1e-7:actual+" != "+expected;
    }
}
