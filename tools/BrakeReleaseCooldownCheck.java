import com.yasui_i.emucab.telemetry.BrakeReleaseState;

/** Pure regression check: the release recording cannot overlap itself on one bogey. */
public final class BrakeReleaseCooldownCheck {
    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
    private static boolean release(BrakeReleaseState state,int cooldown) {
        for(int i=0;i<3;i++)check(!state.update(.5f,cooldown),"Apply played release");
        check(!state.update(0,cooldown)&&!state.update(0,cooldown),"Missing release debounce");
        return state.update(0,cooldown);
    }
    public static void main(String[] args) {
        var state=new BrakeReleaseState();
        for(int i=0;i<200;i++)check(!state.update(0),"Released startup played");
        check(release(state,100),"First real release missing");
        // Repeated commands during the full recording may not retrigger it.
        for(int i=0;i<15;i++)check(!release(state,100),"Chatter overlapped recording");
        for(int i=0;i<120;i++)check(!state.update(0),"Suppressed edge was queued");
        check(release(state,100),"Deliberate later release missing");

        var floor=new BrakeReleaseState();check(release(floor,1),"First floor release missing");
        for(int i=0;i<10;i++)check(!release(floor,1),"Configured interval bypassed recording floor");

        var custom=new BrakeReleaseState();check(release(custom,200),"Custom first release missing");
        for(int i=0;i<110;i++)check(!custom.update(0,200),"Continuous release repeated");
        check(!release(custom,200),"Custom long cooldown ignored");
        for(int i=0;i<100;i++)check(!custom.update(0,200),"Long cooldown queued a release");
        check(release(custom,200),"Custom cooldown never reopened");
        System.out.println("PASS brake release: startup silent; 5-second overlap floor; chatter dropped, not queued; custom 10-second interval; later deliberate release");
    }
}
