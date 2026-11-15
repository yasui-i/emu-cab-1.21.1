package com.yasui_i.emucab.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.gui.AllIcons;
import com.yasui_i.emucab.blockentity.GradualThrottleBlockEntity;
import com.yasui_i.emucab.network.GradualThrottlePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;
import java.util.Optional;

/** Create-style configuration screen adapted from the Redstone Throttle UI. */
final class GradualThrottleScreen extends Screen {
    private static final ResourceLocation GUI_TEXTURE=ResourceLocation.fromNamespaceAndPath("emucab","textures/gui/gradual_throttle.png");
    private static final int WIDTH=174,HEIGHT=116,TITLE_Y=3,MODE_Y=25,INTERVAL_Y=47,OUTPUT_Y=69,CONFIRM_X=141,CONFIRM_Y=93;
    private final GradualThrottleBlockEntity throttle; private final net.minecraft.core.BlockPos pos;
    private int guiLeft,guiTop,intervalTicks; private long lastSoundMs;
    private EditBox stepInput;
    GradualThrottleScreen(GradualThrottleBlockEntity t){super(Component.translatable("screen.emucab.gradual_throttle"));throttle=t;pos=t.getBlockPos();intervalTicks=t.intervalTicks();}
    @Override protected void init(){
        guiLeft=(width-WIDTH)/2;guiTop=(height-HEIGHT)/2;
        stepInput=new EditBox(font,guiLeft+99,guiTop+20,60,16,Component.translatable("screen.emucab.throttle_step"));
        stepInput.setMaxLength(10);stepInput.setFilter(s->s.matches("[0-9]{0,10}"));stepInput.setValue(Integer.toString(throttle.rpmPerLevel()));
        stepInput.setResponder(s->{if(validStep())sendUpdate();});addRenderableWidget(stepInput);
        addRenderableWidget(new ConfirmButton(guiLeft+CONFIRM_X,guiTop+CONFIRM_Y,16));
    }
    private boolean validStep(){try{return Integer.parseInt(stepInput.getValue())>=1;}catch(NumberFormatException e){return false;}}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g,mx,my,partial);RenderSystem.setShaderColor(1,1,1,1);g.blit(GUI_TEXTURE,guiLeft,guiTop,0,0,WIDTH,HEIGHT,230,170);
        g.drawCenteredString(font,title,guiLeft+83,guiTop+TITLE_Y,0xFFECBF);g.drawString(font,Component.translatable("screen.emucab.throttle_step"),guiLeft+40,guiTop+MODE_Y,0xFFFFFF);
        g.drawString(font,Component.translatable("screen.emucab.throttle_interval"),guiLeft+40,guiTop+INTERVAL_Y,0xFFFFFF);g.drawCenteredString(font,String.format("%.2f s",intervalTicks/20f),guiLeft+112,guiTop+INTERVAL_Y,0xFFFFFF);
        g.drawString(font,Component.translatable("screen.emucab.throttle_output",throttle.outputRpm()),guiLeft+40,guiTop+OUTPUT_Y,0xFFFFFF);
        g.drawString(font,Component.translatable("screen.emucab.throttle_maximum",throttle.maximumRpm()),guiLeft+8,guiTop+95,0xC5D3DF);
        renderBlockPreview(g);super.render(g,mx,my,partial);renderTooltip(g,mx,my);
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){
        // Create's neutral dark screen overlay. The blue highlight is drawn
        // only by the confirm button, so it cannot tint the whole screen.
        g.fillGradient(0,0,width,height,1074794512,1343229968);
    }
    private void renderBlockPreview(GuiGraphics g){g.pose().pushPose();g.pose().translate(guiLeft+180,guiTop+62,0);g.pose().scale(5,5,1);Lighting.setupForFlatItems();g.renderItem(new ItemStack(throttle.getBlockState().getBlock()),0,0);g.pose().popPose();}
    private boolean inRow(double y,int row){return y>=guiTop+row-6&&y<=guiTop+row+14;}
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){if(sy==0)return super.mouseScrolled(mx,my,sx,sy);if(inRow(my,INTERVAL_Y)){int step=hasShiftDown()?10:1;intervalTicks=Mth.clamp(intervalTicks+(sy>0?step:-step),1,200);playScrollSound();sendUpdate();return true;}return super.mouseScrolled(mx,my,sx,sy);}
    private void sendUpdate(){if(validStep())PacketDistributor.sendToServer(new GradualThrottlePayload(pos,intervalTicks,Integer.parseInt(stepInput.getValue())));}
    private void playScrollSound(){long now=System.currentTimeMillis();if(now-lastSoundMs<50)return;lastSoundMs=now;Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(),1));}
    private void renderTooltip(GuiGraphics g,int mx,int my){if(inRow(my,INTERVAL_Y))g.renderTooltip(font,List.of(Component.translatable("screen.emucab.throttle_interval").withStyle(ChatFormatting.BLUE),Component.translatable("screen.emucab.throttle_scroll").withStyle(ChatFormatting.DARK_GRAY,ChatFormatting.ITALIC)),Optional.empty(),mx,my);
        else if(inRow(my,MODE_Y)&&mx<guiLeft+99)g.renderTooltip(font,Component.translatable("screen.emucab.throttle_step_hint"),mx,my);}
    @Override public void onClose(){AllSoundEvents.CONFIRM.playOnServer(throttle.getLevel(),pos);super.onClose();}
    @Override public boolean isPauseScreen(){return false;}
    private final class ConfirmButton extends AbstractButton {
        ConfirmButton(int x,int y,int size){super(x,y,size,size,Component.translatable("gui.done"));}
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){if(isHoveredOrFocused())g.fill(getX()-1,getY()-1,getX()+width+1,getY()+height+1,0xFFB9D8FF);AllIcons.I_CONFIRM.render(g,getX(),getY());}
        @Override public void onPress(){if(validStep())GradualThrottleScreen.this.onClose();}
        @Override protected void updateWidgetNarration(NarrationElementOutput n){defaultButtonNarrationText(n);}
    }
}
