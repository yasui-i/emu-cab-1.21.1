package com.yasui_i.emucab.network;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.control.AtpAnnouncement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Send the desk's storage position, rather than freezing the sound at its initial world position. */
public record CabAtpSoundPayload(BlockPos pos,String cue,float volume) implements CustomPacketPayload {
    public static final Type<CabAtpSoundPayload> TYPE=new Type<>(EMUCab.id("atp_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CabAtpSoundPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeBlockPos(p.pos);b.writeUtf(p.cue,64);b.writeFloat(p.volume);},
            b->new CabAtpSoundPayload(b.readBlockPos(),b.readUtf(64),b.readFloat()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static Vec3 worldPosition(Level level,BlockPos pos) {
        Vec3 local=pos.getCenter();
        var sub=SableCompanion.INSTANCE.getContaining(level,pos);
        return sub==null?local:sub.logicalPose().transformPosition(local);
    }
    public AtpAnnouncement announcement() {
        for(var entry:AtpAnnouncement.values())if(entry.id().equals(cue))return entry;
        return null;
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE,CODEC,(packet,context)->
                context.enqueueWork(()->com.yasui_i.emucab.client.CabAtpSounds.play(packet)));
    }
}
