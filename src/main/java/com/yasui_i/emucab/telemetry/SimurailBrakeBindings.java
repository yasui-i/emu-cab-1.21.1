package com.yasui_i.emucab.telemetry;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.config.CabConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Durable membership survives an unloaded cab. Physics reads only an immutable UUID snapshot. */
@EventBusSubscriber(modid=EMUCab.MOD_ID)
public final class SimurailBrakeBindings extends SavedData {
    private static final Map<Level,SimurailBrakeBindings> LOADED=new ConcurrentHashMap<>();
    private final Map<UUID,Set<UUID>> cabs=new HashMap<>();
    private volatile Set<UUID> registered=Set.of();

    public static SimurailBrakeBindings get(ServerLevel level){
        var store=level.getDataStorage().computeIfAbsent(new Factory<>(SimurailBrakeBindings::new,SimurailBrakeBindings::load),"emucab_brake_bindings");
        LOADED.put(level,store);return store;
    }
    public static void update(CabDeskBlockEntity desk){
        if(desk.isCenter()&&desk.getLevel() instanceof ServerLevel server)get(server).put(desk.telemetryId(),desk.telemetry().bindings().stream().map(CabTelemetry.Binding::id).toList());
    }
    public static void remove(CabDeskBlockEntity desk){
        if(desk.isCenter()&&desk.getLevel() instanceof ServerLevel server)get(server).put(desk.telemetryId(),List.of());
    }
    public static double multiplier(Level level,UUID bogey){
        var store=LOADED.get(level);
        return store!=null&&store.registered.contains(bogey)?CabConfig.SIMURAIL_BRAKE_MULTIPLIER.get():1;
    }
    /** Immutable, deduplicated membership shared by force scaling and release audio. */
    public static Set<UUID> registered(Level level){
        var store=LOADED.get(level);return store==null?Set.of():store.registered;
    }
    private void put(UUID cab,Collection<UUID> bogeys){
        var ids=Set.copyOf(bogeys);
        if(ids.isEmpty()){if(cabs.remove(cab)==null)return;}
        else if(ids.equals(cabs.put(cab,ids)))return;
        snapshot();setDirty();
    }
    private void snapshot(){var all=new HashSet<UUID>();cabs.values().forEach(all::addAll);registered=Set.copyOf(all);}
    private static SimurailBrakeBindings load(CompoundTag tag,HolderLookup.Provider registries){
        var result=new SimurailBrakeBindings();
        for(var entry:tag.getList("Cabs",Tag.TAG_COMPOUND)){
            var cab=(CompoundTag)entry;if(!cab.hasUUID("Cab"))continue;
            Set<UUID> ids=new HashSet<>();
            for(var value:cab.getList("Bogeys",Tag.TAG_COMPOUND)){var t=(CompoundTag)value;if(t.hasUUID("Id")&&ids.size()<32)ids.add(t.getUUID("Id"));}
            if(!ids.isEmpty())result.cabs.put(cab.getUUID("Cab"),Set.copyOf(ids));
        }
        result.snapshot();return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        var list=new ListTag();cabs.forEach((id,bogeys)->{
            var cab=new CompoundTag();cab.putUUID("Cab",id);var ids=new ListTag();
            bogeys.forEach(key->{var t=new CompoundTag();t.putUUID("Id",key);ids.add(t);});cab.put("Bogeys",ids);list.add(cab);
        });tag.put("Cabs",list);return tag;
    }
    @SubscribeEvent public static void levelLoaded(LevelEvent.Load event){if(event.getLevel() instanceof ServerLevel server)get(server);}
    @SubscribeEvent public static void levelUnloaded(LevelEvent.Unload event){LOADED.remove(event.getLevel());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){LOADED.clear();}
}
