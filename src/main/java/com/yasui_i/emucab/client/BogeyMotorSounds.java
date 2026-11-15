package com.yasui_i.emucab.client;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.audio.VvvfSynthesizer;
import com.yasui_i.emucab.config.BogeyAudioConfig;
import com.yasui_i.emucab.network.BogeyMotorSoundPayload;
import com.yasui_i.emucab.registry.ModSounds;
import com.yasui_i.emucab.telemetry.BogeyIdentity;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sound.MovingSoundInstanceDelegate;
import dev.ryanhcode.sable.sound.SoundInstanceDelegated;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Bounded nearby VVVF streams plus persistent whole-train exterior loops. */
@EventBusSubscriber(modid=EMUCab.MOD_ID,value=Dist.CLIENT)
public final class BogeyMotorSounds {
    private record Snapshot(BogeyMotorSoundPayload packet,long tick) {}
    private static final Map<UUID,Snapshot> SNAPSHOTS=new HashMap<>();
    private static final Map<UUID,MotorSound> PLAYING=new HashMap<>();
    private static ClientLevel session;
    private static long ticks;
    private static long textureGeneration=-1;
    private static final int SNAPSHOT_TIMEOUT_TICKS=100;
    private static final int CHANNEL_BUDGET=4;

    public static void accept(BogeyMotorSoundPayload packet) {
        var level=Minecraft.getInstance().level;
        if(level==null||!Float.isFinite(packet.wheelRpm())||Math.abs(packet.wheelRpm())>20000
                ||!Float.isFinite(packet.bodySpeedKmh())||packet.bodySpeedKmh()<0||packet.bodySpeedKmh()>2000
                ||!Float.isFinite(packet.traction01())||!Float.isFinite(packet.brake01())
                ||packet.traction01()<0||packet.traction01()>1||packet.brake01()<0||packet.brake01()>1)return;
        if(session!=level){clear();session=level;}
        var previous=SNAPSHOTS.get(packet.bogey());
        if(previous!=null&&packet.sequence()<=previous.packet().sequence())return;
        SNAPSHOTS.put(packet.bogey(),new Snapshot(packet,ticks));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();var level=mc.level;ticks++;
        if(level!=session){clear();session=level;}
        if(level==null)return;
        long generation=BogeyRecordedLayers.generation();
        if(textureGeneration!=generation){stopAll();textureGeneration=generation;}
        SNAPSHOTS.entrySet().removeIf(e->ticks-e.getValue().tick()>SNAPSHOT_TIMEOUT_TICKS);
        if(!BogeyAudioConfig.ENABLED.get()||BogeyAudioConfig.VOLUME.get()<=0){stopAll();return;}
        var textures=BogeyRecordedLayers.load().getNow(null);
        if(textures==null)return;
        double radius=BogeyAudioConfig.RADIUS.get();var camera=mc.gameRenderer.getMainCamera().getPosition();
        BogeyExteriorSounds.update(level,SNAPSHOTS.values().stream().map(Snapshot::packet).toList(),camera);
        var nearby=new ArrayList<Snapshot>();
        for(var snapshot:SNAPSHOTS.values()) {
            var p=snapshot.packet();var be=level.getBlockEntity(p.pos());
            if(!valid(be,p.bogey())||Math.abs(p.wheelRpm())<.1&&p.bodySpeedKmh()<.1&&p.traction01()<.001)continue;
            if(world(level,be).distanceToSqr(camera)<radius*radius)nearby.add(snapshot);
        }
        // A current source has a small distance advantage. Near-equal bogeys
        // do not continually swap channels as the camera or train moves.
        nearby.sort(Comparator.comparingDouble(s->world(level,level.getBlockEntity(s.packet().pos())).distanceToSqr(camera)
                *(PLAYING.containsKey(s.packet().bogey())&&!PLAYING.get(s.packet().bogey()).retiring?.81:1)));
        var selected=new HashSet<UUID>();int count=Math.min(nearby.size(),BogeyAudioConfig.SOURCES.get());
        for(int i=0;i<count;i++)selected.add(nearby.get(i).packet().bogey());
        PLAYING.entrySet().removeIf(e->{
            var sound=e.getValue();
            if(!sound.validOwner()||!SNAPSHOTS.containsKey(e.getKey())){sound.finish();return true;}
            if(selected.contains(e.getKey()))return false;
            sound.retiring=true;
            return sound.advanceFade(false);
        });
        // Distant selected bogeys contribute less to normalization than close
        // ones, so two barely audible neighbours cannot suppress a near bogey.
        double power=0;
        for(int i=0;i<count;i++) {
            double distance=world(level,level.getBlockEntity(nearby.get(i).packet().pos())).distanceTo(camera);
            double gain=com.yasui_i.emucab.audio.ExteriorSoundRange.gain(distance,radius,.8);
            power+=gain*gain;
        }
        float balance=(float)com.yasui_i.emucab.audio.ExteriorSoundRange.balance(power);
        for(int i=0;i<count;i++) {
            var packet=nearby.get(i).packet();var sound=PLAYING.get(packet.bogey());
            var be=level.getBlockEntity(packet.pos());
            if(sound==null||sound.isStopped()||sound.owner!=be||sound.lostChannel()) {
                if(sound!=null)sound.finish();
                // A retiring source is only a tail for a bogey that is no
                // longer one of the nearest sources. It must not block a new
                // nearby bogey from taking its channel: at high speed that
                // turns the tail into an audible gap between cars. Reclaim
                // the quietest retiring source before giving up allocation.
                if(sound==null&&PLAYING.size()>=CHANNEL_BUDGET
                        &&!reclaimRetiringChannel())continue;
                sound=new MotorSound(level,be,packet.bogey(),textures);PLAYING.put(packet.bogey(),sound);
                sound.update(packet,balance);mc.getSoundManager().play(sound);
            }else sound.update(packet,balance);
            sound.retiring=false;sound.advanceFade(true);
        }
    }

    public static boolean replacesRumble(BlockEntity bogey) {
        return originalRumbleGain(bogey)<1;
    }
    public static float originalRumbleGain(BlockEntity bogey) {
        if(!BogeyAudioConfig.ENABLED.get()||BogeyAudioConfig.VOLUME.get()<=0||!BogeyAudioConfig.REPLACE_RUMBLE.get()
                ||!(bogey instanceof BogeyIdentity id))return 1;
        var snapshot=SNAPSHOTS.get(id.emucab$identity());
        // Registration/telemetry determines ownership, not which three bogeys
        // won a channel this tick. Otherwise every other bogey still rattles.
        return snapshot!=null&&ticks-snapshot.tick()<=SNAPSHOT_TIMEOUT_TICKS
                &&valid(bogey,snapshot.packet().bogey())?0:1;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
    private static boolean valid(BlockEntity be,UUID id){return be!=null&&!be.isRemoved()&&be instanceof BogeyIdentity b&&b.emucab$identity().equals(id);}
    private static Vec3 world(ClientLevel level,BlockEntity be) {
        var local=be.getBlockPos().getCenter();var sub=SableCompanion.INSTANCE.getContaining(level,be.getBlockPos());
        return sub instanceof ClientSubLevelAccess client?client.renderPose().transformPosition(local):local;
    }
    private static void stopAll(){PLAYING.values().forEach(MotorSound::finish);PLAYING.clear();BogeyExteriorSounds.clear();}
    private static boolean reclaimRetiringChannel() {
        var candidate=PLAYING.entrySet().stream()
                .filter(e->e.getValue().retiring)
                .min(Comparator.comparingDouble(e->e.getValue().fade));
        if(candidate.isEmpty())return false;
        var entry=candidate.get();
        entry.getValue().finish();
        PLAYING.remove(entry.getKey(),entry.getValue());
        return true;
    }
    private static void clear(){stopAll();SNAPSHOTS.clear();session=null;}

    private static final class MotorSound extends AbstractTickableSoundInstance implements SoundInstanceDelegated {
        private final ClientLevel level;
        private final BlockEntity owner;
        private final UUID id;
        private final VvvfSynthesizer synth=new VvvfSynthesizer();
        private MovingSoundInstanceDelegate delegate;
        private float balance=1;
        private float fade;
        private boolean retiring;
        private int silentTicks;
        private final long startedAt=ticks;
        private int inactiveTicks;
        private volatile BogeyPcmStream stream;
        MotorSound(ClientLevel level,BlockEntity owner,UUID id,BogeyRecordedLayers.Layers textures) {
            super(ModSounds.BOGEY_MOTOR.get(),SoundSource.BLOCKS,RandomSource.create());
            this.level=level;this.owner=owner;this.id=id;
            synth.setRecordedLayers(textures.mechanical(),textures.wind());synth.setOutputGain(0);
            synth.setExteriorLayersEnabled(false);
            looping=true;attenuation=Attenuation.NONE;updatePosition();
        }
        void update(BogeyMotorSoundPayload packet,float balance) {
            this.balance=balance;
            synth.setMix(BogeyAudioConfig.ELECTRICAL.get(),BogeyAudioConfig.MECHANICAL.get(),BogeyAudioConfig.WIND.get());
            synth.setMotorGain(BogeyAudioConfig.MOTOR_GAIN.get());
            synth.setTarget(packet.wheelRpm(),packet.bodySpeedKmh(),packet.motorPowered()?packet.traction01():0,
                    packet.motorPowered()?packet.brake01():0,BogeyAudioConfig.GEAR_RATIO.get(),BogeyAudioConfig.POLE_PAIRS.get());
            updatePosition();
        }
        boolean validOwner(){return Minecraft.getInstance().level==level&&valid(owner,id)&&level.getBlockEntity(owner.getBlockPos())==owner;}
        boolean lostChannel() {
            if(Minecraft.getInstance().getSoundManager().isActive(delegate==null?this:delegate)){inactiveTicks=0;return false;}
            return ticks-startedAt>20&&++inactiveTicks>=20;
        }
        boolean advanceFade(boolean active) {
            fade=Math.clamp(fade+(active?.25f:-.25f),0,1);
            synth.setOutputGain(fade*balance);
            // Let gain settle and all four 100 ms buffers drain before closing.
            if(active)silentTicks=0;
            else if(fade<=0&&++silentTicks>=10){finish();return true;}
            return false;
        }
        private void updatePosition() {
            var local=owner.getBlockPos().getCenter();x=local.x;y=local.y;z=local.z;
            double radius=BogeyAudioConfig.RADIUS.get(),distance=world(level,owner).distanceTo(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
            volume=(float)(BogeyAudioConfig.VOLUME.get()*com.yasui_i.emucab.audio.ExteriorSoundRange.gain(distance,radius,.8));
        }
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick() {
            if(!validOwner()
                    ||!SNAPSHOTS.containsKey(id)||ticks-SNAPSHOTS.get(id).tick()>SNAPSHOT_TIMEOUT_TICKS){finish();return;}
            updatePosition();
        }
        void finish(){if(stream!=null)stream.disableRecovery();stop();synth.setOutputGain(0);synth.setTarget(0,0,0,0,2.5,2);}
        @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers,Sound sound,boolean looping){
            var pcm=new BogeyPcmStream(synth);stream=pcm;
            if(isStopped())pcm.disableRecovery();
            return CompletableFuture.completedFuture(pcm);
        }
        @Override public void setDelegate(MovingSoundInstanceDelegate delegate){this.delegate=delegate;}
        @Override public MovingSoundInstanceDelegate getDelegate(){return delegate;}
    }

    private BogeyMotorSounds() {}
}
