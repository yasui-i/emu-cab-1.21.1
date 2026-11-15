package com.yasui_i.emucab.network;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.telemetry.CabTelemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record CabTelemetryPayload(BlockPos pos,long sequence,CabTelemetry.Car[] cars) implements CustomPacketPayload {
    public static final Type<CabTelemetryPayload> TYPE=new Type<>(EMUCab.id("cab_telemetry"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CabTelemetryPayload> CODEC=StreamCodec.of((b,p)->{
        b.writeBlockPos(p.pos);b.writeVarLong(p.sequence);
        for(var c:p.cars){b.writeVarInt(c.bound());b.writeVarInt(c.loaded());b.writeVarInt(c.measured());b.writeDouble(c.traction());b.writeDouble(c.brake());b.writeFloat(c.localCommand());b.writeFloat(c.effectiveCommand());b.writeBoolean(c.fault());b.writeBoolean(c.slip());}
    },b->{
        var pos=b.readBlockPos();long sequence=b.readVarLong();var cars=CabTelemetry.empty();
        for(int i=0;i<8;i++)cars[i]=new CabTelemetry.Car(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readDouble(),b.readDouble(),b.readFloat(),b.readFloat(),b.readBoolean(),b.readBoolean());
        return new CabTelemetryPayload(pos,sequence,cars);
    });
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(TYPE,CODEC,(p,c)->c.enqueueWork(()->{
        var level=net.minecraft.client.Minecraft.getInstance().level;
        if(level!=null&&level.getBlockEntity(p.pos) instanceof com.yasui_i.emucab.blockentity.CabDeskBlockEntity desk)desk.telemetry().accept(p.sequence,level.getGameTime(),p.cars);
    }));}
}
