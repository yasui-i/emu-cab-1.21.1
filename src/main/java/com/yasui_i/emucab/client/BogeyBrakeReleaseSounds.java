package com.yasui_i.emucab.client;

import com.yasui_i.emucab.network.BogeyBrakeSoundPayload;
import com.yasui_i.emucab.network.CabAtpSoundPayload;
import com.yasui_i.emucab.registry.ModSounds;
import com.yasui_i.emucab.telemetry.BogeyIdentity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.UUID;

/** One-shot recordings follow the bogey, with world-space gain and one Sable transform. */
public final class BogeyBrakeReleaseSounds {
    public static void play(BogeyBrakeSoundPayload packet) {
        var mc=Minecraft.getInstance();var level=mc.level;
        if(level==null||!Float.isFinite(packet.volume())||packet.volume()<=0||!Float.isFinite(packet.radius())||packet.radius()<1)return;
        var be=level.getBlockEntity(packet.pos());
        if(!(be instanceof BogeyIdentity id)||!id.emucab$identity().equals(packet.bogey()))return;
        mc.getSoundManager().play(new MovingRelease(level,be,packet.bogey(),Math.clamp(packet.volume(),0,1),Math.clamp(packet.radius(),64,512)));
    }
    private static final class MovingRelease extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final BlockEntity bogey;
        private final UUID identity;
        private final float recordingVolume,radius;
        private int life=20*30;
        MovingRelease(ClientLevel level,BlockEntity bogey,UUID identity,float volume,float radius) {
            super(ModSounds.BOGEY_BRAKE_RELEASE.get(),SoundSource.BLOCKS,RandomSource.create());
            this.level=level;this.bogey=bogey;this.identity=identity;this.recordingVolume=volume;this.radius=radius;
            attenuation=Attenuation.NONE;updatePosition();
        }
        private void updatePosition() {
            var local=bogey.getBlockPos().getCenter();
            var sub=dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(level,bogey.getBlockPos());
            var world=sub instanceof dev.ryanhcode.sable.companion.ClientSubLevelAccess client
                    ?client.renderPose().transformPosition(local):CabAtpSoundPayload.worldPosition(level,bogey.getBlockPos());
            // Sable's sound wrapper performs the spatial transform from these coordinates.
            x=local.x;y=local.y;z=local.z;
            double distance=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(world);
            // A square-root curve keeps a long-range pass-by audible without
            // changing the configured hard cutoff.
            volume=recordingVolume*(float)com.yasui_i.emucab.audio.ExteriorSoundRange.gain(distance,radius,.65);
        }
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick() {
            if(Minecraft.getInstance().level!=level||bogey.isRemoved()||--life<=0||level.getBlockEntity(bogey.getBlockPos())!=bogey
                    ||!(bogey instanceof BogeyIdentity id)||!id.emucab$identity().equals(identity)) {stop();return;}
            updatePosition();
        }
    }
    private BogeyBrakeReleaseSounds(){}
}
