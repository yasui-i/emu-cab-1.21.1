package com.yasui_i.emucab.control;

import net.minecraft.world.phys.Vec3;

/** Validates a local input snapshot against the player's current local position. Coordinates are model pixels. */
public final class CabButtonValidation {
    public static CabGeometry.Control pick(String number,Vec3 eye,Vec3 surface,Vec3 actualEye,double reach,int lever) {
        if(!finite(eye) || !finite(surface) || !finite(actualEye) || !Double.isFinite(reach) || reach<=0
                || eye.distanceToSqr(actualEye)>24*24 || eye.distanceToSqr(surface)<.0001)return null;
        var picked=CabHitTesting.pickRay(eye,surface,reach*16,lever);
        if(picked==null || !picked.number().equals(number) || !CabHitTesting.interactive(picked))return null;
        return inRange(picked,actualEye,reach+.25,lever)?picked:null;
    }
    /** A render-space handle ray remains valid during a Sable pose correction.
     * The server still checks the actual rider's local reach; camera direction and
     * world-space clipping from another physics tick must not reject this grip. */
    public static CabGeometry.Control movingHandle(Vec3 eye,Vec3 surface,Vec3 actualEye,double reach,int lever) {
        var picked=pick("B035",eye,surface,eye,reach,lever);
        if(picked==null || !finite(actualEye) || eye.distanceToSqr(actualEye)>128*128)return null;
        return inRange(picked,actualEye,reach+1.5,lever)?picked:null;
    }
    private static boolean inRange(CabGeometry.Control picked,Vec3 actualEye,double reach,int lever) {
        var box=CabHitTesting.bounds(picked,lever);
        double dx=Math.max(Math.max(box.minX-actualEye.x,0),actualEye.x-box.maxX);
        double dy=Math.max(Math.max(box.minY-actualEye.y,0),actualEye.y-box.maxY);
        double dz=Math.max(Math.max(box.minZ-actualEye.z,0),actualEye.z-box.maxZ);
        return dx*dx+dy*dy+dz*dz<=Math.pow(reach*16,2);
    }
    private static boolean finite(Vec3 v) { return v!=null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private CabButtonValidation() {}
}
