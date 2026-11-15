package emucabgaugetest;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.*;
import com.yasui_i.emucab.telemetry.CabTelemetry;
import net.minecraft.client.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.file.*;
import java.util.*;

@EventBusSubscriber(modid="emucabgaugetest",value=Dist.CLIENT)
public final class GaugeClient {
    static int ticks,stage;static boolean connected,done;static final StringBuilder report=new StringBuilder();
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static boolean blue(int rgba){return (rgba&255)<70&&((rgba>>>8)&255)>60&&((rgba>>>8)&255)<100&&((rgba>>>16)&255)>180;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();if(done)return;
        if(mc.level==null||mc.player==null){
            if(mc.screen!=null&&mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
            if(!connected&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen){
                connected=true;net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25583"),new net.minecraft.client.multiplayer.ServerData("Gauge check","127.0.0.1:25583",net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);
            }
            return;
        }
        ticks++;mc.options.hideGui=true;
    }
    static CabDeskBlockEntity desk(){return (CabDeskBlockEntity)Minecraft.getInstance().level.getBlockEntity(GaugeHarness.CENTER);}
    static void seed(CabDeskBlockEntity desk,double voltage,double amps){
        CeeElectricalTelemetry.accept(desk,1_000_000+stage,new CeeElectricalTelemetry.Reading(true,true,true,true,voltage,amps),CeeElectricalTelemetry.Reading.EMPTY);
        for(int i=0;i<100;i++)desk.electricalDisplay().tick(voltage,amps);
        var cars=CabTelemetry.empty();for(int car:new int[]{2,4,5,7})cars[car-1]=new CabTelemetry.Car(2,2,2,2500,1000,0,0,false,false);
        desk.telemetry().accept(1_000_000+stage,desk.getLevel().getGameTime(),cars);for(int i=0;i<40;i++)desk.telemetry().expire(desk.getLevel().getGameTime());
    }
    static void gaugeImage(String name,double volts,double amps)throws Exception {
        var mc=Minecraft.getInstance();var desk=desk();seed(desk,volts,amps);
        var render=Class.forName("com.yasui_i.emucab.client.CabDisplayRenderer").getDeclaredMethod("render",int.class,CabDeskBlockEntity.class,PoseStack.class,MultiBufferSource.class,float.class);render.setAccessible(true);
        var target=new TextureTarget(1024,704,true,Minecraft.ON_OSX);
        var oldProjection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
        var oldModelView=new Matrix4f(RenderSystem.getModelViewStack());
        try(var memory=new ByteBufferBuilder(262144)){
            target.setClearColor(0,0,0,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,256,176,0,-10,10),VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.getModelViewStack().identity();RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShaderLights(new Vector3f(0,0,1),new Vector3f(0,0,1));
            mc.gameRenderer.lightTexture().turnOnLightLayer();mc.gameRenderer.overlayTexture().setupOverlayColor();
            var source=MultiBufferSource.immediate(memory);var pose=new PoseStack();
            render.invoke(null,1,desk,pose,source,1f);source.endBatch();
            try(var image=Screenshot.takeScreenshot(target)){
                image.writeToFile(mc.gameDirectory.toPath().resolve(name));
                int voltageTop=154*4-(int)Math.round(CabElectricalDisplay.voltageFraction(volts)*106*4);
                int currentTop=154*4-(int)Math.round(CabElectricalDisplay.currentFraction(amps)*106*4);
                for(int x:new int[]{115,125,140})for(int y=198;y<610;y++){
                    boolean blue=blue(image.getPixelRGBA(x,y));
                    check(blue==(y>=voltageTop),"Voltage fill hole/scale mismatch "+name+" at "+x+","+y+" top="+voltageTop+" color="+Integer.toHexString(image.getPixelRGBA(x,y)));
                }
                for(int x:new int[]{886,900,915})for(int y=198;y<610;y++){
                    boolean blue=blue(image.getPixelRGBA(x,y));
                    check(blue==(y>=currentTop),"Current fill hole/scale mismatch "+name+" at "+x+","+y);
                }
                for(int x:new int[]{326,339,454,467,582,595,710,723})for(int y=424;y<480;y++)
                    check(blue(image.getPixelRGBA(x,y)),"Traction fill has grey stripe "+x+","+y);
                var path=mc.gameDirectory.toPath().resolve(name);image.writeToFile(path);
                report.append(name).append(": contiguous fills at ").append(volts).append(" V / ").append(amps).append(" A; voltage top=").append(voltageTop).append(", current top=").append(currentTop).append('\n');
            }
        }finally {
            target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.setProjectionMatrix(oldProjection,sorting);RenderSystem.getModelViewStack().set(oldModelView);RenderSystem.applyModelViewMatrix();
            mc.gameRenderer.overlayTexture().teardownOverlayColor();mc.gameRenderer.lightTexture().turnOffLightLayer();
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post e){
        if(done||ticks<60||desk()==null)return;
        var mc=Minecraft.getInstance();
        try {
            if(stage==0)gaugeImage("gauge-01-half.png",25000,300);
            if(stage==1)gaugeImage("gauge-02-screenshot-values.png",21250,177.4);
            if(stage==2)gaugeImage("gauge-03-maximum.png",32500,600);
            if(stage==3)gaugeImage("gauge-04-off.png",0,0);
            if(stage==4){
                seed(desk(),25000,300);
                Screenshot.grab(mc.gameDirectory,"gauge-world.png",mc.getMainRenderTarget(),m->{});
            }
            if(++stage>4){Files.writeString(mc.gameDirectory.toPath().resolve("gauge-check.txt"),"PASS actual client offscreen production rendering: no holes in blue electrical/traction bars; gauge endpoints and screenshots\n"+report);done=true;mc.getConnection().sendCommand("stop");mc.stop();}
        }catch(Throwable t){t.printStackTrace();done=true;try{Files.writeString(mc.gameDirectory.toPath().resolve("gauge-check.txt"),"FAIL: "+t+"\n"+report);}catch(Exception ignored){}mc.getConnection().sendCommand("stop");mc.stop();}
    }
}
