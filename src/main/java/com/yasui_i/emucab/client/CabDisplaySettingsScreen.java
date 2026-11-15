package com.yasui_i.emucab.client;

import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.network.CabDisplaySettingsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Compact Create-style wrench menu for the cab's clock, date and locomotive number. */
final class CabDisplaySettingsScreen extends Screen {
    private final CabDeskBlockEntity desk;
    private EditBox locomotive,customDate,forceScale,consist;
    private int clockMode,dateMode,endpoint;
    private Button clockButton,dateButton,save;
    CabDisplaySettingsScreen(CabDeskBlockEntity desk){super(Component.translatable("screen.emucab.display_settings"));this.desk=desk;
        clockMode=desk.clockMode();dateMode=desk.dateMode();endpoint=desk.endpoint()==8?8:1;}
    @Override protected void init(){
        int x=width/2,y=height/2;
        locomotive=new EditBox(font,x-142,y-80,140,20,Component.translatable("screen.emucab.locomotive_number"));
        locomotive.setMaxLength(24);locomotive.setValue(desk.locomotiveNumber());
        locomotive.setFilter(s->s.length()<=24&&!s.contains("\n")&&!s.contains("\r"));addRenderableWidget(locomotive);
        consist=new EditBox(font,x+2,y-80,140,20,Component.translatable("screen.emucab.consist_id"));consist.setMaxLength(24);consist.setFilter(s->s.length()<=24&&!s.contains("\n")&&!s.contains("\r"));consist.setValue(desk.consistId());addRenderableWidget(consist);
        addRenderableWidget(Button.builder(Component.translatable("screen.emucab.endpoint",endpoint==1?"01":"08"),b->{endpoint=endpoint==1?8:1;b.setMessage(Component.translatable("screen.emucab.endpoint",endpoint==1?"01":"08"));}).bounds(x+2,y-53,140,20).build());
        clockButton=addRenderableWidget(Button.builder(clockLabel(),b->{clockMode=1-clockMode;b.setMessage(clockLabel());}).bounds(x-142,y-53,140,20).build());
        dateButton=addRenderableWidget(Button.builder(dateLabel(),b->{dateMode=1-dateMode;customDate.active=dateMode==1;b.setMessage(dateLabel());save.active=validSettings();}).bounds(x-142,y-28,140,20).build());
        customDate=new EditBox(font,x-142,y-3,140,20,Component.translatable("screen.emucab.custom_date"));
        customDate.setMaxLength(10);customDate.setFilter(s->s.matches("[0-9-]{0,10}"));customDate.setValue(desk.customDate().isBlank()?java.time.LocalDate.now().toString():desk.customDate());customDate.active=dateMode==1;addRenderableWidget(customDate);
        forceScale=new EditBox(font,x+2,y-3,140,20,Component.translatable("screen.emucab.force_scale"));
        forceScale.setMaxLength(6);forceScale.setFilter(s->s.matches("[0-9]{0,4}(\\.[0-9]?)?"));
        forceScale.setValue(com.yasui_i.emucab.control.CabDisplayFormat.scaleLabel(desk.forceScaleKn()));addRenderableWidget(forceScale);
        save=addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->{
            if(validSettings())PacketDistributor.sendToServer(new CabDisplaySettingsPayload(desk.getBlockPos(),clockMode,dateMode,customDate.getValue(),locomotive.getValue(),Double.parseDouble(forceScale.getValue()),consist.getValue(),endpoint));onClose();
        }).bounds(x-142,y+67,140,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(x+2,y+67,140,20).build());
        forceScale.setResponder(v->save.active=validSettings());
        customDate.setResponder(v->save.active=validSettings());save.active=validSettings();setInitialFocus(locomotive);
        addRenderableWidget(Button.builder(Component.translatable("screen.emucab.telemetry"),b->minecraft.setScreen(new CabTelemetryScreen(desk))).bounds(x-142,y+35,284,20).build());
    }
    private Component clockLabel(){return Component.translatable("screen.emucab.clock_mode",Component.translatable(clockMode==0?"screen.emucab.game_time":"screen.emucab.real_time"));}
    private Component dateLabel(){return Component.translatable("screen.emucab.date_mode",Component.translatable(dateMode==0?"screen.emucab.real_date":"screen.emucab.custom_date"));}
    private boolean validDate(){return dateMode==0||customDate!=null&&com.yasui_i.emucab.control.CabDisplayFormat.validDate(customDate.getValue());}
    private boolean validSettings(){
        if(!validDate()||forceScale==null)return false;
        try{return com.yasui_i.emucab.control.CabDisplayFormat.validForceScale(Double.parseDouble(forceScale.getValue()));}
        catch(NumberFormatException e){return false;}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fillGradient(0,0,width,height,0xA010141B,0xC010141B);int x=width/2,y=height/2;
        g.fill(x-152,y-111,x+152,y+112,0xF0121820);g.renderOutline(x-152,y-111,304,223,0xFF71808C);
        g.drawCenteredString(font,title,x,y-103,0xFFECCB73);g.drawString(font,Component.translatable("screen.emucab.locomotive_number"),x-142,y-92,0xFFDCE2DC);g.drawString(font,Component.translatable("screen.emucab.consist_id"),x+2,y-92,0xFFDCE2DC);
        g.drawString(font,Component.translatable("screen.emucab.force_scale"),x+2,y-16,0xFFDCE2DC);g.drawString(font,Component.translatable("screen.emucab.settings_hint"),x-142,y+97,0xFF9AA8B2);super.render(g,mx,my,partial);
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
