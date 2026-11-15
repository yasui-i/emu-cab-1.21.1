package com.yasui_i.emucab.motor;

import com.george_vi.electroenergetics.content.electric_motor.ElectricMotorBlock;
import com.george_vi.electroenergetics.content.electric_motor.ElectricMotorBlockEntity;
import com.george_vi.electroenergetics.content.electric_motor.ElectricMotorDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** CEE's placement, wiring terminals, fluid logging, collision and shaft connectivity are inherited. */
public final class VvvfMotorBlock extends ElectricMotorBlock {
    public VvvfMotorBlock(Properties properties) { super(properties); }

    @Override public SimulatedDeviceType<ElectricMotorDevice> getDevice() { return VvvfMotors.DEVICE.get(); }
    @Override public BlockEntityType<? extends ElectricMotorBlockEntity> getBlockEntityType() { return VvvfMotors.BLOCK_ENTITY.get(); }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        // CEE's dye action replaces the block with its ordinary motor, discarding redstone control.
        if (stack.getItem() instanceof DyeItem) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
