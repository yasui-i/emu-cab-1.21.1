package com.yasui_i.emucab.client;

import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.network.CabTelemetryBindingPayload;
import com.yasui_i.emucab.network.CabTelemetryBindingResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Registration sets brake-scaling membership; the diagnostics display actual post-scale force. */
public final class CabTelemetryScreen extends Screen {
    private int pending;
    private Component status;
    private final java.util.List<Button> carButtons=new java.util.ArrayList<>();
    private final CabDeskBlockEntity desk;private final CabTelemetryBindingPayload selection;
    public CabTelemetryScreen(CabDeskBlockEntity desk){this(desk,null);}
    private CabTelemetryScreen(CabDeskBlockEntity desk,CabTelemetryBindingPayload selection){super(Component.translatable("screen.emucab.telemetry"));this.desk=desk;this.selection=selection;}
    public static void open(CabTelemetryBindingPayload p){var mc=Minecraft.getInstance();if(mc.level!=null)mc.setScreen(new CabTelemetryScreen(mc.level.getBlockEntity(p.cab()) instanceof CabDeskBlockEntity desk?desk:null,p));}
    public static void result(CabTelemetryBindingResultPayload p){
        var mc=Minecraft.getInstance();
        if(!(mc.screen instanceof CabTelemetryScreen screen)||screen.pending!=p.car()
                ||!p.cab().equals(screen.selection==null?screen.desk.getBlockPos():screen.selection.cab()))return;
        screen.pending=0;screen.carButtons.forEach(button->button.active=true);
        screen.status=p.accepted()?Component.literal((p.repaired()?"已修复重复标识，":"")+String.format("%02d 车已登记 %d 个转向架",Math.abs(p.car()),p.count())):Component.translatable("message.emucab.telemetry_failed");
        if(mc.player!=null)mc.player.displayClientMessage(screen.status,true);
        if(p.accepted()&&screen.selection!=null)mc.setScreen(null);
    }

    @Override protected void init(){int x=width/2,y=height/2;
        carButtons.clear();
        for(int i=1;i<=8;i++){
            final int car=i;
            var button=addRenderableWidget(Button.builder(Component.literal(selection==null?"清除":"登记到 "+String.format("%02d",i)+" 车"),b->{
                if(pending!=0)return;
                pending=selection==null?-car:car;
                status=Component.literal("正在等待服务端登记结果…");carButtons.forEach(other->other.active=false);
                PacketDistributor.sendToServer(new CabTelemetryBindingPayload(selection==null?desk.getBlockPos():selection.cab(),selection==null?desk.getBlockPos():selection.bogey(),selection==null?desk.telemetryId():selection.cabId(),selection==null?-car:car));
            }).bounds(x+126,y-80+(i-1)*20,selection==null?42:100,20).build());
            button.active=pending==0;carButtons.add(button);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x-40,y+110,80,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){int x=width/2,y=height/2;
        g.fillGradient(0,0,width,height,0xB010141B,0xD010141B);g.fill(x-235,y-119,x+235,y+148,0xFF18212A);
        g.drawCenteredString(font,title,x,y-108,0xFFECCB73);
        g.drawString(font,"车  登记/在线/测量   牵引kN 制动kN  本地/组指令",x-225,y-92,0xFFACBAC5);
        for(int car=1;car<=8;car++){
            final int number=car;var c=desk==null?com.yasui_i.emucab.telemetry.CabTelemetry.Car.EMPTY:desk.telemetry().car(car);long count=desk==null?0:desk.telemetry().bindings().stream().filter(b->b.car()==number).count();
            String forces=c.complete()?String.format(java.util.Locale.ROOT,"%7.1f %7.1f",c.traction()/1000,c.brake()/1000):"      —       —";
            String row=String.format(java.util.Locale.ROOT,"%02d  %d/%d/%d  %s   %3.0f/%3.0f%%",car,count,c.loaded(),c.measured(),forces,c.localCommand()*100,c.effectiveCommand()*100);
            g.drawString(font,row,x-225,y-74+(car-1)*20,c.fault()?0xFFFF655E:0xFFDCE2DC);
        }
        g.drawString(font,"E行仅作简化指示；此处制动力是Simurail机械制动反馈。",x-225,y+88,0xFF9AA8B2);
        g.drawString(font,selection==null?"工具先右键驾驶台，再右键转向架选择车号。":"登记后应用配置的制动力倍率；无需增加接线。",x-225,y+101,0xFF9AA8B2);
        if(status!=null)g.drawString(font,status,x-225,y+133,0xFFECCB73);
        super.render(g,mx,my,partial);
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
