package emucabelectricaltest;

import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.block.CabDeskPart;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.*;
import com.yasui_i.emucab.registry.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;

/** Real-server checks exercise the production cab registry and NBT lifecycle. */
public final class CabLinkageChecks {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static CabDeskBlockEntity place(ServerLevel level,BlockPos pos){
        var state=ModBlocks.CAB_DESK.get().defaultBlockState().setValue(CabDeskBlock.FACING,Direction.NORTH);
        level.setBlockAndUpdate(pos.west(),state.setValue(CabDeskBlock.PART,CabDeskPart.LEFT));
        level.setBlockAndUpdate(pos.east(),state.setValue(CabDeskBlock.PART,CabDeskPart.RIGHT));
        level.setBlockAndUpdate(pos,state);
        var desk=(CabDeskBlockEntity)level.getBlockEntity(pos);CabEndpointRegistry.register(desk);return desk;
    }
    private static void claim(CabDeskBlockEntity desk){desk.controls().switches.put(CabAction.MASTER,1);CabEndpointRegistry.activate(desk);}
    public static void run(ServerLevel level){
        var a=place(level,new BlockPos(0,96,0));var b=place(level,new BlockPos(0,96,20));
        var unrelated=place(level,new BlockPos(0,96,40));
        a.setConsistEndpoint("link-test",1);b.setConsistEndpoint("link-test",8);unrelated.setConsistEndpoint("other-test",8);
        claim(b);claim(unrelated);
        b.controls().parkingBrake=true;b.controls().direction=1;b.controls().notch=7;
        b.controls().switches.put(CabAction.PANTOGRAPH,1);b.controls().switches.put(CabAction.BREAKER,1);
        b.controls().press("horn",CabAction.HORN,0,false,false);
        claim(a);
        check(a.controls().on(CabAction.MASTER)&&!b.controls().on(CabAction.MASTER),"01 insertion must eject 08 immediately");
        check(b.controls().notch==0&&b.controls().direction==0&&!b.controls().on(CabAction.PANTOGRAPH)&&!b.controls().on(CabAction.BREAKER),"Old cab returned to safe detents");
        check(b.controls().signals().get("parking_brake")==15,"Takeover preserves parking brake");
        check(b.controls().signals().entrySet().stream().allMatch(e->e.getKey().equals("parking_brake")||e.getValue()==0),"All old-cab outputs cleared");
        check(unrelated.controls().on(CabAction.MASTER),"Independent consist isolated");
        claim(b);check(!a.controls().on(CabAction.MASTER)&&b.controls().on(CabAction.MASTER),"08 insertion ejects 01");
        CabEndpointRegistry.unregister(a);a.controls().switches.put(CabAction.MASTER,1);
        CabEndpointRegistry.reconcile(a);check(!a.controls().on(CabAction.MASTER),"Reloaded old owner cannot reclaim key");
        CabEndpointRegistry.release(b);b.forceTakeoverRelease();a.controls().switches.put(CabAction.MASTER,1);
        CabEndpointRegistry.reconcile(a);check(!a.controls().on(CabAction.MASTER),"Key removal must not revive a stale unloaded cab");
        var saved=a.getUpdateTag(level.registryAccess());
        var moved=place(level,new BlockPos(0,96,60));
        a.beginAssemblyMove();CabEndpointRegistry.unregister(a);moved.loadWithComponents(saved,level.registryAccess());CabEndpointRegistry.register(moved);
        check(moved.linkId().equals(a.linkId()),"Assembly relocation preserves cab ID");
        claim(moved);check(!b.controls().on(CabAction.MASTER),"Relocated cab can claim opposite end");
        var clone=place(level,new BlockPos(0,96,80));clone.loadWithComponents(moved.getUpdateTag(level.registryAccess()),level.registryAccess());CabEndpointRegistry.register(clone);
        check(!clone.linkId().equals(moved.linkId()),"Copied NBT gets a distinct live identity");
        clone.setConsistEndpoint("other-test",1);claim(clone);check(!unrelated.controls().on(CabAction.MASTER),"Reconfigured cab takes new group only");
        check(moved.controls().on(CabAction.MASTER),"Reconfiguration leaves former group owner active");
        moved.setConsistEndpoint("third-test",8);check(!moved.controls().on(CabAction.MASTER),"Changing linkage safely removes key");
        System.out.println("CAB_LINKAGE_CHECK: PASS 01/08 takeover, parking retention, unrelated isolation, unload/reload, assembly relocation, cloned identity, reconfiguration");
    }
}
