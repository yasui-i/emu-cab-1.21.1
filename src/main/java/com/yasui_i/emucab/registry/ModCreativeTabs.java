package com.yasui_i.emucab.registry;

import com.yasui_i.emucab.EMUCab;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EMUCab.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.emucab"))
                    .icon(() -> new ItemStack(ModItems.CAB_DESK.get()))
                    .displayItems((parameters, output) -> { output.accept(ModItems.CAB_DESK.get());output.accept(ModItems.SPEED_SIGN.get());output.accept(ModItems.BALISE.get());output.accept(ModItems.GRADUAL_THROTTLE.get());output.accept(ModItems.TELEMETRY_TOOL.get());com.yasui_i.emucab.motor.VvvfMotorIntegration.addCreativeItem(output); })
                    .build());

    private ModCreativeTabs() {
    }
}
