package com.yasui_i.emucab.telemetry;

import com.yasui_i.emucab.config.CabConfig;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import org.joml.Vector3d;
import java.util.*;

/** Called on Sable's physics thread immediately before the Rapier step, after queued forces.
 * Rapier has no per-body damping setter. Its linear damping is v/(1+drag*dt), so
 * pre-scaling by (1+drag*dt)/(1+drag*multiplier*dt) selects a smaller damping.
 * There is no target velocity, tick-rate-dependent push, or correction of collision losses.
 * Only horizontal velocity is adjusted; suspension and rotational damping are retained.
 */
public final class SimurailCoasting {
    public static void beforeStep(ServerLevel level,PhysicsPipeline pipeline,double drag,double dt) {
        double multiplier=CabConfig.SIMURAIL_COASTING_DRAG_MULTIPLIER.get();
        if(multiplier>=1||!Double.isFinite(drag)||drag<=0||!Double.isFinite(dt)||dt<=0||
                !ModList.get().isLoaded("simurail"))return;
        double correction=(1+drag*dt)/(1+drag*multiplier*dt)-1;
        for(var body:Adapter.coastingBodies(level)) {
            if(body.isRemoved())continue;
            var velocity=pipeline.getLinearVelocity(body,new Vector3d());
            if(!velocity.isFinite()||velocity.x*velocity.x+velocity.z*velocity.z<1E-10)continue;
            pipeline.addLinearAndAngularVelocity(body,new Vector3d(velocity.x*correction,0,velocity.z*correction),new Vector3d());
        }
    }

    // No mutable state or cached membership survives unload, assembly, splitting or cab deletion.
    // The optional mod's loaded collection is read in its own server-side physics tick.
    private static final class Adapter {
        static Set<ServerSubLevel> coastingBodies(ServerLevel level) {
            var registered=SimurailBrakeBindings.registered(level);
            if(registered.isEmpty())return Set.of();
            var eligible=new HashSet<ServerSubLevel>();
            var busy=new HashSet<ServerSubLevel>();
            var live=new HashMap<UUID,ServerSubLevel>();
            var seen=new HashSet<UUID>();
            var ambiguous=new HashSet<UUID>();
            for(var be:com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity.LOADED_BOGEYS) {
                if(be.getLevel()!=level||be.isRemoved()||level.getBlockEntity(be.getBlockPos())!=be)continue;
                UUID id=be instanceof BogeyIdentity identity?identity.emucab$identity():null;
                if(id!=null&&registered.contains(id)&&!seen.add(id))ambiguous.add(id);
                if(!(SableCompanion.INSTANCE.getContaining(level,be.getBlockPos()) instanceof ServerSubLevel body)||body.isRemoved())continue;
                if(be.getOptions().enabled&&((!be.isUnpowered()&&Math.abs(be.getSpeed())>1E-4)||be.getGroupBrakeStrength()>0))busy.add(body);
                if(id!=null&&registered.contains(id))live.put(id,body);
            }
            ambiguous.forEach(live::remove);
            eligible.addAll(live.values());eligible.removeAll(busy);
            return Set.copyOf(eligible);
        }
    }
    private SimurailCoasting(){}
}
