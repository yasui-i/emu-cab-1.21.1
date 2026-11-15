package com.yasui_i.emucab.balise;

import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.yasui_i.emucab.EMUCab;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;
import java.util.*;

public final class BalisePackets {
    // Leave ample room below Minecraft's one-MiB custom-payload ceiling.
    public static final int MAX_SNAPSHOT_DEVICES=1024;
    private BalisePackets(){}
    public static void sendInstall(BaliseTarget target,InteractionHand hand){PacketDistributor.sendToServer(new Action(target.trackPos(),target.curve(),target.positive(),0,hand));}
    public static void sendInteract(BaliseTarget target,boolean remove){PacketDistributor.sendToServer(new Action(target.trackPos(),target.curve(),target.positive(),remove?2:1,InteractionHand.MAIN_HAND));}
    public static void sendConfigure(UUID id,int limit,String cue,boolean enabled,boolean reverse){PacketDistributor.sendToServer(new Configure(id,limit,cue,enabled,reverse));}

    public record Action(BlockPos trackPos,@Nullable BezierTrackPointLocation curve,boolean positive,int action,InteractionHand hand) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(EMUCab.id("balise_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((b,p)->{
            b.writeBlockPos(p.trackPos);b.writeBoolean(p.curve!=null);if(p.curve!=null){b.writeBlockPos(p.curve.curveTarget());b.writeVarInt(p.curve.segment());}
            b.writeBoolean(p.positive);b.writeVarInt(p.action);b.writeEnum(p.hand);
        },b->{var pos=b.readBlockPos();var curve=b.readBoolean()?new BezierTrackPointLocation(b.readBlockPos(),b.readVarInt()):null;
            return new Action(pos,curve,b.readBoolean(),b.readVarInt(),b.readEnum(InteractionHand.class));});
        @Override public Type<Action> type(){return TYPE;}
    }
    public record Configure(UUID id,int limit,String cue,boolean enabled,boolean reverse) implements CustomPacketPayload {
        public static final Type<Configure> TYPE=new Type<>(EMUCab.id("balise_configure"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Configure> CODEC=StreamCodec.of((b,p)->{
            b.writeUUID(p.id);b.writeVarInt(p.limit);b.writeUtf(p.cue,64);b.writeBoolean(p.enabled);b.writeBoolean(p.reverse);
        },b->new Configure(b.readUUID(),b.readVarInt(),b.readUtf(64),b.readBoolean(),b.readBoolean()));
        @Override public Type<Configure> type(){return TYPE;}
    }
    public record Snapshot(ResourceLocation dimension,List<BaliseData> devices) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(EMUCab.id("balise_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->{
            b.writeResourceLocation(p.dimension);b.writeVarInt(p.devices.size());p.devices.forEach(d->b.writeNbt(d.save()));
        },b->{var dimension=b.readResourceLocation();int size=b.readVarInt();if(size<0||size>MAX_SNAPSHOT_DEVICES)throw new IllegalArgumentException("Too many balises");
            var list=new ArrayList<BaliseData>();for(int i=0;i<size;i++){var tag=b.readNbt();var data=tag==null?null:BaliseData.load(tag);if(data!=null)list.add(data);}return new Snapshot(dimension,List.copyOf(list));});
        @Override public Type<Snapshot> type(){return TYPE;}
    }
    public record Open(BaliseData device) implements CustomPacketPayload {
        public static final Type<Open> TYPE=new Type<>(EMUCab.id("balise_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Open> CODEC=StreamCodec.of((b,p)->b.writeNbt(p.device.save()),b->{
            var tag=b.readNbt();var data=tag==null?null:BaliseData.load(tag);if(data==null)throw new IllegalArgumentException("Invalid balise");return new Open(data);});
        @Override public Type<Open> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("1");
        registrar.playToServer(Action.TYPE,Action.CODEC,(packet,context)->{
            if(context.player() instanceof ServerPlayer player)context.enqueueWork(()->{
                var target=BaliseTarget.resolve(player.serverLevel(),packet.trackPos,packet.curve,packet.positive);
                if(target==null)return;
                if(packet.action==0)BaliseService.install(player,target,packet.hand);
                else if(packet.action==1||packet.action==2)BaliseService.interact(player,target,packet.action==2);
            });
        });
        registrar.playToServer(Configure.TYPE,Configure.CODEC,(packet,context)->{
            if(context.player() instanceof ServerPlayer player)context.enqueueWork(()->BaliseService.configure(player,packet));
        });
        registrar.playToClient(Snapshot.TYPE,Snapshot.CODEC,(packet,context)->context.enqueueWork(()->BaliseClientAccess.sync(packet.dimension,packet.devices)));
        registrar.playToClient(Open.TYPE,Open.CODEC,(packet,context)->context.enqueueWork(()->BaliseClientAccess.open(packet.device)));
    }
}
