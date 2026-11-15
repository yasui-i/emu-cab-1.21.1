package com.yasui_i.emucab.client;

import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.CabAction;
import com.yasui_i.emucab.control.CabGeometry;
import com.yasui_i.emucab.network.CabControlAnimationPayload;
import net.minecraft.client.Minecraft;

public final class CabControlAnimations {
    public static void receive(CabControlAnimationPayload packet) {
        var level=Minecraft.getInstance().level;
        if(level==null || !(level.getBlockEntity(packet.pos()) instanceof CabDeskBlockEntity desk) || !desk.isCenter())return;
        for(var control:CabGeometry.CONTROLS) {
            if(!control.id().equals(packet.id()))continue;
            if(control.action()!=CabAction.UNASSIGNED && control.action()!=CabAction.HANDLE)
                desk.controlAnimation().press(control.id(),packet.sequence(),level.getGameTime());
            return;
        }
    }
    private CabControlAnimations() {}
}
