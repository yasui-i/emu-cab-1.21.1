package emucabtest;

import com.yasui_i.emucab.registry.ModBlocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.core.BlockPos;
import java.nio.file.*;

@Mod("emucabwiretest")
public class WireHarness {
    public WireHarness() { NeoForge.EVENT_BUS.addListener(this::started); }
    private void started(ServerStartedEvent e) {
        String result;
        try {
            if(Boolean.getBoolean("emucab.test.parkingWire"))ParkingWireCheck.run(e.getServer().overworld());
            else if(ModList.get().isLoaded("drivebysable")){
                WireLoadedCheck.run(e.getServer().overworld());
                ParkingWireCheck.run(e.getServer().overworld());
                if(ModList.get().isLoaded("drivebywire")){
                    if(!ModBlocks.CAB_DESK.get().getClass().getSimpleName().equals("SableWiredCabDeskBlock"))throw new AssertionError("DriveBySable priority");
                    if(!edn.stratodonut.drivebywire.wire.WireNetworkManager.get(e.getServer().overworld()).getNetwork().isEmpty())throw new AssertionError("Duplicated legacy outputs");
                }
            }
            else if(ModList.get().isLoaded("drivebywire")){
                LegacyWireLoadedCheck.run(e.getServer().overworld());
                LegacyParkingWireCheck.run(e.getServer().overworld());
            }
            else {
                var level=e.getServer().overworld();
                var pos=new BlockPos(8,80,8);
                level.setBlock(pos,ModBlocks.CAB_DESK.get().defaultBlockState(),3);
                ((com.yasui_i.emucab.blockentity.CabDeskBlockEntity)level.getBlockEntity(pos)).serverTick();
                if(!ModBlocks.CAB_DESK.get().getClass().getSimpleName().equals("CabDeskBlock"))throw new AssertionError("Optional block");
                level.removeBlock(pos,false);
            }
            result="PASS "+(ModList.get().isLoaded("drivebysable")?"DriveBySable loaded integration":"DriveByWire integration");
        } catch(Throwable t) { t.printStackTrace();result="FAIL "+t; }
        System.out.println("EMU_WIRE_CHECK: "+result);
        try { Files.writeString(Path.of("wire-check-result.txt"),result); } catch(Exception ex) { throw new RuntimeException(ex); }
        e.getServer().halt(false);
    }
}
