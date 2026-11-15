package com.yasui_i.emucab.client;

import com.yasui_i.emucab.balise.BaliseData;
import com.yasui_i.emucab.balise.BaliseTarget;
import com.yasui_i.emucab.control.AtpAnnouncement;
import com.yasui_i.emucab.balise.BalisePackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Settings belong to the rail attachment, with no replacement rail block. */
public final class BaliseScreen extends Screen {
    private final BaliseData balise;
    private EditBox speed;
    private Button save;
    private boolean positive,enabled;
    private AtpAnnouncement cue;

    public BaliseScreen(BaliseData balise) {
        super(Component.translatable(balise.kind()==BaliseData.Kind.LIMIT
                ?"screen.emucab.balise_limit":"screen.emucab.balise_announcement"));
        this.balise=balise;positive=balise.target().positive();enabled=balise.enabled();
        cue=AtpAnnouncement.NEUTRAL_SECTION_AHEAD;
        for(var entry:AtpAnnouncement.values())if(entry.id().equals(balise.cue()))cue=entry;
    }

    @Override protected void init() {
        int x=width/2,y=height/2;
        if(balise.kind()==BaliseData.Kind.LIMIT) {
            speed=new EditBox(font,x-94,y-48,188,20,Component.translatable("screen.emucab.balise_speed"));
            speed.setMaxLength(4);speed.setFilter(s->s.matches("[0-9]{0,4}"));
            speed.setValue(Integer.toString(balise.limit()));addRenderableWidget(speed);
            addRenderableWidget(Button.builder(directionLabel(),button->{positive=!positive;button.setMessage(directionLabel());})
                    .bounds(x-94,y-14,188,20).build());
        } else {
            addRenderableWidget(CycleButton.<AtpAnnouncement>builder(c->Component.translatable("subtitle.emucab.atp."+c.id()))
                    .withValues(AtpAnnouncement.values()).withInitialValue(cue)
                    .create(x-112,y-38,224,20,Component.translatable("screen.emucab.balise_cue"),(button,value)->cue=value));
        }
        addRenderableWidget(CycleButton.booleanBuilder(Component.translatable("screen.emucab.balise_enabled"),
                        Component.translatable("screen.emucab.balise_disabled"))
                .withInitialValue(enabled).displayOnlyValue()
                .create(x-94,y+18,188,20,Component.empty(),(button,value)->enabled=value));
        save=addRenderableWidget(Button.builder(Component.translatable("gui.done"),button->{
            BalisePackets.sendConfigure(balise.id(),speed==null?balise.limit():Integer.parseInt(speed.getValue()),
                    cue.id(),enabled,positive!=balise.target().positive());
            onClose();
        }).bounds(x-94,y+56,90,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),button->onClose()).bounds(x+4,y+56,90,20).build());
        if(speed!=null){speed.setResponder(value->save.active=validSpeed(value));save.active=validSpeed(speed.getValue());setInitialFocus(speed);}
    }

    private Component directionLabel() {
        return Component.translatable(positive==balise.target().positive()?"screen.emucab.balise_direction":"screen.emucab.balise_reverse");
    }
    public BaliseTarget directionPreview(){
        if(balise.kind()!=BaliseData.Kind.LIMIT)return null;
        return positive==balise.target().positive()?balise.target():balise.target().reversed();
    }
    private boolean validSpeed(String value) {
        try {int n=Integer.parseInt(value);return n>=1&&n<=1000;}catch(NumberFormatException ignored){return false;}
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        renderBackground(g,mouseX,mouseY,partialTick);
        int x=width/2,y=height/2;
        g.fill(x-132,y-105,x+132,y+94,0xFF272A2D);
        g.fill(x-132,y-105,x+132,y-103,0xFFE0B94A);
        g.drawCenteredString(font,title,x,y-87,0xFFF0CA);
        if(balise.kind()==BaliseData.Kind.LIMIT)g.drawCenteredString(font,Component.translatable("screen.emucab.balise_speed"),x,y-63,0xC9CED2);
        else g.drawCenteredString(font,Component.translatable("screen.emucab.balise_bidirectional"),x,y-65,0xC9CED2);
        super.render(g,mouseX,mouseY,partialTick);
    }
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partialTick){g.fill(0,0,width,height,0x80101010);}
    @Override public boolean isPauseScreen(){return false;}
}
