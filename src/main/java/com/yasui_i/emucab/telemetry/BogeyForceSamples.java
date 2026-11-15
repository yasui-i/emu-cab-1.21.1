package com.yasui_i.emucab.telemetry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Immutable snapshots are the only data shared with the physics thread. No BE references. */
public final class BogeyForceSamples {
    public record Sample(double traction,double brake,double slip,long nanos) {}
    private static final Map<UUID,Sample[]> SAMPLES=new ConcurrentHashMap<>();
    public static void record(UUID id,boolean front,double traction,double brake,double slip){
        if(!Double.isFinite(traction)||!Double.isFinite(brake)||!Double.isFinite(slip))return;
        long now=System.nanoTime();
        var sample=new Sample(Math.abs(traction),Math.abs(brake),Math.abs(slip),now);
        SAMPLES.compute(id,(key,old)->{
            Sample[] next=old==null?new Sample[2]:old.clone();next[front?0:1]=sample;return next;
        });
    }
    public static Sample read(UUID id){
        var pair=SAMPLES.get(id);if(pair==null)return null;
        long now=System.nanoTime();double t=0,b=0,s=0;int count=0;
        for(var v:pair)if(v!=null&&now-v.nanos()<1_000_000_000L){t+=v.traction();b+=v.brake();s=Math.max(s,v.slip());count++;}
        return count==2?new Sample(t,b,s,now):null;
    }
    public static void remove(UUID id){SAMPLES.remove(id);}
    public static void clear(){SAMPLES.clear();}
    private BogeyForceSamples(){}
}
