package emucabdisplaytest;

import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.minecraft.client.gui.components.*;
import net.minecraft.world.phys.*;
import net.minecraft.core.Direction;
import java.nio.file.*;

@EventBusSubscriber(modid="emucabdisplaytest",value=Dist.CLIENT)
public final class DisplayClientCheck {
    private static int ticks;private static String capture;private static boolean done;
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static Object field(Object object,String name)throws Exception{
        var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(done||mc.player==null||mc.level==null||!(mc.level.getBlockEntity(DisplayHarness.CENTER) instanceof CabDeskBlockEntity desk))return;
        ticks++;mc.options.hideGui=true;
        try {
            if(ticks==60){
                check(desk.clockMode()==0&&desk.dateMode()==0,"Wrong default display modes");
                check(desk.displayTime().equals("12:00:00"),"Game noon incorrect: "+desk.displayTime());
                check(desk.displayDate().equals(java.time.LocalDate.now().toString()),"Not using client system date");
                mc.hitResult=new BlockHitResult(DisplayHarness.CENTER.getCenter(),Direction.NORTH,DisplayHarness.CENTER,false);
                var use=new net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered(1,mc.options.keyUse,net.minecraft.world.InteractionHand.MAIN_HAND);
                com.yasui_i.emucab.client.CabInteraction.use(use);
                check(use.isCanceled()&&mc.screen!=null&&mc.screen.getClass().getSimpleName().equals("CabDisplaySettingsScreen"),"Wrench did not open settings");
                ((EditBox)field(mc.screen,"locomotive")).setValue("EMU-1234");
                ((Button)field(mc.screen,"dateButton")).onPress();
                ((EditBox)field(mc.screen,"customDate")).setValue("2024-02-29");
                check(((Button)field(mc.screen,"save")).active,"Leap date not accepted");
                capture="settings-01-menu.png";
            }
            if(ticks==80){((Button)field(mc.screen,"save")).onPress();}
            if(ticks==110){
                check(desk.dateMode()==1&&desk.customDate().equals("2024-02-29")&&desk.locomotiveNumber().equals("EMU-1234"),"Settings did not reach server and return");
                
                check(Math.abs(desk.getShownSpeedKmh(1)-184)<.02,"Missing speed");
                capture="settings-02-live.png";
            }
            if(ticks==130){mc.getConnection().sendCommand("emucabdisplayeb");}
            if(ticks==155){check(desk.isEmergency(),"Emergency not synchronized");capture="settings-03-eb.png";}
            if(ticks==180){
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.yasui_i.emucab.network.CabDisplaySettingsPayload(DisplayHarness.CENTER,1,0,"","EMU-1234"));
            }
            if(ticks==205){
                check(desk.clockMode()==1&&desk.dateMode()==0,"Real clock settings not synchronized");
                check(desk.displayDate().equals(java.time.LocalDate.now().toString()),"System date incorrect");
                String real=java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                check(desk.displayTime().substring(0,5).equals(real.substring(0,5)),"System time incorrect");
                capture="settings-04-real-clock.png";
            }
            if(ticks==220){mc.getConnection().sendCommand("emucabdisplaymon");}
            if(ticks==245){
                check(mc.screen!=null&&mc.screen.getClass().getSimpleName().equals("CabMonScreen"),"MON softkey failed");
                var buttons=mc.screen.children().stream().filter(w->w instanceof Button).map(w->(Button)w).toList();
                buttons.get(1).onPress();capture="settings-05-mon.png";
            }
            if(ticks==255){mc.screen.children().stream().filter(w->w instanceof Button).map(w->(Button)w).toList().get(2).onPress();}
            if(ticks==280){check(desk.pantographs().cutRear&&!desk.pantographs().cutFront,"Isolation not synchronized");check(!desk.controls().on(com.yasui_i.emucab.control.CabAction.PANTOGRAPH),"Isolation did not lower");}
            if(ticks>290){
                Files.writeString(mc.gameDirectory.toPath().resolve("display-settings-check.txt"),
                    "PASS: Create wrench input opens menu; defaults game noon/system date; GUI saves locomotive and valid leap date through production packet; server NBT round trip; synchronized emergency flag; real/system clock switching; MON softkey open and rear isolation save/lower; five screenshots. Synthetic fixed desk, no driven Sable test.\n");
                System.out.println("EMU_DISPLAY_SETTINGS_CLIENT: PASS");done=true;mc.getConnection().sendCommand("stop");mc.stop();
            }
        }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("display-settings-check.txt"),"FAIL: "+t);}catch(Exception ignored){}done=true;mc.stop();}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if(capture==null)return;String name=capture;capture=null;var mc=Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});
    }
}
