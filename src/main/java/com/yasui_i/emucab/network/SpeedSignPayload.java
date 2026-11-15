package com.yasui_i.emucab.network;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.blockentity.SpeedSignBlockEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record SpeedSignPayload(BlockPos pos,int limit,boolean reverse) implements CustomPacketPayload {
    public static final Type<SpeedSignPayload> TYPE=new Type<>(EMUCab.id("speed_sign"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SpeedSignPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeBlockPos(p.pos);b.writeInt(p.limit);b.writeBoolean(p.reverse);},
            b->new SpeedSignPayload(b.readBlockPos(),b.readInt(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE,CODEC,(packet,context)-> {
            if(!(context.player() instanceof ServerPlayer player))return;
            var level=player.serverLevel();
            if(packet.limit<1 || packet.limit>1000 || !player.isAlive() || player.isSpectator() || !player.mayBuild()
                    || !level.hasChunkAt(packet.pos) || !level.mayInteract(player,packet.pos))return;
            double reach=player.blockInteractionRange()+1;
            if(SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level,player.getEyePosition(),packet.pos.getCenter())>reach*reach)return;
            if(level.getBlockEntity(packet.pos) instanceof SpeedSignBlockEntity sign)sign.configure(packet.limit,packet.reverse);
        });
    }
}
