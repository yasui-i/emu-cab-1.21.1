package com.yasui_i.emucab.motor;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;

/** Keeps all CEE implementation types behind a presence check. */
public final class VvvfMotorIntegration {
    private static boolean registered;

    public static void register(IEventBus bus, ModContainer container) {
        if (!ModList.get().isLoaded("electroenergetics")) return;
        Adapter.register(bus, container);
        registered = true;
    }

    public static boolean available() { return registered; }

    public static void addCreativeItem(net.minecraft.world.item.CreativeModeTab.Output output) {
        if (registered) Adapter.addCreativeItem(output);
    }

    private static final class Adapter {
        static void register(IEventBus bus, ModContainer container) {
            // Preserve custom ramp/audio settings from the former P&W-owned block.
            var folder = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
            var legacy = folder.resolve("pwsablefix-motor.toml");
            var current = folder.resolve("emucab-motor.toml");
            if (!java.nio.file.Files.exists(current) && java.nio.file.Files.isRegularFile(legacy)) {
                try { java.nio.file.Files.copy(legacy, current); }
                catch (java.io.IOException error) {
                    com.yasui_i.emucab.EMUCab.LOGGER.warn("Could not migrate VVVF motor settings", error);
                }
            }
            container.registerConfig(ModConfig.Type.COMMON, VvvfMotorConfig.SPEC, "emucab-motor.toml");
            VvvfMotors.register(bus);
            if (FMLEnvironment.dist == Dist.CLIENT) VvvfMotorClient.register(bus);
        }

        static void addCreativeItem(net.minecraft.world.item.CreativeModeTab.Output output) {
            output.accept(VvvfMotors.ITEM.get());
        }
    }

    private VvvfMotorIntegration() {}
}
