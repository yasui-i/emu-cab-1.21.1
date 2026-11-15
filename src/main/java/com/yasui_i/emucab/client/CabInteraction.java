package com.yasui_i.emucab.client;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid=EMUCab.MOD_ID,value=Dist.CLIENT)
public final class CabInteraction {
    public record Target(CabDeskBlockEntity desk,CabGeometry.Control control,net.minecraft.world.phys.Vec3 eye,net.minecraft.world.phys.Vec3 surface) {}
    private static final java.util.Map<CabDeskBlockEntity,Long> VISIBLE=new java.util.WeakHashMap<>();
    private static Target shownHandle;
    private static long shownHandleAt;
    static void visible(CabDeskBlockEntity desk){VISIBLE.put(desk,desk.getLevel().getGameTime());}
    static net.minecraft.world.phys.Vec3 localEye(CabDeskBlockEntity desk){
        return toLocal(desk,Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }
    private static net.minecraft.world.phys.Vec3 toLocal(CabDeskBlockEntity desk,net.minecraft.world.phys.Vec3 world){
        var sub=dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(desk.getLevel(),desk.getBlockPos());
        var pose=sub instanceof dev.ryanhcode.sable.companion.ClientSubLevelAccess client?client.renderPose():sub==null?null:sub.logicalPose();
        return pose==null?world:pose.transformPositionInverse(world);
    }
    public static Target target() {
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.player.isSpectator())return null;
        java.util.Set<CabDeskBlockEntity> candidates=new java.util.HashSet<>();
        // Raycast visible controls directly: a raised handle can be outside the
        // coarse cabinet outline, and Sable's logical pose can lag its render pose.
        VISIBLE.entrySet().removeIf(entry->entry.getKey().isRemoved()||entry.getKey().getLevel()!=mc.level||mc.level.getGameTime()-entry.getValue()>40);
        candidates.addAll(VISIBLE.keySet());
        if(mc.hitResult instanceof BlockHitResult hit){
            var center=CabDeskBlock.findCenter(mc.level,hit.getBlockPos(),mc.level.getBlockState(hit.getBlockPos()));
            if(center!=null&&mc.level.getBlockEntity(center) instanceof CabDeskBlockEntity desk)candidates.add(desk);
        }
        var camera=mc.gameRenderer.getMainCamera();var worldEye=camera.getPosition();
        var direction=new net.minecraft.world.phys.Vec3(camera.getLookVector());
        Target best=null;double nearest=Double.MAX_VALUE;double reach=mc.player.blockInteractionRange();
        for(var desk:candidates){
            var center=desk.getBlockPos();var facing=desk.getBlockState().getValue(CabDeskBlock.FACING);
            var eye=CabHitTesting.toModel(center,facing,toLocal(desk,worldEye));
            if(eye.distanceToSqr(new net.minecraft.world.phys.Vec3(8,16,8))>Math.pow((reach+3)*16,2))continue;
            var surface=CabHitTesting.toModel(center,facing,toLocal(desk,worldEye.add(direction.scale(reach))));
            var c=CabHitTesting.pickRay(eye,surface,reach*16,desk.leverPosition());
            if(c==null)continue;
            var box=CabHitTesting.bounds(c,desk.leverPosition());
            double distance=box.getCenter().distanceToSqr(eye);
            if(mc.hitResult instanceof BlockHitResult hit&&hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK){
                var hitCenter=CabDeskBlock.findCenter(mc.level,hit.getBlockPos(),mc.level.getBlockState(hit.getBlockPos()));
                if(!center.equals(hitCenter)){
                    var hitSub=dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(mc.level,hit.getBlockPos());
                    var hitPose=hitSub instanceof dev.ryanhcode.sable.companion.ClientSubLevelAccess client?client.renderPose():hitSub==null?null:hitSub.logicalPose();
                    var hitWorld=hitPose==null?hit.getLocation():hitPose.transformPosition(hit.getLocation());
                    if(hitWorld.distanceToSqr(worldEye)+.01<distance/256)continue;
                }
            }
            if(distance<nearest){nearest=distance;best=new Target(desk,c,eye,surface);}
        }
        return best;
    }
    @SubscribeEvent public static void use(InputEvent.InteractionKeyMappingTriggered e) {
        if(!e.isUseItem())return;
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof CabHandleScreen) {e.setCanceled(true);e.setSwingHand(false);return;}
        if(mc.screen!=null || e.getHand()!=InteractionHand.MAIN_HAND)return;
        if(mc.player!=null && !mc.player.isSpectator() && mc.level!=null
                && com.yasui_i.emucab.balise.BaliseService.isWrench(mc.player.getMainHandItem())
                && mc.hitResult instanceof BlockHitResult hit){
            var center=CabDeskBlock.findCenter(mc.level,hit.getBlockPos(),mc.level.getBlockState(hit.getBlockPos()));
            if(center!=null && mc.level.getBlockEntity(center) instanceof CabDeskBlockEntity desk){
                e.setCanceled(true);e.setSwingHand(false);mc.setScreen(new CabDisplaySettingsScreen(desk));return;
            }
        }
        // Other held tools (including DBW wire/cutters) reach their own handlers.
        if(mc.player==null || !mc.player.getMainHandItem().isEmpty())return;
        if(mc.level!=null && mc.hitResult instanceof BlockHitResult hit
                && mc.level.getBlockEntity(hit.getBlockPos()) instanceof com.yasui_i.emucab.blockentity.SpeedSignBlockEntity sign
                && mc.player!=null && mc.player.getMainHandItem().isEmpty() && !mc.player.isSpectator()) {
            e.setCanceled(true);e.setSwingHand(false);mc.setScreen(new SpeedSignScreen(sign));return;
        }
        if(mc.level!=null && mc.hitResult instanceof BlockHitResult hit
                && mc.level.getBlockEntity(hit.getBlockPos()) instanceof com.yasui_i.emucab.blockentity.GradualThrottleBlockEntity throttle
                && mc.player!=null && mc.player.getMainHandItem().isEmpty() && mc.player.getOffhandItem().isEmpty() && !mc.player.isSpectator()) {
            e.setCanceled(true);e.setSwingHand(false);mc.setScreen(new GradualThrottleScreen(throttle));return;
        }
        var target=target();
        // Reuse the same recent render ray that drew the handle tooltip. A body
        // correction between that frame and the input event must not lose the grip.
        if(target==null && shownHandle!=null && System.nanoTime()-shownHandleAt<150_000_000L
                && !shownHandle.desk().isRemoved() && shownHandle.desk().getLevel()==mc.level)target=shownHandle;
        if(target==null)return;
        e.setCanceled(true);e.setSwingHand(false);
        if(target.control.action()==CabAction.HANDLE)mc.setScreen(new CabHandleScreen(target));
        else net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.yasui_i.emucab.network.CabButtonPayload(
                target.desk.getBlockPos(),target.control.number(),target.eye,target.surface));
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e) {
        var mc=Minecraft.getInstance();
        if(mc.screen!=null || mc.options.hideGui)return;
        var target=target();
        shownHandle=target!=null && target.control().action()==CabAction.HANDLE?target:null;
        if(shownHandle!=null)shownHandleAt=System.nanoTime();
        if(target!=null)CabTooltip.draw(e.getGuiGraphics(),target,false);
    }
    public static float leverPosition(CabDeskBlockEntity desk) {
        var screen=Minecraft.getInstance().screen;
        return screen instanceof CabHandleScreen drag && drag.target.desk()==desk?drag.visualPosition():desk.leverPosition();
    }
    private CabInteraction() {}
}
