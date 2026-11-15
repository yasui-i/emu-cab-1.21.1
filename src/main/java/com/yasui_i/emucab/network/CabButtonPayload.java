package com.yasui_i.emucab.network;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.*;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.WeakHashMap;

/** The HUD's local ray survives Sable world-pose changes between input and server handling. */
public record CabButtonPayload(BlockPos pos,String number,Vec3 eye,Vec3 surface) implements CustomPacketPayload {
    public static final Type<CabButtonPayload> TYPE=new Type<>(EMUCab.id("button_use"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CabButtonPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeBlockPos(p.pos);b.writeUtf(p.number,8);write(b,p.eye);write(b,p.surface);},
            b->new CabButtonPayload(b.readBlockPos(),b.readUtf(8),read(b),read(b)));
    private static void write(RegistryFriendlyByteBuf b,Vec3 p) { b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z); }
    private static Vec3 read(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(),b.readDouble(),b.readDouble()); }
    private static final WeakHashMap<ServerPlayer,Long> LAST_USE=new WeakHashMap<>();
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE,CODEC,(packet,context)-> {
            if(context.player() instanceof ServerPlayer player)packet.handle(player);
        });
    }
    private void handle(ServerPlayer player) {
        var level=player.serverLevel();
        if(player.isSpectator() || !player.isAlive() || !player.getMainHandItem().isEmpty()
                || !player.mayBuild() || !level.hasChunkAt(pos) || !level.mayInteract(player,pos))return;
        if(!(level.getBlockEntity(pos) instanceof CabDeskBlockEntity desk) || !desk.isCenter())return;
        long now=level.getGameTime();
        if(now-LAST_USE.getOrDefault(player,Long.MIN_VALUE/2)<4)return;
        var facing=desk.getBlockState().getValue(CabDeskBlock.FACING);
        Vec3 actualEye=CabHitTesting.toModel(pos,facing,CabHitTesting.eyeInDesk(player,pos));
        var sub=SableCompanion.INSTANCE.getContaining(level,pos);
        if(number.equals("B035") && sub!=null) {
            if(CabButtonValidation.movingHandle(eye,surface,actualEye,player.blockInteractionRange(),desk.leverPosition())!=null) {
                LAST_USE.put(player,now);CabHandlePayload.begin(player,desk);
            }
            return;
        }
        var control=CabButtonValidation.pick(number,eye,surface,actualEye,player.blockInteractionRange(),desk.leverPosition());
        if(control==null)return;
        // Compare with the current server look in desk coordinates, not a stale world pose.
        Vec3 currentLocalEye=CabHitTesting.fromModel(pos,facing,actualEye);
        Vec3 rawEye=player.getEyePosition(),worldEnd=rawEye.add(player.getLookAngle());
        Vec3 localEnd=sub==null?worldEnd:sub.logicalPose().transformPositionInverse(worldEnd);
        // Convert direction using inverse pose without introducing the rider's position correction.
        Vec3 rawLocalEye=sub==null?rawEye:sub.logicalPose().transformPositionInverse(rawEye);
        Vec3 look=CabHitTesting.toModel(pos,facing,localEnd).subtract(CabHitTesting.toModel(pos,facing,rawLocalEye)).normalize();
        if(look.dot(surface.subtract(eye).normalize())<.7)return;
        // Reject walls, while allowing the cab's deliberately coarse shell ahead of a recessed button.
        Vec3 localTarget=CabHitTesting.fromModel(pos,facing,CabHitTesting.bounds(control,desk.leverPosition()).getCenter());
        Vec3 worldTarget=sub==null?localTarget:sub.logicalPose().transformPosition(localTarget);
        Vec3 worldEye=sub==null?currentLocalEye:sub.logicalPose().transformPosition(currentLocalEye);
        var hit=level.clip(new ClipContext(worldEye,worldTarget,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(hit.getType()==HitResult.Type.BLOCK && !pos.equals(CabDeskBlock.findCenter(level,hit.getBlockPos(),level.getBlockState(hit.getBlockPos())))
                && hit.getLocation().distanceTo(worldEye)+.25<worldTarget.distanceTo(worldEye))return;
        LAST_USE.put(player,now);
        if(control.action()==CabAction.HANDLE)CabHandlePayload.begin(player,desk);
        else desk.activate(control,player);
    }
}
