package com.yasui_i.emucab.network;
import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.control.CeeElectricalTelemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
public record CabElectricalPayload(BlockPos pos,long sequence,CeeElectricalTelemetry.Reading car03,CeeElectricalTelemetry.Reading car06) implements CustomPacketPayload {
    public static final Type<CabElectricalPayload> TYPE=new Type<>(EMUCab.id("cab_electrical"));
    private static void write(RegistryFriendlyByteBuf b,CeeElectricalTelemetry.Reading r){b.writeBoolean(r.online());b.writeBoolean(r.raised());b.writeBoolean(r.contact());b.writeBoolean(r.breaker());b.writeDouble(r.voltage());b.writeDouble(r.current());}
    private static CeeElectricalTelemetry.Reading read(RegistryFriendlyByteBuf b){return new CeeElectricalTelemetry.Reading(b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readDouble(),b.readDouble());}
    public static final StreamCodec<RegistryFriendlyByteBuf,CabElectricalPayload> CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.pos);b.writeVarLong(p.sequence);write(b,p.car03);write(b,p.car06);},b->new CabElectricalPayload(b.readBlockPos(),b.readVarLong(),read(b),read(b)));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToClient(TYPE,CODEC,(p,c)->c.enqueueWork(()->{
        var level=net.minecraft.client.Minecraft.getInstance().level;
        if(level!=null&&level.getBlockEntity(p.pos) instanceof com.yasui_i.emucab.blockentity.CabDeskBlockEntity desk&&desk.isCenter())CeeElectricalTelemetry.accept(desk,p.sequence,p.car03,p.car06);
    }));}
}
