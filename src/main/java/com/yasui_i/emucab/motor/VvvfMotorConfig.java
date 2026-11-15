package com.yasui_i.emucab.motor;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class VvvfMotorConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue SOUND_CONFIG_VERSION = BUILDER
            .defineInRange("motor.sound_config_version", 0, 0, 3);
    public static final ModConfigSpec.DoubleValue RAMP_UP_SECONDS = BUILDER
            .comment("Seconds to ramp from zero to full redstone command. A zero signal always cuts traction immediately.")
            .defineInRange("motor.ramp_up_seconds", 10.0, 0.05, 600.0);
    public static final ModConfigSpec.DoubleValue RAMP_DOWN_SECONDS = BUILDER
            .comment("Seconds for full-range decrease between nonzero commands. Zero redstone bypasses this ramp.")
            .defineInRange("motor.ramp_down_seconds", 5.0, 0.05, 600.0);
    public static final ModConfigSpec.DoubleValue SOUND_RANGE = BUILDER
            .comment("Audible radius in blocks for the new motor's CEE motor and wind sounds; follows Sable vehicles.")
            .defineInRange("motor.sound_range", com.yasui_i.emucab.audio.ExteriorSoundRange.DEFAULT, 64.0, 512.0);
    public static final ModConfigSpec.DoubleValue SOUND_VOLUME = BUILDER
            .comment("Volume of the new motor, before distance attenuation.")
            .defineInRange("motor.sound_volume", 1.0, 0.0, 4.0);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private VvvfMotorConfig() {}
}
