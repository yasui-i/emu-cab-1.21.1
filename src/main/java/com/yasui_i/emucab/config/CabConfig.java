package com.yasui_i.emucab.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CabConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Internal migration marker for the moving brake-release sound settings. */
    public static final ModConfigSpec.IntValue BRAKE_AUDIO_CONFIG_VERSION =
            BUILDER.defineInRange("simurail.brake_release_config_version", 0, 0, 4);

    public static final ModConfigSpec.DoubleValue VELOCITY_SCALE = BUILDER
            .comment(
                    "Factor applied to the Sable physics velocity before it is shown as km/h.",
                    "Sable reports velocity in metres per second, so 3.6 converts m/s to km/h.",
                    "If the desk shows a speed that is 20x too small, set this to 72.0 (blocks/tick).",
                    "Default: 3.6")
            .defineInRange("display.velocity_scale", 3.6, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue DEFAULT_LIMIT = BUILDER
            .comment("Speed limit shown on the ATP display, in km/h. Default: 350")
            .defineInRange("display.default_speed_limit", 350.0, 1.0, 1000.0);

    public static final ModConfigSpec.BooleanValue ATP_TONES = BUILDER
            .comment("Play recorded ATP announcements while the cab displays are active. Default: true")
            .define("atp.tones", true);

    public static final ModConfigSpec.DoubleValue ATP_VOLUME = BUILDER
            .comment("ATP announcement volume in the Blocks sound category. Default: 1.0")
            .defineInRange("atp.volume", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue SIMURAIL_BRAKE_MULTIPLIER=BUILDER
            .comment("Multiply actual Simurail axle brake force only for bogeys registered to an EMU Cab.",
                    "0.25 = 25% of original braking; 1.0 restores original braking. Traction and control signals are unchanged.",
                    "Applies to service, emergency and holding/parking brake forces on registered bogeys. Server setting.")
            .defineInRange("simurail.registered_bogey_brake_multiplier",.25,.01,1.0);

    public static final ModConfigSpec.DoubleValue SIMURAIL_COASTING_DRAG_MULTIPLIER=BUILDER
            .comment("Horizontal Sable universal-drag multiplier while registered physics cars coast.",
                    "0.25 keeps 25% of the original drag; 1.0 restores Sable drag; 0 removes this drag only.",
                    "Only bodies containing a live cab-registered bogey are affected, once per body.",
                    "Any enabled bogey on that body requesting traction or braking disables the adjustment.",
                    "Does not change mass, vertical/angular damping, brake forces or track/contact resistance. Server setting.")
            .defineInRange("simurail.registered_coasting_drag_multiplier",.25,0,1.0);

    public static final ModConfigSpec.BooleanValue BOGEY_RELEASE_SOUNDS=BUILDER
            .comment("Play brake-release recordings on a stable applied-to-released brake input edge.",
                    "Only bogeys explicitly registered to this mod's cabs are affected. Server setting.")
            .define("simurail.brake_release_sounds",true);
    public static final ModConfigSpec.DoubleValue BOGEY_RELEASE_VOLUME=BUILDER
            .comment("Brake-release recording volume in the Blocks sound category.")
            .defineInRange("simurail.brake_release_volume",.85,0,1);
    public static final ModConfigSpec.DoubleValue BOGEY_RELEASE_RADIUS=BUILDER
            .comment("Audible brake-release radius in world blocks; follows moving Sable bogeys. Distance attenuation remains active.")
            .defineInRange("simurail.brake_release_radius",64.0,64.0,512.0);
    public static final ModConfigSpec.DoubleValue BOGEY_RELEASE_COOLDOWN=BUILDER
            .comment("Minimum seconds between brake-release recordings on the same registered bogey.",
                    "At least 5 seconds lets the 4.481-second recording finish; repeated release edges are discarded, not queued.")
            .defineInRange("simurail.brake_release_cooldown_seconds",5.0,5.0,300.0);

    public static final ModConfigSpec.BooleanValue TRACTION_ENABLED = BUILDER
            .comment(
                    "Let the master controller actually push the Sable physics train.",
                    "Impulses are applied per physics tick with force * timestep, so the acceleration",
                    "depends on the real mass of the train (a light test train will accelerate a lot).",
                    "Default: false - enable once you have checked the forces are sane for your pack.")
            .define("traction.enabled", false);

    public static final ModConfigSpec.DoubleValue TRACTION_FORCE_PER_NOTCH = BUILDER
            .comment("Traction force per notch (P1..P7) in newtons. Default: 30000")
            .defineInRange("traction.traction_force_per_notch", 30000.0, 0.0, 5000000.0);

    public static final ModConfigSpec.DoubleValue BRAKE_FORCE_PER_NOTCH = BUILDER
            .comment("Brake force per notch (B1..B7) in newtons, applied against the current velocity. Default: 40000")
            .defineInRange("traction.brake_force_per_notch", 40000.0, 0.0, 5000000.0);

    public static final ModConfigSpec.DoubleValue EMERGENCY_FORCE = BUILDER
            .comment("Emergency brake (EB) force in newtons, applied against the current velocity. Default: 350000")
            .defineInRange("traction.emergency_force", 350000.0, 0.0, 10000000.0);

    public static final ModConfigSpec.BooleanValue TRACK_LIMITS=BUILDER
            .comment("Read this mod's directional speed signs on Create tracks for Sable cabs. No Tramways dependency.")
            .define("track.enabled",true);
    public static final ModConfigSpec.DoubleValue TRACK_LOOKAHEAD=BUILDER
            .comment("Maximum forward track scan distance in blocks. Only loaded signs, no forced chunks.")
            .defineInRange("track.lookahead",1024.0,16.0,8192.0);
    public static final ModConfigSpec.DoubleValue TRACK_WARNING_DECEL=BUILDER
            .comment("Assumed deceleration m/s^2 for advance audio and the advisory speed-distance curve; does not apply braking.")
            .defineInRange("track.warning_deceleration",.7,.1,3.0);
    public static final ModConfigSpec.DoubleValue TRACK_LATERAL=BUILDER
            .comment("Maximum cab offset from track centre in blocks. Keep below half parallel-track spacing.")
            .defineInRange("track.lateral_tolerance",1.4,.3,8.0);
    public static final ModConfigSpec.DoubleValue TRACK_BELOW=BUILDER
            .comment("Maximum distance from cab block centre down to rail in blocks.")
            .defineInRange("track.rail_below_cab",8.0,1.0,32.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CabConfig() {
    }
}
