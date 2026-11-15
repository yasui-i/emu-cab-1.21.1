import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yasui_i.emucab.client.CabRenderOrientation;
import com.yasui_i.emucab.control.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.file.*;
import java.util.*;

/** Checks exported motion groups against the source rather than a second renderer implementation. */
public final class CabControlAnimationCheck {
    private static final Path ASSETS=Path.of("src/main/resources/assets/emucab");
    private static JsonArray read(String file)throws Exception{return JsonParser.parseString(Files.readString(ASSETS.resolve("meshes/"+file))).getAsJsonArray();}
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private static Map<String,JsonObject> meshes(JsonArray list){var result=new HashMap<String,JsonObject>();for(var value:list){var o=value.getAsJsonObject();assert result.put(o.get("name").getAsString(),o)==null;}return result;}
    private static double area(JsonObject mesh){double sum=0;for(var t:mesh.getAsJsonArray("triangles")){var p=t.getAsJsonObject().getAsJsonArray("p");var a=vector(p.get(0).getAsJsonArray());var b=vector(p.get(1).getAsJsonArray());var c=vector(p.get(2).getAsJsonArray());sum+=b.subtract(a).cross(c.subtract(a)).length()/2;}return sum;}
    private static Vec3 normal(JsonObject mesh){var p=mesh.getAsJsonArray("triangles").get(0).getAsJsonObject().getAsJsonArray("p");var a=vector(p.get(0).getAsJsonArray());return vector(p.get(1).getAsJsonArray()).subtract(a).cross(vector(p.get(2).getAsJsonArray()).subtract(a)).normalize();}
    private static void checkDirectionMount(Map<String,JsonObject> source,JsonArray motions){
        var direction=java.util.stream.StreamSupport.stream(motions.spliterator(),false).map(JsonElement::getAsJsonObject).filter(m->m.get("number").getAsString().equals("B042")).findFirst().orElseThrow();
        var axis=vector(direction.getAsJsonArray("axis"));
        assert axis.distanceTo(normal(source.get("05_Remote_monitor_|_LCD")))<1e-5:"B042 must turn around right backplate normal";
        double plate=Double.NEGATIVE_INFINITY,rear=Double.POSITIVE_INFINITY;
        for(var t:source.get("Screen_hood_|_right_angled_wing").getAsJsonArray("triangles"))for(var p:t.getAsJsonObject().getAsJsonArray("p"))plate=Math.max(plate,vector(p.getAsJsonArray()).dot(axis));
        for(var part:direction.getAsJsonArray("parts"))for(var t:source.get(part.getAsString()).getAsJsonArray("triangles"))for(var p:t.getAsJsonObject().getAsJsonArray("p"))rear=Math.min(rear,vector(p.getAsJsonArray()).dot(axis));
        assert rear<plate&&plate-rear<.001:"B042 rear face must rest on backplate";
    }
    private static boolean lever(String name){return name.startsWith("Traction_controller_|_")&&(name.contains("moving_lever")||name.contains("ergonomic_grip")||name.contains("top_release")||name.contains("red_cap"));}
    private static boolean fixedPart(String name){return name.contains("chrome_ring")||name.contains("socket")||name.contains("boot")||name.contains("base")||name.contains("mounting")||name.contains("plate");}
    public static void main(String[]args)throws Exception{
        var source=meshes(read("cab_mesh.json"));var dynamic=meshes(read("cab_dynamic.json"));var motions=read("cab_animations.json");
        checkDirectionMount(source,motions);
        var controls=new HashMap<String,CabGeometry.Control>();for(var c:CabGeometry.CONTROLS)controls.put(c.id(),c);
        Set<String> parts=new HashSet<>(),ids=new HashSet<>(),numbers=new HashSet<>();int press=0,rotary=0;double movingArea=0;
        for(var value:motions){
            var m=value.getAsJsonObject();String id=m.get("id").getAsString(),number=m.get("number").getAsString(),kind=m.get("kind").getAsString();
            assert ids.add(id)&&numbers.add(number):"Duplicate animation identity";
            var control=controls.get(id);assert control!=null&&control.number().equals(number):"Lost control identity "+id;
            assert kind.equals("press")||kind.equals("rotary"):kind;
            if(kind.equals("press"))press++;else rotary++;
            var axis=vector(m.getAsJsonArray("axis"));var pivot=vector(m.getAsJsonArray("pivot"));
            var generated=CabAnimationGeometry.byId(id);assert generated!=null&&generated.number().equals(number)&&generated.kind().equals(kind):"Generated geometry identity "+number;
            assert pivot.distanceTo(new Vec3(generated.pivotX(),generated.pivotY(),generated.pivotZ()))<1e-8:"Generated pivot differs "+number;
            assert axis.distanceTo(new Vec3(generated.axisX(),generated.axisY(),generated.axisZ()))<1e-8:"Generated axis differs "+number;
            assert Math.abs(axis.length()-1)<1e-5:"Non-unit mounting axis "+number;
            assert Double.isFinite(pivot.lengthSqr());
            if(number.equals("B042")) {
                var lcd=source.get("05_Remote_monitor_|_LCD").getAsJsonArray("triangles").get(0).getAsJsonObject().getAsJsonArray("p");
                var a=vector(lcd.get(0).getAsJsonArray());var b=vector(lcd.get(1).getAsJsonArray());var c=vector(lcd.get(2).getAsJsonArray());
                var normal=b.subtract(a).cross(c.subtract(a)).normalize();
                assert axis.distanceTo(normal)<1e-5:"Direction selector must turn normal to the right backplate";
                double front=Double.NEGATIVE_INFINITY,base=Double.POSITIVE_INFINITY;
                for(var t:source.get("Screen_hood_|_right_angled_wing").getAsJsonArray("triangles"))
                    for(var p:t.getAsJsonObject().getAsJsonArray("p"))front=Math.max(front,vector(p.getAsJsonArray()).dot(axis));
                for(var t:source.get("Pantograph_selector_|_selector.001").getAsJsonArray("triangles"))
                    for(var p:t.getAsJsonObject().getAsJsonArray("p"))base=Math.min(base,vector(p.getAsJsonArray()).dot(axis));
                assert front-base>0&&front-base<.0006:"Direction selector rear face must meet the actual backplate";
                int rearFaces=0;
                for(var t:source.get("Pantograph_selector_|_selector.001").getAsJsonArray("triangles")) {
                    var p=t.getAsJsonObject().getAsJsonArray("p");
                    var fa=vector(p.get(0).getAsJsonArray());var fb=vector(p.get(1).getAsJsonArray());var fc=vector(p.get(2).getAsJsonArray());
                    var faceNormal=fb.subtract(fa).cross(fc.subtract(fa)).normalize();
                    if(Math.abs(fa.dot(axis)-base)<1e-6&&Math.abs(fb.dot(axis)-base)<1e-6&&Math.abs(fc.dot(axis)-base)<1e-6) {
                        rearFaces++;
                        assert Math.abs(faceNormal.dot(axis))>.99999:"Direction selector mounting face is tilted";
                    }
                }
                assert rearFaces==2:"Direction selector must rest on its whole rectangular rear face";
            }
            double travel=m.get("travel").getAsDouble();
            assert kind.equals("press")?travel>0&&travel<.02:travel==0:"Invalid stroke "+number;
            assert !m.getAsJsonArray("parts").isEmpty():"Empty motion "+number;
            for(var member:m.getAsJsonArray("parts")){
                String name=member.getAsString();assert source.containsKey(name)&&dynamic.containsKey(name):"Missing moving mesh "+name;
                assert parts.add(name):"Mesh belongs to two buttons "+name;
                assert !fixedPart(name):"Stationary mounting part animated "+name;
                movingArea+=area(source.get(name));
                var hitBounds=CabHitTesting.bounds(control,0).inflate(.0001);
                for(var triangle:source.get(name).getAsJsonArray("triangles"))for(var point:triangle.getAsJsonObject().getAsJsonArray("p")) {
                    var vertex=vector(point.getAsJsonArray());
                    if(kind.equals("press")) {
                        var depressed=vertex.subtract(axis.scale(travel));
                        assert hitBounds.contains(vertex.scale(16))&&hitBounds.contains(depressed.scale(16)):"Press motion leaves picking bounds "+number;
                    }else for(double angle:new double[]{-45,-22.5,0,22.5,45,67.5,90}) {
                        var delta=vertex.subtract(pivot);double radians=Math.toRadians(angle);
                        var turned=pivot.add(delta.scale(Math.cos(radians))).add(axis.cross(delta).scale(Math.sin(radians)))
                                .add(axis.scale(axis.dot(delta)*(1-Math.cos(radians))));
                        assert hitBounds.contains(turned.scale(16)):"Rotary motion leaves picking bounds "+number+" at "+angle;
                    }
                }
            }
            // The mounting centre must remain fixed even after yaw/pitch/roll supplied by Sable.
            for(Direction facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST})for(boolean body:new boolean[]{false,true}){
                Matrix4f parent=body?new Matrix4f().translation(20,7,-30).rotateYXZ(.83F,-.21F,.17F):new Matrix4f();
                var pose=new PoseStack();pose.mulPose(parent);CabRenderOrientation.apply(pose,facing);
                var before=pose.last().pose().transformPosition(new Vector3f((float)pivot.x,(float)pivot.y,(float)pivot.z));
                for(float angle:new float[]{-45,0,45,90}){
                    var transformed=new Matrix4f(pose.last().pose()).translate((float)pivot.x,(float)pivot.y,(float)pivot.z)
                            .rotate((float)Math.toRadians(angle),(float)axis.x,(float)axis.y,(float)axis.z).translate((float)-pivot.x,(float)-pivot.y,(float)-pivot.z);
                    var after=transformed.transformPosition(new Vector3f((float)pivot.x,(float)pivot.y,(float)pivot.z));
                    assert before.distance(after)<.00002:"Rotary mounting centre moves "+number;
                }
                for(var member:m.getAsJsonArray("parts"))for(var t:source.get(member.getAsString()).getAsJsonArray("triangles"))for(var p:t.getAsJsonObject().getAsJsonArray("p")){
                    var v=vector(p.getAsJsonArray());var moved=pose.last().pose().transformPosition(new Vector3f((float)v.x,(float)v.y,(float)v.z));
                    var local=new Matrix4f(parent).invert().transformPosition(moved);
                    var picked=CabHitTesting.toModel(BlockPos.ZERO,facing,new Vec3(local.x,local.y,local.z));
                    assert picked.distanceTo(v.scale(16))<.001:"Motion picking parent mismatch "+number+"/"+facing;
                }
            }
        }
        assert press>10&&rotary>=4:"Incomplete animation groups";
        for(var name:dynamic.keySet())assert parts.contains(name)||lever(name)||name.endsWith("_|_LCD"):"Unexpected dynamic mesh "+name;
        for(var entry:source.entrySet())if(lever(entry.getKey()))movingArea+=area(entry.getValue());
        var report=JsonParser.parseString(Files.readString(Path.of("model-source/pixel-integration-20261101/integration-report.json"))).getAsJsonObject();
        double total=source.values().stream().mapToDouble(CabControlAnimationCheck::area).sum();
        assert Math.abs(total-report.get("static_area").getAsDouble()-movingArea)<1e-5:"Static/dynamic split lost or duplicated source surfaces";
        System.out.println("Control animation geometry: "+press+" press / "+rotary+" rotary; stationary rims/bases, normalized axes, conserved surfaces and four-facing/tilted-parent transforms passed");
    }
}
