package com.yasui_i.emucab.track;

import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.config.CabConfig;
import com.yasui_i.emucab.control.AtpAnnouncement;
import com.yasui_i.emucab.balise.BaliseStore;
import com.yasui_i.emucab.balise.BaliseData;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Track projection uses the moving Sable world pose, never plot coordinates. */
public final class CabTrackLimits {
    public record Result(double current,double next,double distance,String warningKey,CabSpeedPlan plan) {}
    private Vec3 lastHeading=Vec3.ZERO;
    private double current=-1;
    private String directionKey="";
    private final BalisePassageTracker passages=new BalisePassageTracker();
    private static final Map<ServerLevel,Map<UUID,Receiver>> RECEIVERS=new WeakHashMap<>();
    private static final Map<ServerLevel,Long> RECEIVER_CLEANUP=new WeakHashMap<>();
    private record Receiver(CabDeskBlockEntity desk,long tick) {}

    private static boolean ownsReceiver(ServerLevel level,UUID body,CabDeskBlockEntity desk) {
        long now=level.getGameTime();var receivers=RECEIVERS.computeIfAbsent(level,k->new HashMap<>());
        long cleaned=RECEIVER_CLEANUP.getOrDefault(level,Long.MIN_VALUE);
        if(cleaned==Long.MIN_VALUE||now-cleaned>=200||now<cleaned){
            receivers.entrySet().removeIf(e->now-e.getValue().tick()>40||e.getValue().desk().isRemoved());
            RECEIVER_CLEANUP.put(level,now);
        }
        var owner=receivers.get(body);
        if(owner==null||owner.desk().isRemoved()||!owner.desk().controls().displaysActive()
                ||now-owner.tick()>20||owner.desk()==desk){receivers.put(body,new Receiver(desk,now));return true;}
        return false;
    }

    public Result update(CabDeskBlockEntity desk) {
        if(!(desk.getLevel() instanceof ServerLevel level) || !CabConfig.TRACK_LIMITS.get())return unavailable();
        var sub=SableCompanion.INSTANCE.getContaining(level,desk.getBlockPos());
        if(sub==null)return unavailable(); // fixed desks must not acquire a line by proximity
        var network=SpeedSignRegistry.network(level);if(network==null)return unavailable();
        Vec3 position=sub.logicalPose().transformPosition(desk.getBlockPos().getCenter());
        Vec3 velocity=SableCompanion.INSTANCE.getVelocity(level,desk.getBlockPos().getCenter());
        Direction forward=desk.getBlockState().getValue(CabDeskBlock.FACING).getOpposite();
        Vec3 heading=sub.logicalPose().transformNormal(new Vec3(forward.getStepX(),0,forward.getStepZ())).normalize();
        if(velocity.horizontalDistanceSqr()>.04) {
            if(heading.dot(velocity)<0)heading=heading.scale(-1);
        } else if(desk.controls().direction<0)heading=heading.scale(-1);
        String key=level.dimension().location()+":"+sub.getUniqueId();
        if((!directionKey.isEmpty() && !directionKey.equals(key))
                || (lastHeading.lengthSqr()>.5 && lastHeading.dot(heading)<.25))current=-1;
        directionKey=key;lastHeading=heading;
        var projection=network.project(position,heading,CabConfig.TRACK_LATERAL.get(),CabConfig.TRACK_BELOW.get(),1.0);
        if(projection==null) { current=-1;passages.reset();return new Result(-1,-1,Double.POSITIVE_INFINITY,"",CabSpeedPlan.EMPTY); }
        // One active cab per Sable body receives voice events. Inactive desks
        // still read limits but cannot consume a crossing before the driver.
        if(desk.controls().displaysActive() && ownsReceiver(level,sub.getUniqueId(),desk)) {
            var voices=SpeedSignRegistry.announcementMarkers(level);
            double maxTravel=Math.max(8,velocity.length()*.25*2+8);
            for(var hit:passages.update(key,level.getGameTime(),network,projection,heading,voices,maxTravel)) {
                BaliseData device;
                try{device=BaliseStore.get(level).get(UUID.fromString(hit.marker().id()));}catch(IllegalArgumentException e){continue;}
                if(device==null||!device.enabled()||device.kind()!=BaliseData.Kind.ANNOUNCEMENT)continue;
                for(var cue:AtpAnnouncement.values())if(cue.id().equals(device.cue()))desk.requestAtpAnnouncement(cue);
            }
        }else passages.reset();
        var markers=SpeedSignRegistry.markers(level);
        var behind=network.scan(projection,heading,markers,CabConfig.TRACK_LOOKAHEAD.get(),false);
        // Only signs facing this direction count; the nearest passed sign governs.
        if(!behind.isEmpty())current=behind.getFirst().marker().limit();
        double active=current>0?current:CabConfig.DEFAULT_LIMIT.get();
        double next=-1,distance=Double.POSITIVE_INFINITY;String warning="";
        var ahead=network.scan(projection,heading,markers,CabConfig.TRACK_LOOKAHEAD.get(),true);
        var targets=new ArrayList<CabSpeedPlan.Target>();
        for(var hit:ahead) {
            if(hit.distance()<.05)continue;
            double target=hit.marker().limit();
            targets.add(new CabSpeedPlan.Target(hit.distance(),target));
            if(next<0){next=target;distance=hit.distance();}
            // Increasing limits only become effective after the sign is passed.
            if(warning.isEmpty() && target<active && warningDistance(desk.getSpeedKmh(),target,CabConfig.TRACK_WARNING_DECEL.get())>=hit.distance()) {
                warning=hit.marker().id()+":"+target;
            }
        }
        return new Result(current,next,distance,warning,new CabSpeedPlan(true,CabConfig.TRACK_LOOKAHEAD.get(),
                CabConfig.TRACK_WARNING_DECEL.get(),targets));
    }
    private Result unavailable() { current=-1;directionKey="";passages.reset();return new Result(-1,-1,Double.POSITIVE_INFINITY,"",CabSpeedPlan.EMPTY); }
    public static double warningDistance(double speed,double target,double deceleration) {
        if(!Double.isFinite(speed) || !Double.isFinite(target) || deceleration<=0 || speed<=target+2)return 0;
        double v=speed/3.6,u=target/3.6;
        return Math.max(0,(v*v-u*u)/(2*deceleration))+v*8+20;
    }
    public double stored() { return current; }
    public void save(net.minecraft.nbt.CompoundTag tag) {
        tag.putDouble("PassedTrackLimit",current);tag.putString("TrackDirectionKey",directionKey);
        tag.putDouble("TrackDX",lastHeading.x);tag.putDouble("TrackDY",lastHeading.y);tag.putDouble("TrackDZ",lastHeading.z);
    }
    public void load(net.minecraft.nbt.CompoundTag tag) {
        double value=tag.getDouble("PassedTrackLimit");
        current=Double.isFinite(value) && value>0 && value<=1000?value:-1;
        directionKey=tag.getString("TrackDirectionKey");
        lastHeading=new Vec3(tag.getDouble("TrackDX"),tag.getDouble("TrackDY"),tag.getDouble("TrackDZ"));
        if(!Double.isFinite(lastHeading.lengthSqr()))lastHeading=Vec3.ZERO;
    }
}
