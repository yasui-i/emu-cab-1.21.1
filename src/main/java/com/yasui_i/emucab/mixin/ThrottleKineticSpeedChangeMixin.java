package com.yasui_i.emucab.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.yasui_i.emucab.control.ThrottleKineticUpdate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value=KineticBlockEntity.class,remap=false)
public abstract class ThrottleKineticSpeedChangeMixin {
    @Redirect(method={"removeSource","updateFromNetwork"},at=@At(value="INVOKE",
            target="Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;onSpeedChanged(F)V"))
    private void emucab$deferTransientThrottleStop(KineticBlockEntity entity,float previousSpeed) {
        if(!ThrottleKineticUpdate.deferSpeedChange(entity,previousSpeed))entity.onSpeedChanged(previousSpeed);
    }
}
