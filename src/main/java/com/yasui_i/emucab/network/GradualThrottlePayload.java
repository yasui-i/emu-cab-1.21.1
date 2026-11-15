package com.yasui_i.emucab.network;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.blockentity.GradualThrottleBlockEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record GradualThrottlePayload(BlockPos pos,int interval,int rpmPerLevel) implements CustomPacketPayload {
    public static final Type<GradualThrottlePayload> TYPE=new Type<>(EMUCab.id("gradual_throttle"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GradualThrottlePayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeBlockPos(p.pos);b.writeVarInt(p.interval);b.writeVarInt(p.rpmPerLevel);},
            b->new GradualThrottlePayload(b.readBlockPos(),b.readVarInt(),b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("2").playToServer(TYPE,CODEC,(packet,context)->{
            if(!(context.player() instanceof ServerPlayer player))return;
            var level=player.serverLevel();
            if(packet.interval<1||packet.interval>200||packet.rpmPerLevel<1||!player.isAlive()||player.isSpectator()
                    ||!player.mayBuild()||!level.hasChunkAt(packet.pos)||!level.mayInteract(player,packet.pos))return;
            double reach=player.blockInteractionRange()+1;
            if(SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level,player.getEyePosition(),packet.pos.getCenter())>reach*reach)return;
            if(level.getBlockEntity(packet.pos) instanceof GradualThrottleBlockEntity throttle)throttle.configure(packet.interval,packet.rpmPerLevel);
        });
    }
}
