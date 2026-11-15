package emucabthrottleuitest;

import com.yasui_i.emucab.blockentity.GradualThrottleBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.nio.file.Files;

@EventBusSubscriber(modid="emucabthrottleuitest",value=Dist.CLIENT)
public final class ThrottleUiClient {
    static int ticks;static boolean done;static String capture;
    static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(done||mc.level==null||mc.player==null||!(mc.level.getBlockEntity(ThrottleUiHarness.POS) instanceof GradualThrottleBlockEntity be))return;
        try{
            ticks++;
            if(ticks==50){
                check(be.maximumRpm()==512,"Server cap not synchronized");
                var ctor=Class.forName("com.yasui_i.emucab.client.GradualThrottleScreen").getDeclaredConstructor(GradualThrottleBlockEntity.class);ctor.setAccessible(true);
                mc.setScreen((Screen)ctor.newInstance(be));
                var input=mc.screen.children().stream().filter(w->w instanceof EditBox).map(w->(EditBox)w).findFirst().orElseThrow();
                check(input.getValue().equals("17"),"Default step");input.setValue("20");
            }
            if(ticks==75){
                check(be.rpmPerLevel()==20,"GUI edit did not reach server/return");
                capture="throttle-step20-cap512.png";
                mc.screen.mouseScrolled(mc.screen.width/2.,mc.screen.height/2.-11,0,1);
            }
            if(ticks==100){
                check(be.intervalTicks()==2&&be.rpmPerLevel()==20,"Interval edit lost step");
                capture="throttle-step20-interval2.png";
            }
            if(ticks==125){
                Files.writeString(mc.gameDirectory.toPath().resolve("throttle-ui-check.txt"),"PASS: actual client GUI default17; step20 production packet/server echo; server cap512 sync; interval scroll preserves step; screenshots captured.\n");
                done=true;mc.getConnection().sendCommand("stop");mc.stop();
            }
        }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("throttle-ui-check.txt"),"FAIL: "+t);}catch(Exception ignored){}done=true;mc.getConnection().sendCommand("stop");mc.stop();}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){
        if(capture==null)return;var mc=Minecraft.getInstance();var name=capture;capture=null;
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});
    }
}
