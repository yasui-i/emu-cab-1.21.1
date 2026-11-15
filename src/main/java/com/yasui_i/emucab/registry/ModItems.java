package com.yasui_i.emucab.registry;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.block.CabDeskItem;
import com.yasui_i.emucab.balise.BaliseItem;
import com.yasui_i.emucab.balise.BaliseData;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, EMUCab.MOD_ID);

    public static final DeferredHolder<Item, CabDeskItem> CAB_DESK = ITEMS.register("cab_desk",
            () -> new CabDeskItem(ModBlocks.CAB_DESK.get(), new Item.Properties()));

    public static final DeferredHolder<Item,BaliseItem> SPEED_SIGN=ITEMS.register("speed_sign",
            ()->new BaliseItem(new Item.Properties(),BaliseData.Kind.LIMIT));
    public static final DeferredHolder<Item,BaliseItem> BALISE=ITEMS.register("balise",
            ()->new BaliseItem(new Item.Properties(),BaliseData.Kind.ANNOUNCEMENT));
    public static final DeferredHolder<Item,Item> GRADUAL_THROTTLE=ITEMS.register("gradual_throttle",
            ()->new net.minecraft.world.item.BlockItem(ModBlocks.GRADUAL_THROTTLE.get(),new Item.Properties()));
    public static final DeferredHolder<Item,Item> TELEMETRY_TOOL=ITEMS.register("telemetry_tool",()->new Item(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
