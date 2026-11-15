package emucabelectricaltest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Exercises real sound-engine channels and Sable delegates, without claiming human listening. */
public final class MovingAudioChecks {
    static Object field(Object object,String name)throws Exception {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try {
            var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);
        }catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    static void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    static Map<net.minecraft.client.resources.sounds.SoundInstance,?> channels()throws Exception {
        return (Map<net.minecraft.client.resources.sounds.SoundInstance,?>)field(field(Minecraft.getInstance().getSoundManager(),"soundEngine"),"instanceToChannel");
    }
    public static void run(BlockEntity collector)throws Exception {
        var mc=Minecraft.getInstance();var level=mc.level;var camera=mc.gameRenderer.getMainCamera();var old=camera.getPosition();
        var set=net.minecraft.client.Camera.class.getDeclaredMethod("setPosition",double.class,double.class,double.class);set.setAccessible(true);
        var sub=dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(level,collector.getBlockPos());
        check(sub instanceof dev.ryanhcode.sable.companion.ClientSubLevelAccess,"No client Sable body for moving sound check");
        var client=(dev.ryanhcode.sable.companion.ClientSubLevelAccess)sub;
        var local=collector.getBlockPos().getCenter();
        var render=(dev.ryanhcode.sable.companion.math.Pose3d)client.renderPose();
        var logical=(dev.ryanhcode.sable.companion.math.Pose3d)sub.logicalPose();
        var savedRender=new org.joml.Vector3d(render.position());var savedLogical=new org.joml.Vector3d(logical.position());
        try {
            // Both packet edges reference CEE's long arc, with different bounded durations.
            Class<?> payload=Class.forName("com.yasui_i.pwsablefix.network.PantographContactSoundPayload");
            var constructor=payload.getDeclaredConstructor(net.minecraft.core.BlockPos.class,float.class,float.class,float.class,boolean.class);
            Class<?> sounds=Class.forName("com.yasui_i.pwsablefix.client.PantographContactSounds");
            for(boolean departure:List.of(false,true)) {
                Object packet=constructor.newInstance(collector.getBlockPos(),.5f,2f,.5f,departure);
                var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
                var codec=(net.minecraft.network.codec.StreamCodec)payload.getField("CODEC").get(null);
                try {codec.encode(buffer,packet);check(packet.equals(codec.decode(buffer)),"Contact/departure packet codec lost edge");}finally{buffer.release();}
                var before=new HashSet<>(channels().keySet());
                var world=render.transformPosition(local);set.invoke(camera,world.x+1,world.y+1.5,world.z);
                sounds.getMethod("play",payload).invoke(null,packet);
                var arc=channels().keySet().stream().filter(s->!before.contains(s)&&s instanceof dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate d
                    &&d.instance.getClass().getName().contains("PantographContactSounds")).findFirst().orElseThrow(()->new AssertionError("Arc has no real Sable sound channel"));
                check(arc.getLocation().toString().equals("electroenergetics:arc"),"Wrong selected arc event: "+arc.getLocation());
                check(arc.getSound().getPath().toString().equals("electroenergetics:sounds/arc.ogg"),"Arc sample did not resolve to CEE long arc: "+arc.getSound().getPath());
                var source=(TickableSoundInstance)((dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate)arc).instance;
                int duration=departure?24:7,fade=departure?3:2;
                check(((Integer)field(source,"duration"))==duration,"Wrong selected arc cut length");
                for(int i=1;i<duration;i++) {
                    logical.position().set(savedLogical).add(350/3.6/20*i,0,0);
                    render.position().set(savedRender).add(350/3.6/20*i,0,0);
                    world=render.transformPosition(local);set.invoke(camera,world.x+1,world.y+1.5,world.z);
                    ((TickableSoundInstance)arc).tick();
                    double expected=.9*Math.min(1,(duration-i)/(double)fade);
                    check(Math.abs(arc.getVolume()-expected)<.001,"Moving arc lost gain or fade: "+arc.getVolume()+" expected "+expected);
                    check(!source.isStopped(),"Arc stopped before selected duration");
                    var localArc=new Vec3(source.getX(),source.getY(),source.getZ());
                    check(Math.abs(arc.getX()-logical.transformPosition(localArc).x)<1e-5,"Arc was transformed twice");
                }
                ((TickableSoundInstance)arc).tick();check(source.isStopped(),"Arc exceeded selected cut length");
                mc.getSoundManager().stop(arc);
            }
            // A real optional Simurail bogey on the same test plot exercises the imported recording.
            var bogeyPos=collector.getBlockPos().east(3);
            var bogeyBlock=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("simurail:physics_bogey"));
            level.setBlock(bogeyPos,bogeyBlock.defaultBlockState(),3);
            var bogey=level.getBlockEntity(bogeyPos);
            check(bogey instanceof com.yasui_i.emucab.telemetry.BogeyIdentity,"Client bogey audio identity missing");
            var id=((com.yasui_i.emucab.telemetry.BogeyIdentity)bogey).emucab$identity();
            var point=render.transformPosition(bogeyPos.getCenter());set.invoke(camera,point.x+1,point.y,point.z);
            com.yasui_i.emucab.client.BogeyBrakeReleaseSounds.play(new com.yasui_i.emucab.network.BogeyBrakeSoundPayload(bogeyPos,id,.65f,24));
            var release=channels().keySet().stream().filter(s->s instanceof dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate d
                &&d.instance.getLocation().toString().equals("emucab:bogey_brake_release")).findFirst().orElseThrow(()->new AssertionError("Release has no real Sable sound channel"));
            check(release.getSound().getPath().getNamespace().equals("emucab")&&release.getSound().getPath().getPath().endsWith("bogey_brake_release.ogg"),"Release recording missing: "+release.getSound().getPath());
            for(int i=0;i<10;i++) {
                render.position().set(savedRender).add(350/3.6/20*i,0,0);logical.position().set(savedLogical).add(350/3.6/20*i,0,0);
                point=render.transformPosition(bogeyPos.getCenter());set.invoke(camera,point.x+1,point.y,point.z);
                ((TickableSoundInstance)release).tick();check(release.getVolume()>.64,"Release lost volume while moving");
                check(Math.abs(release.getX()-logical.transformPosition(bogeyPos.getCenter()).x)<1e-5,"Release source transformed twice");
            }
            mc.getSoundManager().stop(release);level.removeBlock(bogeyPos,false);
        }finally {set.invoke(camera,old.x,old.y,old.z);render.position().set(savedRender);logical.position().set(savedLogical);}
    }
    private MovingAudioChecks(){}
}
