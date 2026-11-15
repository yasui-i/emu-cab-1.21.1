package com.yasui_i.emucab.track;

import com.yasui_i.emucab.blockentity.SpeedSignBlockEntity;
import com.yasui_i.emucab.balise.BaliseData;
import com.yasui_i.emucab.balise.BaliseStore;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import java.util.*;

/** Per-world cache; only loaded signs are retained, never force-load route chunks. */
public final class SpeedSignRegistry {
    private static final Map<ServerLevel,Cache> WORLDS=new WeakHashMap<>();
    private static final class Cache {
        final Set<SpeedSignBlockEntity> signs=Collections.newSetFromMap(new WeakHashMap<>());
        RailNetwork network;
        long geometryTick=Long.MIN_VALUE,markersTick=Long.MIN_VALUE;
        List<RailNetwork.Marker> markers=List.of();
        List<RailNetwork.Marker> announcements=List.of();
        long baliseRevision=-1;
    }
    private static Cache cache(ServerLevel level) { return WORLDS.computeIfAbsent(level,k->new Cache()); }
    public static void register(SpeedSignBlockEntity sign) {
        if(sign.getLevel() instanceof ServerLevel level) { var c=cache(level);c.signs.add(sign);c.markersTick=Long.MIN_VALUE; }
    }
    public static void remove(SpeedSignBlockEntity sign) {
        if(sign.getLevel() instanceof ServerLevel level) { var c=cache(level);c.signs.remove(sign);c.markersTick=Long.MIN_VALUE; }
    }
    public static void invalidate(ServerLevel level){cache(level).markersTick=Long.MIN_VALUE;}
    public static RailNetwork network(ServerLevel level) {
        if(!ModList.get().isLoaded("create"))return null;
        var c=cache(level);long now=level.getGameTime();
        if(c.network==null || now-c.geometryTick>=100) {
            c.network=CreateRailGeometry.build(level);c.geometryTick=now;c.markersTick=Long.MIN_VALUE;
        }
        return c.network;
    }
    public static List<RailNetwork.Marker> markers(ServerLevel level) {
        var network=network(level);if(network==null)return List.of();
        var c=cache(level);long now=level.getGameTime();
        var store=BaliseStore.get(level);
        if(c.markersTick==Long.MIN_VALUE || now-c.markersTick>=20 || c.baliseRevision!=store.revision()) {
            var list=new ArrayList<RailNetwork.Marker>();
            var announcements=new ArrayList<RailNetwork.Marker>();
            for(var sign:c.signs) {
                if(sign.isRemoved() || !sign.bound() || !level.hasChunkAt(sign.getBlockPos()))continue;
                var projection=network.project(sign.anchor(),sign.forward(),.8,1,1);
                if(projection!=null)list.add(new RailNetwork.Marker(sign.getBlockPos().asLong()+"",projection.edge().id,
                        projection.distance(),sign.forward(),sign.limit()));
            }
            for(var device:store.loaded(level)) {
                if(!device.enabled() || !device.target().stillValid(level))continue;
                var projection=network.project(device.target().anchor(),device.target().tangent(),.8,1,1);
                if(projection==null)continue;
                var marker=new RailNetwork.Marker(device.id().toString(),projection.edge().id,projection.distance(),
                        device.kind()==BaliseData.Kind.LIMIT?device.forward():net.minecraft.world.phys.Vec3.ZERO,
                        device.kind()==BaliseData.Kind.LIMIT?device.limit():0);
                if(device.kind()==BaliseData.Kind.LIMIT)list.add(marker);else announcements.add(marker);
            }
            c.markers=List.copyOf(list);c.announcements=List.copyOf(announcements);c.markersTick=now;c.baliseRevision=store.revision();
        }
        return c.markers;
    }
    public static List<RailNetwork.Marker> announcementMarkers(ServerLevel level){markers(level);return cache(level).announcements;}
    private SpeedSignRegistry() {}
}
