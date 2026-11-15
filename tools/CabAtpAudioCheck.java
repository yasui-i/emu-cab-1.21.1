import com.yasui_i.emucab.control.*;

public class CabAtpAudioCheck {
    public static void main(String[] args) {
        var audio=new AtpAudioController();
        assert audio.tick(true,0,350)==null;
        assert audio.tick(true,344,350)==null;
        assert audio.tick(true,345,350)==AtpAnnouncement.CONTROL_SPEED;
        for(int i=1;i<AtpAnnouncement.CONTROL_SPEED.ticks;i++)assert audio.tick(true,350,350)==null;
        assert audio.tick(true,350,350)==null; // near-limit announcement does not repeat
        assert audio.tick(true,353,350)==AtpAnnouncement.OVERSPEED;
        assert audio.simultaneous()==AtpAnnouncement.REDUCE_SPEED;
        assert !audio.request(AtpAnnouncement.REDUCE_SPEED); // already playing as the second recording
        int repeats=0;
        for(int i=1;i<=400;i++) {
            var cue=audio.tick(true,354,350);
            if(cue!=null) { assert cue==AtpAnnouncement.OVERSPEED && i%180==0;
                assert audio.simultaneous()==AtpAnnouncement.REDUCE_SPEED;repeats++; }
        }
        assert repeats==2;
        for(int i=0;i<300;i++)assert audio.tick(true,340,350)==null;
        assert audio.tick(true,345,350)==AtpAnnouncement.CONTROL_SPEED; // rearmed below limit-7
        audio.clear();
        assert audio.request(AtpAnnouncement.PLATFORM_LEFT);
        assert !audio.request(AtpAnnouncement.PLATFORM_LEFT);
        assert audio.request(AtpAnnouncement.NOTICE);
        assert audio.tick(true,355,350)==AtpAnnouncement.OVERSPEED; // highest priority first
        assert !audio.request(AtpAnnouncement.OVERSPEED);
        for(int i=1;i<AtpAnnouncement.OVERSPEED.ticks;i++)assert audio.tick(true,340,350)==null;
        assert audio.tick(true,340,350)==AtpAnnouncement.PLATFORM_LEFT;
        for(int i=1;i<AtpAnnouncement.PLATFORM_LEFT.ticks;i++)assert audio.tick(true,340,350)==null;
        assert audio.tick(true,340,350)==AtpAnnouncement.NOTICE;
        audio.clear();
        audio.request(AtpAnnouncement.NEUTRAL_SECTION_AHEAD);
        assert audio.tick(true,340,350)==AtpAnnouncement.NEUTRAL_SECTION_AHEAD;
        assert audio.tick(true,360,350)==null; // wait for existing voice
        for(int i=2;i<=AtpAnnouncement.NEUTRAL_SECTION_AHEAD.ticks;i++)assert audio.tick(true,340,350)==null;
        // A warning queued automatically is discarded if the condition disappears.
        audio.clear();
        audio.request(AtpAnnouncement.OVERSPEED);
        assert audio.tick(true,0,350)==AtpAnnouncement.OVERSPEED; // explicit preview survives safe speed
        audio.request(AtpAnnouncement.PLATFORM_RIGHT);
        assert audio.tick(false,360,350)==null;
        for(int i=2;i<AtpAnnouncement.OVERSPEED.ticks;i++)assert audio.tick(true,360,350)==null;
        assert audio.tick(true,360,350)==AtpAnnouncement.OVERSPEED; // no overlap on power cycling
        audio.clear();
        for(var cue:AtpAnnouncement.values()) {
            assert audio.request(cue);
            assert audio.tick(true,0,350)==cue:cue;
            assert audio.simultaneous()==(cue==AtpAnnouncement.OVERSPEED?AtpAnnouncement.REDUCE_SPEED:null);
            for(int i=1;i<cue.ticks;i++)assert audio.tick(true,0,350)==null;
            assert audio.tick(true,0,350)==null;
            assert audio.simultaneous()==null;
        }
        audio.clear();
        audio.request(AtpAnnouncement.REDUCE_SPEED);
        assert audio.tick(true,360,350,"lower-sign")==AtpAnnouncement.OVERSPEED;
        assert audio.simultaneous()==AtpAnnouncement.REDUCE_SPEED;
        for(int i=1;i<AtpAnnouncement.OVERSPEED.ticks;i++)assert audio.tick(true,340,350,"lower-sign")==null;
        assert audio.tick(true,340,350,"lower-sign")==null; // don't replay the same reduction after the pair
        assert audio.simultaneous()==null;
        assert audio.tick(true,Double.NaN,350)==null;
        assert audio.tick(true,Double.POSITIVE_INFINITY,350)==null;
        assert audio.tick(true,300,Double.NaN)==null;
        System.out.println("ATP audio: 15 cues, simultaneous overspeed/reduce group, duration gaps, priority, deduplication, warning hysteresis/repeat, stale warning removal and power-cycle isolation passed");
    }
}
