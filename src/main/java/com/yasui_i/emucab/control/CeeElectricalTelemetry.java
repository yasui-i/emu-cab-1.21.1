package com.yasui_i.emucab.control;

import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.network.CabElectricalPayload;
import com.yasui_i.emucab.network.CabAtpSoundPayload;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;
import java.lang.reflect.Method;

/** Only explicitly registered 03/06 receivers contribute. No proximity or nearest-wire guesses. */
public final class CeeElectricalTelemetry {
    public record Reading(boolean online,boolean raised,boolean contact,boolean breaker,double voltage,double current){
        public static final Reading EMPTY=new Reading(false,false,false,false,0,0);
        public Reading { voltage=Double.isFinite(voltage)?Math.max(0,voltage):0;current=Double.isFinite(current)?Math.abs(current):0; }
        public static Reading decode(double[] a){return a!=null&&a.length>=6?new Reading(a[0]==1,a[1]==1,a[2]==1,a[3]==1,a[4],a[5]):EMPTY;}
    }
    private record Pair(Reading car03,Reading car06,long tick,long sequence){}
    // An integrated game has both logical threads; each level's entries stay on its owner thread.
    private static final Map<Level,Map<net.minecraft.core.BlockPos,Pair>> READINGS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerLevel,Set<CabDeskBlockEntity>> DESKS=new WeakHashMap<>();
    private static Method sample,setBreaker; private static boolean initialized,failed;
    public static void init(){
        NeoForge.EVENT_BUS.addListener(CeeElectricalTelemetry::tick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload e)->{
            READINGS.remove(e.getLevel());
            if(e.getLevel() instanceof ServerLevel level)DESKS.remove(level);
        });
    }
    private static boolean available(){
        if(!initialized){initialized=true;
            if(!ModList.get().isLoaded("pwsablefix"))return false;
            try{Class<?> type=Class.forName("com.yasui_i.pwsablefix.compat.PWSableElectricalBridge");
                sample=type.getMethod("sample",Level.class,String.class,int.class);
                setBreaker=type.getMethod("setBreaker",Level.class,String.class,int.class,boolean.class);
            }catch(ReflectiveOperationException e){failed=true;com.yasui_i.emucab.EMUCab.LOGGER.warn("P&W addon does not expose electrical telemetry",e);}}
        return sample!=null&&!failed;
    }
    public static void register(CabDeskBlockEntity desk){if(desk.isCenter()&&desk.getLevel() instanceof ServerLevel l)DESKS.computeIfAbsent(l,k->Collections.newSetFromMap(new WeakHashMap<>())).add(desk);}
    public static void unregister(CabDeskBlockEntity desk){
        Level level=desk.getLevel();
        if(level instanceof ServerLevel l&&DESKS.containsKey(l))DESKS.get(l).remove(desk);
        var readings=READINGS.get(level);if(readings!=null)readings.remove(desk.getBlockPos());
    }
    public static void clear(){DESKS.clear();READINGS.clear();}
    public static boolean isReceiver(BlockEntity be){return be!=null&&be.getClass().getName().equals("de.mrjulsen.paw.blockentity.PantographBlockEntity")&&ModList.get().isLoaded("pwsablefix");}
    public static boolean bind(BlockEntity be,String consist,int car){
        if(!isReceiver(be)||consist.isBlank()||(car!=3&&car!=6&&car!=0))return false;
        try{be.getClass().getMethod("pwsablefix$configure",String.class,int.class).invoke(be,consist,car);return true;}
        catch(ReflectiveOperationException e){com.yasui_i.emucab.EMUCab.LOGGER.warn("Unable to register P&W receiver",e);return false;}
    }
    private static void tick(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel level)||level.getGameTime()%5!=0)return;
        var desks=Set.copyOf(DESKS.getOrDefault(level,Set.of()));
        if(available())try{
            Map<String,Integer> closedCars=new HashMap<>();
            for(var desk:desks)if(!desk.isRemoved()&&desk.isCenter()&&!desk.consistId().isBlank()){
                closedCars.putIfAbsent(desk.consistId(),0);
                if(desk.controls().on(CabAction.MASTER)&&desk.controls().on(CabAction.BREAKER))closedCars.put(desk.consistId(),desk.commandedPantographCar());
            }
            for(var entry:closedCars.entrySet()){
                setBreaker.invoke(null,level,entry.getKey(),3,entry.getValue()==3);
                setBreaker.invoke(null,level,entry.getKey(),6,entry.getValue()==6);
            }
        }catch(ReflectiveOperationException e){failed=true;com.yasui_i.emucab.EMUCab.LOGGER.error("P&W breaker update failed",e);}
        for(var desk:desks){
            if(desk.isRemoved()||!desk.isCenter())continue;
            Reading a=Reading.EMPTY,b=Reading.EMPTY;
            if(available()&&!desk.consistId().isBlank())try{
                a=Reading.decode((double[])sample.invoke(null,level,desk.consistId(),3));
                b=Reading.decode((double[])sample.invoke(null,level,desk.consistId(),6));
            }catch(ReflectiveOperationException e){failed=true;com.yasui_i.emucab.EMUCab.LOGGER.error("P&W electrical sampling failed",e);}
            accept(desk,level.getGameTime(),a,b);
            var position=CabAtpSoundPayload.worldPosition(level,desk.getBlockPos());
            var packet=new CabElectricalPayload(desk.getBlockPos(),level.getGameTime(),a,b);
            for(var player:level.players())if(player.distanceToSqr(position.x,position.y,position.z)<128*128)PacketDistributor.sendToPlayer(player,packet);
        }
    }
    public static void accept(CabDeskBlockEntity desk,long sequence,Reading a,Reading b){
        Level level=desk.getLevel();if(level==null)return;var map=READINGS.computeIfAbsent(level,k->new HashMap<>());var old=map.get(desk.getBlockPos());
        if(old==null||sequence>=old.sequence)map.put(desk.getBlockPos(),new Pair(a,b,level.getGameTime(),sequence));
    }
    public static Reading reading(CabDeskBlockEntity desk,int car){
        Level level=desk.getLevel();if(level==null)return Reading.EMPTY;var p=READINGS.getOrDefault(level,Map.of()).get(desk.getBlockPos());
        return p==null||level.getGameTime()-p.tick>40?Reading.EMPTY:car==3?p.car03:p.car06;
    }
    public static double voltage(CabDeskBlockEntity desk){return Math.max(reading(desk,3).voltage,reading(desk,6).voltage);}
    public static double current(CabDeskBlockEntity desk){return reading(desk,3).current+reading(desk,6).current;}
    private CeeElectricalTelemetry(){}
}
