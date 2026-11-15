package com.yasui_i.emucab.motor;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.electric_motor.ElectricMotorDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.yasui_i.emucab.EMUCab;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class VvvfMotors {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EMUCab.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EMUCab.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EMUCab.MOD_ID);
    private static final DeferredRegister<SimulatedDeviceType<?>> DEVICES = DeferredRegister.create(CEERegistries.SIMULATED_DEVICE_TYPE, EMUCab.MOD_ID);

    public static final DeferredBlock<VvvfMotorBlock> BLOCK = BLOCKS.register("vvvf_motor",
            () -> new VvvfMotorBlock(BlockBehaviour.Properties.ofFullCopy(CEEBlocks.ELECTRIC_MOTORS[0].get())));
    public static final DeferredItem<BlockItem> ITEM = ITEMS.register("vvvf_motor", () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VvvfMotorBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register("vvvf_motor",
            () -> BlockEntityType.Builder.of((pos, state) -> new VvvfMotorBlockEntity(VvvfMotors.BLOCK_ENTITY.get(), pos, state), BLOCK.get()).build(null));
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<ElectricMotorDevice>> DEVICE = DEVICES.register("vvvf_motor",
            () -> new SimulatedDeviceType<>(EMUCab.id("vvvf_motor"),
                    (type, level, pos, data) -> new VvvfMotorDevice(level, pos, data, type), List.of(BLOCK.get())));
    public static void register(IEventBus bus) {
        // Keep placed motors, inventory items and CEE saved device IDs from version 1.4.0.
        var legacy = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("pwsablefix", "vvvf_motor");
        var current = EMUCab.id("vvvf_motor");
        BLOCKS.addAlias(legacy, current);
        ITEMS.addAlias(legacy, current);
        BLOCK_ENTITIES.addAlias(legacy, current);
        DEVICES.addAlias(legacy, current);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        DEVICES.register(bus);
    }

    private VvvfMotors() {}
}
