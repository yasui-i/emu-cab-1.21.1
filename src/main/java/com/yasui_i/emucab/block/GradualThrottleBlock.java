package com.yasui_i.emucab.block;

import com.mojang.serialization.MapCodec;
import com.yasui_i.emucab.blockentity.GradualThrottleBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Redstone target follower: output slowly approaches the input strength. */
public final class GradualThrottleBlock extends DirectionalKineticBlock implements EntityBlock {
    public static final DirectionProperty FACING=DirectionalKineticBlock.FACING;
    /** Full support cube: levers, buttons and other attachments can use every face. */
    private static final VoxelShape SHAPE=Block.box(0,0,0,16,16,16);
    public GradualThrottleBlock(Properties properties) { super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH)); }
    @Override protected MapCodec<? extends KineticBlock> codec() { return simpleCodec(GradualThrottleBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { super.createBlockStateDefinition(builder); }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new GradualThrottleBlockEntity(pos,state); }
    @Override public boolean hasShaftTowards(net.minecraft.world.level.LevelReader level,BlockPos pos,BlockState state,Direction direction) {
        return direction.getAxis()==getRotationAxis(state);
    }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(FACING).getAxis(); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return SHAPE; }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        // 1.21 invokes the default block interaction even while holding a block
        // item. Consume the UI interaction only when both hands are empty.
        if(!player.getMainHandItem().isEmpty()||!player.getOffhandItem().isEmpty())return InteractionResult.PASS;
        return level.getBlockEntity(pos) instanceof GradualThrottleBlockEntity
                ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }
    /**
     * Leave held block items and Create tools to the normal item interaction
     * pipeline. Non-empty hands skip the default block interaction so that a
     * shaft, gearbox, lever, or other block can be placed on any face without
     * sneaking; the empty hand still uses the configuration screen.
     */
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                         Player player, net.minecraft.world.InteractionHand hand,
                                                         BlockHitResult hit) {
        return stack.isEmpty()?ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                :ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) { return (l,p,s,b)->{if(b instanceof GradualThrottleBlockEntity t)t.tick();}; }
}
