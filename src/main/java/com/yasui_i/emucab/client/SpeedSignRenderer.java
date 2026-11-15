package com.yasui_i.emucab.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yasui_i.emucab.block.SpeedSignBlock;
import com.yasui_i.emucab.blockentity.SpeedSignBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;

public final class SpeedSignRenderer implements BlockEntityRenderer<SpeedSignBlockEntity> {
    public SpeedSignRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(SpeedSignBlockEntity sign,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        pose.pushPose();pose.translate(.5,.69,.5);
        pose.mulPose(Axis.YP.rotationDegrees(-sign.getBlockState().getValue(SpeedSignBlock.FACING).toYRot()));
        pose.translate(0,0,.064);pose.scale(.015F,-.015F,.015F);
        var font=Minecraft.getInstance().font;String value=Integer.toString(sign.limit());
        font.drawInBatch(value,-font.width(value)/2F,-4,0xFF111111,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,light);
        pose.popPose();
    }
}
