package com.yasui_i.emucab.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockOutline;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour;
import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.balise.BaliseData;
import com.yasui_i.emucab.balise.BaliseClientAccess;
import com.yasui_i.emucab.balise.BaliseItem;
import com.yasui_i.emucab.balise.BaliseTarget;
import com.yasui_i.emucab.balise.BalisePackets;
import com.yasui_i.emucab.balise.BaliseService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/** Native Create targeting overlays and visible attachments without block cells. */
@EventBusSubscriber(modid=EMUCab.MOD_ID,value=Dist.CLIENT)
public final class BaliseClient {
    public static final ModelResourceLocation MODEL=ModelResourceLocation.standalone(EMUCab.id("block/balise"));
    private static ResourceLocation dimension;
    private static List<BaliseData> mounted=List.of();
    private BaliseClient() {}

    public static void receive(ResourceLocation world,List<BaliseData> entries) {
        dimension=world;mounted=List.copyOf(entries);
    }
    public static void open(BaliseData data) {
        var mc=Minecraft.getInstance();
        if(mc.level!=null)mc.setScreen(new BaliseScreen(data));
    }
    public static void bind(){BaliseClientAccess.bind(BaliseClient::receive,BaliseClient::open);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){dimension=null;mounted=List.of();}
    private static List<BaliseData> visible(ClientLevel level){return level.dimension().location().equals(dimension)?mounted:List.of();}

    private record Selection(BlockPos track,BezierTrackPointLocation curve,boolean positive,BaliseTarget target) {}
    private static Selection selection() {
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null)return null;
        var curve=TrackBlockOutline.result;
        if(curve!=null) {
            BlockPos pos=curve.blockEntity().getBlockPos();
            boolean positive=mc.player.getLookAngle().dot(curve.direction())<0;
            var target=BaliseTarget.resolve(mc.level,pos,curve.loc(),positive);
            return target==null?null:new Selection(pos,curve.loc(),positive,target);
        }
        if(!(mc.hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK)return null;
        BlockPos pos=hit.getBlockPos();var state=mc.level.getBlockState(pos);
        if(!(state.getBlock() instanceof ITrackBlock track))return null;
        boolean positive=track.getNearestTrackAxis(mc.level,pos,state,mc.player.getLookAngle()).getSecond()==Direction.AxisDirection.POSITIVE;
        var target=BaliseTarget.resolve(mc.level,pos,null,positive);
        return target==null?null:new Selection(pos,null,positive,target);
    }
    private static BaliseData selected(Selection selection,ClientLevel level) {
        if(selection==null)return null;
        BaliseData best=null;double distance=Double.MAX_VALUE;
        for(var balise:visible(level)) {
            if(balise.target().sameMount(selection.target()))return balise;
            if(!balise.target().trackPos().equals(selection.track()))continue;
            if((balise.target().curve()==null)!=(selection.curve()==null))continue;
            if(balise.target().curve()!=null&&!balise.target().curve().curveTarget().equals(selection.curve().curveTarget()))continue;
            double d=balise.target().anchor().distanceToSqr(selection.target().anchor());
            if(d<distance){best=balise;distance=d;}
        }
        // A normal rail cell is one interaction target; curve attachments are
        // selected only within the hovered half-metre segment neighbourhood.
        return selection.curve()==null||distance<1?best:null;
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        if(!event.isUseItem()||event.getHand()!=InteractionHand.MAIN_HAND)return;
        var mc=Minecraft.getInstance();
        if(mc.screen!=null||mc.player==null||mc.level==null||mc.player.isSpectator())return;
        var held=mc.player.getMainHandItem();
        boolean wrench=BaliseService.isWrench(held);
        if(!(held.getItem() instanceof BaliseItem)&&!wrench&&(!held.isEmpty()||!mc.player.getOffhandItem().isEmpty()))return;
        var selection=selection();if(selection==null)return;
        var installed=selected(selection,mc.level);
        if(held.getItem() instanceof BaliseItem) {
            event.setCanceled(true);event.setSwingHand(false);
            // Create's rendered Bezier spans are not complete rail blocks.
            if(selection.curve()!=null)return;
            BalisePackets.sendInstall(selection.target(),InteractionHand.MAIN_HAND);
        } else if(installed!=null) {
            event.setCanceled(true);event.setSwingHand(false);
            BalisePackets.sendInteract(installed.target(),wrench);
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        var camera=event.getCamera().getPosition();
        var pose=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        var model=mc.getModelManager().getModel(MODEL);
        var type=RenderType.solid();
        for(var balise:visible(mc.level)) {
            var target=balise.target();
            if(!mc.level.hasChunkAt(BlockPos.containing(target.anchor()))||target.anchor().distanceToSqr(camera)>128*128)continue;
            if(!event.getFrustum().isVisible(new AABB(target.anchor().add(-1,-.2,-1),target.anchor().add(1,.5,1))))continue;
            pose.pushPose();
            Vec3 relative=target.anchor().subtract(camera);pose.translate(relative.x,relative.y,relative.z);
            // Sleepers top out about 0.08 above the straight reference. Bezier
            // centre lines sit 0.25 higher than their rendered sleeper mesh.
            orient(pose,target.tangent(),target.normal());pose.translate(-.5,target.curve()==null?.1:-.15,-.5);
            mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffers.getBuffer(type),
                    Blocks.STONE.defaultBlockState(),model,1,1,1,LevelRenderer.getLightColor(mc.level,BlockPos.containing(target.anchor()).above()),
                    OverlayTexture.NO_OVERLAY,ModelData.EMPTY,type);
            pose.popPose();
        }
        if(mc.screen==null&&mc.player.getMainHandItem().getItem() instanceof BaliseItem item) {
            var selection=selection();
            if(selection!=null&&selection.curve()==null) {
                pose.pushPose();Vec3 relative=Vec3.atLowerCornerOf(selection.track()).subtract(camera);pose.translate(relative.x,relative.y,relative.z);
                TrackTargetingBehaviour.render(mc.level,selection.track(),selection.positive()?Direction.AxisDirection.POSITIVE:Direction.AxisDirection.NEGATIVE,
                        selection.curve(),pose,buffers,LevelRenderer.getLightColor(mc.level,selection.track()),OverlayTexture.NO_OVERLAY,
                        item.kind()==BaliseData.Kind.LIMIT?TrackTargetingBehaviour.RenderedTrackOverlayType.SIGNAL:TrackTargetingBehaviour.RenderedTrackOverlayType.OBSERVER,1.0625f);
                pose.popPose();
            }
        } else if(mc.screen instanceof BaliseScreen screen&&screen.directionPreview()!=null) {
            var target=screen.directionPreview();pose.pushPose();
            Vec3 relative=Vec3.atLowerCornerOf(target.trackPos()).subtract(camera);pose.translate(relative.x,relative.y,relative.z);
            TrackTargetingBehaviour.render(mc.level,target.trackPos(),target.positive()?Direction.AxisDirection.POSITIVE:Direction.AxisDirection.NEGATIVE,
                    target.curve(),pose,buffers,LevelRenderer.getLightColor(mc.level,target.trackPos()),OverlayTexture.NO_OVERLAY,
                    TrackTargetingBehaviour.RenderedTrackOverlayType.SIGNAL,1.0625f);
            pose.popPose();
        }
        buffers.endBatch(type);
        buffers.endBatch(RenderType.cutoutMipped());
    }
    private static void orient(PoseStack pose,Vec3 tangent,Vec3 normal) {
        Vec3 y=normal.normalize(),z=tangent.subtract(y.scale(tangent.dot(y))).normalize(),x=y.cross(z).normalize();
        var basis=new Matrix3f().setColumn(0,new Vector3f((float)x.x,(float)x.y,(float)x.z))
                .setColumn(1,new Vector3f((float)y.x,(float)y.y,(float)y.z))
                .setColumn(2,new Vector3f((float)z.x,(float)z.y,(float)z.z));
        pose.mulPose(new Quaternionf().setFromNormalized(basis));
    }
    @SubscribeEvent public static void hint(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null||mc.options.hideGui)return;
        var selection=selection();if(selection==null)return;
        Component hint;
        if(mc.player.getMainHandItem().getItem() instanceof BaliseItem)
            hint=Component.translatable(selection.curve()==null?"screen.emucab.balise_install_hint":"screen.emucab.balise_full_track_only");
        else if(BaliseService.isWrench(mc.player.getMainHandItem())&&selected(selection,mc.level)!=null)
            hint=Component.translatable("screen.emucab.balise_remove_hint");
        else if(mc.player.getMainHandItem().isEmpty()&&mc.player.getOffhandItem().isEmpty()&&selected(selection,mc.level)!=null)
            hint=Component.translatable("screen.emucab.balise_open_hint");
        else return;
        var g=event.getGuiGraphics();int x=mc.getWindow().getGuiScaledWidth()/2,y=mc.getWindow().getGuiScaledHeight()/2+25;
        int half=mc.font.width(hint)/2;g.fill(x-half-5,y-3,x+half+5,y+12,0xC0202020);g.drawCenteredString(mc.font,hint,x,y,0xFFE8BB);
    }
}
