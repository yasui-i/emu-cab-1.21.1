package com.yasui_i.emucab.control;

import java.util.EnumSet;

/** One announcement group per desk; overspeed intentionally layers the reduce-speed recording. */
public final class AtpAudioController {
    private final EnumSet<AtpAnnouncement> pending=EnumSet.noneOf(AtpAnnouncement.class);
    private final EnumSet<AtpAnnouncement> automatic=EnumSet.noneOf(AtpAnnouncement.class);
    private AtpAnnouncement playing;
    private int remaining,repeat;
    private boolean overspeed,nearLimit;
    private String reductionKey="";

    public boolean request(AtpAnnouncement cue) {
        if(cue==null || cue==playing || cue==simultaneous())return false;
        return pending.add(cue);
    }
    /** Emitted alongside the cue returned by tick(), using the same moving sound origin. */
    public AtpAnnouncement simultaneous() { return playing==AtpAnnouncement.OVERSPEED?AtpAnnouncement.REDUCE_SPEED:null; }
    public void clear() {
        pending.clear();automatic.clear();playing=null;remaining=repeat=0;overspeed=nearLimit=false;reductionKey="";
    }
    public AtpAnnouncement tick(boolean enabled,double speed,double limit) {
        return tick(enabled,speed,limit,"");
    }
    public AtpAnnouncement tick(boolean enabled,double speed,double limit,String upcomingReduction) {
        if(remaining>0 && --remaining==0)playing=null;
        if(!enabled) {
            // Already emitted recordings finish naturally. Retain their remaining
            // duration across a quick power cycle so another voice cannot overlap.
            pending.clear();automatic.clear();repeat=0;overspeed=nearLimit=false;reductionKey="";
            return null;
        }
        if(repeat>0)repeat--;
        if(upcomingReduction.isEmpty()) { automatic.remove(AtpAnnouncement.REDUCE_SPEED);reductionKey=""; }
        else if(simultaneous()==AtpAnnouncement.REDUCE_SPEED) {
            reductionKey=upcomingReduction;automatic.remove(AtpAnnouncement.REDUCE_SPEED);
        }
        else if(!upcomingReduction.equals(reductionKey) && playing!=AtpAnnouncement.REDUCE_SPEED && simultaneous()!=AtpAnnouncement.REDUCE_SPEED) {
            automatic.add(AtpAnnouncement.REDUCE_SPEED);reductionKey=upcomingReduction;
        }
        if(Double.isFinite(speed) && Double.isFinite(limit) && speed>=0 && limit>0) {
            // Game warning thresholds; these are not a CTCS braking curve.
            boolean over=speed>limit+2 || (overspeed && speed>limit);
            boolean near=speed>1 && speed>=limit-5 && !over;
            if(over) {
                automatic.remove(AtpAnnouncement.CONTROL_SPEED);
                if((!overspeed || repeat==0) && playing!=AtpAnnouncement.OVERSPEED)automatic.add(AtpAnnouncement.OVERSPEED);
            } else automatic.remove(AtpAnnouncement.OVERSPEED);
            if(near && !nearLimit && !overspeed && playing!=AtpAnnouncement.CONTROL_SPEED)automatic.add(AtpAnnouncement.CONTROL_SPEED);
            if(speed<limit-7)nearLimit=false;
            else if(near)nearLimit=true;
            overspeed=over;
            if(!near)automatic.remove(AtpAnnouncement.CONTROL_SPEED);
        } else {
            automatic.clear();
            overspeed=nearLimit=false;
        }
        if(playing!=null)return null;
        AtpAnnouncement next=null;
        for(var cue:pending)if(next==null || cue.priority>next.priority)next=cue;
        for(var cue:automatic)if(next==null || cue.priority>next.priority)next=cue;
        if(next!=null) {
            pending.remove(next);automatic.remove(next);playing=next;remaining=next.ticks;
            if(next==AtpAnnouncement.OVERSPEED) {
                repeat=180;
                // This recording is part of the same simultaneous group, not another queued voice.
                pending.remove(AtpAnnouncement.REDUCE_SPEED);automatic.remove(AtpAnnouncement.REDUCE_SPEED);
                remaining=Math.max(remaining,AtpAnnouncement.REDUCE_SPEED.ticks);
                if(!upcomingReduction.isEmpty())reductionKey=upcomingReduction;
            }
        }
        return next;
    }
}
