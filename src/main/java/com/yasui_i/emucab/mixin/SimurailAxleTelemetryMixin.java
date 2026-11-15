package com.yasui_i.emucab.mixin;

import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import com.yasui_i.emucab.telemetry.BogeyIdentity;
import com.yasui_i.emucab.telemetry.BogeyForceSamples;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scale registered bogey braking before submission; report the resulting actual force. */
@Pseudo
@Mixin(targets="com.crystaelix.simurail.content.bogey.PhysicsBogeyAxle",remap=false)
public abstract class SimurailAxleTelemetryMixin {
    @Shadow @Final protected PhysicsBogeyBlockEntity bogey;
    @Shadow @Final protected boolean logicalFront;
    @Shadow @Final protected Vector3d queuedTractionForce;
    @Shadow @Final protected Vector3d queuedBrakeForce;
    @Shadow protected double slipSpeed;
    @Unique private double emucab$traction,emucab$brake;
    @Inject(method="updateForces",at=@At("HEAD"))
    private void emucab$begin(ServerSubLevel sub,double dt,CallbackInfo ci){emucab$traction=0;emucab$brake=0;}
    @Redirect(method={"updateTrackForces","updateWorldForces"},at=@At(value="INVOKE",target="Ldev/ryanhcode/sable/api/physics/force/QueuedForceGroup;applyAndRecordPointForce(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V"))
    private void emucab$observe(QueuedForceGroup group,Vector3dc point,Vector3dc impulse){
        if(impulse==queuedBrakeForce&&bogey instanceof BogeyIdentity id){
            // Mutate before submission so the subsequent reaction force on moving
            // rails uses the same reduced impulse. Never scale traction or suspension.
            queuedBrakeForce.mul(com.yasui_i.emucab.telemetry.SimurailBrakeBindings.multiplier(bogey.getLevel(),id.emucab$identity()));
        }
        group.applyAndRecordPointForce(point,impulse);
        if(impulse==queuedTractionForce)emucab$traction+=impulse.length();
        else if(impulse==queuedBrakeForce)emucab$brake+=impulse.length();
    }
    @Inject(method="updateForces",at=@At("TAIL"))
    private void emucab$end(ServerSubLevel sub,double dt,CallbackInfo ci){
        if(dt>0&&bogey instanceof BogeyIdentity id)BogeyForceSamples.record(id.emucab$identity(),logicalFront,emucab$traction/dt,emucab$brake/dt,slipSpeed);
    }
}
