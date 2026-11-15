package com.yasui_i.emucab.network;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.client.CabTelemetryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server acknowledgement for a binding attempt; the chooser stays open until this arrives. */
public record CabTelemetryBindingResultPayload(BlockPos cab,int car,boolean accepted,boolean repaired,int count) implements CustomPacketPayload {
    public static final Type<CabTelemetryBindingResultPayload> TYPE=new Type<>(EMUCab.id("cab_telemetry_binding_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CabTelemetryBindingResultPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeBlockPos(p.cab);b.writeVarInt(p.car);b.writeBoolean(p.accepted);b.writeBoolean(p.repaired);b.writeVarInt(p.count);},
            b->new CabTelemetryBindingResultPayload(b.readBlockPos(),b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(TYPE,CODEC,(p,c)->c.enqueueWork(()->CabTelemetryScreen.result(p)));}
}
