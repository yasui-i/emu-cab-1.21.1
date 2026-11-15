package com.yasui_i.emucab.client;

import com.yasui_i.emucab.blockentity.SpeedSignBlockEntity;
import com.yasui_i.emucab.network.SpeedSignPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

final class SpeedSignScreen extends Screen {
    private final SpeedSignBlockEntity sign;
    private EditBox speed;
    private boolean reverse;
    private Button save;
    SpeedSignScreen(SpeedSignBlockEntity sign) { super(Component.translatable("screen.emucab.speed_sign"));this.sign=sign; }
    @Override protected void init() {
        int x=width/2,y=height/2;
        speed=new EditBox(font,x-65,y-20,130,20,Component.translatable("screen.emucab.speed_limit"));
        speed.setMaxLength(4);speed.setFilter(s->s.matches("[0-9]{0,4}"));speed.setValue(Integer.toString(sign.limit()));
        addRenderableWidget(speed);
        addRenderableWidget(Button.builder(Component.translatable("screen.emucab.sign_reverse"),button->{
            reverse=!reverse;button.setMessage(Component.translatable(reverse?"screen.emucab.sign_reversed":"screen.emucab.sign_reverse"));
        }).bounds(x-100,y+15,200,20).build());
        save=addRenderableWidget(Button.builder(Component.translatable("gui.done"),button->{
            PacketDistributor.sendToServer(new SpeedSignPayload(sign.getBlockPos(),Integer.parseInt(speed.getValue()),reverse));onClose();
        }).bounds(x-100,y+50,95,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(x+5,y+50,95,20).build());
        speed.setResponder(s->save.active=valid(s));save.active=valid(speed.getValue());setInitialFocus(speed);
    }
    private boolean valid(String value) {
        try { int n=Integer.parseInt(value);return n>=1 && n<=1000; }catch(NumberFormatException e) { return false; }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        super.render(g,mx,my,delta);
        g.drawCenteredString(font,title,width/2,height/2-70,0xFFFFFF);
        g.drawCenteredString(font,Component.translatable("screen.emucab.speed_limit"),width/2,height/2-42,0xFFDC80);
        g.drawCenteredString(font,Component.translatable("screen.emucab.sign_direction",String.format("%.1f, %.1f",sign.forward().x,sign.forward().z)),width/2,height/2-95,0xA0B0C0);
    }
    @Override public boolean isPauseScreen() { return false; }
}
