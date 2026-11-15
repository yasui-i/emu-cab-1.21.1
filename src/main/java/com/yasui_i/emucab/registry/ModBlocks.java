package com.yasui_i.emucab.registry;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.block.SpeedSignBlock;
import com.yasui_i.emucab.block.GradualThrottleBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, EMUCab.MOD_ID);

    public static final DeferredHolder<Block, CabDeskBlock> CAB_DESK = BLOCKS.register("cab_desk",
            () -> com.yasui_i.emucab.compat.CabWireIntegration.createBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(CabDeskBlock.LIT) ? 10 : 0)
                    .noOcclusion()));

    public static final DeferredHolder<Block,SpeedSignBlock> SPEED_SIGN=BLOCKS.register("speed_sign",
            ()->new SpeedSignBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredHolder<Block,GradualThrottleBlock> GRADUAL_THROTTLE=BLOCKS.register("gradual_throttle",
            ()->new GradualThrottleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2).sound(SoundType.METAL)));

    private ModBlocks() {
    }
}
