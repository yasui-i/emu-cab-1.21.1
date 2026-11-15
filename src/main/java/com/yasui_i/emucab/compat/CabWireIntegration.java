package com.yasui_i.emucab.compat;

import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.fml.ModList;

/** Isolate optional APIs; prefer DriveBySable when both providers are installed. */
public final class CabWireIntegration {
    private static boolean sable() { return ModList.get().isLoaded("drivebysable"); }
    private static boolean wire() { return ModList.get().isLoaded("drivebywire"); }
    public static void init() {
        if(!sable()&&wire())DriveByWireCompat.init();
    }
    public static CabDeskBlock createBlock(BlockBehaviour.Properties properties) {
        if(sable())return DriveBySableCompat.createBlock(properties);
        return wire()?DriveByWireCompat.createBlock(properties):new CabDeskBlock(properties);
    }
    public static void tick(CabDeskBlockEntity desk) {
        tick(desk,desk.getCabSignals());
    }
    public static void tick(CabDeskBlockEntity desk,java.util.Map<String,Integer> signals) {
        if(sable())DriveBySableCompat.tick(desk,signals);
        else if(wire())DriveByWireCompat.tick(desk,signals);
    }
    public static void unload(CabDeskBlockEntity desk) {
        if(sable())DriveBySableCompat.unload(desk);
        else if(wire())DriveByWireCompat.unload(desk);
    }
    public static void removed(Level level,BlockPos pos) {
        if(sable())DriveBySableCompat.removed(level,pos);
        else if(wire())DriveByWireCompat.removed(level,pos);
    }
    private CabWireIntegration() {}
}
