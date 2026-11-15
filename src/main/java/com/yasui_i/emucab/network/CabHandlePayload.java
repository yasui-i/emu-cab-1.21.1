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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.WeakHashMap;

/** A short lived server-authorized grip, followed by absolute notch updates. */
public record CabHandlePayload(BlockPos pos,int phase,int notch) implements CustomPacketPayload {
    public static final Type<CabHandlePayload> TYPE=new Type<>(EMUCab.id("handle_drag"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CabHandlePayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeBlockPos(p.pos);b.writeByte(p.phase);b.writeByte(p.notch);},
            b->new CabHandlePayload(b.readBlockPos(),b.readByte(),b.readByte()));
    /** Keep the server-authorized entity itself: its local block position stays stable while Sable moves the body. */
    private record Grip(CabDeskBlockEntity desk,BlockPos pos,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,long tick) {}
    private static final WeakHashMap<ServerPlayer,Grip> GRIPS=new WeakHashMap<>();
    public static void begin(ServerPlayer player,CabDeskBlockEntity desk) {
        GRIPS.put(player,new Grip(desk,desk.getBlockPos().immutable(),player.level().dimension(),player.level().getGameTime()));
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE,CODEC,(packet,context)-> {
            if(context.player() instanceof ServerPlayer player)packet.handle(player);
        });
    }
    private void handle(ServerPlayer player) {
        var level=player.serverLevel();
        if(phase==2) {
            Grip active=GRIPS.get(player);
            if(active==null && notch>=-8 && notch<=7) {
                CabDeskBlockEntity fallback=findDesk(level,pos);
                if(fallback!=null)active=new Grip(fallback,fallback.getBlockPos().immutable(),level.dimension(),level.getGameTime());
            }
            if(active!=null && active.dimension.equals(level.dimension()) && notch>=-8 && notch<=7) {
                // The release packet is also the reliable final commit. This covers a
                // delayed final drag packet while Sable is moving the assembly.
                CabDeskBlockEntity desk=active.desk;
                if(level.getBlockEntity(active.pos) instanceof CabDeskBlockEntity current && current.isCenter())desk=current;
                if(!desk.isRemoved())desk.dragHandle(notch,player);
            }
            GRIPS.remove(player);return;
        }
        if(phase<0 || phase>1 || notch< -8 || notch>7 || player.isSpectator() || !player.isAlive())return;
        CabDeskBlockEntity desk;
        if(phase==1) {
            Grip active=GRIPS.get(player);
            if(active==null) {
                desk=findDesk(level,pos);
                if(desk==null) return;
                active=new Grip(desk,desk.getBlockPos().immutable(),level.dimension(),level.getGameTime());
                GRIPS.put(player,active);
            }
            if(!active.dimension.equals(level.dimension())) { GRIPS.remove(player); return; }
            desk=active.desk;
            // Sable can replace a block entity while remapping an assembly. Prefer
            // the current entity at the same local storage position when available.
            if(level.getBlockEntity(active.pos) instanceof CabDeskBlockEntity current && current.isCenter())desk=current;
            if(desk.isRemoved()) { GRIPS.remove(player); return; }
        } else {
            if(!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof CabDeskBlockEntity found) || !found.isCenter())return;
            desk=found;
        }
        double reach=player.blockInteractionRange();
        // During an active grip, avoid re-running the static-world distance test:
        // Sable may update the body's
        // world pose one tick after the cab entity, which otherwise drops every drag packet.
        if(phase==0 && SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level,player.getEyePosition(),desk.getBlockPos().getCenter())>(reach+1.5)*(reach+1.5)) {
            GRIPS.remove(player);return;
        }
        long now=level.getGameTime();
        if(phase==0) {
            Vec3 eye=player.getEyePosition(),end=eye.add(player.getLookAngle().scale(reach));
            BlockHitResult hit=level.clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
            var center=CabDeskBlock.findCenter(level,hit.getBlockPos(),level.getBlockState(hit.getBlockPos()));
            if(!desk.getBlockPos().equals(center))return;
            var facing=desk.getBlockState().getValue(CabDeskBlock.FACING);
            var control=CabHitTesting.pickRay(CabHitTesting.toModel(desk.getBlockPos(),facing,CabHitTesting.eyeInDesk(player,desk.getBlockPos())),
                    CabHitTesting.toModel(desk.getBlockPos(),facing,hit.getLocation()),reach*16,desk.leverPosition());
            if(control==null || control.action()!=CabAction.HANDLE)return;
            GRIPS.put(player,new Grip(desk,desk.getBlockPos().immutable(),level.dimension(),now));
            return;
        }
        Grip grip=GRIPS.get(player);
        if(grip==null || !grip.dimension.equals(level.dimension()) || now-grip.tick>100) {
            GRIPS.remove(player);return;
        }
        GRIPS.put(player,new Grip(desk,desk.getBlockPos().immutable(),level.dimension(),now));
        desk.dragHandle(notch,player);
    }
    private static CabDeskBlockEntity findDesk(net.minecraft.server.level.ServerLevel level,BlockPos pos) {
        if(level.getBlockEntity(pos) instanceof CabDeskBlockEntity desk && desk.isCenter())return desk;
        BlockPos center=CabDeskBlock.findCenter(level,pos,level.getBlockState(pos));
        return center!=null && level.getBlockEntity(center) instanceof CabDeskBlockEntity desk && desk.isCenter()?desk:null;
    }
}
