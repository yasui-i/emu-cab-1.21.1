package emucabmotortest;

import com.yasui_i.emucab.client.BogeyMotorSounds;
import com.yasui_i.emucab.client.BogeyRecordedLayers;
import com.yasui_i.emucab.config.BogeyAudioConfig;
import com.yasui_i.emucab.network.BogeyMotorSoundPayload;
import com.yasui_i.emucab.telemetry.BogeyIdentity;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;
import java.util.*;

/** Real client routing under synthetic350km/h poses; no timing/ear-test claim. */
final class ContinuousBogeyPassbyCheck {
    static void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    static Object field(Object owner,String name)throws Exception{return MotorClientCheck.field(owner,name);}
    static Map<UUID,?> exterior()throws Exception {
        var f=Class.forName("com.yasui_i.emucab.client.BogeyExteriorSounds").getDeclaredField("PLAYING");
        f.setAccessible(true);return (Map<UUID,?>)f.get(null);
    }
    static Map<UUID,?> motors()throws Exception {
        var f=BogeyMotorSounds.class.getDeclaredField("PLAYING");f.setAccessible(true);return (Map<UUID,?>)f.get(null);
    }
    static SoundInstance sound(Object pair,String layer)throws Exception{return (SoundInstance)field(pair,layer);}
    static void run(BlockEntity motor)throws Exception {
        var mc=Minecraft.getInstance();var level=mc.level;var camera=mc.gameRenderer.getMainCamera();var oldCamera=camera.getPosition();
        var set=net.minecraft.client.Camera.class.getDeclaredMethod("setPosition",double.class,double.class,double.class);set.setAccessible(true);
        var sub=SableCompanion.INSTANCE.getContaining(level,motor.getBlockPos());
        check(sub instanceof ClientSubLevelAccess,"Pass-by check needs a real Sable body");
        var render=(Pose3d)((ClientSubLevelAccess)sub).renderPose();var logical=(Pose3d)sub.logicalPose();
        var rp=new org.joml.Vector3d(render.position());var lp=new org.joml.Vector3d(logical.position());
        double oldRadius=BogeyAudioConfig.RADIUS.get();
        var restore=new LinkedHashMap<BlockPos,BlockState>();var bogeys=new ArrayList<BlockEntity>();var ids=new ArrayList<UUID>();
        var persistent=new HashMap<UUID,Object>();var nearestSets=new HashSet<Set<UUID>>();
        var csv=new StringBuilder("tick,translation_m,aggregate_gain_power,ambient_bogeys,selected_motor_bogeys,selected_indices\n");
        try {
            BogeyAudioConfig.RADIUS.set(256.0);
            var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("simurail:physics_bogey"));
            int chunkX=motor.getBlockPos().getX()&~15,chunkZ=motor.getBlockPos().getZ()&~15;
            for(int i=0;i<6;i++) {
                // Stay in the actual loaded storage chunk rather than relying
                // on setBlock silently constructing unloaded plot chunks.
                var pos=new BlockPos(chunkX+2+i*2,motor.getBlockPos().getY()+3,chunkZ+2);
                restore.put(pos,level.getBlockState(pos));level.setBlock(pos,block.defaultBlockState(),3);
                var be=level.getBlockEntity(pos);check(be instanceof BogeyIdentity,"Missing pass-by bogey at "+pos);
                bogeys.add(be);ids.add(((BogeyIdentity)be).emucab$identity());
            }
            var local=bogeys.get(2).getBlockPos().getCenter();var center=render.transformPosition(local);
            // Plot-local X may be rotated relative to world X. Move along the
            // actual row and put the listener beside it, so nearest selection
            // changes as the train passes instead of moving across the row.
            var axis=render.transformPosition(bogeys.get(5).getBlockPos().getCenter())
                    .subtract(render.transformPosition(bogeys.get(0).getBlockPos().getCenter())).normalize();
            var side=axis.cross(new net.minecraft.world.phys.Vec3(0,1,0));
            if(side.lengthSqr()<1e-8)side=axis.cross(new net.minecraft.world.phys.Vec3(1,0,0));
            side=side.normalize().scale(3);
            set.invoke(camera,center.x+side.x,center.y+side.y+2,center.z+side.z);
            double previousPower=-1,minPower=Double.POSITIVE_INFINITY,maxStep=0;
            for(int step=0;step<40;step++) {
                double travel=-90+350/3.6/20*step;
                render.position().set(rp).add(axis.x*travel,axis.y*travel,axis.z*travel);
                logical.position().set(lp).add(axis.x*travel,axis.y*travel,axis.z*travel);
                for(int i=0;i<ids.size();i++)BogeyMotorSounds.accept(new BogeyMotorSoundPayload(bogeys.get(i).getBlockPos(),ids.get(i),10000+step,1200,350,1,0,true));
                BogeyMotorSounds.tick(new ClientTickEvent.Post());
                var playing=exterior();check(playing.keySet().containsAll(ids),"Whole-train ambience was limited to nearest motor selection");
                double power=0;
                for(var id:ids) {
                    Object pair=playing.get(id);Object old=persistent.putIfAbsent(id,pair);
                    check(old==null||old==pair,"Wind/rolling pair restarted on nearest-source handoff");
                    for(String layer:new String[]{"rolling","wind"}) {
                        var s=sound(pair,layer);s.resolve(mc.getSoundManager());
                        check(!s.getSound().shouldStream(),"Exterior ambience consumed a streaming channel");
                        check(s.isLooping()&&!((TickableSoundInstance)s).isStopped(),"Exterior ambience stopped during pass-by");
                        Object delegate=field(s,"delegate");check(delegate instanceof MovingSoundInstanceDelegate,"No Sable wrapper for ambience");
                        ((TickableSoundInstance)delegate).tick();
                        check(Math.abs(((SoundInstance)delegate).getX()-logical.transformPosition(bogeys.get(ids.indexOf(id)).getBlockPos().getCenter()).x)<1e-5,"Ambient transform was applied twice");
                        power+=s.getVolume()*s.getVolume();
                    }
                }
                var selected=new HashSet<UUID>();for(var e:motors().entrySet())if(ids.contains(e.getKey())&&!(boolean)field(e.getValue(),"retiring"))selected.add(e.getKey());
                nearestSets.add(Set.copyOf(selected));
                if(step>5) {
                    check(power>1e-5,"Nearby train exterior power became silent");minPower=Math.min(minPower,power);
                    if(previousPower>0)maxStep=Math.max(maxStep,Math.abs(10*Math.log10(power/previousPower)));
                }
                previousPower=power;
                var selectedIndices=new ArrayList<Integer>();for(int i=0;i<ids.size();i++)if(selected.contains(ids.get(i)))selectedIndices.add(i);
                csv.append(step).append(',').append(travel).append(',').append(power).append(',').append(playing.size()).append(',').append(selected.size()).append(',').append('"').append(selectedIndices).append('"').append('\n');
            }
            Files.writeString(mc.gameDirectory.toPath().resolve("continuous-bogey-passby.csv"),csv);
            check(nearestSets.size()>=2,"Pass-by did not exercise a nearest-three selection change");
            check(maxStep<3,"Ambient distance gain jumped more than3dB per350km/h pose step: "+maxStep);
            // Standstill and restart preserve the allocated loop/phase rather
            // than starting a fresh wind sound for every speed edge.
            for(int step=0;step<45;step++) {
                for(int i=0;i<ids.size();i++)BogeyMotorSounds.accept(new BogeyMotorSoundPayload(bogeys.get(i).getBlockPos(),ids.get(i),11000+step,0,0,0,0,true));
                BogeyMotorSounds.tick(new ClientTickEvent.Post());
            }
            for(var id:ids)check(exterior().get(id)==persistent.get(id),"Standstill discarded continuous exterior loop");
            for(int i=0;i<ids.size();i++)BogeyMotorSounds.accept(new BogeyMotorSoundPayload(bogeys.get(i).getBlockPos(),ids.get(i),12000,1200,350,1,0,true));
            for(int step=0;step<4;step++)BogeyMotorSounds.tick(new ClientTickEvent.Post());
            for(var id:ids)check(exterior().get(id)==persistent.get(id),"Restart recreated continuous exterior loop");
            // Expired telemetry and owner removal must still release sources.
            for(int step=0;step<105;step++)BogeyMotorSounds.tick(new ClientTickEvent.Post());
            for(var id:ids)check(!exterior().containsKey(id),"Expired telemetry left continuous exterior loop alive");
            for(Object pair:persistent.values())for(String layer:new String[]{"rolling","wind"})check(((TickableSoundInstance)sound(pair,layer)).isStopped(),"Expired exterior sound not stopped");
            Files.writeString(mc.gameDirectory.toPath().resolve("continuous-bogey-passby.csv"),csv);
            MotorClientCheck.evidence.append("six bogeys/twelve static exterior loops retained across40 synthetic350km/h poses and "+nearestSets.size()+" nearest-motor selections; no ambient streaming channels; no silent aggregate tick, max gain step="+maxStep+"dB; stop/restart phase identity retained, stale cleanup passed; ");
        } finally {
            for(var entry:restore.entrySet())level.setBlock(entry.getKey(),entry.getValue(),3);
            for(int i=0;i<105;i++)BogeyMotorSounds.tick(new ClientTickEvent.Post());
            BogeyAudioConfig.RADIUS.set(oldRadius);set.invoke(camera,oldCamera.x,oldCamera.y,oldCamera.z);
            render.position().set(rp);logical.position().set(lp);
        }
    }
    private ContinuousBogeyPassbyCheck(){}
}
