package com.yasui_i.emucab.network;

import com.yasui_i.emucab.EMUCab;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Ephemeral actuation event. Chunk loads and saved data cannot replay an old button press. */
public record CabControlAnimationPayload(BlockPos pos,String id,long sequence) implements CustomPacketPayload {
    public static final Type<CabControlAnimationPayload> TYPE=new Type<>(EMUCab.id("control_animation"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CabControlAnimationPayload> CODEC=StreamCodec.of(
            (buffer,packet)->{buffer.writeBlockPos(packet.pos);buffer.writeUtf(packet.id,128);buffer.writeVarLong(packet.sequence);},
            buffer->new CabControlAnimationPayload(buffer.readBlockPos(),buffer.readUtf(128),buffer.readVarLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE,CODEC,(packet,context)->
                com.yasui_i.emucab.client.CabControlAnimations.receive(packet));
    }
}
