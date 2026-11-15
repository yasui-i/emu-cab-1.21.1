package com.yasui_i.emucab.telemetry;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.config.CabConfig;
import com.yasui_i.emucab.network.BogeyBrakeSoundPayload;
import com.yasui_i.emucab.network.CabAtpSoundPayload;
import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Only real brake input on explicitly registered bogeys can request release audio. */
@EventBusSubscriber(modid=EMUCab.MOD_ID)
public final class BogeyBrakeSounds {
    private record Observed(BlockEntity instance,BrakeReleaseState edge){}
    private static final Map<ServerLevel,Map<UUID,Observed>> STATES=new HashMap<>();

    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!ModList.get().isLoaded("simurail"))return;
        if(!CabConfig.BOGEY_RELEASE_SOUNDS.get()||CabConfig.BOGEY_RELEASE_VOLUME.get()<=0) {STATES.remove(level);return;}
        Adapter.tick(level);
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){STATES.remove(event.getLevel());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){STATES.clear();}

    /** This class is loaded only with Simurail installed. */
    private static final class Adapter {
        static void tick(ServerLevel level) {
            Set<UUID> registered=SimurailBrakeBindings.registered(level);
            if(registered.isEmpty()) {STATES.remove(level);return;}
            Map<UUID,PhysicsBogeyBlockEntity> live=new HashMap<>();
            Set<UUID> duplicates=new HashSet<>();
            for(var be:PhysicsBogeyBlockEntity.LOADED_BOGEYS) {
                if(be.getLevel()!=level||be.isRemoved()||level.getBlockEntity(be.getBlockPos())!=be||!(be instanceof BogeyIdentity identity))continue;
                UUID id=identity.emucab$identity();
                if(registered.contains(id)&&live.put(id,be)!=null)duplicates.add(id);
            }
            duplicates.forEach(live::remove);
            var states=STATES.computeIfAbsent(level,key->new HashMap<>());
            states.keySet().retainAll(live.keySet());
            for(var entry:live.entrySet()) {
                UUID id=entry.getKey();var be=entry.getValue();var state=states.get(id);
                // Unload/reload and copied UUIDs must not manufacture release edges.
                if(state==null||state.instance()!=be) {state=new Observed(be,new BrakeReleaseState());states.put(id,state);}
                int cooldownTicks=(int)Math.ceil(CabConfig.BOGEY_RELEASE_COOLDOWN.get()*20);
                if(!state.edge().update(be.getGroupBrakeStrength(),cooldownTicks))continue;
                var position=CabAtpSoundPayload.worldPosition(level,be.getBlockPos());
                float radius=CabConfig.BOGEY_RELEASE_RADIUS.get().floatValue();
                var packet=new BogeyBrakeSoundPayload(be.getBlockPos(),id,CabConfig.BOGEY_RELEASE_VOLUME.get().floatValue(),radius);
                double delivery=Math.max(128,radius+64);
                for(var player:level.players())if(player.getEyePosition().distanceToSqr(position)<=delivery*delivery)PacketDistributor.sendToPlayer(player,packet);
            }
        }
    }
    private BogeyBrakeSounds(){}
}
