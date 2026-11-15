package com.yasui_i.emucab.telemetry;

import net.minecraft.nbt.*;
import java.util.*;

/** Saved registration is separate from ephemeral, read-only vehicle feedback. */
public final class CabTelemetry {
    public record Binding(UUID id,int car){}
    public record Car(int bound,int loaded,int measured,double traction,double brake,float localCommand,float effectiveCommand,boolean fault,boolean slip) {
        public static final Car EMPTY=new Car(0,0,0,0,0,0,0,false,false);
        public boolean complete(){return bound>0&&loaded==bound&&measured==bound;}
    }
    private final List<Binding> bindings=new ArrayList<>();
    private Car[] cars=empty();private long sequence=-1,lastClientTick=-100;
    private final double[] shownTraction=new double[8],shownBrake=new double[8];
    public static Car[] empty(){Car[] values=new Car[8];Arrays.fill(values,Car.EMPTY);return values;}
    public List<Binding> bindings(){return List.copyOf(bindings);}
    public int registeredCount(int car){return (int)bindings.stream().filter(b->b.car()==car).count();}
    public boolean canBind(UUID id){return bindings.size()<32||bindings.stream().anyMatch(b->b.id().equals(id));}
    public boolean bind(UUID id,int car){
        if(car<1||car>8)return false;
        if(!canBind(id))return false;
        bindings.removeIf(b->b.id().equals(id));
        bindings.add(new Binding(id,car));return true;
    }
    public void clearCar(int car){bindings.removeIf(b->b.car()==car);}
    public Car car(int car){return cars[car-1];}
    public double shownTraction(int car){return shownTraction[car-1];}
    public double shownBrake(int car){return shownBrake[car-1];}
    public void serverCars(Car[] values){cars=values.clone();}
    public boolean accept(long seq,long tick,Car[] values){if(seq<=sequence||values.length!=8)return false;sequence=seq;lastClientTick=tick;cars=values.clone();return true;}
    public void expire(long tick){
        if(tick-lastClientTick>30)cars=empty();
        for(int i=0;i<8;i++){
            if(!cars[i].complete()){shownTraction[i]=0;shownBrake[i]=0;continue;}
            shownTraction[i]+=(cars[i].traction()-shownTraction[i])*.3;
            shownBrake[i]+=(cars[i].brake()-shownBrake[i])*.3;
        }
    }
    public void save(CompoundTag tag){
        ListTag list=new ListTag();for(var b:bindings){CompoundTag t=new CompoundTag();t.putUUID("Id",b.id());t.putInt("Car",b.car());list.add(t);}tag.put("TelemetryBogeys",list);
    }
    public void load(CompoundTag tag){
        bindings.clear();for(var entry:tag.getList("TelemetryBogeys",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)entry;if(t.hasUUID("Id")&&bindings.size()<32)bind(t.getUUID("Id"),t.getInt("Car"));
        }
    }
}
