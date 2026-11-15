package com.yasui_i.emucab.motor;

import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.electric_motor.ElectricMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.yasui_i.emucab.mixin.motor.CeeMotorLoadAccessor;
import com.yasui_i.emucab.control.ThrottleKineticUpdate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class VvvfMotorBlockEntity extends ElectricMotorBlockEntity {
    private float driveFraction;
    private int redstoneInput;
    private float lastDriveSpeed = Float.NaN;
    private float lastDriveCapacity = Float.NaN;
    private int driveTicks;

    public VvvfMotorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        int maximum = AllConfigs.server().kinetics.maxRotationSpeed.get();
        generatedSpeed.between(-maximum, maximum);
        generatedSpeed.value = maximum;
    }

    @Override public void tick() {
        if (level != null && !level.isClientSide) {
            redstoneInput = level.getBestNeighborSignal(worldPosition);
            driveFraction = VvvfDriveCommand.next(driveFraction, redstoneInput,
                    VvvfMotorConfig.RAMP_UP_SECONDS.get(), VvvfMotorConfig.RAMP_DOWN_SECONDS.get());
        }
        super.tick();
        if (level == null || level.isClientSide) return;
        // Limit network rebuilds during the ramp, but a zero signal is always applied in the same tick.
        if (++driveTicks % 2 != 0 && redstoneInput != 0) return;
        float nextSpeed = getGeneratedSpeed();
        float nextCapacity = calculateAddedStressCapacity();
        if (nextSpeed != lastDriveSpeed || Math.abs(nextCapacity - lastDriveCapacity) > 0.01f || Float.isNaN(lastDriveCapacity)) {
            lastDriveSpeed = nextSpeed;
            lastDriveCapacity = nextCapacity;
            updateGeneratedRotation();
            setChanged();
        }
        if (driveTicks % 5 == 0) sendData();
    }

    public float getDriveFraction() { return driveFraction; }

    @Override public void updateGeneratedRotation() {
        // Rebuilding a ramped source briefly stops downstream shafts. Count the
        // completed speed change once, including CEE's own source reactivation.
        ThrottleKineticUpdate.rebuild(super::updateGeneratedRotation);
    }

    public double getEquivalentResistance() {
        double load = ((CeeMotorLoadAccessor) (Object) this).emucab$getLoad();
        if (!Double.isFinite(load)) load = 0;
        load = Mth.clamp(load, 0.05, 1.0);
        double resistance = CEEConfigs.server().resistanceValues.motorResistance.get();
        return 0.8 * Math.min(resistance * 6.0, resistance / load) / Math.max(0.0001, driveFraction);
    }

    @Override public float getGeneratedSpeed() {
        float baseSpeed = super.getGeneratedSpeed();
        int maximum = AllConfigs.server().kinetics.maxRotationSpeed.get();
        return Mth.clamp(Math.round(baseSpeed * driveFraction), -maximum, maximum);
    }

    @Override public float calculateAddedStressCapacity() {
        float speed = Math.abs(getGeneratedSpeed());
        double voltage = ((CeeMotorLoadAccessor) (Object) this).emucab$getAverageVoltage().get();
        // Scale total available mechanical power along with RPM; retain CEE's voltage/resistance model.
        float power = (float) (voltage * voltage / CEEConfigs.server().resistanceValues.motorResistance.get() * driveFraction);
        lastCapacityProvided = speed < 1 ? 0 : power / speed;
        return lastCapacityProvided;
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.write(tag, registries, packet);
        tag.putFloat("VvvfDrive", driveFraction);
        tag.putInt("VvvfInput", redstoneInput);
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.read(tag, registries, packet);
        driveFraction = Mth.clamp(tag.getFloat("VvvfDrive"), 0, 1);
        redstoneInput = Mth.clamp(tag.getInt("VvvfInput"), 0, 15);
    }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        tooltip.add(Component.translatable("block.emucab.vvvf_motor").withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("emucab.motor.command", redstoneInput,
                Math.round(driveFraction * 100)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("emucab.motor.output", Math.round(getGeneratedSpeed())).withStyle(ChatFormatting.AQUA));
        double voltage = ((CeeMotorLoadAccessor) (Object) this).emucab$getAverageVoltage().get();
        double watts = driveFraction > 0 ? voltage * voltage / getEquivalentResistance() : 0;
        tooltip.add(Component.translatable("emucab.motor.electrical", Math.round(voltage), Math.round(watts)).withStyle(ChatFormatting.GRAY));
        return true;
    }

    @Override public void tickAudio() {
        // This call is only made on the logical client by Create. Keep client classes out of server initialization.
        VvvfMotorClient.tickAudio(this);
    }
}
