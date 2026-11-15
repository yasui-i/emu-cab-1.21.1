package com.yasui_i.emucab.config;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Migrates prior exterior sound defaults once. Values equal to a prior default
 * cannot be distinguished from a manual choice; other custom ranges and all
 * edits after the current migration are preserved.
 */
public final class ExteriorAudioConfigMigration {
    private static final int CURRENT_VERSION = 3;
    private static final double OLD_MOTOR_RADIUS = 48.0;
    private static final double OLD_BRAKE_RADIUS = 24.0;
    private static final double NEW_RADIUS = com.yasui_i.emucab.audio.ExteriorSoundRange.DEFAULT;

    public static void onLoading(ModConfigEvent.Loading event) {
        ModConfig config = event.getConfig();
        if (!"emucab".equals(config.getModId())) return;
        if (config.getType() == ModConfig.Type.CLIENT && config.getSpec() == BogeyAudioConfig.SPEC)
            migrateMotor();
        else if (config.getType() == ModConfig.Type.COMMON && config.getSpec() == CabConfig.SPEC)
            migrateBrakeRelease();
        else if (config.getType() == ModConfig.Type.COMMON && config.getSpec() == com.yasui_i.emucab.motor.VvvfMotorConfig.SPEC)
            migrateStandaloneMotor();
    }

    private static void migrateMotor() {
        if (BogeyAudioConfig.CONFIG_VERSION.get() >= CURRENT_VERSION) return;
        if (oldDefault(BogeyAudioConfig.RADIUS.get(),BogeyAudioConfig.CONFIG_VERSION.get(),OLD_MOTOR_RADIUS))
            BogeyAudioConfig.RADIUS.set(NEW_RADIUS);
        if (same(BogeyAudioConfig.VOLUME.get(), .65) && BogeyAudioConfig.CONFIG_VERSION.get()<2)
            BogeyAudioConfig.VOLUME.set(.85);
        BogeyAudioConfig.CONFIG_VERSION.set(CURRENT_VERSION);
        BogeyAudioConfig.CONFIG_VERSION.save();
    }

    private static void migrateBrakeRelease() {
        if (CabConfig.BRAKE_AUDIO_CONFIG_VERSION.get() >= 4) return;
        if (same(CabConfig.BOGEY_RELEASE_RADIUS.get(),128.0)
                ||oldDefault(CabConfig.BOGEY_RELEASE_RADIUS.get(),CabConfig.BRAKE_AUDIO_CONFIG_VERSION.get(),OLD_BRAKE_RADIUS))
            CabConfig.BOGEY_RELEASE_RADIUS.set(64.0);
        if (same(CabConfig.BOGEY_RELEASE_VOLUME.get(), .65) && CabConfig.BRAKE_AUDIO_CONFIG_VERSION.get()<2)
            CabConfig.BOGEY_RELEASE_VOLUME.set(.85);
        CabConfig.BRAKE_AUDIO_CONFIG_VERSION.set(4);
        CabConfig.BRAKE_AUDIO_CONFIG_VERSION.save();
    }

    private static void migrateStandaloneMotor() {
        if (com.yasui_i.emucab.motor.VvvfMotorConfig.SOUND_CONFIG_VERSION.get() >= CURRENT_VERSION) return;
        if (oldDefault(com.yasui_i.emucab.motor.VvvfMotorConfig.SOUND_RANGE.get(),
                com.yasui_i.emucab.motor.VvvfMotorConfig.SOUND_CONFIG_VERSION.get(),OLD_MOTOR_RADIUS))
            com.yasui_i.emucab.motor.VvvfMotorConfig.SOUND_RANGE.set(NEW_RADIUS);
        if (same(com.yasui_i.emucab.motor.VvvfMotorConfig.SOUND_VOLUME.get(), .8)
                &&com.yasui_i.emucab.motor.VvvfMotorConfig.SOUND_CONFIG_VERSION.get()<2)
            com.yasui_i.emucab.motor.VvvfMotorConfig.SOUND_VOLUME.set(1.0);
        com.yasui_i.emucab.motor.VvvfMotorConfig.SOUND_CONFIG_VERSION.set(CURRENT_VERSION);
        com.yasui_i.emucab.motor.VvvfMotorConfig.SOUND_CONFIG_VERSION.save();
    }

    private static boolean oldDefault(double radius,int version,double legacy) {
        return same(radius,256.0)||version<2&&(same(radius,legacy)||same(radius,64.0));
    }

    private static boolean same(double value, double expected) {
        return Double.isFinite(value) && Math.abs(value - expected) < 1e-9;
    }

    private ExteriorAudioConfigMigration() {}
}
