import com.yasui_i.emucab.control.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.*;

public class CabButtonValidationCheck {
    public static void main(String[] args) {
        int count=0;
        for(var facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
            var center=new BlockPos(1400,65,-2400);
            for(var c:CabGeometry.CONTROLS) {
                if(c.action()==CabAction.UNASSIGNED&&!c.number().equals("B140"))continue;
                Vec3 p=CabHitTesting.bounds(c,0).getCenter(),eye=p.add(0,2,-2);
                var roundtrip=CabHitTesting.toModel(center,facing,CabHitTesting.fromModel(center,facing,eye));
                assert roundtrip.distanceTo(eye)<1E-8;
                assert CabButtonValidation.pick(c.number(),eye,p,roundtrip,4.5,0)==c:c.number();
                assert CabButtonValidation.pick(c.number(),eye,p,eye.add(200,0,0),4.5,0)==null;
                assert CabButtonValidation.pick(c.number(),new Vec3(Double.NaN,0,0),p,eye,4.5,0)==null;
                assert CabButtonValidation.pick("B999",eye,p,eye,4.5,0)==null;
                assert CabButtonValidation.pick(c.number(),eye,p,eye,-1,0)==null;
                assert CabButtonValidation.pick(c.number(),eye,eye,eye,4.5,0)==null;
                count++;
            }
        }
        var handle=java.util.Arrays.stream(CabGeometry.CONTROLS).filter(c->c.action()==CabAction.HANDLE).findFirst().orElseThrow();
        for(int lever=-8;lever<=7;lever++) {
            var target=CabHitTesting.bounds(handle,lever).getCenter();var eye=target.add(0,12,-14);
            // 350 km/h is 4.86 world blocks per 20 TPS tick. A three-block local
            // snapshot discrepancy must not invalidate a ray that hit the grip.
            assert CabButtonValidation.movingHandle(eye,target,eye.add(0,0,-48),4.5,lever)==handle;
            assert CabButtonValidation.pick("B035",eye,target,eye.add(0,0,-48),4.5,lever)==null;
            assert CabButtonValidation.movingHandle(eye,target,eye.add(0,0,-200),4.5,lever)==null;
            assert CabButtonValidation.movingHandle(eye,eye,eye,4.5,lever)==null;
            assert CabButtonValidation.movingHandle(eye,new Vec3(Double.NaN,0,0),eye,4.5,lever)==null;
        }
        System.out.println("Button packets: local ray, all four facings, 25 assigned controls, invalid IDs/range/NaN rejected ("+count+" targets)");
    }
}
