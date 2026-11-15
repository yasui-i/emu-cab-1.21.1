package com.yasui_i.emucab.block;

import com.yasui_i.emucab.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places the three desk parts at once: the clicked block becomes the CENTER part, the two
 * neighbouring blocks become the LEFT and RIGHT extensions.
 */
public class CabDeskItem extends BlockItem {
    public CabDeskItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.FAIL;
        }

        Direction facing = context.getHorizontalDirection().getOpposite();
        Direction along = facing.getClockWise();
        BlockPos center = context.getClickedPos();
        BlockPos left = center.relative(along.getOpposite());
        BlockPos right = center.relative(along);

        BlockPos[] positions = {left, center, right};
        for (BlockPos pos : positions) {
            if (!level.getBlockState(pos).canBeReplaced(context)) {
                return InteractionResult.FAIL;
            }
            if (!level.isInWorldBounds(pos)) {
                return InteractionResult.FAIL;
            }
        }

        if (!level.isClientSide) {
            placePart(level, left, CabDeskPart.LEFT, facing);
            placePart(level, center, CabDeskPart.CENTER, facing);
            placePart(level, right, CabDeskPart.RIGHT, facing);
            level.playSound(null, center, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.9F,
                    0.9F + level.random.nextFloat() * 0.1F);
        }

        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void placePart(Level level, BlockPos pos, CabDeskPart part, Direction facing) {
        BlockState state = ModBlocks.CAB_DESK.get().defaultBlockState()
                .setValue(CabDeskBlock.PART, part)
                .setValue(CabDeskBlock.FACING, facing);
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }
}
