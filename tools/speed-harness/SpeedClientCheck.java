package emucabspeedtest;

import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.nio.file.*;

/** Samples exact production display API while the fixed body's pose velocity stays zero. */
@EventBusSubscriber(modid="emucabspeedtest",value=Dist.CLIENT)
public final class SpeedClientCheck {
    private static int ticks,phase;private static boolean done;private static String capture;
    private static double last=200,maxSteadyError;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(done||mc.player==null||mc.level==null||!(mc.level.getBlockEntity(SpeedHarness.CENTER) instanceof CabDeskBlockEntity desk))return;
        ticks++;mc.options.hideGui=true;
        try{
            double start=desk.getShownSpeedKmh(0),mid=desk.getShownSpeedKmh(.5F),end=desk.getShownSpeedKmh(1);
            check(Double.isFinite(end)&&end>=0,"Invalid display speed");
            check(Math.abs(mid-(start+end)/2)<1e-6,"Display has no frame interpolation");
            if(phase==0&&ticks>60){
                check(Math.abs(end-200)<.02,"Authoritative 200 sample not received: "+end);
                maxSteadyError=Math.max(maxSteadyError,Math.abs(end-200));
                if(ticks>100){capture="speed-01-200.png";mc.getConnection().sendCommand("emucabspeed 350");phase=1;ticks=0;last=end;}
            }else if(phase==1){
                check(end>=last-.001&&end<=350.001,"Acceleration jumped backwards/overshot: "+last+" -> "+end);
                last=end;
                if(ticks>70){check(Math.abs(end-350)<.02,"350 not settled");capture="speed-02-350.png";phase=2;ticks=0;}
            }else if(phase==2){
                maxSteadyError=Math.max(maxSteadyError,Math.abs(end-350));
                check(Math.abs(end-350)<.02,"Constant high speed jitters despite fixed zero-velocity client pose");
                // Invalid/stale direct samples must not overwrite received authoritative data.
                desk.acceptSpeedSample(-1,0);desk.acceptSpeedSample(Long.MAX_VALUE,Double.NaN);
                if(ticks>80){mc.getConnection().sendCommand("emucabspeed 0");phase=3;ticks=0;last=end;}
            }else if(phase==3){
                check(end<=last+.001&&end>=0,"Deceleration jumped forwards/below zero");last=end;
                if(ticks>80){check(end<.02,"Stop not settled");capture="speed-03-stop.png";phase=4;ticks=0;}
            }else if(phase==4&&ticks>8){
                Files.writeString(mc.gameDirectory.toPath().resolve("speed-client-check.txt"),"PASS: synthetic authoritative samples traversed real server/client payload at 200/350/0 km/h; high-speed steady error="+maxSteadyError+" km/h; fixed zero-velocity client pose did not overwrite speed; acceleration/deceleration monotonic; frame interpolation; stale/invalid rejection. This is a synthetic synchronization/display test, not a driven moving Sable train.\n");
                System.out.println("EMU_SPEED_CLIENT: PASS");done=true;mc.stop();
            }
        }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("speed-client-check.txt"),"FAIL phase "+phase+": "+t);}catch(Exception ignored){}done=true;mc.stop();}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){
        if(capture==null)return;var mc=Minecraft.getInstance();String name=capture;capture=null;
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});
    }
}
