package com.yasui_i.emucab.client;

import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.AtpAnnouncement;
import com.yasui_i.emucab.control.CabAtpAcoustics;
import com.yasui_i.emucab.network.CabAtpSoundPayload;
import com.yasui_i.emucab.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** A non-looping recording with a position updated throughout playback. */
public final class CabAtpSounds {
    public static void play(CabAtpSoundPayload packet) {
        var mc=Minecraft.getInstance();var level=mc.level;var cue=packet.announcement();
        if(level==null || cue==null || !Float.isFinite(packet.volume()) || packet.volume()<=0
                || !(level.getBlockEntity(packet.pos()) instanceof CabDeskBlockEntity desk))return;
        mc.getSoundManager().play(new MovingAnnouncement(level,desk,cue,Math.clamp(packet.volume(),0,1)));
    }
    private static final class MovingAnnouncement extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final CabDeskBlockEntity desk;
        private final float recordingVolume;
        private int life;
        MovingAnnouncement(ClientLevel level,CabDeskBlockEntity desk,AtpAnnouncement cue,float volume) {
            super(ModSounds.atp(cue),SoundSource.BLOCKS,RandomSource.create());
            this.level=level;this.desk=desk;this.recordingVolume=volume;life=cue.ticks+40;
            attenuation=Attenuation.NONE;
            updatePosition();
        }
        private void updatePosition() {
            // Keep the SoundInstance in Sable's local plot coordinates. Sable wraps
            // sublevel sounds and applies its physical pose exactly once. Feeding
            // it a world-space position causes a second transform and makes the
            // recording appear fixed or inaudible on a moving train.
            Vec3 local=desk.getBlockPos().getCenter();
            var sub=dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(level,desk.getBlockPos());
            // The manual gain uses the rendered pose so the audible boundary follows
            // the cab the player actually sees, rather than a stale physics tick.
            Vec3 worldPosition=sub instanceof dev.ryanhcode.sable.companion.ClientSubLevelAccess client
                    ?client.renderPose().transformPosition(local):CabAtpSoundPayload.worldPosition(level,desk.getBlockPos());
            x=local.x;y=local.y;z=local.z;
            var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            volume=recordingVolume*(float)CabAtpAcoustics.gain(camera.distanceToSqr(worldPosition));
        }
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick() {
            if(Minecraft.getInstance().level!=level || desk.isRemoved() || --life<=0
                    || level.getBlockEntity(desk.getBlockPos())!=desk) { stop();return; }
            updatePosition();
            // Leaving the audible sphere mutes the recording; a brief pose discrepancy
            // must not cancel it permanently while the cab moves.
        }
    }
    private CabAtpSounds() {}
}
