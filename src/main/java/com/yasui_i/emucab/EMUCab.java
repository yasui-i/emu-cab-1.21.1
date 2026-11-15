package com.yasui_i.emucab;

import com.yasui_i.emucab.config.CabConfig;
import com.yasui_i.emucab.registry.ModBlockEntities;
import com.yasui_i.emucab.registry.ModBlocks;
import com.yasui_i.emucab.registry.ModCreativeTabs;
import com.yasui_i.emucab.registry.ModItems;
import com.yasui_i.emucab.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EMU Cab - a CR400 series (Fuxing) driver's desk for Create/Sable physics trains.
 */
@Mod(EMUCab.MOD_ID)
public class EMUCab {
    public static final String MOD_ID = "emucab";
    public static final Logger LOGGER = LoggerFactory.getLogger("EMU Cab");

    public EMUCab(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        com.yasui_i.emucab.motor.VvvfMotorIntegration.register(modEventBus, modContainer);
        modEventBus.addListener(com.yasui_i.emucab.config.ExteriorAudioConfigMigration::onLoading);
        modContainer.registerConfig(ModConfig.Type.CLIENT, com.yasui_i.emucab.config.BogeyAudioConfig.SPEC, "emucab-bogey-audio.toml");
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.yasui_i.emucab.control.CabAtpCommands::register);
        modEventBus.addListener(com.yasui_i.emucab.network.CabHandlePayload::register);
        modEventBus.addListener(com.yasui_i.emucab.network.CabButtonPayload::register);
        modEventBus.addListener(com.yasui_i.emucab.network.CabControlAnimationPayload::register);
        modEventBus.addListener(com.yasui_i.emucab.network.SpeedSignPayload::register);
        modEventBus.addListener(com.yasui_i.emucab.balise.BalisePackets::register);
        modEventBus.addListener(com.yasui_i.emucab.network.GradualThrottlePayload::register);
        modEventBus.addListener(com.yasui_i.emucab.network.CabAtpSoundPayload::register);
        modEventBus.addListener(com.yasui_i.emucab.network.BogeyBrakeSoundPayload::register);
        modEventBus.addListener(com.yasui_i.emucab.network.BogeyMotorSoundPayload::register);
        modEventBus.addListener(com.yasui_i.emucab.network.CabSpeedPayload::register);
        modEventBus.addListener(com.yasui_i.emucab.network.CabElectricalPayload::register);
        modEventBus.addListener(com.yasui_i.emucab.network.CabPantographBindingPayload::register);
        modEventBus.addListener(com.yasui_i.emucab.network.CabDisplaySettingsPayload::register);
        modEventBus.addListener(com.yasui_i.emucab.network.CabMonPayload::register);
        modEventBus.addListener(com.yasui_i.emucab.network.CabTelemetryPayload::register);
        modEventBus.addListener(com.yasui_i.emucab.network.CabTelemetryBindingPayload::register);
        modEventBus.addListener(com.yasui_i.emucab.network.CabTelemetryBindingResultPayload::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e)->com.yasui_i.emucab.telemetry.BogeyForceSamples.clear());
        modContainer.registerConfig(ModConfig.Type.COMMON, CabConfig.SPEC);
        com.yasui_i.emucab.compat.CabWireIntegration.init();
        com.yasui_i.emucab.control.CeeElectricalTelemetry.init();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e)->com.yasui_i.emucab.control.CeeElectricalTelemetry.clear());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e)->com.yasui_i.emucab.control.CabEndpointRegistry.clear());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
