package com.yasui_i.emucab.balise;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.blockentity.SpeedSignBlockEntity;
import com.yasui_i.emucab.control.AtpAnnouncement;
import com.yasui_i.emucab.registry.ModItems;
import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

@EventBusSubscriber(modid=EMUCab.MOD_ID)
public final class BaliseService {
    private static final Map<ServerLevel,Set<UUID>> PENDING_SYNC=new WeakHashMap<>();
    private static final Map<ServerLevel,Set<BlockPos>> PENDING_MIGRATION=new WeakHashMap<>();
    private BaliseService(){}
    public static boolean isWrench(ItemStack stack){return AllItems.WRENCH.isIn(stack);}
    public static boolean canEdit(ServerPlayer player,BaliseTarget target){
        var level=player.serverLevel();double reach=player.blockInteractionRange()+1;
        return player.isAlive()&&!player.isSpectator()&&player.mayBuild()&&level.hasChunkAt(target.trackPos())
                &&level.hasChunkAt(BlockPos.containing(target.anchor()))&&level.mayInteract(player,target.trackPos())
                &&level.mayInteract(player,BlockPos.containing(target.anchor()))
                &&player.getEyePosition().distanceToSqr(target.anchor())<=reach*reach;
    }
    public static boolean install(ServerPlayer player,BaliseTarget requested,InteractionHand hand){
        // Create curves are rendered connections rather than complete rail
        // blocks. Keep resolving old curve mounts for editing/removal only.
        if(requested.curve()!=null)return false;
        var level=player.serverLevel();var target=BaliseTarget.resolve(level,requested.trackPos(),requested.curve(),requested.positive());
        if(target==null||!canEdit(player,target))return false;
        var stack=player.getItemInHand(hand);if(!(stack.getItem() instanceof BaliseItem item))return false;
        var store=BaliseStore.get(level);var existing=store.find(target);
        if(existing!=null){PacketDistributor.sendToPlayer(player,new BalisePackets.Open(existing));return false;}
        var device=new BaliseData(UUID.randomUUID(),target,item.kind(),80,AtpAnnouncement.NEUTRAL_SECTION_AHEAD.id(),true);
        if(!store.add(device))return false;
        if(!player.getAbilities().instabuild)stack.shrink(1);
        click(level,target.anchor(),1.1f);queueAll(level);
        return true;
    }
    public static boolean interact(ServerPlayer player,BaliseTarget requested,boolean remove){
        var level=player.serverLevel();var target=BaliseTarget.resolve(level,requested.trackPos(),requested.curve(),requested.positive());
        if(target==null||!canEdit(player,target))return false;
        if(remove){if(!isWrench(player.getMainHandItem()))return false;}
        else if(!player.getMainHandItem().isEmpty()||!player.getOffhandItem().isEmpty())return false;
        var store=BaliseStore.get(level);var device=store.find(target);if(device==null)return false;
        if(remove){
            store.remove(device.id());
            if(!player.getAbilities().instabuild){var stack=item(device);if(!player.getInventory().add(stack))player.drop(stack,false);}
            click(level,target.anchor(),.7f);queueAll(level);
        }else PacketDistributor.sendToPlayer(player,new BalisePackets.Open(device));
        return true;
    }
    public static boolean configure(ServerPlayer player,BalisePackets.Configure packet){
        var store=BaliseStore.get(player.serverLevel());var device=store.get(packet.id());
        if(device==null||!canEdit(player,device.target())||!device.target().stillValid(player.serverLevel())
                ||packet.limit()<1||packet.limit()>1000||!BaliseData.validCue(packet.cue()))return false;
        store.put(device.configure(packet.limit(),packet.cue(),packet.enabled(),packet.reverse()));queueAll(player.serverLevel());
        return true;
    }
    public static ItemStack item(BaliseData data){return new ItemStack(data.kind()==BaliseData.Kind.LIMIT?ModItems.SPEED_SIGN.get():ModItems.BALISE.get());}
    private static void click(ServerLevel level,Vec3 pos,float pitch){level.playSound(null,pos.x,pos.y,pos.z,SoundEvents.STONE_BUTTON_CLICK_ON,SoundSource.BLOCKS,.6f,pitch);}
    public static void queueAll(ServerLevel level){var set=PENDING_SYNC.computeIfAbsent(level,k->new HashSet<>());level.players().forEach(p->set.add(p.getUUID()));}
    private static void queue(ServerPlayer player){PENDING_SYNC.computeIfAbsent(player.serverLevel(),k->new HashSet<>()).add(player.getUUID());}
    public static void sendSnapshot(ServerPlayer player){
        var level=player.serverLevel();int range=level.getServer().getPlayerList().getViewDistance()+2;var center=player.chunkPosition();
        var devices=BaliseStore.get(level).loaded(level).stream().filter(d->{var chunk=new net.minecraft.world.level.ChunkPos(BlockPos.containing(d.target().anchor()));return Math.abs(chunk.x-center.x)<=range&&Math.abs(chunk.z-center.z)<=range;})
                .sorted(Comparator.comparingDouble(d->d.target().anchor().distanceToSqr(player.position()))).limit(BalisePackets.MAX_SNAPSHOT_DEVICES).toList();
        PacketDistributor.sendToPlayer(player,new BalisePackets.Snapshot(level.dimension().location(),devices));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer p)queue(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer p)queue(p);}
    @SubscribeEvent public static void watch(ChunkWatchEvent.Watch event){queue(event.getPlayer());}
    @SubscribeEvent public static void unwatch(ChunkWatchEvent.UnWatch event){queue(event.getPlayer());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void clickRail(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player)||event.getHand()!=InteractionHand.MAIN_HAND)return;
        boolean wrench=isWrench(player.getMainHandItem());
        if(!wrench&&!player.getMainHandItem().isEmpty())return;
        var target=BaliseTarget.forLook(player.serverLevel(),event.getPos(),null,player.getLookAngle());
        if(target==null||BaliseStore.get(player.serverLevel()).find(target)==null)return;
        // Consume the wrench interaction before Create can rotate or dismantle
        // the supporting rail. Permission failures must not fall through.
        if(wrench){
            event.setCanceled(true);
            event.setCancellationResult(interact(player,target,true)?InteractionResult.SUCCESS:InteractionResult.FAIL);
        }else if(interact(player,target,false)){
            event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
    @SubscribeEvent public static void afterTick(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel level))return;
        if(level.getGameTime()%100==0){
            var migrations=PENDING_MIGRATION.get(level);
            if(migrations!=null){
                migrations.removeIf(pos->{
                    if(!level.hasChunkAt(pos))return true; // onLoad retries when the sign returns
                    if(!(level.getBlockEntity(pos) instanceof SpeedSignBlockEntity sign)||!sign.bound())return true;
                    return migrate(sign);
                });
                if(migrations.isEmpty())PENDING_MIGRATION.remove(level);
            }
        }
        // No forced chunk loads. An unloaded curve endpoint is retained until it
        // loads; a removed track/curve drops the attachment after revalidation.
        if(level.getGameTime()%20==0){
            var store=BaliseStore.get(level);boolean changed=false;
            for(var device:store.loaded(level)){
                if(device.target().curve()!=null&&!level.hasChunkAt(device.target().curve().curveTarget()))continue;
                if(device.target().stillValid(level))continue;
                store.remove(device.id());var point=device.target().anchor();level.addFreshEntity(new ItemEntity(level,point.x,point.y+.2,point.z,item(device)));changed=true;
            }
            if(changed)queueAll(level);
        }
        var pending=PENDING_SYNC.remove(level);if(pending!=null)for(var player:level.players())if(pending.contains(player.getUUID()))sendSnapshot(player);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level){PENDING_SYNC.remove(level);PENDING_MIGRATION.remove(level);}}
    public static void queueMigration(SpeedSignBlockEntity sign){if(sign.getLevel() instanceof ServerLevel level&&sign.bound())PENDING_MIGRATION.computeIfAbsent(level,k->new HashSet<>()).add(sign.getBlockPos());}
    public static boolean migrate(SpeedSignBlockEntity sign){
        if(!(sign.getLevel() instanceof ServerLevel level)||!sign.bound()||sign.isRemoved())return false;
        var target=legacyTarget(level,sign.anchor(),sign.forward());if(target==null)return false;
        var store=BaliseStore.get(level);var existing=store.find(target);
        if(existing!=null){
            // Remove only a duplicate already migrated copy; a conflicting new
            // attachment keeps the old sign intact for manual resolution.
            if(existing.kind()!=BaliseData.Kind.LIMIT||existing.limit()!=sign.limit()||existing.forward().dot(sign.forward())<.9)return false;
        }else if(!store.add(new BaliseData(UUID.randomUUID(),target,BaliseData.Kind.LIMIT,sign.limit(),AtpAnnouncement.NEUTRAL_SECTION_AHEAD.id(),true)))return false;
        level.removeBlock(sign.getBlockPos(),false);queueAll(level);return true;
    }
    private static BaliseTarget legacyTarget(ServerLevel level,Vec3 anchor,Vec3 forward){
        BaliseTarget best=null;double error=.75*.75;
        var origin=BlockPos.containing(anchor);
        for(var pos:BlockPos.betweenClosed(origin.offset(-1,-1,-1),origin.offset(1,1,1))){
            var target=BaliseTarget.forLook(level,pos,null,forward);if(target==null)continue;
            // forLook uses Create's installation convention, choose the actual
            // former sign direction explicitly for migration.
            if(target.forward().dot(forward)<0)target=target.reversed();
            double distance=target.anchor().distanceToSqr(anchor);
            if(distance<error&&Math.abs(target.tangent().dot(forward))>.9){best=target;error=distance;}
        }
        // Curved signs retain their exact selected segment through a nearby
        // curve endpoint. Search the existing graph turns, never nearby lines.
        for(var graph:com.simibubi.create.Create.RAILWAYS.sided(level).trackNetworks.values())for(var location:graph.getNodes()){
            if(!location.getDimension().equals(level.dimension()))continue;var node=graph.locateNode(location);
            for(var entry:graph.getConnectionsFrom(node).entrySet()){
                var connection=entry.getValue().getTurn();if(connection==null||!connection.getBounds().inflate(1).contains(anchor))continue;
                var pos=connection.bePositions.getFirst();if(!level.hasChunkAt(pos))continue;
                int segments=(int)Math.floor(connection.getLength()*2);
                for(int segment=0;segment<segments;segment++){
                    double t=(segment+1.0)/segments;if(connection.getPosition(t).distanceToSqr(anchor)>=error)continue;
                    var target=BaliseTarget.forLook(level,pos,new com.simibubi.create.content.trains.track.BezierTrackPointLocation(connection.bePositions.getSecond(),segment),forward);
                    if(target==null||Math.abs(target.tangent().dot(forward))<.9)continue;
                    if(target.forward().dot(forward)<0)target=target.reversed();best=target;error=target.anchor().distanceToSqr(anchor);
                }
            }
        }
        return best;
    }
}
