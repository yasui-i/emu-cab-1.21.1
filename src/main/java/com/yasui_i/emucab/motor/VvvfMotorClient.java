package com.yasui_i.emucab.motor;

import com.george_vi.electroenergetics.CEEPartialModels;
import com.george_vi.electroenergetics.CEESoundEvents;
import com.george_vi.electroenergetics.content.ElectricHumSoundInstance;
import com.george_vi.electroenergetics.content.electric_motor.ElectricMotorRenderer;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.yasui_i.emucab.EMUCab;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Vector3d;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

public final class VvvfMotorClient {
    private static final Map<VvvfMotorBlockEntity, MotorAudio> AUDIO = new WeakHashMap<>();

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener(VvvfMotorClient::renderers);
        bus.addListener(VvvfMotorClient::setup);
    }

    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        // The CEE renderer accepts this ElectricMotorBlockEntity subclass and renders the original shaft.
        event.registerBlockEntityRenderer(VvvfMotors.BLOCK_ENTITY.get(), ElectricMotorRenderer::new);
    }

    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SimpleBlockEntityVisualizer.builder(VvvfMotors.BLOCK_ENTITY.get())
                .factory(OrientedRotatingVisual.of(CEEPartialModels.ELECTRIC_MOTOR_SHAFT)).apply());
    }

    public static void tickAudio(VvvfMotorBlockEntity motor) {
        MotorAudio audio = AUDIO.computeIfAbsent(motor, ignored -> new MotorAudio());
        float speed = motor.isOverStressed() ? 0 : Math.abs(motor.getSpeed());
        audio.smoothedSpeed = Mth.lerp(0.25f, audio.smoothedSpeed, speed);
        float normalized = Mth.clamp(audio.smoothedSpeed / Math.max(1, AllConfigs.server().kinetics.maxRotationSpeed.get()), 0, 1);
        if (normalized < 0.005f || VvvfMotorConfig.SOUND_VOLUME.get() <= 0) return;
        var sounds = Minecraft.getInstance().getSoundManager();
        if (audio.motor == null || audio.motor.isStopped()) {
            audio.motor = new MovingMotorSound(CEESoundEvents.DC_TRAIN.get(), motor);
            sounds.play(audio.motor);
        }
        if (audio.wind == null || audio.wind.isStopped()) {
            audio.wind = new MovingMotorSound(CEESoundEvents.TRAIN_WIND_STATIC.get(), motor);
            sounds.play(audio.wind);
        }
        audio.motor.setMotorVolume((float) (VvvfMotorConfig.SOUND_VOLUME.get() * Math.min(1, Math.sqrt(normalized))));
        audio.motor.setPitch(Mth.lerp(normalized, 0.1f, 2f));
        audio.motor.keepAlive();
        audio.wind.setMotorVolume((float) (VvvfMotorConfig.SOUND_VOLUME.get() * normalized * 0.5));
        audio.wind.setPitch(Mth.lerp(normalized, 0.1f, 1.5f));
        audio.wind.keepAlive();
    }

    private static final class MotorAudio {
        float smoothedSpeed;
        MovingMotorSound motor;
        MovingMotorSound wind;
    }

    private static final class MovingMotorSound extends ElectricHumSoundInstance {
        private final WeakReference<VvvfMotorBlockEntity> owner;
        private float motorVolume;
        private Vec3 worldPosition;

        MovingMotorSound(SoundEvent sound, VvvfMotorBlockEntity motor) {
            super(sound, motor.getBlockPos());
            owner = new WeakReference<>(motor);
            attenuation = SoundInstance.Attenuation.NONE;
            setVolumeImmediately(0);
            updatePosition(motor);
        }

        @Override public boolean canStartSilent() { return true; }
        void setMotorVolume(float volume) { motorVolume = volume; }

        private void updatePosition(VvvfMotorBlockEntity motor) {
            Vec3 local = motor.getBlockPos().getCenter();
            Vector3d point = new Vector3d(motor.getBlockPos().getX() + 0.5, motor.getBlockPos().getY() + 0.5, motor.getBlockPos().getZ() + 0.5);
            var sub = SableCompanion.INSTANCE.getContaining(motor.getLevel(), motor.getBlockPos());
            if (sub instanceof ClientSubLevelAccess clientSub) clientSub.renderPose().transformPosition(point, point);
            else if (sub != null) sub.logicalPose().transformPosition(point, point);
            worldPosition = new Vec3(point.x, point.y, point.z);
            // Sable's sound delegate transforms the local origin exactly once.
            x = local.x;
            y = local.y;
            z = local.z;
        }

        @Override public void tick() {
            VvvfMotorBlockEntity motor = owner.get();
            var client = Minecraft.getInstance();
            if (motor == null || motor.isRemoved() || motor.getLevel() != client.level) {
                stop();
                return;
            }
            updatePosition(motor);
            double radius = VvvfMotorConfig.SOUND_RANGE.get();
            Vec3 listener = client.gameRenderer.getMainCamera().getPosition();
            float falloff = (float) com.yasui_i.emucab.audio.ExteriorSoundRange.gain(listener.distanceTo(worldPosition),radius,1);
            setVolume(motorVolume * falloff);
            super.tick();
        }
    }

    private VvvfMotorClient() {}
}
