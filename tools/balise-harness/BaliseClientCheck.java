package emucabtest;

import com.yasui_i.emucab.client.BaliseClient;
import com.yasui_i.emucab.client.BaliseScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.nio.file.*;

/** Test-only in-engine input events and real network round trips. Never shipped. */
@EventBusSubscriber(modid="emucabbalisetest",value=Dist.CLIENT)
public final class BaliseClientCheck {
    private static int ticks,phase;private static String capture;
    private static boolean initialized;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void use(){var mc=Minecraft.getInstance();mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(mc.player.getInventory().selected));var event=new InputEvent.InteractionKeyMappingTriggered(1,mc.options.keyUse,InteractionHand.MAIN_HAND);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);check(event.isCanceled(),"Balise did not consume rail use phase "+phase+" screen="+mc.screen+" hit="+mc.hitResult+" held="+mc.player.getMainHandItem());}
    private static void remove(){var mc=Minecraft.getInstance();mc.player.getInventory().selected=3;use();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||phase<0)return;
        if(!initialized){mc.player.getInventory().selected=0;mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));initialized=true;}
        if(++ticks<60)return;ticks=0;
        try{
            switch(phase++){
                case 0->{capture="01-observer-preview.png";}
                case 1->{use();}
                case 2->{capture="02-installed-normal.png";mc.player.getInventory().selected=1;}
                case 3->{use();}
                case 4->{check(mc.screen instanceof BaliseScreen&&mc.screen.children().stream().noneMatch(c->c instanceof EditBox),"Normal settings packet did not open correct screen");capture="03-normal-menu.png";}
                case 5->{((BaliseScreen)mc.screen).onClose();remove();}
                case 6->{mc.player.getInventory().selected=2;capture="04-signal-preview.png";}
                case 7->{use();}
                case 8->{check(mc.level.getBlockState(new net.minecraft.core.BlockPos(10,85,8)).getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock,"Wrench removal damaged rail");capture="05-installed-limit.png";mc.player.getInventory().selected=1;}
                case 9->{use();}
                case 10->{check(mc.screen instanceof BaliseScreen,"Limit settings packet did not open screen");capture="06-limit-menu.png";}
                case 11->{var screen=(BaliseScreen)mc.screen;for(var child:screen.children())if(child instanceof EditBox box)box.setValue("125");for(var child:screen.children())if(child instanceof Button button&&button.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("gui.done").getString()))button.onPress();}
                case 12->{use();}
                case 13->{check(mc.screen instanceof BaliseScreen,"Reopen configured limit failed");check(((BaliseScreen)mc.screen).children().stream().anyMatch(c->c instanceof EditBox box&&box.getValue().equals("125")),"Configured limit did not survive server round trip");capture="07-saved-limit.png";}
                case 14->{((BaliseScreen)mc.screen).onClose();remove();}
                case 15->{check(mc.level.getBlockState(new net.minecraft.core.BlockPos(10,85,8)).getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock,"Wrench removal damaged rail");mc.player.getInventory().selected=1;var use=new InputEvent.InteractionKeyMappingTriggered(1,mc.options.keyUse,InteractionHand.MAIN_HAND);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(use);check(!use.isCanceled(),"Removed balise still intercepts empty-hand use");mc.player.getInventory().selected=0;capture="08-rail-preserved.png";Files.writeString(mc.gameDirectory.toPath().resolve("balise-client-check.txt"),"PASS: model baked/rendered; full event-bus install/open/configure125/reopen/wrench-remove packets; rail preserved; attachment removed");System.out.println("EMU_BALISE_CLIENT: PASS");phase=-1;}
            }
        }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("balise-client-check.txt"),"FAIL phase "+phase+": "+t);}catch(Exception ignored){}phase=-1;}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){
        if(capture==null)return;var name=capture;capture=null;var mc=Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});
    }
}
