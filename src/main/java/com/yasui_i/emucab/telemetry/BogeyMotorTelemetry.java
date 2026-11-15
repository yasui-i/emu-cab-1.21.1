package com.yasui_i.emucab.telemetry;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.network.BogeyMotorSoundPayload;
import com.yasui_i.emucab.network.CabAtpSoundPayload;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Observes registered physical bogeys at 5 Hz; no control overrides or synthetic forces. */
@EventBusSubscriber(modid=EMUCab.MOD_ID)
public final class BogeyMotorTelemetry {
    // A client may choose an audible radius up to 512 blocks. Extra margin
    // covers the moving receiver and the 5 Hz telemetry interval.
    private static final double DELIVERY_RADIUS_SQUARED=Math.pow(com.yasui_i.emucab.audio.ExteriorSoundRange.DELIVERY,2);

    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level)||level.getGameTime()%4!=0||level.players().isEmpty())return;
        for(var packet:snapshot(level)) {
            var world=CabAtpSoundPayload.worldPosition(level,packet.pos());
            for(var player:level.players())if(player.getEyePosition().distanceToSqr(world)<=DELIVERY_RADIUS_SQUARED)
                PacketDistributor.sendToPlayer(player,packet);
        }
    }

    /** Also used by isolated integration checks; optional Simurail types stay in Adapter. */
    public static List<BogeyMotorSoundPayload> snapshot(ServerLevel level) {
        return ModList.get().isLoaded("simurail")?Adapter.snapshot(level):List.of();
    }

    /** Sable's server API reads rigid-body linear + angular point velocity in m/s.
     * It does not differentiate world positions, consult the display scale, or use wheel slip. */
    private static float bodySpeedKmh(ServerLevel level,net.minecraft.core.BlockPos pos) {
        var velocity=SableCompanion.INSTANCE.getVelocity(level,pos.getCenter());
        double kmh=velocity.length()*3.6;
        return Double.isFinite(kmh)?(float)Math.clamp(kmh,0,2000):0;
    }

    private static final class Adapter {
        static List<BogeyMotorSoundPayload> snapshot(ServerLevel level) {
            var registered=SimurailBrakeBindings.registered(level);
            if(registered.isEmpty())return List.of();
            Map<UUID,com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity> live=new LinkedHashMap<>();
            Set<UUID> duplicates=new HashSet<>();
            for(var be:com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity.LOADED_BOGEYS) {
                if(be.getLevel()!=level||be.isRemoved()||level.getBlockEntity(be.getBlockPos())!=be||!(be instanceof BogeyIdentity identity))continue;
                UUID id=identity.emucab$identity();
                if(registered.contains(id)&&live.put(id,be)!=null)duplicates.add(id);
            }
            duplicates.forEach(live::remove);
            if(live.isEmpty())return List.of();
            var physics=com.crystaelix.simurail.config.SimurailConfig.server().physics;
            double speedFactor=physics.axleTargetSpeedFactor.get(),driveFactor=physics.axleDriveForceFactor.get();
            var packets=new ArrayList<BogeyMotorSoundPayload>(live.size());
            for(var entry:live.entrySet()) {
                UUID id=entry.getKey();var be=entry.getValue();var options=be.getOptions();
                boolean powered=options.enabled&&!be.isUnpowered();
                // Simurail getWheelAngle() integrates visualSpeed/radius. This is actual
                // wheel surface speed (including its slip model), not be.getSpeed().
                float rpm=BogeyMotorMath.wheelRpm(be.visualSpeed,options.type.wheelRadius());
                var sample=BogeyForceSamples.read(id);
                float traction=powered&&sample!=null?BogeyMotorMath.tractionLoad(sample.traction(),
                        be.getSpeed()*speedFactor,options.getStress(),driveFactor):0;
                // Simurail exposes a friction-brake command, not a regenerative current.
                // The synthesizer may use this to model a brake tone; no energy is invented.
                float brake=options.enabled?BogeyMotorMath.unit(be.getGroupBrakeStrength()):0;
                packets.add(new BogeyMotorSoundPayload(be.getBlockPos(),id,level.getGameTime(),rpm,
                        bodySpeedKmh(level,be.getBlockPos()),traction,brake,powered));
            }
            return List.copyOf(packets);
        }
    }
    private BogeyMotorTelemetry(){}
}
