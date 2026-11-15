package com.yasui_i.emucab.block;

import com.mojang.serialization.MapCodec;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.CabGeometry;
import com.yasui_i.emucab.control.CabHitTesting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

import javax.annotation.Nullable;

/**
 * One part of the three block wide CR400 driver's desk. The whole desk faces the driver:
 * {@link #FACING} points at the seat, the screen sits on that face.
 */
public class CabDeskBlock extends HorizontalDirectionalBlock implements EntityBlock,dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener {
    public static final EnumProperty<CabDeskPart> PART =
            EnumProperty.create("part", CabDeskPart.class);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    private static final VoxelShape[][] SHAPES = createShapes();

    private static VoxelShape[][] createShapes() {
        VoxelShape[][] result = new VoxelShape[3][4];
        for (int part = 0; part < 3; part++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                VoxelShape shape = Shapes.empty();
                for (double[] box : CabGeometry.SHAPES[part]) {
                    double x0=box[0], z0=box[2], x1=box[3], z1=box[5];
                    for (int r=0; r<rotation; r++) {
                        double nx0=16-z1, nx1=16-z0;
                        z0=x0; z1=x1; x0=nx0; x1=nx1;
                    }
                    shape = Shapes.or(shape, Block.box(x0,box[1],z0,x1,box[4],z1));
                }
                result[part][rotation] = shape.optimize();
            }
        }
        return result;
    }

    public CabDeskBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false)
                .setValue(PART, CabDeskPart.CENTER));
    }

    @Override
    protected MapCodec<CabDeskBlock> codec() {
        return simpleCodec(CabDeskBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, PART, LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int part = switch (state.getValue(PART)) { case LEFT -> 0; case CENTER -> 1; case RIGHT -> 2; };
        int rotation = switch (state.getValue(FACING)) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
        return SHAPES[part][rotation];
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == CabDeskPart.CENTER ? new CabDeskBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) != CabDeskPart.CENTER) {
            return null;
        }
        if (level.isClientSide) {
            return (lvl, pos, st, be) -> {
                if (be instanceof CabDeskBlockEntity desk) {
                    desk.clientTick();
                }
            };
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof CabDeskBlockEntity desk) {
                desk.serverTick();
            }
        };
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,
            BlockPos pos,Player player,net.minecraft.world.InteractionHand hand,BlockHitResult hit){
        if(com.yasui_i.emucab.balise.BaliseService.isWrench(stack))return net.minecraft.world.ItemInteractionResult.SUCCESS;
        return super.useItemOn(stack,state,level,pos,player,hand,hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos center = findCenter(level, pos, state);
        if (center == null) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(center) instanceof CabDeskBlockEntity desk)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        var local = CabHitTesting.toModel(center, state.getValue(FACING), hit.getLocation());
        var eye = CabHitTesting.toModel(center, state.getValue(FACING), CabHitTesting.eyeInDesk(player,center));
        var control = CabHitTesting.pickRay(eye, local, player.blockInteractionRange()*16,desk.leverPosition());
        if(control!=null && control.action()==com.yasui_i.emucab.control.CabAction.HANDLE)return InteractionResult.SUCCESS;
        if(control==null)return InteractionResult.PASS;
        desk.activate(control, player);
        return InteractionResult.SUCCESS;
    }

    /**
     * Walks up to two blocks along the desk row to find the center part that owns the block entity.
     */
    @Nullable
    public static BlockPos findCenter(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CabDeskBlock)) {
            return null;
        }
        if (state.getValue(PART) == CabDeskPart.CENTER) {
            return pos;
        }
        Direction facing = state.getValue(FACING);
        int offset = state.getValue(PART) == CabDeskPart.LEFT ? 1 : -1;
        BlockPos candidate = pos.relative(facing.getClockWise(), offset);
        BlockState other = level.getBlockState(candidate);
        return other.getBlock() instanceof CabDeskBlock && other.getValue(PART) == CabDeskPart.CENTER
                && other.getValue(FACING) == facing ? candidate : null;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            if (state.getValue(PART) == CabDeskPart.CENTER) {
                Block.popResource(level, pos, new ItemStack(com.yasui_i.emucab.registry.ModItems.CAB_DESK.get()));
            }
            removeSiblings(level, pos, state);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override public void beforeMove(net.minecraft.server.level.ServerLevel from,net.minecraft.server.level.ServerLevel to,BlockState state,BlockPos oldPos,BlockPos newPos){
        if(from.getBlockEntity(oldPos) instanceof CabDeskBlockEntity desk)desk.beginAssemblyMove();
    }
    @Override public void afterMove(net.minecraft.server.level.ServerLevel from,net.minecraft.server.level.ServerLevel to,BlockState state,BlockPos oldPos,BlockPos newPos){
        if(to.getBlockEntity(newPos) instanceof CabDeskBlockEntity desk){
            com.yasui_i.emucab.telemetry.SimurailBrakeBindings.update(desk);
            com.yasui_i.emucab.control.CabEndpointRegistry.register(desk);
            com.yasui_i.emucab.control.CeeElectricalTelemetry.register(desk);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if(level.getBlockEntity(pos) instanceof CabDeskBlockEntity desk&&!desk.movingForAssembly())
                {com.yasui_i.emucab.telemetry.SimurailBrakeBindings.remove(desk);com.yasui_i.emucab.control.CabEndpointRegistry.release(desk);}
            if(level.getBlockEntity(pos) instanceof CabDeskBlockEntity desk){
                com.yasui_i.emucab.control.CabEndpointRegistry.unregister(desk);
                com.yasui_i.emucab.control.CeeElectricalTelemetry.unregister(desk);
            }
            com.yasui_i.emucab.compat.CabWireIntegration.removed(level,pos);
            removeSiblings(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void removeSiblings(Level level, BlockPos pos, BlockState state) {
        Direction along = state.getValue(FACING).getClockWise();
        for (int i = -2; i <= 2; i++) {
            if (i == 0) {
                continue;
            }
            BlockPos sibling = pos.relative(along, i);
            BlockState other = level.getBlockState(sibling);
            if (other.getBlock() instanceof CabDeskBlock && other.getValue(FACING) == state.getValue(FACING)) {
                level.setBlock(sibling, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }
}
