package com.yasui_i.emucab.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.block.CabDeskPart;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cached cab shell with independently animated control caps and selectors. */
public final class CabDeskRenderer implements BlockEntityRenderer<CabDeskBlockEntity> {
    private static final RenderType ATLAS=RenderType.entitySolid(EMUCab.id("textures/block/cab_atlas.png"));
    private static final RenderType WHITE=RenderType.entitySolid(EMUCab.id("textures/block/cab_white.png"));
    private static final class Triangle { float[][] p; String texture; float[] color; transient Vector3f normal; }
    private static final class Mesh { String name; Triangle[] triangles; }
    private static final class Motion {
        String id,number,kind; float[] pivot,axis; float travel; String[] parts;
        transient CabGeometry.Control control;
        transient Quaternionf rotation=new Quaternionf();
        transient Map<String,List<Triangle>> batches=new LinkedHashMap<>();
    }
    private static Mesh[] meshes;
    private static Motion[] motions;
    private static final Map<String,List<Triangle>> leverBatches=new LinkedHashMap<>();
    private static final Map<String,RenderType> leverMaterials=new LinkedHashMap<>();
    private static final List<Mesh> screens=new ArrayList<>();
    private static final int[] CORNERS={0,1,2,2};
    public static void clearMesh() { meshes=null; motions=null; leverBatches.clear(); leverMaterials.clear(); screens.clear(); }
    public CabDeskRenderer(BlockEntityRendererProvider.Context context) {}

    private static Mesh[] mesh() {
        if(meshes==null) {
            try(var reader=Minecraft.getInstance().getResourceManager().openAsReader(EMUCab.id("meshes/cab_dynamic.json"))) {
                meshes=new Gson().fromJson(reader,Mesh[].class);
                var byName=new LinkedHashMap<String,Mesh>();
                for(Mesh m:meshes) {
                    byName.put(m.name,m);
                    if(m.name.endsWith("_|_LCD"))screens.add(m);
                    if(moving(m.name))cache(m,leverBatches);
                }
                try(var motionReader=Minecraft.getInstance().getResourceManager().openAsReader(EMUCab.id("meshes/cab_animations.json"))) {
                    motions=new Gson().fromJson(motionReader,Motion[].class);
                }
                for(Motion motion:motions) {
                    motion.control=java.util.Arrays.stream(CabGeometry.CONTROLS).filter(c->c.id().equals(motion.id)).findFirst().orElseThrow();
                    for(String part:motion.parts) {
                        Mesh m=byName.get(part);
                        if(m==null)throw new IllegalStateException("Missing animated control mesh: "+part);
                        cache(m,motion.batches);
                    }
                }
            } catch(IOException e) { throw new IllegalStateException("Cannot load cab mesh",e); }
        }
        return meshes;
    }

    private static void cache(Mesh mesh,Map<String,List<Triangle>> batches) {
        for(Triangle t:mesh.triangles) {
            t.normal=new Vector3f(t.p[1][0]-t.p[0][0],t.p[1][1]-t.p[0][1],t.p[1][2]-t.p[0][2])
                    .cross(new Vector3f(t.p[2][0]-t.p[0][0],t.p[2][1]-t.p[0][1],t.p[2][2]-t.p[0][2])).normalize();
            batches.computeIfAbsent(t.texture,key->new ArrayList<>()).add(t);
            leverMaterials.computeIfAbsent(t.texture,CabDeskRenderer::material);
        }
    }

    private static boolean moving(String name) {
        return name.startsWith("Traction_controller_|_") && (name.contains("moving_lever")
                || name.contains("ergonomic_grip") || name.contains("top_release") || name.contains("red_cap"));
    }

    private static RenderType material(String texture) {
        if(texture.equals("atlas"))return ATLAS;
        if(texture.startsWith("pixel_"))return RenderType.entitySolid(
                EMUCab.id("textures/block/cab_pixel/"+texture.substring(6)+".png"));
        return WHITE;
    }

    @Override public AABB getRenderBoundingBox(CabDeskBlockEntity desk) {
        return new AABB(desk.getBlockPos()).inflate(2,2,2);
    }
    @Override public void render(CabDeskBlockEntity desk,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(desk.getLevel()==null || !desk.isCenter())return;
        CabInteraction.visible(desk);
        Direction facing=desk.getBlockState().getValue(CabDeskBlock.FACING);
        pose.pushPose();CabRenderOrientation.apply(pose,facing);
        mesh();
        // The shell and silver rims remain in chunk buffers. Only moving caps,
        // selectors and the master handle are submitted, with cached normals.
        pose.pushPose();pose.translate(CabGeometry.LEVER_PIVOT_X,CabGeometry.LEVER_PIVOT_Y,CabGeometry.LEVER_PIVOT_Z);
        pose.mulPose(Axis.XP.rotationDegrees(CabInteraction.leverPosition(desk)*5));
        pose.translate(-CabGeometry.LEVER_PIVOT_X,-CabGeometry.LEVER_PIVOT_Y,-CabGeometry.LEVER_PIVOT_Z);
        leverBatches.forEach((texture,batch)->drawBatch(batch,texture,pose,buffers,light));
        pose.popPose();
        double time=desk.getLevel().getGameTime()+partial;
        var animation=desk.controlAnimation();
        for(Motion motion:motions) {
            if(!present(desk,motion.pivot[0]))continue;
            pose.pushPose();
            if(motion.kind.equals("press")) {
                double distance=-motion.travel*animation.pressAmount(motion.id,time);
                pose.translate(motion.axis[0]*distance,motion.axis[1]*distance,motion.axis[2]*distance);
            } else {
                double degrees=animation.rotationDegrees(motion.control,desk.controls(),time);
                pose.translate(motion.pivot[0],motion.pivot[1],motion.pivot[2]);
                pose.mulPose(motion.rotation.rotationAxis((float)Math.toRadians(degrees),motion.axis[0],motion.axis[1],motion.axis[2]));
                pose.translate(-motion.pivot[0],-motion.pivot[1],-motion.pivot[2]);
            }
            motion.batches.forEach((texture,batch)->drawBatch(batch,texture,pose,buffers,light));
            pose.popPose();
        }
        // Live text is aligned to each original LCD plane, including angled side wings.
        if(desk.controls().displaysActive()) renderScreens(desk,pose,buffers,partial);
        pose.popPose();
    }

    private static void drawBatch(List<Triangle> batch,String texture,PoseStack pose,MultiBufferSource buffers,int light) {
        if(batch.isEmpty())return;
        boolean textured=texture.equals("atlas") || texture.startsWith("pixel_");
        VertexConsumer consumer=buffers.getBuffer(leverMaterials.get(texture));
        for(Triangle t:batch)for(int k:CORNERS) {
            float[] p=t.p[k];
            consumer.addVertex(pose.last(),p[0],p[1],p[2]).setColor(t.color[0],t.color[1],t.color[2],1)
                    .setUv(textured?p[3]:.5F,textured?p[4]:.5F).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light).setNormal(pose.last(),t.normal.x,t.normal.y,t.normal.z);
        }
    }

    private static boolean present(CabDeskBlockEntity desk,float x) {
        if(x>=0 && x<=1)return true;
        Direction along=desk.getBlockState().getValue(CabDeskBlock.FACING).getClockWise();
        var s=desk.getLevel().getBlockState(desk.getBlockPos().relative(along,x<0?-1:1));
        return s.getBlock() instanceof CabDeskBlock && s.getValue(CabDeskBlock.PART)==(x<0?CabDeskPart.LEFT:CabDeskPart.RIGHT)
                && s.getValue(CabDeskBlock.FACING)==desk.getBlockState().getValue(CabDeskBlock.FACING);
    }

    private static void renderScreens(CabDeskBlockEntity desk,PoseStack pose,MultiBufferSource buffers,float partial) {
        for(Mesh m:screens) {
            int index=Integer.parseInt(m.name.substring(0,2))-1;
            if(m.triangles.length!=2 || !present(desk,(float)CabGeometry.DISPLAYS[index].x()/16))continue;
            // Use the LCD's actual corners; no axis-aligned replacement or screen relocation.
            float[] a=m.triangles[0].p[0],b=m.triangles[0].p[1],c=m.triangles[0].p[2];
            Vector3f right=new Vector3f(b[0]-a[0],b[1]-a[1],b[2]-a[2]);
            Vector3f down=new Vector3f(b[0]-c[0],b[1]-c[1],b[2]-c[2]);
            float width=right.length(),height=down.length();right.normalize();down.normalize();
            Vector3f normal=new Vector3f(down).cross(right).normalize();
            Vector3f origin=new Vector3f(c[0],c[1],c[2]).sub(new Vector3f(right).mul(width)).fma(.0015F,normal);
            pose.pushPose();pose.translate(origin.x,origin.y,origin.z);
            pose.mulPose(new org.joml.Matrix4f().m00(right.x).m01(right.y).m02(right.z)
                    .m10(-down.x).m11(-down.y).m12(-down.z)
                    .m20(normal.x).m21(normal.y).m22(normal.z));
            pose.scale(width/CabDisplayRenderer.WIDTH,-height/CabDisplayRenderer.HEIGHT,1);
            CabDisplayRenderer.render(index,desk,pose,buffers,partial);
            pose.popPose();
        }
    }
}
