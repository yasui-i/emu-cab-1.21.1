package com.yasui_i.emucab.control;

import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Server-side cab ownership survives chunk unloads and Sable plot relocation. */
public final class CabEndpointRegistry extends SavedData {
    private static final Map<ServerLevel,CabEndpointRegistry> LOADED=new WeakHashMap<>();
    private final Map<String,UUID> owners=new HashMap<>();
    private final Map<String,Integer> isolations=new HashMap<>();
    private final Set<CabDeskBlockEntity> desks=Collections.newSetFromMap(new WeakHashMap<>());
    private CabEndpointRegistry() {}
    private static CabEndpointRegistry get(ServerLevel level){
        var store=level.getDataStorage().computeIfAbsent(new Factory<>(CabEndpointRegistry::new,CabEndpointRegistry::load),"emucab_cab_ownership");
        LOADED.put(level,store);return store;
    }
    public static void register(CabDeskBlockEntity desk){
        if(!desk.isCenter()||!(desk.getLevel() instanceof ServerLevel level))return;
        var store=get(level);
        if(store.desks.stream().anyMatch(other->other!=desk&&!other.isRemoved()&&!other.movingForAssembly()
                &&other.linkId().equals(desk.linkId())))desk.renewLinkId();
        store.desks.add(desk);
        if(configured(desk)){
            int bits=store.isolations.computeIfAbsent(desk.consistId(),id->{store.setDirty();return (desk.pantographs().cutFront?1:0)|(desk.pantographs().cutRear?2:0);});
            desk.applySharedPantographIsolation((bits&1)!=0,(bits&2)!=0);
        }
    }
    public static void unregister(CabDeskBlockEntity desk){
        if(desk.getLevel() instanceof ServerLevel level){var store=LOADED.get(level);if(store!=null)store.desks.remove(desk);}
    }
    public static boolean configured(CabDeskBlockEntity desk){return !desk.consistId().isBlank()&&(desk.endpoint()==1||desk.endpoint()==8);}
    /** Last explicit key insertion wins. All other loaded cabs in the group are released immediately. */
    public static void activate(CabDeskBlockEntity active){
        if(!(active.getLevel() instanceof ServerLevel level)||!configured(active))return;
        register(active);var store=get(level);
        store.owners.put(active.consistId(),active.linkId());store.setDirty();
        for(var desk:List.copyOf(store.desks))if(desk!=active&&!desk.isRemoved()&&configured(desk)&&desk.consistId().equals(active.consistId()))desk.forceTakeoverRelease();
    }
    /** Called before publishing controls, also catches an inactive/unloaded cab returning after takeover. */
    public static void reconcile(CabDeskBlockEntity desk){
        if(!(desk.getLevel() instanceof ServerLevel level)||!desk.isCenter())return;
        register(desk);var store=get(level);
        if(!configured(desk)||!desk.controls().on(CabAction.MASTER))return;
        UUID owner=store.owners.get(desk.consistId());
        if(owner==null){activate(desk);return;}
        if(!owner.equals(desk.linkId()))desk.forceTakeoverRelease();
    }
    public static void release(CabDeskBlockEntity desk){
        if(!(desk.getLevel() instanceof ServerLevel level)||!configured(desk))return;
        var store=get(level);
        // Keep the most recent winner as a tombstone. Otherwise an old cab
        // saved/unloaded with its key inserted could become owner again after
        // the replacement cab removes its key. Explicit insertion always calls
        // activate() and is allowed to replace this remembered winner.
        if(!store.owners.containsKey(desk.consistId())){
            store.owners.put(desk.consistId(),desk.linkId());store.setDirty();
        }
    }
    public static void clear(){LOADED.clear();}
    public static void configureIsolation(CabDeskBlockEntity source,boolean car03,boolean car06){
        if(!(source.getLevel() instanceof ServerLevel level)||!configured(source)){
            source.applySharedPantographIsolation(car03,car06);return;
        }
        var store=get(level);store.isolations.put(source.consistId(),(car03?1:0)|(car06?2:0));store.setDirty();
        for(var desk:List.copyOf(store.desks))if(!desk.isRemoved()&&configured(desk)&&source.consistId().equals(desk.consistId()))
            desk.applySharedPantographIsolation(car03,car06);
    }
    private static CabEndpointRegistry load(CompoundTag tag,HolderLookup.Provider registries){
        var result=new CabEndpointRegistry();
        for(var value:tag.getList("Owners",Tag.TAG_COMPOUND)){
            var entry=(CompoundTag)value;String id=CabDisplayFormat.locomotive(entry.getString("Consist"));
            if(!id.isBlank()&&entry.hasUUID("Cab"))result.owners.put(id,entry.getUUID("Cab"));
        }
        for(var value:tag.getList("Isolations",Tag.TAG_COMPOUND)){
            var entry=(CompoundTag)value;String id=CabDisplayFormat.locomotive(entry.getString("Consist"));
            if(!id.isBlank())result.isolations.put(id,entry.getInt("Cut")&3);
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        var entries=new ListTag();owners.forEach((id,cab)->{var entry=new CompoundTag();entry.putString("Consist",id);entry.putUUID("Cab",cab);entries.add(entry);});
        tag.put("Owners",entries);
        var cuts=new ListTag();isolations.forEach((id,bits)->{var entry=new CompoundTag();entry.putString("Consist",id);entry.putInt("Cut",bits);cuts.add(entry);});
        tag.put("Isolations",cuts);return tag;
    }
}
