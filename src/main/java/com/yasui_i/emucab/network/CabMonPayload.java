package com.yasui_i.emucab.network;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** MON device isolation menu, opened by the existing right-inner display softkey. */
public record CabMonPayload(BlockPos pos,boolean cutFront,boolean cutRear) implements CustomPacketPayload {
    public static final Type<CabMonPayload> TYPE=new Type<>(EMUCab.id("cab_mon"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CabMonPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeBlockPos(p.pos);b.writeBoolean(p.cutFront);b.writeBoolean(p.cutRear);},
            b->new CabMonPayload(b.readBlockPos(),b.readBoolean(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void open(ServerPlayer player,CabDeskBlockEntity desk){
        if(desk.controls().displaysActive())PacketDistributor.sendToPlayer(player,new CabMonPayload(desk.getBlockPos(),
                desk.pantographs().cutFront,desk.pantographs().cutRear));
    }
    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("1").playBidirectional(TYPE,CODEC,(packet,context)->{
            if(context.player() instanceof ServerPlayer player){
                var level=player.serverLevel();
                if(!player.isAlive()||player.isSpectator()||!player.mayBuild()||!player.getMainHandItem().isEmpty()
                        ||!level.hasChunkAt(packet.pos)||!level.mayInteract(player,packet.pos))return;
                double reach=player.blockInteractionRange()+1;
                if(SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level,player.getEyePosition(),packet.pos.getCenter())>reach*reach)return;
                if(level.getBlockEntity(packet.pos) instanceof CabDeskBlockEntity desk && desk.isCenter()
                        &&desk.controls().displaysActive())desk.configurePantographs(packet.cutFront,packet.cutRear);
            }else com.yasui_i.emucab.client.CabMonScreen.open(packet);
        });
    }
}
