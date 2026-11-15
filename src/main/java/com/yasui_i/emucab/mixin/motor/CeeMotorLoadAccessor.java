package com.yasui_i.emucab.mixin.motor;

import com.george_vi.electroenergetics.foundation.RMSHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the same measured voltage and mechanical load used by CEE's electric motor. */
@Pseudo
@Mixin(targets = "com.george_vi.electroenergetics.content.electric_motor.ElectricMotorBlockEntity", remap = false)
public interface CeeMotorLoadAccessor {
    @Accessor("load") float emucab$getLoad();
    @Accessor("averageVoltage") RMSHolder emucab$getAverageVoltage();
}
