package com.yasui_i.emucab.client;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = EMUCab.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) { event.enqueueWork(BaliseClient::bind); }
    @SubscribeEvent public static void additionalModels(ModelEvent.RegisterAdditional event) { event.register(BaliseClient.MODEL); }
    @SubscribeEvent public static void modelsReloaded(ModelEvent.BakingCompleted event) { CabDeskRenderer.clearMesh(); }
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        CabDeskRenderer.clearMesh();
        event.registerBlockEntityRenderer(ModBlockEntities.CAB_DESK.get(), CabDeskRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SPEED_SIGN.get(),SpeedSignRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GRADUAL_THROTTLE.get(),GradualThrottleRenderer::new);
    }

    private ClientSetup() {
    }
}
