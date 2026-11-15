package com.yasui_i.emucab.balise;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Client callbacks are installed only on a client, keeping servers class-load safe. */
public final class BaliseClientAccess {
    private static BiConsumer<ResourceLocation,List<BaliseData>> sync=(dimension,devices)->{};
    private static Consumer<BaliseData> open=device->{};
    private BaliseClientAccess(){}
    public static void bind(BiConsumer<ResourceLocation,List<BaliseData>> onSync,Consumer<BaliseData> onOpen){sync=onSync;open=onOpen;}
    public static void sync(ResourceLocation dimension,List<BaliseData> devices){sync.accept(dimension,devices);}
    public static void open(BaliseData device){open.accept(device);}
}
