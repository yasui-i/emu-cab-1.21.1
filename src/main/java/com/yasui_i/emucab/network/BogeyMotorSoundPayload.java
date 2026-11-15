package com.yasui_i.emucab.network;

import com.yasui_i.emucab.EMUCab;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;

/** Wheel RPM drives motor pitch; independently measured body velocity drives the air layer. */
public record BogeyMotorSoundPayload(BlockPos pos,UUID bogey,long sequence,float wheelRpm,
                                     float bodySpeedKmh,float traction01,float brake01,boolean motorPowered) implements CustomPacketPayload {
    /** Source compatibility for existing integrations: an unknown body speed must not invent wind. */
    public BogeyMotorSoundPayload(BlockPos pos,UUID bogey,long sequence,float wheelRpm,
                                  float traction01,float brake01,boolean motorPowered) {
        this(pos,bogey,sequence,wheelRpm,0,traction01,brake01,motorPowered);
    }
    public static final Type<BogeyMotorSoundPayload> TYPE=new Type<>(EMUCab.id("bogey_motor"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BogeyMotorSoundPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeBlockPos(p.pos);b.writeUUID(p.bogey);b.writeVarLong(p.sequence);b.writeFloat(p.wheelRpm);
                b.writeFloat(p.bodySpeedKmh);b.writeFloat(p.traction01);b.writeFloat(p.brake01);b.writeBoolean(p.motorPowered);},
            b->new BogeyMotorSoundPayload(b.readBlockPos(),b.readUUID(),b.readVarLong(),b.readFloat(),
                    b.readFloat(),b.readFloat(),b.readFloat(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(TYPE,CODEC,(packet,context)->
                context.enqueueWork(()->com.yasui_i.emucab.client.BogeyMotorSounds.accept(packet)));
    }
}
