package com.yasui_i.emucab.client;

import com.yasui_i.emucab.control.CabAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import java.util.ArrayList;

/** Create's tooltip renderer, anchored beside the crosshair without requiring goggles. */
final class CabTooltip {
    static void draw(GuiGraphics g,CabInteraction.Target target,boolean dragging) {
        var mc=Minecraft.getInstance();var desk=target.desk();var c=target.control();
        if(!com.yasui_i.emucab.control.CabHitTesting.interactive(c))return;
        var lines=new ArrayList<Component>();
        boolean mon=c.number().equals("B140");
        lines.add(Component.translatable(mon?"screen.emucab.mon":"control.emucab."+c.action().channel()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable(mon?"screen.emucab.mon_open_hint":"hint.emucab."+c.action().channel()).withStyle(ChatFormatting.GRAY));
        if(!mon)lines.add(Component.translatable("tooltip.emucab.state",desk.controlStatus(c)).withStyle(ChatFormatting.AQUA));
        lines.add(Component.empty());
        if(c.action()!=CabAction.UNASSIGNED||mon)
            lines.add(Component.translatable(c.action()==CabAction.HANDLE?"tooltip.emucab.drag":"tooltip.emucab.use").withStyle(ChatFormatting.WHITE));
        if(c.action()==CabAction.HANDLE) {
            lines.add(Component.translatable("tooltip.emucab.handle_range").withStyle(ChatFormatting.DARK_GRAY));
            if(desk.isEmergency())lines.add(Component.translatable("message.emucab.reset_first").withStyle(ChatFormatting.RED));
            else if(!desk.controls().ready())lines.add(Component.translatable("tooltip.emucab.interlock").withStyle(ChatFormatting.RED));
            if(dragging)lines.add(Component.translatable("tooltip.emucab.release").withStyle(ChatFormatting.YELLOW));
        }
        int w=g.guiWidth(),h=g.guiHeight();
        var wrapped=new ArrayList<FormattedText>();
        int maxWidth=Math.min(220,Math.max(90,w/2-40));
        for(var line:lines) {
            if(line.getString().isEmpty())wrapped.add(line);
            else wrapped.addAll(mc.font.getSplitter().splitLines(line,maxWidth,Style.EMPTY));
        }
        if(net.neoforged.fml.ModList.get().isLoaded("create"))CreateCabTooltip.draw(g,wrapped,w/2+16,h/2-12);
        else g.renderComponentTooltip(mc.font,lines,w/2+16,h/2-12);
        if(dragging) {
            int x=w/2-28,top=Math.max(12,h/2-77);
            g.fill(x-10,top-6,x+12,top+158,0xD0101720);
            g.fill(x,top,x+2,top+150,0xFF6D8D9D);
            for(int n=-8;n<=7;n++) {
                int y=top+(7-n)*10;g.fill(x-3,y,x+6,y+1,n==0?0xFFFFDC80:0xFF8396A0);
            }
            int y=top+Math.round((7-CabInteraction.leverPosition(desk))*10);
            g.fill(x-6,y-2,x+9,y+3,0xFF70D8ED);
        }
    }
}
