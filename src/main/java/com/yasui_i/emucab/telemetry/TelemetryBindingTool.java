package com.yasui_i.emucab.telemetry;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.registry.ModItems;
import com.yasui_i.emucab.network.CabTelemetryBindingPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=EMUCab.MOD_ID)
public final class TelemetryBindingTool {
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickBlock event){
        if(!event.getItemStack().is(ModItems.TELEMETRY_TOOL.get()))return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        if(!(event.getEntity() instanceof ServerPlayer player)||event.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND||!player.isAlive()||player.isSpectator()||!player.mayBuild()||!event.getLevel().mayInteract(player,event.getPos()))return;
        var level=event.getLevel();var pos=event.getPos();var be=level.getBlockEntity(pos);
        var center=CabDeskBlock.findCenter(level,pos,level.getBlockState(pos));
        if(center!=null&&level.getBlockEntity(center) instanceof CabDeskBlockEntity desk){
            var tag=new CompoundTag();tag.putLong("Cab",center.asLong());tag.putUUID("CabId",desk.telemetryId());tag.putString("Dimension",level.dimension().location().toString());
            event.getItemStack().set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
            player.displayClientMessage(Component.translatable("message.emucab.telemetry_selected"),true);return;
        }
        boolean receiver=com.yasui_i.emucab.control.CeeElectricalTelemetry.isReceiver(be);
        if(!receiver&&!SimurailTelemetry.isBogey(be))return;
        var tag=event.getItemStack().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        var cab=net.minecraft.core.BlockPos.of(tag.getLong("Cab"));
        if(!tag.hasUUID("CabId")||!tag.getString("Dimension").equals(level.dimension().location().toString())||!level.hasChunkAt(cab)
                ||!(level.getBlockEntity(cab) instanceof CabDeskBlockEntity desk)||!desk.telemetryId().equals(tag.getUUID("CabId"))){
            player.displayClientMessage(Component.translatable("message.emucab.telemetry_select_first"),true);return;
        }
        if(receiver)PacketDistributor.sendToPlayer(player,new com.yasui_i.emucab.network.CabPantographBindingPayload(cab,pos,tag.getUUID("CabId"),0));
        else PacketDistributor.sendToPlayer(player,new CabTelemetryBindingPayload(cab,pos,tag.getUUID("CabId"),0));
    }
}
