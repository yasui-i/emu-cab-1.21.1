package com.yasui_i.emucab.client;

import com.yasui_i.emucab.network.CabHandlePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Transparent non-pausing grip screen: the mouse moves the handle, never the camera. */
public final class CabHandleScreen extends Screen {
    final CabInteraction.Target target;
    private double anchorY,position;
    private int anchorNotch,lastSent,ticks;
    private boolean ended;
    CabHandleScreen(CabInteraction.Target target) {super(Component.translatable("control.emucab.handle"));this.target=target;}
    @Override protected void init() {
        anchorY=minecraft.mouseHandler.ypos()*height/minecraft.getWindow().getHeight();
        anchorNotch=target.desk().leverPosition();position=anchorNotch;lastSent=anchorNotch;
        PacketDistributor.sendToServer(new com.yasui_i.emucab.network.CabButtonPayload(target.desk().getBlockPos(),
                target.control().number(),target.eye(),target.surface()));
    }
    @Override public void mouseMoved(double x,double y) {
        double step=Math.min(10,Math.max(2,(height/2.0-12)/15));
        position=Math.clamp(anchorNotch+(anchorY-y)/step,-8,7);
    }
    public float visualPosition() {
        if(target.desk().isEmergency())return -8;
        if(position>0 && !target.desk().controls().ready())return target.desk().leverPosition();
        return (float)position;
    }
    public int selectedNotch(){return (int)Math.round(position);}
    private void send(boolean force) {
        int notch=selectedNotch();
        if(minecraft.getConnection()!=null && (force || notch!=lastSent || ticks%5==0)) {
            PacketDistributor.sendToServer(new CabHandlePayload(target.desk().getBlockPos(),1,notch));lastSent=notch;
        }
    }
    @Override public void tick() {
        ticks++;
        var desk=target.desk();
        if(minecraft.player==null || minecraft.level!=desk.getLevel() || desk.isRemoved() || !minecraft.player.isAlive()
                || !minecraft.isWindowActive()) {
            minecraft.setScreen(null);return;
        }
        if(GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(),GLFW.GLFW_MOUSE_BUTTON_RIGHT)!=GLFW.GLFW_PRESS) {onClose();return;}
        send(false);
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        if(button==GLFW.GLFW_MOUSE_BUTTON_RIGHT){mouseMoved(x,y);onClose();return true;}
        return super.mouseReleased(x,y,button);
    }
    @Override public void onClose() {
        if(!ended) {
            // Send the final absolute notch twice across consecutive client ticks;
            // Sable assembly movement can delay one packet during a pose update.
            send(true);
            minecraft.tell(()->{if(minecraft.getConnection()!=null && !ended)send(true);});
        }
        minecraft.setScreen(null);
    }
    @Override public void removed() {
        if(!ended) {
            ended=true;
            if(minecraft.getConnection()!=null)PacketDistributor.sendToServer(new CabHandlePayload(target.desk().getBlockPos(),2,lastSent));
        }
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {CabTooltip.draw(g,target,true);}
}
