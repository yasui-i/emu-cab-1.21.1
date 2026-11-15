package com.yasui_i.emucab.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.yasui_i.emucab.blockentity.GradualThrottleBlockEntity;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;

/** Renders both Create shaft halves, applying the throttle ratio to the output. */
final class GradualThrottleRenderer extends KineticBlockEntityRenderer<GradualThrottleBlockEntity> {
    GradualThrottleRenderer(Context context) { super(context); }

    @Override
    protected void renderSafe(GradualThrottleBlockEntity be, float partialTicks, PoseStack pose,
                              MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        Axis axis = ((IRotate) state.getBlock()).getRotationAxis(state);
        BlockPos pos = be.getBlockPos();
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float baseSpeed = be.getSpeed();
        float offset = getRotationOffsetForPosition(be, pos, axis);
        for (Direction direction : Iterate.directionsInAxis(axis)) {
            float renderedSpeed = baseSpeed * be.getRotationSpeedModifier(direction);
            float angle = (time * renderedSpeed * 3.0F / 10.0F + offset) % 360.0F
                    / 180.0F * (float) Math.PI;
            SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, direction);
            kineticRotationTransform(shaft, be, axis, angle, light);
            shaft.renderInto(pose, buffer.getBuffer(RenderType.solid()));
        }
    }
}
