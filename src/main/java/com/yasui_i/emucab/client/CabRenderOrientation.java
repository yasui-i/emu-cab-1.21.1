package com.yasui_i.emucab.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;

/** Block-local rotation only; Sable supplies the enclosing body's world transform. */
public final class CabRenderOrientation {
    public static void apply(PoseStack pose,Direction facing) {
        pose.translate(.5,0,.5);
        // Blockstate JSON's positive Y rotations are negative Y-axis quaternions.
        pose.mulPose(Axis.YP.rotationDegrees(-facing.getOpposite().toYRot()));
        pose.translate(-.5,0,-.5);
    }

    private CabRenderOrientation() {}
}
