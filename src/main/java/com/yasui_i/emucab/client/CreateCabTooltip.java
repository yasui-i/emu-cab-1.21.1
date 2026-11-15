package com.yasui_i.emucab.client;

import com.simibubi.create.foundation.gui.RemovedGuiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import java.util.List;

/** Loaded only when the optional Create dependency is present. */
final class CreateCabTooltip {
    static void draw(GuiGraphics g,List<FormattedText> lines,int x,int y) {
        RemovedGuiUtils.drawHoveringText(g,lines,x,y,g.guiWidth(),g.guiHeight(),-1,Minecraft.getInstance().font);
    }
}
