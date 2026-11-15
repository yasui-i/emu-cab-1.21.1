package com.yasui_i.emucab.network;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Per-cab display preferences edited with Create's wrench. */
public record CabDisplaySettingsPayload(BlockPos pos,int clockMode,int dateMode,String customDate,String locomotiveNumber,double forceScaleKn,String consistId,int endpoint)
        implements CustomPacketPayload {
    public CabDisplaySettingsPayload(BlockPos pos,int clockMode,int dateMode,String customDate,String locomotiveNumber){this(pos,clockMode,dateMode,customDate,locomotiveNumber,com.yasui_i.emucab.control.CabDisplayFormat.DEFAULT_FORCE_SCALE_KN,"",0);}
    public static final Type<CabDisplaySettingsPayload> TYPE=new Type<>(EMUCab.id("cab_display_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CabDisplaySettingsPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeBlockPos(p.pos);b.writeVarInt(p.clockMode);b.writeVarInt(p.dateMode);
                b.writeUtf(p.customDate,16);b.writeUtf(p.locomotiveNumber,32);b.writeDouble(p.forceScaleKn);b.writeUtf(p.consistId,24);b.writeVarInt(p.endpoint);},
            b->new CabDisplaySettingsPayload(b.readBlockPos(),b.readVarInt(),b.readVarInt(),b.readUtf(16),b.readUtf(32),b.readDouble(),b.readUtf(24),b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}

    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("3").playToServer(TYPE,CODEC,(packet,context)->{
            if(!(context.player() instanceof ServerPlayer player)||!player.isAlive()||player.isSpectator()
                    ||!player.mayBuild())return;
            var level=player.serverLevel();
            if(!level.hasChunkAt(packet.pos())||!level.mayInteract(player,packet.pos()))return;
            double reach=player.blockInteractionRange()+1;
            if(SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level,player.getEyePosition(),packet.pos().getCenter())>reach*reach)return;
            var center=CabDeskBlock.findCenter(level,packet.pos(),level.getBlockState(packet.pos()));
            if(center==null||!(level.getBlockEntity(center) instanceof CabDeskBlockEntity desk)||!desk.isCenter())return;
            // The server rechecks the tool so a forged packet cannot edit a cab remotely.
            if(!com.yasui_i.emucab.balise.BaliseService.isWrench(player.getMainHandItem()))return;
            desk.setDisplaySettings(packet.clockMode,packet.dateMode,packet.customDate,packet.locomotiveNumber,packet.forceScaleKn);
            desk.setConsistEndpoint(packet.consistId,packet.endpoint);
        });
    }
}
