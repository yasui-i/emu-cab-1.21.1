import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yasui_i.emucab.client.CabRenderOrientation;
import com.yasui_i.emucab.control.CabHitTesting;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.file.*;

public class CabRenderOrientationCheck {
    public static void main(String[] args) throws Exception {
        Path resources=Path.of("src/main/resources/assets/emucab");
        var variants=JsonParser.parseString(Files.readString(resources.resolve("blockstates/cab_desk.json")))
                .getAsJsonObject().getAsJsonObject("variants");
        var meshes=JsonParser.parseString(Files.readString(resources.resolve("meshes/cab_dynamic.json"))).getAsJsonArray();
        int checked=0;
        for(Direction facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
            // Read the actual static model rotation; its sign convention differs from a positive Y quaternion.
            int degrees=variants.getAsJsonObject("part=center,facing="+facing.getSerializedName()).get("y").getAsInt();
            for(String part:new String[]{"left","right"})
                assert variants.getAsJsonObject("part="+part+",facing="+facing.getSerializedName()).get("y").getAsInt()==degrees;
            Matrix4f rotation=BlockModelRotation.by(0,degrees).getRotation().getMatrix();
            for(boolean body:new boolean[]{false,true}) {
                // A translated, yawed, pitched and rolled parent models the pose supplied by Sable.
                Matrix4f parent=body?new Matrix4f().translation(20,7,-30).rotateYXZ(.83F,-.21F,.17F):new Matrix4f();
                var pose=new PoseStack();pose.mulPose(parent);
                CabRenderOrientation.apply(pose,facing);
                Matrix4f expected=new Matrix4f(parent).translate(.5F,0,.5F).mul(rotation).translate(-.5F,0,-.5F);
                for(var element:meshes)for(var triangle:element.getAsJsonObject().getAsJsonArray("triangles"))
                    for(var vertex:triangle.getAsJsonObject().getAsJsonArray("p")) {
                        var p=vertex.getAsJsonArray();
                        Vector3f model=new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat());
                        Vector3f rendered=pose.last().pose().transformPosition(new Vector3f(model));
                        Vector3f baked=expected.transformPosition(new Vector3f(model));
                        assert rendered.distance(baked)<.00002F : facing+" body="+body+" screen/lever differs from body: "+rendered+" vs "+baked;
                        // Picking works in the body's local coordinates, after undoing the parent world pose.
                        Vector3f local=new Matrix4f(parent).invert().transformPosition(new Vector3f(rendered));
                        Vec3 picked=CabHitTesting.toModel(BlockPos.ZERO,facing,new Vec3(local.x,local.y,local.z));
                        assert picked.distanceTo(new Vec3(model.x*16,model.y*16,model.z*16))<.001 : "Picking orientation "+facing;
                        checked++;
                    }
                // Check plane directions and normals as well as positions.
                for(var axis:new Vector3f[]{new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1)})
                    assert pose.last().pose().transformDirection(new Vector3f(axis))
                            .distance(expected.transformDirection(new Vector3f(axis)))<.00001F;
            }
        }
        System.out.println("Render orientation: four facings, static model alignment, tilted body parent, axes and picking passed ("+checked+" vertices)");
    }
}
