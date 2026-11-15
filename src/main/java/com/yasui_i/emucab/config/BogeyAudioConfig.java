package com.yasui_i.emucab.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local mixing controls; never changes the physical drive or braking model. */
public final class BogeyAudioConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    /** Internal migration marker; users should change the audio values below instead. */
    public static final ModConfigSpec.IntValue CONFIG_VERSION = B.defineInRange("bogey_motor.config_version", 0, 0, 3);
    public static final ModConfigSpec.BooleanValue ENABLED = B.define("bogey_motor.enabled", true);
    public static final ModConfigSpec.DoubleValue VOLUME = B.defineInRange("bogey_motor.volume", .85, 0, 1);
    public static final ModConfigSpec.DoubleValue RADIUS = B.comment("Audible radius of the moving exterior bogey sound in world blocks; distance attenuation remains active.")
            .defineInRange("bogey_motor.radius", com.yasui_i.emucab.audio.ExteriorSoundRange.DEFAULT, 64, 512);
    public static final ModConfigSpec.IntValue SOURCES = B.comment("Maximum nearby VVVF synthesizers. Persistent rolling/wind loops are independent (up to 32 bogeys in the static sound pool). At most four streaming channels including fading sources.")
            .defineInRange("bogey_motor.maximum_sources", 3, 1, 3);
    public static final ModConfigSpec.DoubleValue GEAR_RATIO = B.comment("Motor RPM / wheel RPM, used for the synthetic sound only.")
            .defineInRange("bogey_motor.gear_ratio", 2.5, .1, 20);
    public static final ModConfigSpec.IntValue POLE_PAIRS = B.defineInRange("bogey_motor.pole_pairs", 2, 1, 8);
    public static final ModConfigSpec.DoubleValue ELECTRICAL = B.defineInRange("mix.electrical", .8, 0, 2);
    public static final ModConfigSpec.DoubleValue MOTOR_GAIN = B.comment("Additional motor-tone gain before the PCM limiter; leaves rolling/wind unchanged. 1.0 restores the previous motor level.")
            .defineInRange("mix.motor_gain", 1.8, 0, 4);
    public static final ModConfigSpec.DoubleValue MECHANICAL = B.defineInRange("mix.mechanical", .35, 0, 2);
    public static final ModConfigSpec.DoubleValue WIND = B.defineInRange("mix.wind", .25, 0, 2);
    public static final ModConfigSpec.BooleanValue REPLACE_RUMBLE = B.comment("Suppress Simurail's original impact recording on all bogeys with live registered telemetry, including those outside the custom channel selection.")
            .define("bogey_motor.replace_original_rumble", true);
    public static final ModConfigSpec SPEC = B.build();
    private BogeyAudioConfig() {}
}
