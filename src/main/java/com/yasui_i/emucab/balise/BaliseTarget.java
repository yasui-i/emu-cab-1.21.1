package com.yasui_i.emucab.balise;

import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Stable physical track anchor; never persists Create's rebuildable graph IDs. */
public record BaliseTarget(BlockPos trackPos,@Nullable BezierTrackPointLocation curve,boolean positive,
                           Vec3 anchor,Vec3 tangent,Vec3 normal) {
    public Vec3 forward(){return positive?tangent.scale(-1):tangent;}
    public BaliseTarget reversed(){return new BaliseTarget(trackPos,curve,!positive,anchor,tangent,normal);}

    @Nullable public static BaliseTarget forLook(Level level,BlockPos pos,@Nullable BezierTrackPointLocation curve,Vec3 look){
        var base=resolve(level,pos,curve,false);
        if(base==null)return null;
        boolean positive;
        if(curve!=null)positive=look.dot(base.tangent)<0;
        else {
            var state=level.getBlockState(pos);var track=(ITrackBlock)state.getBlock();
            positive=track.getNearestTrackAxis(level,pos,state,look).getSecond()==Direction.AxisDirection.POSITIVE;
        }
        return positive?base.reversed():base;
    }

    @Nullable public static BaliseTarget resolve(Level level,BlockPos pos,@Nullable BezierTrackPointLocation curve,boolean positive){
        if(!level.hasChunkAt(pos))return null;
        var state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof ITrackBlock track))return null;
        Vec3 point,tangent,normal;
        if(curve==null){
            var axes=track.getTrackAxes(level,pos,state);
            // A crossing has no unique line: install on either adjacent rail.
            if(axes.size()!=1)return null;
            tangent=axes.getFirst().normalize();normal=track.getUpNormal(level,pos,state).normalize();
            point=new Vec3(pos.getX()+.5,pos.getY()+track.getElevationAtCenter(level,pos,state),pos.getZ()+.5);
        }else{
            if(!(level.getBlockEntity(pos) instanceof TrackBlockEntity be))return null;
            var connection=be.getConnections().get(curve.curveTarget());
            if(connection==null)return null;
            int segments=(int)Math.floor(connection.getLength()*2);
            if(segments<1||curve.segment()<0||curve.segment()>=segments)return null;
            double t=(curve.segment()+1.0)/segments;
            point=connection.getPosition(t);
            tangent=connection.getPosition(Math.min(1,t+.001)).subtract(connection.getPosition(Math.max(0,t-.001))).normalize();
            normal=connection.getNormal(t).normalize();
        }
        if(!valid(point)||!valid(tangent)||!valid(normal)||tangent.lengthSqr()<.9||normal.lengthSqr()<.9)return null;
        if(normal.y<0)normal=normal.scale(-1);
        return new BaliseTarget(pos.immutable(),curve,positive,point,tangent,normal);
    }
    public boolean sameMount(BaliseTarget other){
        // Opposite ends of a curve can select the same physical segment.
        return anchor.distanceToSqr(other.anchor)<.16&&Math.abs(tangent.dot(other.tangent))>.9;
    }
    public boolean stillValid(Level level){
        var resolved=resolve(level,trackPos,curve,positive);
        return resolved!=null&&resolved.anchor.distanceToSqr(anchor)<.04&&Math.abs(resolved.tangent.dot(tangent))>.98;
    }
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putLong("Track",trackPos.asLong());tag.putBoolean("Positive",positive);
        if(curve!=null){tag.putLong("Curve",curve.curveTarget().asLong());tag.putInt("Segment",curve.segment());}
        writeVec(tag,"A",anchor);writeVec(tag,"T",tangent);writeVec(tag,"N",normal);return tag;
    }
    @Nullable public static BaliseTarget load(CompoundTag tag){
        var point=readVec(tag,"A");var tangent=readVec(tag,"T");var normal=readVec(tag,"N");
        if(!valid(point)||!valid(tangent)||!valid(normal)||tangent.lengthSqr()<.9||normal.lengthSqr()<.9)return null;
        var curve=tag.contains("Curve")?new BezierTrackPointLocation(BlockPos.of(tag.getLong("Curve")),tag.getInt("Segment")):null;
        return new BaliseTarget(BlockPos.of(tag.getLong("Track")),curve,tag.getBoolean("Positive"),point,tangent.normalize(),normal.normalize());
    }
    private static boolean valid(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&Math.abs(v.x)<30_000_000&&Math.abs(v.z)<30_000_000&&Math.abs(v.y)<20_000_000;}
    private static void writeVec(CompoundTag t,String key,Vec3 v){t.putDouble(key+"X",v.x);t.putDouble(key+"Y",v.y);t.putDouble(key+"Z",v.z);}
    private static Vec3 readVec(CompoundTag t,String key){return new Vec3(t.getDouble(key+"X"),t.getDouble(key+"Y"),t.getDouble(key+"Z"));}
}
