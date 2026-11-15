package com.yasui_i.emucab.registry;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.blockentity.SpeedSignBlockEntity;
import com.yasui_i.emucab.blockentity.GradualThrottleBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EMUCab.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CabDeskBlockEntity>> CAB_DESK =
            BLOCK_ENTITIES.register("cab_desk",
                    () -> BlockEntityType.Builder.of(CabDeskBlockEntity::new, ModBlocks.CAB_DESK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<SpeedSignBlockEntity>> SPEED_SIGN=
            BLOCK_ENTITIES.register("speed_sign",()->BlockEntityType.Builder.of(SpeedSignBlockEntity::new,ModBlocks.SPEED_SIGN.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<GradualThrottleBlockEntity>> GRADUAL_THROTTLE=
            BLOCK_ENTITIES.register("gradual_throttle",()->BlockEntityType.Builder.of(GradualThrottleBlockEntity::new,ModBlocks.GRADUAL_THROTTLE.get()).build(null));

    private ModBlockEntities() {
    }
}
