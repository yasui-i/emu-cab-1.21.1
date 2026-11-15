package com.yasui_i.emucab.network;

import com.yasui_i.emucab.EMUCab;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Authoritative physical speed sample for the moving desk's live display. */
public record CabSpeedPayload(BlockPos pos,long sequence,double speedKmh) implements CustomPacketPayload {
    public static final Type<CabSpeedPayload> TYPE=new Type<>(EMUCab.id("cab_speed"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CabSpeedPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeBlockPos(p.pos);b.writeVarLong(p.sequence);b.writeDouble(p.speedKmh);},
            b->new CabSpeedPayload(b.readBlockPos(),b.readVarLong(),b.readDouble()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE,CODEC,(packet,context)->
                context.enqueueWork(()-> {
                    var level=net.minecraft.client.Minecraft.getInstance().level;
                    if(level!=null && level.getBlockEntity(packet.pos()) instanceof com.yasui_i.emucab.blockentity.CabDeskBlockEntity desk)
                        desk.acceptSpeedSample(packet.sequence(),packet.speedKmh());
                }));
    }
}
