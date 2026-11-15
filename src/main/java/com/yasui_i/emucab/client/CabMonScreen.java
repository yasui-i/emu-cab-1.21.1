package com.yasui_i.emucab.client;

import com.yasui_i.emucab.network.CabMonPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CabMonScreen extends Screen {
    private final BlockPos pos;
    private boolean cutFront,cutRear;
    private CabMonScreen(CabMonPayload packet){super(Component.translatable("screen.emucab.mon"));pos=packet.pos();cutFront=packet.cutFront();cutRear=packet.cutRear();}
    public static void open(CabMonPayload packet){if(Minecraft.getInstance().level!=null)Minecraft.getInstance().setScreen(new CabMonScreen(packet));}
    private Component label(boolean front){return Component.translatable("screen.emucab.mon_isolation",
            Component.translatable(front?"screen.emucab.front_panto":"screen.emucab.rear_panto"),
            Component.translatable((front?cutFront:cutRear)?"screen.emucab.isolated":"screen.emucab.available"));}
    @Override protected void init(){int x=width/2,y=height/2;
        addRenderableWidget(Button.builder(label(true),b->{cutFront=!cutFront;b.setMessage(label(true));}).bounds(x-100,y-23,200,20).build());
        addRenderableWidget(Button.builder(label(false),b->{cutRear=!cutRear;b.setMessage(label(false));}).bounds(x-100,y+2,200,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->{PacketDistributor.sendToServer(new CabMonPayload(pos,cutFront,cutRear));onClose();}).bounds(x-100,y+54,96,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(x+4,y+54,96,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fillGradient(0,0,width,height,0xA010141B,0xC010141B);int x=width/2,y=height/2;
        g.fill(x-112,y-65,x+112,y+87,0xFF1B242D);g.renderOutline(x-112,y-65,224,152,0xFF71808C);
        g.drawCenteredString(font,title,x,y-51,0xFFECCB73);
        g.drawCenteredString(font,Component.translatable("screen.emucab.mon_hint"),x,y+30,0xFFACBAC5);
        g.drawCenteredString(font,Component.translatable("screen.emucab.mon_lower_hint"),x,y+41,0xFFECCB73);
        super.render(g,mx,my,partial);
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
