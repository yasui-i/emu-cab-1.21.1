package com.yasui_i.emucab.network;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.telemetry.*;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;

/** car 1..8 binds; -1..-8 clears a car while at the cab; 0 opens client chooser. */
public record CabTelemetryBindingPayload(BlockPos cab,BlockPos bogey,UUID cabId,int car) implements CustomPacketPayload {
    public static final Type<CabTelemetryBindingPayload> TYPE=new Type<>(EMUCab.id("cab_telemetry_binding"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CabTelemetryBindingPayload> CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.cab);b.writeBlockPos(p.bogey);b.writeUUID(p.cabId);b.writeInt(p.car);},b->new CabTelemetryBindingPayload(b.readBlockPos(),b.readBlockPos(),b.readUUID(),b.readInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playBidirectional(TYPE,CODEC,(p,c)->{
        if(c.player() instanceof ServerPlayer player){
            var result=apply(player,p);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,result);
        }else c.enqueueWork(()->com.yasui_i.emucab.client.CabTelemetryScreen.open(p));
    });}
    private static CabTelemetryBindingResultPayload apply(ServerPlayer player,CabTelemetryBindingPayload p){
        var rejected=new CabTelemetryBindingResultPayload(p.cab,p.car,false,false,0);
        var level=player.serverLevel();var target=p.car<0?p.cab:p.bogey;
        if(p.car==0||p.car< -8||p.car>8||!player.isAlive()||player.isSpectator()||!player.mayBuild()||!level.hasChunkAt(p.cab)||!level.hasChunkAt(target)||!level.mayInteract(player,p.cab)||!level.mayInteract(player,target))return rejected;
        double reach=player.blockInteractionRange()+1;
        if(SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level,player.getEyePosition(),target.getCenter())>reach*reach)return rejected;
        if(!(level.getBlockEntity(p.cab) instanceof CabDeskBlockEntity desk)||!desk.isCenter()||!desk.telemetryId().equals(p.cabId))return rejected;
        if(p.car<0){desk.telemetry().clearCar(-p.car);desk.telemetryChanged();return new CabTelemetryBindingResultPayload(p.cab,p.car,true,false,0);}
        var stack=player.getMainHandItem();if(!stack.is(com.yasui_i.emucab.registry.ModItems.TELEMETRY_TOOL.get()))return rejected;
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!tag.hasUUID("CabId")||!tag.getUUID("CabId").equals(p.cabId)||!tag.getString("Dimension").equals(level.dimension().location().toString()))return rejected;
        var registration=SimurailTelemetry.register(desk,level.getBlockEntity(p.bogey),p.car);
        if(registration.accepted())desk.telemetryChanged();
        return new CabTelemetryBindingResultPayload(p.cab,p.car,registration.accepted(),registration.repaired(),registration.count());
    }

}
