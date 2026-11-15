package emucabmotortest;

import com.yasui_i.emucab.client.BogeyMotorSounds;
import com.yasui_i.emucab.network.BogeyMotorSoundPayload;
import com.yasui_i.emucab.telemetry.BogeyIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Exercises production PCM routing, moving source, limits and stale-packet retirement. */
final class BogeyAudioClientCheck {
    static void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    static Object field(Object owner,String name)throws Exception{return MotorClientCheck.field(owner,name);}
    static void run(BlockEntity motor)throws Exception {
        var mc=Minecraft.getInstance();var level=mc.level;
        var camera=mc.gameRenderer.getMainCamera();var oldCamera=camera.getPosition();
        var set=net.minecraft.client.Camera.class.getDeclaredMethod("setPosition",double.class,double.class,double.class);set.setAccessible(true);
        var sub=dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(level,motor.getBlockPos());
        check(sub instanceof dev.ryanhcode.sable.companion.ClientSubLevelAccess,"PCM test requires a real Sable body");
        var render=((dev.ryanhcode.sable.companion.ClientSubLevelAccess)sub).renderPose();
        var logical=sub.logicalPose();
        var rp=new org.joml.Vector3d(render.position());var lp=new org.joml.Vector3d(logical.position());
        var pos=motor.getBlockPos().east(3);var oldState=level.getBlockState(pos);
        try {
            var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("simurail:physics_bogey"));
            var layers=com.yasui_i.emucab.client.BogeyRecordedLayers.load().getNow(null);
            check(layers!=null&&layers.mechanical().length>48000&&layers.wind().length>48000,"Create/CEE runtime textures not decoded");
            check(rms(layers.mechanical())>.07&&rms(layers.mechanical())<.13&&rms(layers.wind())>.07&&rms(layers.wind())<.13,"Recorded texture levels differ from preview");
            var noCee=com.yasui_i.emucab.client.BogeyRecordedLayers.load(id2->id2.getNamespace().equals("electroenergetics")?Optional.empty():mc.getResourceManager().getResource(id2)).join();
            check(noCee.mechanical().length>48000&&noCee.wind().length==0,"Missing optional CEE texture did not use fallback");
            MotorClientCheck.evidence.append(RecordedLayerClientCheck.run()).append("; ");
            ExteriorRuntimePreview.render(layers);
            level.setBlock(pos,block.defaultBlockState(),3);var bogey=level.getBlockEntity(pos);
            check(bogey instanceof BogeyIdentity,"Bogey identity missing on client");var id=((BogeyIdentity)bogey).emucab$identity();
            var world=render.transformPosition(pos.getCenter());set.invoke(camera,world.x+1,world.y,world.z);
            BogeyMotorSounds.accept(new BogeyMotorSoundPayload(pos,id,100,600,104,1,0,true));
            for(int i=0;i<4;i++)BogeyMotorSounds.tick(new ClientTickEvent.Post());
            var source=MotorClientCheck.channels().keySet().stream().filter(s->s instanceof dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate d&&d.instance.getLocation().toString().equals("emucab:bogey_motor")).findFirst().orElseThrow(()->new AssertionError("No mixed PCM Sable sound channel"));
            check(BogeyMotorSounds.replacesRumble(bogey),"Original rumble not gated for mixed bogey");
            var controllerType=Class.forName("com.crystaelix.simurail.content.bogey.PhysicsBogeySounds");
            var controllerCtor=controllerType.getDeclaredConstructors()[0];controllerCtor.setAccessible(true);var controller=controllerCtor.newInstance(bogey);
            check(controller instanceof com.yasui_i.emucab.mixin.SimurailBogeyRumbleMixin,"Original rumble owner accessor not applied");
            var rumbleType=Class.forName("com.crystaelix.simurail.content.bogey.PhysicsBogeySounds$PhysicsBogeySoundInstance");
            var rumbleCtor=rumbleType.getDeclaredConstructors()[0];rumbleCtor.setAccessible(true);
            var rumble=rumbleCtor.newInstance(controller,(java.util.function.Supplier<net.minecraft.sounds.SoundEvent>)()->com.yasui_i.emucab.registry.ModSounds.BOGEY_MOTOR.get());
            var setVolume=rumbleType.getDeclaredMethod("setVolume",float.class);setVolume.setAccessible(true);setVolume.invoke(rumble,.7f);
            check((float)field(rumble,"volume")==0,"Original rolling recording was not muted by real mixin");
            // A separate stream source avoids concurrent use of the real channel's DSP.
            var soundClass=Class.forName("com.yasui_i.emucab.client.BogeyMotorSounds$MotorSound");
            var ctor=soundClass.getDeclaredConstructors()[0];ctor.setAccessible(true);
            var probe=ctor.newInstance(level,bogey,id,layers);
            var probeSound=(net.minecraft.client.resources.sounds.SoundInstance)probe;
            probeSound.resolve(mc.getSoundManager());
            var update=soundClass.getDeclaredMethod("update",BogeyMotorSoundPayload.class,float.class);update.setAccessible(true);
            update.invoke(probe,new BogeyMotorSoundPayload(pos,id,100,600,104,1,0,true),1f);
            var fade=soundClass.getDeclaredMethod("advanceFade",boolean.class);fade.setAccessible(true);for(int i=0;i<4;i++)fade.invoke(probe,true);
            var probeTick=(TickableSoundInstance)probe;
            // Exercise the production moving source at all supported useful
            // radii, rather than only testing the shared curve in isolation.
            double oldRadius=com.yasui_i.emucab.config.BogeyAudioConfig.RADIUS.get();
            try {
                for(double radius:new double[]{64,128,256,512}) {
                    com.yasui_i.emucab.config.BogeyAudioConfig.RADIUS.set(radius);
                    for(double distance:new double[]{1,radius*.25,radius*.5,radius*.75,radius-1,radius,radius+1}) {
                        set.invoke(camera,world.x+distance,world.y,world.z);probeTick.tick();
                        double actual=probeSound.getVolume();
                        double expected=com.yasui_i.emucab.config.BogeyAudioConfig.VOLUME.get()
                            *com.yasui_i.emucab.audio.ExteriorSoundRange.gain(distance,radius,.8);
                        check(Math.abs(actual-expected)<1e-5,"Motor distance curve/clamp "+distance+"/"+radius+": "+actual);
                    }
                }
            }finally{com.yasui_i.emucab.config.BogeyAudioConfig.RADIUS.set(oldRadius);}
            set.invoke(camera,world.x+1,world.y,world.z);probeTick.tick();
            check(probeSound.getVolume()>.6,"Motor source did not recover after range crossing");
            checkReleaseRange(bogey,id,set,camera);
            try(var stream=((net.minecraft.client.resources.sounds.SoundInstance)probe).getStream(null,source.getSound(),true).join()) {
                check(stream.getFormat().getSampleRate()==48000&&stream.getFormat().getChannels()==1,"Wrong PCM format");
                long energy=0;int bytes=0;
                for(int j=0;j<30;j++){
                    var buffer=stream.read(96000);check(buffer.remaining()==9600,"Live stream must queue a bounded100ms block");
                    bytes+=buffer.remaining();while(buffer.remaining()>=2){short sample=buffer.getShort();energy+=(long)sample*sample;}
                }
                check(energy>1000000,"DSP PCM is silent");
            }
            var delegate=(dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate)source;
            for(int i=0;i<10;i++) {
                ((dev.ryanhcode.sable.companion.math.Pose3d)render).position().set(rp).add(350/3.6/20*i,0,0);
                ((dev.ryanhcode.sable.companion.math.Pose3d)logical).position().set(lp).add(350/3.6/20*i,0,0);
                world=render.transformPosition(pos.getCenter());set.invoke(camera,world.x+1,world.y,world.z);
                ((TickableSoundInstance)source).tick();
                check(source.getVolume()>.6,"Mixed PCM source lost gain on moving body");
                check(Math.abs(source.getX()-logical.transformPosition(pos.getCenter()).x)<1e-5,"Mixed PCM sound transformed twice");
            }
            // A registered bogey must not bring the stock impact recording
            // back merely because the PCM channel budget selected other bogeys.
            var other=pos.east();var otherState=level.getBlockState(other);
            try {
                level.setBlock(other,block.defaultBlockState(),3);var unselected=level.getBlockEntity(other);
                var otherId=((BogeyIdentity)unselected).emucab$identity();
                BogeyMotorSounds.accept(new BogeyMotorSoundPayload(other,otherId,101,600,104,1,0,true));
                check(BogeyMotorSounds.originalRumbleGain(unselected)==0,"Unselected registered bogey leaked stock impacts");
            }finally{level.setBlock(other,otherState,3);}
            // Old sequence cannot override the latest wheel speed.
            BogeyMotorSounds.accept(new BogeyMotorSoundPayload(pos,id,99,1,0,0,false));
            var snapshotsField=BogeyMotorSounds.class.getDeclaredField("SNAPSHOTS");snapshotsField.setAccessible(true);
            var snapshots=(Map<?,?>)snapshotsField.get(null);var entry=snapshots.get(id);
            var packet=entry.getClass().getDeclaredMethod("packet");packet.setAccessible(true);
            check(((BogeyMotorSoundPayload)packet.invoke(entry)).sequence()==100,"Old motor packet replaced current state");
            // Reload discards PCM cache, closes old channels and keeps the latest snapshots.
            long oldGeneration=com.yasui_i.emucab.client.BogeyRecordedLayers.generation();
            com.yasui_i.emucab.client.BogeyRecordedLayers.reset();
            check(com.yasui_i.emucab.client.BogeyRecordedLayers.generation()>oldGeneration,"Texture cache did not invalidate on reload");
            BogeyMotorSounds.tick(new ClientTickEvent.Post());
            check(((TickableSoundInstance)delegate.instance).isStopped(),"Reload kept the old PCM channel");
            for(int i=0;i<105;i++)BogeyMotorSounds.tick(new ClientTickEvent.Post());
            check(!BogeyMotorSounds.replacesRumble(bogey),"Stale source kept original rumble disabled");
            setVolume.invoke(rumble,.7f);check((float)field(rumble,"volume")==.7f,"Original rolling recording did not resume");
        }finally {
            level.setBlock(pos,oldState,3);
            for(int i=0;i<105;i++)BogeyMotorSounds.tick(new ClientTickEvent.Post());
            set.invoke(camera,oldCamera.x,oldCamera.y,oldCamera.z);
            ((dev.ryanhcode.sable.companion.math.Pose3d)render).position().set(rp);
            ((dev.ryanhcode.sable.companion.math.Pose3d)logical).position().set(lp);
        }
        MotorClientCheck.evidence.append("exterior live mono PCM through real Sable delegate; Create/CEE textures decoded at preview RMS; 100ms PCM blocks, nonzero mixed output, registration-wide rumble gate, motor64/128/256/512 + release256 boundaries and recovery, ten350km/h poses, reload generation and5second stale cleanup; ");
    }
    static void checkReleaseRange(BlockEntity bogey,UUID id,java.lang.reflect.Method set,net.minecraft.client.Camera camera)throws Exception {
        var mc=Minecraft.getInstance();var level=mc.level;var oldCamera=camera.getPosition();
        check(com.yasui_i.emucab.config.CabConfig.BOGEY_RELEASE_RADIUS.get()==64,"Brake-release radius default/migration did not become64");
        var sub=(dev.ryanhcode.sable.companion.ClientSubLevelAccess)dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(level,bogey.getBlockPos());
        var render=(dev.ryanhcode.sable.companion.math.Pose3d)sub.renderPose();
        var logical=(dev.ryanhcode.sable.companion.math.Pose3d)sub.logicalPose();
        var rp=new org.joml.Vector3d(render.position());var lp=new org.joml.Vector3d(logical.position());
        var local=bogey.getBlockPos().getCenter();var world=render.transformPosition(local);
        var type=Class.forName("com.yasui_i.emucab.client.BogeyBrakeReleaseSounds$MovingRelease");
        var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
        var probe=(net.minecraft.client.resources.sounds.SoundInstance)ctor.newInstance(level,bogey,id,.65f,256f);
        net.minecraft.client.resources.sounds.SoundInstance played=null;
        try {
            // Route the isolated recording through Sable's real sound wrapper,
            // rather than merely checking the static attenuation formula.
            set.invoke(camera,world.x+1,world.y,world.z);mc.getSoundManager().play(probe);
            played=MotorClientCheck.channels().keySet().stream().filter(s->s instanceof dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate d&&d.instance==probe).findFirst().orElseThrow(()->new AssertionError("Release range probe has no Sable channel"));
            for(double distance:new double[]{1,64,128,192,255,256,257}) {
                set.invoke(camera,world.x+distance,world.y,world.z);((TickableSoundInstance)played).tick();
                double actual=played.getVolume();
                double expected=.65*com.yasui_i.emucab.audio.ExteriorSoundRange.gain(distance,256,.65);
                check(Math.abs(actual-expected)<1e-5,"Release256 distance curve "+distance+": "+actual);
            }
            set.invoke(camera,world.x+1,world.y,world.z);((TickableSoundInstance)played).tick();
            check(played.getVolume()>.64,"Release failed to recover after radius crossing");
            for(int i=0;i<10;i++) {
                render.position().set(rp).add(350/3.6/20*i,0,0);logical.position().set(lp).add(350/3.6/20*i,0,0);
                world=render.transformPosition(local);set.invoke(camera,world.x+48,world.y,world.z);
                ((TickableSoundInstance)played).tick();
                check(played.getVolume()>.64,"Moving release lost256block distance gain");
                check(Math.abs(played.getX()-logical.transformPosition(local).x)<1e-5,"Release sound transformed twice");
            }
        }finally {
            if(played!=null)mc.getSoundManager().stop(played);else mc.getSoundManager().stop(probe);
            set.invoke(camera,oldCamera.x,oldCamera.y,oldCamera.z);render.position().set(rp);logical.position().set(lp);
        }
    }
    static double rms(float[] data){double sum=0;for(float v:data)sum+=(double)v*v;return Math.sqrt(sum/data.length);}
    private BogeyAudioClientCheck(){}
}
