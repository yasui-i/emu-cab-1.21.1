package com.yasui_i.emucab.mixin;

import com.yasui_i.emucab.telemetry.SimurailCoasting;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hook only the backend whose native linear-damping law is known. Other backends stay unchanged. */
@Pseudo
@Mixin(targets="dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline",remap=false)
public abstract class SableCoastingMixin {
    @Shadow @Final private ServerLevel level;
    @Unique private double emucab$sceneDrag;

    @Inject(method="init",at=@At("HEAD"))
    private void emucab$rememberDrag(Vector3dc gravity,double drag,CallbackInfo ci){emucab$sceneDrag=drag;}

    @Inject(method="physicsTick",at=@At(value="INVOKE",target="Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;step(JD)V"))
    private void emucab$coast(double dt,CallbackInfo ci){
        SimurailCoasting.beforeStep(level,(PhysicsPipeline)(Object)this,emucab$sceneDrag,dt);
    }
}
