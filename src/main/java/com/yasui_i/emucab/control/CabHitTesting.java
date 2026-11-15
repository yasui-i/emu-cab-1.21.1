package com.yasui_i.emucab.control;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;

public final class CabHitTesting {
    /** Controls with no assigned action are model reference points only. B140 is
     * the MON softkey and remains interactive despite its legacy UNASSIGNED tag. */
    public static boolean interactive(CabGeometry.Control c) {
        return c.action()!=CabAction.UNASSIGNED || c.number().equals("B140");
    }
    public static Vec3 eyeInDesk(Player player, BlockPos center) {
        var sub=SableCompanion.INSTANCE.getContaining(player.level(),center);
        var eye=player.getEyePosition();
        if(sub==null)return eye;
        // Sable keeps an authoritative position relative to the body for riders.
        // Using it avoids combining a new body pose with an older player world position.
        if(player instanceof dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension rider){
            var local=rider.sable$getPlotPosition();
            if(local!=null&&SableCompanion.INSTANCE.getContaining(player.level(),local)==sub)
                return local.add(0,player.getEyeHeight(),0);
        }
        var v=sub.logicalPose().transformPositionInverse(new Vector3d(eye.x,eye.y,eye.z));
        return new Vec3(v.x,v.y,v.z);
    }

    public static AABB bounds(CabGeometry.Control c, int lever) {
        var box=new AABB(c.x0(),c.y0(),c.z0(),c.x1(),c.y1(),c.z1());
        if(c.action()!=CabAction.HANDLE)return motionBounds(c,box);
        double py=CabGeometry.LEVER_PIVOT_Y*16,pz=CabGeometry.LEVER_PIVOT_Z*16;
        double angle=Math.toRadians(lever*5),sin=Math.sin(angle),cos=Math.cos(angle);
        double y0=Double.MAX_VALUE,z0=y0,y1=-y0,z1=-y0;
        // Include the rod and release cap, rather than the ergonomic grip alone.
        double bottom=c.number().equals("B035")?Math.min(c.y0(),py):c.y0();
        double top=c.number().equals("B035")?Math.max(c.y1(),18.19):c.y1();
        for(double y:new double[]{bottom,top})for(double z:new double[]{c.z0(),c.z1()}) {
            double ry=py+(y-py)*cos-(z-pz)*sin,rz=pz+(y-py)*sin+(z-pz)*cos;
            y0=Math.min(y0,ry);y1=Math.max(y1,ry);z0=Math.min(z0,rz);z1=Math.max(z1,rz);
        }
        return new AABB(c.x0(),y0,z0,c.x1(),y1,z1);
    }

    /** Stable swept bounds let client tween frames and server detents use the same ray. */
    private static AABB motionBounds(CabGeometry.Control c,AABB box) {
        var motion=CabAnimationGeometry.byId(c.id());
        if(motion==null)return box;
        // Generated from the swept mesh itself: tilted selectors must not steal
        // clicks on adjacent controls through a rotated, oversized box.
        return new AABB(motion.x0(),motion.y0(),motion.z0(),motion.x1(),motion.y1(),motion.z1());
    }
    /** Inverse of the blockstate's rotation about the centre block, for all three parts. */
    public static Vec3 toModel(BlockPos center, Direction facing, Vec3 world) {
        double dx = world.x - center.getX() - .5;
        double dz = world.z - center.getZ() - .5;
        Direction along = facing.getClockWise();
        return new Vec3(8 + 16 * (dx * along.getStepX() + dz * along.getStepZ()),
                16 * (world.y - center.getY()),
                8 - 16 * (dx * facing.getStepX() + dz * facing.getStepZ()));
    }
    public static Vec3 fromModel(BlockPos center,Direction facing,Vec3 p) {
        Direction along=facing.getClockWise();
        double x=(p.x-8)/16,z=(p.z-8)/16;
        return new Vec3(center.getX()+.5+x*along.getStepX()-z*facing.getStepX(),
                center.getY()+p.y/16,center.getZ()+.5+x*along.getStepZ()-z*facing.getStepZ());
    }

    public static CabGeometry.Control pick(Vec3 p) {
        CabGeometry.Control best = null;
        double distance = Double.MAX_VALUE;
        for (var c : CabGeometry.CONTROLS) {
            if(!interactive(c))continue;
            double margin = .065;
            if (p.x < c.x0()-margin || p.x > c.x1()+margin
                    || p.y < c.y0()-margin || p.y > c.y1()+margin
                    || p.z < c.z0()-margin || p.z > c.z1()+margin) continue;
            double d = p.distanceToSqr(new Vec3((c.x0()+c.x1())/2, (c.y0()+c.y1())/2, (c.z0()+c.z1())/2));
            if (d < distance) { distance = d; best = c; }
        }
        return best;
    }

    /** Analytic control bounds, independent of the coarse physical cabinet shape. */
    public static CabGeometry.Control pickRay(Vec3 eye, Vec3 surface, double reach) {
        return pickRay(eye,surface,reach,0);
    }
    public static CabGeometry.Control pickRay(Vec3 eye, Vec3 surface, double reach,int lever) {
        // The coarse hood can be in front of a recessed button. Trace through the
        // desk's shell to the player's reach, selecting the nearest actual control.
        Vec3 end=eye.add(surface.subtract(eye).normalize().scale(reach));
        CabGeometry.Control best=null;
        double nearest=Double.MAX_VALUE;
        // Exact hits always win over assistance. In overlapping assistance zones,
        // choose the control nearest the ray, rather than the largest box.
        for(var c:CabGeometry.CONTROLS) {
            if(!interactive(c))continue;
            var hit=bounds(c,lever).inflate(.065).clip(eye,end);
            if(hit.isEmpty())continue;
            double distance=hit.get().distanceToSqr(eye);
            if(distance<nearest) { nearest=distance;best=c; }
        }
        if(best!=null)return best;
        double score=Double.MAX_VALUE;
        for(var c:CabGeometry.CONTROLS) {
            if(!interactive(c))continue;
            var box=bounds(c,lever);
            double margin=c.action()==CabAction.HANDLE?2.4:.45;
            var hit=box.inflate(margin).clip(eye,end);
            if(hit.isEmpty())continue;
            Vec3 mid=box.getCenter(),delta=end.subtract(eye);
            double along=Math.clamp(mid.subtract(eye).dot(delta)/delta.lengthSqr(),0,1);
            Vec3 p=eye.add(delta.scale(along));
            double dx=Math.max(Math.max(box.minX-p.x,0),p.x-box.maxX);
            double dy=Math.max(Math.max(box.minY-p.y,0),p.y-box.maxY);
            double dz=Math.max(Math.max(box.minZ-p.z,0),p.z-box.maxZ);
            double distance=(dx*dx+dy*dy+dz*dz)/(margin*margin);
            if(distance<score) { score=distance;best=c; }
        }
        return best;
    }

    private CabHitTesting() {}
}
