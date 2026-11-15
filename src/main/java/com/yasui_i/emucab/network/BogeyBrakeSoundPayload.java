package com.yasui_i.emucab.network;

import com.yasui_i.emucab.EMUCab;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;

/** Storage coordinates identify the moving sound source; UUID rejects replaced bogeys. */
public record BogeyBrakeSoundPayload(BlockPos pos,UUID bogey,float volume,float radius) implements CustomPacketPayload {
    public static final Type<BogeyBrakeSoundPayload> TYPE=new Type<>(EMUCab.id("bogey_brake_release"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BogeyBrakeSoundPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeBlockPos(p.pos);b.writeUUID(p.bogey);b.writeFloat(p.volume);b.writeFloat(p.radius);},
            b->new BogeyBrakeSoundPayload(b.readBlockPos(),b.readUUID(),b.readFloat(),b.readFloat()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE,CODEC,(packet,context)->
                context.enqueueWork(()->com.yasui_i.emucab.client.BogeyBrakeReleaseSounds.play(packet)));
    }
}
