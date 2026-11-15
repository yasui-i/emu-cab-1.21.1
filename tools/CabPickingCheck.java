import com.yasui_i.emucab.control.*;
import net.minecraft.world.phys.Vec3;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class CabPickingCheck {
    public static void main(String[] args) throws Exception {
        checkLeverBearing();
        var handle=java.util.Arrays.stream(CabGeometry.CONTROLS).filter(c->c.action()==CabAction.HANDLE).findFirst().orElseThrow();
        for(int n=-8;n<=7;n++) {
            var b=CabHitTesting.bounds(handle,n);var p=b.getCenter();
            assert b.getYsize()>0 && b.getZsize()>0;
            var picked=CabHitTesting.pickRay(p.add(0,12,-14),p,50,n);
            assert picked!=null && picked.action()==CabAction.HANDLE : "Cannot pick moving handle at "+n;
        }
        var b=CabHitTesting.bounds(handle,0);
        // The edited layout places B037 inside the right assistance zone.
        // Check the enlarged grip on its unobstructed left side instead.
        var p=new Vec3(b.minX-2,b.getCenter().y,b.getCenter().z);
        var picked=CabHitTesting.pickRay(p.add(0,12,-14),p,50,0);
        assert picked!=null && picked.action()==CabAction.HANDLE : "Handle assistance missed";
        int exact=0,inert=0;
        for(var c:CabGeometry.CONTROLS) {
            var p0=CabHitTesting.bounds(c,0).getCenter();
            var eye=p0.add(0,2,-2);
            var pick=CabHitTesting.pickRay(eye,p0,eye.distanceTo(p0)+.02,0);
            if(c.action()==CabAction.UNASSIGNED && !c.number().equals("B140")) {
                assert !CabHitTesting.interactive(c);
                assert CabHitTesting.pick(p0)!=c && pick!=c:"Inactive control picked: "+c.number();
                assert CabButtonValidation.pick(c.number(),eye,p0,eye,4.5,0)==null;
                inert++;continue;
            }
            if(c.action()==CabAction.HANDLE)continue;
            assert pick!=null && pick.id().equals(c.id()) : "Adjacent assistance stole "+c.id()+" with "+(pick==null?"none":pick.id());
            exact++;
        }
        System.out.println("Picking: moving handle, enlarged grip, "+exact+" active centers (including MON), "+inert+" inactive controls excluded from point/ray/packet picking passed");
    }

    private static void checkLeverBearing() throws Exception {
        var meshes=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/emucab/meshes/cab_dynamic.json"))).getAsJsonArray();
        var points=new ArrayList<Vec3>();
        for(var element:meshes) {
            var mesh=element.getAsJsonObject();
            if(!mesh.get("name").getAsString().equals("Traction_controller_|_moving_lever"))continue;
            for(var triangle:mesh.getAsJsonArray("triangles"))for(var vertex:triangle.getAsJsonObject().getAsJsonArray("p")) {
                var p=vertex.getAsJsonArray();points.add(new Vec3(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble()));
            }
        }
        double bottom=points.stream().mapToDouble(p->p.y).min().orElseThrow();
        var foot=points.stream().filter(p->Math.abs(p.y-bottom)<1e-6).toList();
        var center=new Vec3((foot.stream().mapToDouble(p->p.x).min().orElseThrow()+foot.stream().mapToDouble(p->p.x).max().orElseThrow())/2,
                bottom,(foot.stream().mapToDouble(p->p.z).min().orElseThrow()+foot.stream().mapToDouble(p->p.z).max().orElseThrow())/2);
        var bearing=new Vec3(CabGeometry.LEVER_PIVOT_X,CabGeometry.LEVER_PIVOT_Y,CabGeometry.LEVER_PIVOT_Z);
        assert bearing.distanceTo(center)<1e-7 : "Lever bearing no longer follows rod lower face: "+bearing+" vs "+center;
        // A point at the bearing must remain fixed throughout the entire throw.
        // Exercising the picking transform also catches block/pixel unit mistakes.
        var c=new CabGeometry.Control("bearing","test",CabAction.HANDLE,0,false,
                bearing.x*16,bearing.y*16,bearing.z*16,bearing.x*16,bearing.y*16,bearing.z*16);
        for(int notch=-8;notch<=7;notch++)
            assert CabHitTesting.bounds(c,notch).getCenter().distanceTo(bearing.scale(16))<1e-9 : "Lever foot moves at "+notch;
    }
}
