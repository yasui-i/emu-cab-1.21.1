package com.yasui_i.emucab.track;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Distance-based advisory envelope; never commands train braking. */
public record CabSpeedPlan(boolean available,double range,double deceleration,List<Target> targets) {
    public record Target(double distance,double limit) {}
    public static final CabSpeedPlan EMPTY=new CabSpeedPlan(false,1024,.7,List.of());
    public CabSpeedPlan {
        range=Double.isFinite(range)?Math.clamp(range,16,8192):1024;
        deceleration=Double.isFinite(deceleration)?Math.clamp(deceleration,.1,3):.7;
        final double maxRange=range;
        targets=targets.stream().filter(t->Double.isFinite(t.distance)&&t.distance>=0&&t.distance<=maxRange
                &&Double.isFinite(t.limit)&&t.limit>0&&t.limit<=1000)
                .sorted(java.util.Comparator.comparingDouble(Target::distance)).limit(32).toList();
    }
    public double speedAt(double currentLimit,double distance) {
        if(!Double.isFinite(currentLimit)||currentLimit<=0)return 0;
        double x=Double.isFinite(distance)?Math.clamp(distance,0,range):0;
        double ceiling=currentLimit;
        for(var t:targets)if(t.distance<=x)ceiling=t.limit;
        for(var t:targets)if(t.distance>x) {
            double v=t.limit/3.6;
            ceiling=Math.min(ceiling,Math.sqrt(v*v+2*deceleration*(t.distance-x))*3.6);
        }
        return ceiling;
    }
    public CompoundTag save() {
        var tag=new CompoundTag();tag.putBoolean("available",available);
        tag.putDouble("range",range);tag.putDouble("deceleration",deceleration);
        var list=new ListTag();
        for(var t:targets){var item=new CompoundTag();item.putDouble("distance",t.distance);item.putDouble("limit",t.limit);list.add(item);}
        tag.put("targets",list);return tag;
    }
    public static CabSpeedPlan load(CompoundTag tag) {
        var list=new java.util.ArrayList<Target>();
        var input=tag.getList("targets",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(input.size(),32);i++){
            var t=input.getCompound(i);list.add(new Target(t.getDouble("distance"),t.getDouble("limit")));
        }
        return new CabSpeedPlan(tag.getBoolean("available"),tag.getDouble("range"),tag.getDouble("deceleration"),list);
    }
}
