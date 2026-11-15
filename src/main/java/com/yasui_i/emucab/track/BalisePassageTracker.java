package com.yasui_i.emucab.track;

import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Swept, track-constrained reception: never fires just because a cab spawns nearby. */
public final class BalisePassageTracker {
    private RailNetwork.Projection previous;
    private Vec3 previousHeading=Vec3.ZERO;
    private String body="";
    private long previousTick;
    private final Map<String,Vec3> received=new HashMap<>();

    public List<RailNetwork.Hit> update(String identity,long tick,RailNetwork network,
                                        RailNetwork.Projection current,Vec3 heading,
                                        List<RailNetwork.Marker> markers,double maximumTravel) {
        List<RailNetwork.Hit> hits=List.of();
        if(!body.equals(identity))received.clear();
        // Keep a received device locked while a parked body rocks across it.
        // Release after moving two metres away so a later genuine pass works.
        if(current!=null)received.entrySet().removeIf(e->current.point().distanceToSqr(e.getValue())>4);
        if(previous!=null && current!=null && body.equals(identity) && tick>previousTick
                && tick-previousTick<=40 && heading.dot(previousHeading)>.25) {
            var passage=network.passage(previous,current,previousHeading,markers,maximumTravel);
            if(passage!=null){
                var accepted=new ArrayList<RailNetwork.Hit>();
                for(var hit:passage.hits())if(!received.containsKey(hit.marker().id())){
                    accepted.add(hit);var point=network.markerPoint(hit.marker());
                    received.put(hit.marker().id(),point==null?current.point():point);
                }
                hits=List.copyOf(accepted);
            }
        }
        previous=current;previousHeading=heading;body=identity;previousTick=tick;
        return hits;
    }
    public void reset(){previous=null;previousHeading=Vec3.ZERO;body="";received.clear();}
}
