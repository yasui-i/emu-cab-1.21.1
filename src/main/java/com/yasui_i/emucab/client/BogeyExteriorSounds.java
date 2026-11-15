package com.yasui_i.emucab.client;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.audio.ExteriorSoundRange;
import com.yasui_i.emucab.config.BogeyAudioConfig;
import com.yasui_i.emucab.mixin.BogeyLoopChannelAccessor;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import org.lwjgl.openal.AL10;
import java.util.*;

/** Persistent wind/rolling sources independent of the three VVVF channels. */
@EventBusSubscriber(modid=EMUCab.MOD_ID,value=Dist.CLIENT)
public final class BogeyExteriorSounds {
    private record Pair(ExteriorLoopSound rolling,ExteriorLoopSound wind) {
        void finish(){rolling.finish();wind.finish();}
    }
    private static final Map<UUID,Pair> PLAYING=new HashMap<>();
    // 32 bogeys cover two standard eight-car sets. Each uses two cheap static
    // channels; existing sources stay allocated instead of nearest-N churn.
    private static final int MAX_BOGEYS=32;

    static void update(ClientLevel level,List<BogeyMotorSoundPayload> packets,Vec3 camera) {
        double radius=BogeyAudioConfig.RADIUS.get();
        var candidates=new ArrayList<BogeyMotorSoundPayload>();
        for(var packet:packets) {
            var owner=level.getBlockEntity(packet.pos());
            if(valid(level,owner,packet.bogey())&&world(level,owner).distanceToSqr(camera)<Math.pow(radius+32,2))
                candidates.add(packet);
        }
        var live=new HashSet<UUID>();for(var p:candidates)live.add(p.bogey());
        PLAYING.entrySet().removeIf(e->{
            var pair=e.getValue();
            if(!live.contains(e.getKey())||!pair.rolling.validOwner()){pair.finish();return true;}return false;
        });
        candidates.sort(Comparator.comparingDouble(p->world(level,level.getBlockEntity(p.pos())).distanceToSqr(camera)));
        var manager=Minecraft.getInstance().getSoundManager();
        var created=new HashSet<UUID>();
        for(var packet:candidates) {
            var pair=PLAYING.get(packet.bogey());var owner=level.getBlockEntity(packet.pos());
            if(pair==null) {
                if(!moving(packet))continue;
                if(PLAYING.size()>=MAX_BOGEYS&&!releaseParkedSource())continue;
                pair=new Pair(new ExteriorLoopSound(level,owner,packet.bogey(),false),
                        new ExteriorLoopSound(level,owner,packet.bogey(),true));
                PLAYING.put(packet.bogey(),pair);
                created.add(packet.bogey());
            }
        }
        double power=0;
        for(var p:candidates) {
            // Parked or unallocated bogeys must not turn down the passing
            // train. Only sources that can contribute take part in balancing.
            if(!PLAYING.containsKey(p.bogey())||!moving(p))continue;
            double gain=ExteriorSoundRange.gain(world(level,level.getBlockEntity(p.pos())).distanceTo(camera),radius,.8);
            power+=gain*gain;
        }
        float balance=(float)ExteriorSoundRange.balance(power);
        for(var packet:candidates) {
            var pair=PLAYING.get(packet.bogey());
            if(pair==null)continue;
            if(created.contains(packet.bogey())) {
                pair.rolling.update(packet,balance);pair.wind.update(packet,balance);
                manager.play(pair.rolling);manager.play(pair.wind);
            }else {
                pair.rolling.update(packet,balance);pair.wind.update(packet,balance);
                // An exhausted global static pool may reject a source. Retry
                // only after a grace period, never on ordinary nearest swaps.
                if(pair.rolling.lostChannel()){manager.play(pair.rolling);pair.rolling.inactiveTicks=0;}
                if(pair.wind.lostChannel()){manager.play(pair.wind);pair.wind.inactiveTicks=0;}
            }
        }
    }

    private static boolean releaseParkedSource() {
        // Preserve moving loops, but do not let silent parked trains reserve
        // every slot when another train arrives.
        var parked=PLAYING.entrySet().stream().filter(e->e.getValue().rolling.parkedTicks>=20
                &&e.getValue().rolling.getVolume()<.001f&&e.getValue().wind.getVolume()<.001f).findFirst();
        if(parked.isEmpty())return false;
        var entry=parked.get();entry.getValue().finish();PLAYING.remove(entry.getKey());return true;
    }

    static void clear(){PLAYING.values().forEach(Pair::finish);PLAYING.clear();}
    private static boolean moving(BogeyMotorSoundPayload p){return p.bodySpeedKmh()>=.1||Math.abs(p.wheelRpm())>=.1;}
    private static boolean valid(ClientLevel level,BlockEntity be,UUID id) {
        return Minecraft.getInstance().level==level&&be!=null&&!be.isRemoved()
                &&be instanceof BogeyIdentity b&&b.emucab$identity().equals(id)&&level.getBlockEntity(be.getBlockPos())==be;
    }
    private static Vec3 world(ClientLevel level,BlockEntity owner) {
        var local=owner.getBlockPos().getCenter();var sub=SableCompanion.INSTANCE.getContaining(level,owner.getBlockPos());
        return sub instanceof ClientSubLevelAccess client?client.renderPose().transformPosition(local):local;
    }

    // Called on the sound executor, after attaching the shared static buffer.
    @SubscribeEvent public static void started(PlaySoundSourceEvent event) {
        var sound=event.getSound();
        if(sound instanceof MovingSoundInstanceDelegate d)sound=d.instance;
        if(sound instanceof ExteriorLoopSound loop) {
            int source=((BogeyLoopChannelAccessor)event.getChannel()).emucab$source();
            long hash=loop.id.getLeastSignificantBits()^(loop.wind?0x17a813c4L:0x2735b819L);
            AL10.alSourcef(source,org.lwjgl.openal.AL11.AL_SEC_OFFSET,(hash&65535)/65536f*BogeyExteriorBuffers.LOOP_SECONDS);
        }
    }

    private static final class ExteriorLoopSound extends AbstractTickableSoundInstance implements SoundInstanceDelegated {
        private final ClientLevel level;
        private final BlockEntity owner;
        private final UUID id;
        private final boolean wind;
        private MovingSoundInstanceDelegate delegate;
        private double bodySpeed,wheelRpm,targetBody,targetWheel;
        private float balance,targetBalance=1;
        private int age,inactiveTicks,channelChecks,parkedTicks;
        ExteriorLoopSound(ClientLevel level,BlockEntity owner,UUID id,boolean wind) {
            super(wind?ModSounds.BOGEY_WIND.get():ModSounds.BOGEY_ROLLING.get(),SoundSource.BLOCKS,RandomSource.create());
            this.level=level;this.owner=owner;this.id=id;this.wind=wind;
            looping=true;attenuation=Attenuation.NONE;volume=0;
            updatePosition();
        }
        void update(BogeyMotorSoundPayload packet,float balance) {
            targetBody=packet.bodySpeedKmh();targetWheel=Math.abs(packet.wheelRpm());targetBalance=balance;
            parkedTicks=moving(packet)?0:parkedTicks+1;
            if(age==0){bodySpeed=targetBody;wheelRpm=targetWheel;this.balance=balance;}
            updatePosition();
        }
        boolean validOwner(){return valid(level,owner,id);}
        boolean lostChannel() {
            // Rejected channels are never ticked by SoundEngine, so age alone
            // cannot time their retry grace period.
            if(++channelChecks<40)return false;
            if(Minecraft.getInstance().getSoundManager().isActive(delegate==null?this:delegate)){inactiveTicks=0;return false;}
            return ++inactiveTicks>=40;
        }
        private void updatePosition() {
            var local=owner.getBlockPos().getCenter();x=local.x;y=local.y;z=local.z;
            double distance=world(level,owner).distanceTo(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
            double speed=Math.clamp(bodySpeed/350,0,2);
            double wheel=Math.clamp(wheelRpm*2*Math.PI*.46/60*3.6/350,0,2);
            double amplitude=wind?.82*Math.pow(speed,1.85)*BogeyAudioConfig.WIND.get()/.25
                    :(.13+.54*Math.sqrt(speed))*smooth(Math.max(speed,wheel)/.012)*BogeyAudioConfig.MECHANICAL.get()/.35;
            double fade=Math.min(1,age/4.0);
            volume=(float)(BogeyAudioConfig.VOLUME.get()*ExteriorSoundRange.gain(distance,BogeyAudioConfig.RADIUS.get(),.8)
                    *amplitude*1.34307018*balance*fade);
            // Keep the wind recording at its natural pitch; rolling texture
            // follows wheel speed smoothly without restarting the sample.
            pitch=wind?1:(float)(.68+.38*Math.sqrt(wheel));
        }
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick() {
            if(!validOwner()){finish();return;}
            age++;bodySpeed+=(targetBody-bodySpeed)*.48;wheelRpm+=(targetWheel-wheelRpm)*.48;
            balance+=(targetBalance-balance)*.2;updatePosition();
        }
        void finish(){stop();}
        @Override public void setDelegate(MovingSoundInstanceDelegate delegate){this.delegate=delegate;}
        @Override public MovingSoundInstanceDelegate getDelegate(){return delegate;}
    }
    private static double smooth(double x){x=Math.clamp(x,0,1);return x*x*(3-2*x);}
    private BogeyExteriorSounds(){}
}
