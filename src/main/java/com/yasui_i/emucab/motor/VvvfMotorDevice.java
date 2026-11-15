package com.yasui_i.emucab.motor;

import com.george_vi.electroenergetics.content.electric_motor.ElectricMotorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Reuses CEE's measured terminal voltage while scaling its real electrical load with the drive command. */
public final class VvvfMotorDevice extends ElectricMotorDevice {
    public VvvfMotorDevice(Level level, BlockPos pos, DevicesSavedData data, SimulatedDeviceType<?> type) {
        super(level, pos, data, type);
    }

    @Override public void preTick(BridgeCollector bridges) {
        if ((be == null || be.isRemoved()) && level.isLoaded(pos)) {
            be = level.getBlockEntity(pos) instanceof VvvfMotorBlockEntity motor ? motor : null;
        }
        if (!(be instanceof VvvfMotorBlockEntity motor) || motor.isRemoved()) return;
        double drive = motor.getDriveFraction();
        // Leave an open circuit at zero: no phantom motor load while the driver's handle is off.
        if (drive <= 0) return;
        bridges.builder(pos).resistor(0, 1, motor.getEquivalentResistance());
    }
}
