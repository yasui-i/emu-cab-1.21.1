package com.yasui_i.emucab.network;
import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.control.CeeElectricalTelemetry;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;
/** car 0 opens chooser; car3/6 assigns a receiver to the explicitly selected consist. */
public record CabPantographBindingPayload(BlockPos cab,BlockPos receiver,UUID cabId,int car) implements CustomPacketPayload {
    public static final Type<CabPantographBindingPayload> TYPE=new Type<>(EMUCab.id("cab_pantograph_binding"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CabPantographBindingPayload> CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.cab);b.writeBlockPos(p.receiver);b.writeUUID(p.cabId);b.writeVarInt(p.car);},b->new CabPantographBindingPayload(b.readBlockPos(),b.readBlockPos(),b.readUUID(),b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playBidirectional(TYPE,CODEC,(p,c)->{
        if(c.player() instanceof ServerPlayer player){
            var level=player.serverLevel();
            if((p.car!=3&&p.car!=6)||!player.isAlive()||player.isSpectator()||!player.mayBuild()||!level.hasChunkAt(p.cab)||!level.hasChunkAt(p.receiver)||!level.mayInteract(player,p.receiver))return;
            double reach=player.blockInteractionRange()+1;
            if(dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level,player.getEyePosition(),p.receiver.getCenter())>reach*reach)return;
            var stack=player.getMainHandItem();if(!stack.is(ModItems.TELEMETRY_TOOL.get()))return;
            var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            if(!tag.hasUUID("CabId")||!tag.getUUID("CabId").equals(p.cabId)||tag.getLong("Cab")!=p.cab.asLong()||!tag.getString("Dimension").equals(level.dimension().location().toString()))return;
            if(!(level.getBlockEntity(p.cab) instanceof CabDeskBlockEntity desk)||!desk.isCenter()||!desk.telemetryId().equals(p.cabId))return;
            if(desk.consistId().isBlank()){player.displayClientMessage(Component.literal("请先用扳手为驾驶台设置编组 ID。"),true);return;}
            boolean result=CeeElectricalTelemetry.bind(level.getBlockEntity(p.receiver),desk.consistId(),p.car);
            player.displayClientMessage(Component.literal(result?String.format("受电弓已登记为 %s / %02d 车",desk.consistId(),p.car):"受电弓登记失败，请检查附属模组版本。"),true);
        }else c.enqueueWork(()->com.yasui_i.emucab.client.CabPantographBindingScreen.open(p));
    });}
}
