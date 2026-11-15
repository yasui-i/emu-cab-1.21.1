package emucabdisplaytest;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.nio.file.*;
@EventBusSubscriber(modid="emucabdisplaytest",value=Dist.CLIENT)
public final class DisplayClientCheck {
 static final StringBuilder evidence=new StringBuilder();
 static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 static java.util.Map<net.minecraft.client.resources.sounds.SoundInstance,?> channels()throws Exception{return (java.util.Map<net.minecraft.client.resources.sounds.SoundInstance,?>)field(field(Minecraft.getInstance().getSoundManager(),"soundEngine"),"instanceToChannel");}
 static void soundChecks(CabDeskBlockEntity desk)throws Exception{
    var mc=Minecraft.getInstance();var camera=mc.gameRenderer.getMainCamera();var old=camera.getPosition();
    var position=net.minecraft.client.Camera.class.getDeclaredMethod("setPosition",double.class,double.class,double.class);position.setAccessible(true);
    var sub=dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(mc.level,desk.getBlockPos());
    var local=desk.getBlockPos().getCenter();
    var world=sub instanceof dev.ryanhcode.sable.companion.ClientSubLevelAccess client?client.renderPose().transformPosition(local):local;
    var ctor=Class.forName("com.yasui_i.emucab.client.CabAtpSounds$MovingAnnouncement").getDeclaredConstructors()[0];ctor.setAccessible(true);
    var sound=(net.minecraft.client.resources.sounds.TickableSoundInstance)ctor.newInstance(mc.level,desk,com.yasui_i.emucab.control.AtpAnnouncement.NEUTRAL_SECTION_AHEAD,1f);
    sound.resolve(mc.getSoundManager());
    for(double d:new double[]{1,3,4,6,7.9,8,8.1,1}){
        position.invoke(camera,world.x+d,world.y,world.z);sound.tick();
        double expected=com.yasui_i.emucab.control.CabAtpAcoustics.gain(d*d);
        check(Math.abs(sound.getVolume()-expected)<.0001,"Audible radius/volume "+d+" -> "+sound.getVolume());
        check(!sound.isStopped(),"Radius crossing permanently stopped recording");
    }
    if(sub instanceof dev.ryanhcode.sable.companion.ClientSubLevelAccess client){
        var render=(dev.ryanhcode.sable.companion.math.Pose3d)client.renderPose();
        var saved=new org.joml.Vector3d(render.position());
        render.position().add(30,0,0);var moved=render.transformPosition(local);
        position.invoke(camera,moved.x+1,moved.y,moved.z);sound.tick();
        // The raw sound instance stays in the Sable plot's local coordinates;
        // Sable's sound delegate applies the physical pose once when it is
        // registered with SoundManager. Its manual gain still follows the
        // rendered world pose above.
        check(Math.abs(sound.getX()-local.x)<1E-7&&Math.abs(sound.getVolume()-1)<.001,"Moving sound local origin/gain mismatch");
        render.position().set(saved);
        // Exercise SoundManager's real Sable wrapping, not just the raw instance.
        com.yasui_i.emucab.client.CabAtpSounds.play(new com.yasui_i.emucab.network.CabAtpSoundPayload(desk.getBlockPos(),"neutral_section_ahead",1));
        var delegated=channels().keySet().stream().filter(s->s instanceof dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate d
            && d.instance.getLocation().toString().equals("emucab:atp.neutral_section_ahead")).findFirst().orElseThrow(()->new AssertionError("Sable did not wrap local sound"));
        var logicalWorld=sub.logicalPose().transformPosition(local);
        check(Math.abs(delegated.getX()-logicalWorld.x)<1E-6&&Math.abs(delegated.getY()-logicalWorld.y)<1E-6&&Math.abs(delegated.getZ()-logicalWorld.z)<1E-6,"Sable sound was transformed twice");
        var logical=(dev.ryanhcode.sable.companion.math.Pose3d)sub.logicalPose();var oldLogical=new org.joml.Vector3d(logical.position());
        try {
            // Advance an actual sound delegate through 10 synthetic 350 km/h ticks.
            for(int tick=1;tick<=10;tick++) {
                logical.position().set(oldLogical).add(350/3.6/20*tick,0,0);
                render.position().set(saved).add(350/3.6/20*tick,0,0);
                var movingWorld=render.transformPosition(local);position.invoke(camera,movingWorld.x+1,movingWorld.y,movingWorld.z);
                ((net.minecraft.client.resources.sounds.TickableSoundInstance)delegated).tick();
                check(delegated.getVolume()>.99,"Moving driver lost ATP volume at tick "+tick);
                check(Math.abs(delegated.getX()-logical.transformPosition(local).x)<1E-6,"Moving delegate source position mismatch");
            }
        } finally {logical.position().set(oldLogical);render.position().set(saved);mc.getSoundManager().stop(delegated);}
        evidence.append("Sable real sound delegate transformed once, render gain across 30-block pose gap, 10 synthetic 350 km/h pose ticks; ");
    }
    position.invoke(camera,old.x,old.y,old.z);
    evidence.append("production sound gain at 1/3/4/6/7.9/8/8.1 blocks, mute and recovery; ");
 }
 static int ticks;static String capture;static boolean done;static CabDeskBlockEntity gripped;
 static final java.util.Set<java.util.UUID> oldBodies=new java.util.HashSet<>();
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
 static void use(net.minecraft.core.BlockPos pos){var mc=Minecraft.getInstance();mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false));}
 @SubscribeEvent public static void tick(ClientTickEvent.Post e){var mc=Minecraft.getInstance();
  if(done||mc.player==null||mc.level==null||!(mc.level.getBlockEntity(DisplayHarness.CENTER) instanceof CabDeskBlockEntity desk))return;
  try{ticks++;
   if(ticks==60)use(DisplayHarness.CENTER);
   if(ticks==80)use(DisplayHarness.CENTER.north(2).east());
   if(ticks==110){check(mc.screen!=null&&mc.screen.getClass().getSimpleName().equals("CabTelemetryScreen"),"Tool did not open car chooser");capture="vehicle-01-binding.png";}
   if(ticks==120)((Button)mc.screen.children().get(1)).onPress();
   if(ticks==145){check(desk.telemetry().bindings().size()==1&&desk.telemetry().bindings().getFirst().car()==2,"Binding not saved/server echoed");mc.setScreen(new com.yasui_i.emucab.client.CabTelemetryScreen(desk));capture="vehicle-02-diagnostics.png";}
   if(ticks==165){mc.setScreen(null);mc.options.hideGui=true;mc.getConnection().sendCommand("vehicleui 1");}
   if(ticks==195){check(desk.controls().notch==-2,"Light brake state sync");capture="vehicle-03-light.png";}
   if(ticks==210)mc.getConnection().sendCommand("vehicleui 2");
   if(ticks==240){check(desk.controls().notch==-6,"Strong brake state sync");capture="vehicle-04-blend.png";}
   if(ticks==255)mc.getConnection().sendCommand("vehicleui 3");
   if(ticks==285){check(desk.isEmergency(),"EB sync");capture="vehicle-05-eb.png";}
   if(ticks==295)mc.getConnection().sendCommand("rangewrench");
   if(ticks==305){
      mc.hitResult=new BlockHitResult(DisplayHarness.CENTER.getCenter(),Direction.NORTH,DisplayHarness.CENTER,false);
      var use=new net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered(1,mc.options.keyUse,InteractionHand.MAIN_HAND);
      com.yasui_i.emucab.client.CabInteraction.use(use);
      check(use.isCanceled()&&mc.screen!=null&&mc.screen.getClass().getSimpleName().equals("CabDisplaySettingsScreen"),"Wrench did not open range settings");
      var range=(net.minecraft.client.gui.components.EditBox)field(mc.screen,"forceScale");var save=(Button)field(mc.screen,"save");
      check(range.getValue().equals("5"),"Range not default5kN");range.setValue("0");check(!save.active,"Zero range allowed");
      range.setValue("2.5");check(save.active,"Decimal range rejected");capture="force-01-settings.png";
   }
   if(ticks==308)((Button)field(mc.screen,"save")).onPress();
   if(ticks==345){
      check(desk.forceScaleKn()==2.5,"Custom force range did not reach server/client");
      evidence.append("wrench range UI defaults5, rejects0, accepts2.5, saves and syncs; ");
   }
   if(ticks==310){mc.options.hideGui=false;mc.getConnection().sendCommand("audiocue");}
   if(ticks==330){
      var sounds=channels().keySet().stream().filter(s->s.getLocation().toString().equals("emucab:atp.neutral_section_ahead")).toList();
      check(!sounds.isEmpty(),"Server ATP packet did not start an actual sound channel");
      check(sounds.getFirst().getVolume()>0,"Driver position inaudible");
      evidence.append("server ATP request produced an active real sound channel at driver position; ");soundChecks(desk);
      for(var body:dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(mc.level).getAllSubLevels())oldBodies.add(body.getUniqueId());
      mc.getConnection().sendCommand("audioassembly");
   }
   if(ticks==365)mc.getConnection().sendCommand("griptest");
   if(ticks==390){
       var all=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(mc.level).getAllSubLevels();
       CabDeskBlockEntity moved=null;
       for(var sub:all){var pos=sub.getPlot().getCenterBlock();if(!oldBodies.contains(sub.getUniqueId())&&mc.level.getBlockEntity(pos) instanceof CabDeskBlockEntity cab){moved=cab;break;}}
       check(moved!=null,"Assembled cab not received clientside");soundChecks(moved);
       // A render ray must hit the handle even if vanilla has no block hit at all.
       var camera=mc.gameRenderer.getMainCamera();var saved=camera.getPosition();var oldHit=mc.hitResult;
       var facing=moved.getBlockState().getValue(com.yasui_i.emucab.block.CabDeskBlock.FACING);
       var handle=java.util.Arrays.stream(com.yasui_i.emucab.control.CabGeometry.CONTROLS).filter(c->c.action()==com.yasui_i.emucab.control.CabAction.HANDLE).findFirst().orElseThrow();
       var sub=(dev.ryanhcode.sable.companion.ClientSubLevelAccess)dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(mc.level,moved.getBlockPos());
       var local=com.yasui_i.emucab.control.CabHitTesting.fromModel(moved.getBlockPos(),facing,com.yasui_i.emucab.control.CabHitTesting.bounds(handle,0).getCenter());
       var target=sub.renderPose().transformPosition(local);var eye=target.add(0,.5,-.5);
       var set=net.minecraft.client.Camera.class.getDeclaredMethod("setPosition",double.class,double.class,double.class);set.setAccessible(true);set.invoke(camera,eye.x,eye.y,eye.z);
       var rotate=net.minecraft.client.Camera.class.getDeclaredMethod("setRotation",float.class,float.class);rotate.setAccessible(true);rotate.invoke(camera,0f,45f);
       var visible=com.yasui_i.emucab.client.CabInteraction.class.getDeclaredMethod("visible",CabDeskBlockEntity.class);visible.setAccessible(true);visible.invoke(null,moved);
       mc.hitResult=net.minecraft.world.phys.BlockHitResult.miss(target,Direction.UP,net.minecraft.core.BlockPos.containing(target));
       var picked=com.yasui_i.emucab.client.CabInteraction.target();
       check(picked!=null&&picked.control().action()==com.yasui_i.emucab.control.CabAction.HANDLE,"Render handle missed with vanilla miss");
       // Draw the HUD target, then let the body/camera ray change before the
       // input callback. It must still open the same handle, accept a drag and
       // commit the final notch through the real client/server payloads.
       com.yasui_i.emucab.client.CabInteraction.hud(new net.neoforged.neoforge.client.event.RenderGuiEvent.Post(
           new net.minecraft.client.gui.GuiGraphics(mc,mc.renderBuffers().bufferSource()),mc.getTimer()));
       set.invoke(camera,eye.x+30,eye.y,eye.z);
       check(com.yasui_i.emucab.client.CabInteraction.target()==null,"Test camera still points at a control");
       var input=new net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered(1,mc.options.keyUse,InteractionHand.MAIN_HAND);
       com.yasui_i.emucab.client.CabInteraction.use(input);
       check(input.isCanceled()&&mc.screen instanceof com.yasui_i.emucab.client.CabHandleScreen,"Recent tooltip ray did not open handle");
       var grip=(com.yasui_i.emucab.client.CabHandleScreen)mc.screen;
       double anchor=(double)field(grip,"anchorY"),step=Math.min(10,Math.max(2,(mc.screen.height/2.0-12)/15));
       grip.mouseMoved(0,anchor+4*step);check(grip.selectedNotch()==-4,"Drag did not select B4");
       grip.mouseReleased(0,anchor+4*step,org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
       check(mc.screen==null,"Released handle did not close");gripped=moved;
       set.invoke(camera,saved.x,saved.y,saved.z);mc.hitResult=oldHit;
       evidence.append("render-space handle ray succeeds without vanilla cabinet hit; ");
   }
   if(ticks==420){
       check(gripped!=null&&gripped.leverPosition()==-4,"Handle release did not reach server and sync B4");
       evidence.append("recent HUD ray opens handle after 30-block camera correction; drag B4 and release committed server-side and echoed; ");
       Files.writeString(mc.gameDirectory.toPath().resolve("vehicle-ui-check.txt"),"PASS: "+evidence+"real binder item use on cab then real Simurail bogey opens chooser; car02 selection saved and server echoed; diagnostic screen; light/strong/emergency production cab state sync and screenshots. Force values in screenshots are synthetic. No driven rail test.");done=true;mc.getConnection().sendCommand("stop");mc.stop();}
  }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("vehicle-ui-check.txt"),"FAIL: "+t);}catch(Exception ignored){}done=true;mc.getConnection().sendCommand("stop");mc.stop();}
 }
 @SubscribeEvent public static void render(RenderFrameEvent.Post e){if(capture==null)return;var mc=Minecraft.getInstance();var name=capture;capture=null;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});}
}
