package com.yasui_i.emucab.mixin;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.yasui_i.emucab.control.ThrottleKineticUpdate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value=RotationPropagator.class,remap=false)
public abstract class ThrottleRotationPropagationMixin {
    @Redirect(method="propagateNewSource",at=@At(value="INVOKE",
            target="Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;onSpeedChanged(F)V"))
    private static void emucab$coalesceThrottleSpeedChange(KineticBlockEntity entity,float previousSpeed) {
        if(!ThrottleKineticUpdate.deferSpeedChange(entity,previousSpeed))entity.onSpeedChanged(previousSpeed);
    }
}
