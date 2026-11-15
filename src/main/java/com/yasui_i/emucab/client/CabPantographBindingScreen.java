package com.yasui_i.emucab.client;
import com.yasui_i.emucab.network.CabPantographBindingPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
public final class CabPantographBindingScreen extends Screen {
    private final CabPantographBindingPayload selection;
    private CabPantographBindingScreen(CabPantographBindingPayload p){super(Component.literal("登记受电弓"));selection=p;}
    public static void open(CabPantographBindingPayload p){if(Minecraft.getInstance().level!=null)Minecraft.getInstance().setScreen(new CabPantographBindingScreen(p));}
    @Override protected void init(){int x=width/2,y=height/2;
        for(int i=0;i<2;i++){int car=i==0?3:6;addRenderableWidget(Button.builder(Component.literal(String.format("登记到 %02d 车",car)),b->{PacketDistributor.sendToServer(new CabPantographBindingPayload(selection.cab(),selection.receiver(),selection.cabId(),car));onClose();}).bounds(x-95,y-10+i*25,190,20).build());}
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(x-95,y+55,190,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fillGradient(0,0,width,height,0xB010141B,0xD010141B);int x=width/2,y=height/2;g.fill(x-115,y-63,x+115,y+89,0xFF18212A);g.drawCenteredString(font,title,x,y-49,0xFFECCB73);g.drawCenteredString(font,"两端相同编组 ID 共享该受电弓反馈",x,y-30,0xFFACBAC5);super.render(g,mx,my,partial);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
