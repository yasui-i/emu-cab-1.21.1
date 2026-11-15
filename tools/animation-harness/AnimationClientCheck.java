package emucabanimationtest;

import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.nio.file.*;
import java.util.Arrays;

/** The real server changes controls; the client waits for received state/events and samples frames. */
@EventBusSubscriber(modid="emucabanimationtest",value=Dist.CLIENT)
public final class AnimationClientCheck {
    private static int ticks,phase;private static String capture;private static boolean done;private static int presses;
    private static String id(CabAction action){return Arrays.stream(CabGeometry.CONTROLS).filter(c->c.action()==action).findFirst().orElseThrow().id();}
    private static CabGeometry.Control control(CabAction action){return Arrays.stream(CabGeometry.CONTROLS).filter(c->c.action()==action).findFirst().orElseThrow();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static final boolean DIRECTION_AXIS=Boolean.getBoolean("emucab.test.directionAxis");
    private static void directionTick(Minecraft mc,CabDeskBlockEntity desk)throws Exception{
        if(phase==0&&ticks>40){
            var motion=CabAnimationGeometry.byId(id(CabAction.DIRECTION));
            var axis=new net.minecraft.world.phys.Vec3(motion.axisX(),motion.axisY(),motion.axisZ());
            try(var reader=mc.getResourceManager().openAsReader(com.yasui_i.emucab.EMUCab.id("meshes/cab_dynamic.json"))){
                var meshes=com.google.gson.JsonParser.parseReader(reader).getAsJsonArray();
                for(var value:meshes){var mesh=value.getAsJsonObject();if(!mesh.get("name").getAsString().equals("05_Remote_monitor_|_LCD"))continue;
                    var p=mesh.getAsJsonArray("triangles").get(0).getAsJsonObject().getAsJsonArray("p");
                    var a=point(p.get(0));var b=point(p.get(1));var c=point(p.get(2));
                    check(axis.distanceTo(b.subtract(a).cross(c.subtract(a)).normalize())<1e-5,"Direction axis does not follow right backplate");
                }
            }
            check(desk.controls().direction==0,"Direction should initially be zero");
            capture="direction-01-zero.png";phase=1;ticks=0;
        }else if(phase==1&&ticks>4){mc.getConnection().sendCommand("emucabanimation B042");phase=2;ticks=0;
        }else if(phase==2&&ticks>16){check(desk.controls().direction==1,"Forward detent missing");capture="direction-02-forward.png";phase=3;ticks=0;
        }else if(phase==3&&ticks>4){mc.getConnection().sendCommand("emucabanimation B042");phase=4;ticks=0;
        }else if(phase==4&&ticks>16){check(desk.controls().direction==-1,"Reverse detent missing");capture="direction-03-reverse.png";phase=5;ticks=0;
        }else if(phase==5&&ticks>4){mc.getConnection().sendCommand("emucabanimation B042");phase=6;ticks=0;
        }else if(phase==6&&ticks>16){check(desk.controls().direction==0,"Return to zero missing");capture="direction-04-zero-return.png";phase=7;ticks=0;
        }else if(phase==7&&ticks>6){
            Files.writeString(mc.gameDirectory.toPath().resolve("direction-client-check.txt"),"PASS: right-backplate normal axis; actual server direction 0/forward/reverse/0; four settled close-up screenshots\n");
            System.out.println("EMU_DIRECTION_AXIS_CLIENT: PASS");done=true;mc.stop();
        }
    }
    private static net.minecraft.world.phys.Vec3 point(com.google.gson.JsonElement value){var p=value.getAsJsonArray();return new net.minecraft.world.phys.Vec3(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(done||mc.player==null||mc.level==null||!(mc.level.getBlockEntity(AnimationHarness.CENTER) instanceof CabDeskBlockEntity desk))return;
        ticks++;mc.options.hideGui=true;
        try{
            if(DIRECTION_AXIS){directionTick(mc,desk);return;}
            if(phase==0&&ticks>40){
                mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();
                check(!desk.controls().on(CabAction.MASTER)&&desk.controls().direction==0,"New cab not closed/zero");
                capture="01-off-zero.png";mc.getConnection().sendCommand("emucabanimation B172");phase=1;ticks=0;
            }else if(phase==1&&ticks>20){
                check(desk.controls().on(CabAction.MASTER),"Server master state did not arrive");
                check(desk.controlAnimation().rotationDegrees(control(CabAction.MASTER),desk.controls(),mc.level.getGameTime())==90,"Key did not settle on");
                capture="02-key-on.png";mc.getConnection().sendCommand("emucabanimation B042");phase=2;ticks=0;
            }else if(phase==2&&ticks>20){
                check(desk.controls().direction==1,"Server forward detent did not arrive");capture="03-forward.png";
                mc.getConnection().sendCommand("emucabanimation B017");phase=3;ticks=0;
                // Door opening is intentionally rejected without release. No actuation should play.
            }else if(phase==3&&ticks>15){
                check(desk.controlAnimation().pressAmount(id(CabAction.DOOR_OPEN_LEFT),mc.level.getGameTime())==0,"Rejected command animated");
                mc.getConnection().sendCommand("emucabanimation B019");phase=4;ticks=0;
            }else if(phase==4&&ticks>15){
                check(desk.controls().on(CabAction.DOOR_RELEASE_LEFT),"Release state missing");
                mc.getConnection().sendCommand("emucabanimation B017");phase=5;ticks=0;
            }else if(phase==5&&ticks>30)throw new AssertionError("No successful button actuation packet arrived");
            else if(phase==6&&ticks>12){
                check(desk.controlAnimation().pressAmount(id(CabAction.DOOR_OPEN_LEFT),mc.level.getGameTime())==0,"Button did not rebound");
                capture="05-button-released.png";mc.getConnection().sendCommand("emucabanimation B017");phase=7;ticks=0;
            }else if(phase==7&&ticks>30)throw new AssertionError("Repeated successful press did not retrigger");
            else if(phase==8&&ticks>12){
                check(presses==2,"Expected two distinct received presses");
                mc.getConnection().sendCommand("emucabanimation B172");phase=9;ticks=0;
            }else if(phase==9&&ticks>20){
                check(!desk.controls().on(CabAction.MASTER),"Server master off did not arrive");capture="07-key-off.png";phase=10;ticks=0;
            }else if(phase==10&&ticks>10){
                Files.writeString(mc.gameDirectory.toPath().resolve("animation-client-check.txt"),"PASS: actual server activate and client event packets; initial key-off/direction-zero; key on/off and forward detent; rejected door command silent; successful button depressed/rebounded and repeated press retriggered; screenshots captured\n");
                System.out.println("EMU_ANIMATION_CLIENT: PASS");done=true;mc.stop();
            }
        }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve(DIRECTION_AXIS?"direction-client-check.txt":"animation-client-check.txt"),"FAIL phase "+phase+": "+t);}catch(Exception ignored){}done=true;mc.stop();}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){
        var mc=Minecraft.getInstance();
        if(!DIRECTION_AXIS&&!done&&(phase==5||phase==7)&&mc.level!=null&&mc.level.getBlockEntity(AnimationHarness.CENTER) instanceof CabDeskBlockEntity desk){
            double now=mc.level.getGameTime();
            if(desk.controlAnimation().pressAmount(id(CabAction.DOOR_OPEN_LEFT),now)>.8){
                capture=phase==5?"04-button-depressed.png":"06-repeated-depressed.png";presses++;phase=phase==5?6:8;ticks=0;
            }
        }
        if(capture==null)return;String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});
    }
}
