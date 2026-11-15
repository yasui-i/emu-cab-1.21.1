package emucabtest;

import com.yasui_i.emucab.block.*;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.compat.CabWireIntegration;
import com.yasui_i.emucab.control.*;
import com.yasui_i.emucab.registry.ModBlocks;
import edn.lakeopossmc.drivebysable.cable.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Focused checks against the real DriveBySable manager, including its cached legacy channels. */
final class ParkingWireCheck {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void press(CabDeskBlockEntity desk,CabAction action){
        var c=Arrays.stream(CabGeometry.CONTROLS).filter(v->v.action()==action).findFirst().orElseThrow();
        check(desk.controls().press(c.id(),action,c.argument(),false,false)==null,"Press "+action);
        CabWireIntegration.tick(desk);
    }
    static void run(ServerLevel level){
        var source=(MultiChannelCableSource)ModBlocks.CAB_DESK.get();
        check(source.cable$getChannels(level,new BlockPos(8,80,8)).size()==27,"27 selectable channels");
        check(!source.cable$getChannels(level,new BlockPos(8,80,8)).contains("emucab.wire.button.B022")&&!source.cable$getChannels(level,new BlockPos(8,80,8)).contains("emucab.wire.button.B023"),"No separate parking button choices");
        var manager=CableNetworkManager.get(level);
        for(Direction facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}){
            BlockPos center=new BlockPos(8,80,8),sink=new BlockPos(16,80,16);
            var state=ModBlocks.CAB_DESK.get().defaultBlockState().setValue(CabDeskBlock.FACING,facing);
            level.setBlockAndUpdate(center,state);
            level.setBlockAndUpdate(center.relative(facing.getCounterClockWise()),state.setValue(CabDeskBlock.PART,CabDeskPart.LEFT));
            level.setBlockAndUpdate(center.relative(facing.getClockWise()),state.setValue(CabDeskBlock.PART,CabDeskPart.RIGHT));
            level.setBlockAndUpdate(sink,Blocks.STONE.defaultBlockState());
            var desk=(CabDeskBlockEntity)level.getBlockEntity(center);
            List<String> channels=List.of("emucab.wire.parking_brake","emucab.wire.direction_forward");
            List<Direction> faces=List.of(Direction.UP,Direction.WEST);
            for(int i=0;i<channels.size();i++)check(manager.addConnection(level,center,sink,faces.get(i),channels.get(i)).isSuccess(),"Connect "+channels.get(i));
            press(desk,CabAction.MASTER);press(desk,CabAction.DIRECTION);press(desk,CabAction.PARKING_APPLY);
            for(int tick=0;tick<200;tick++)desk.controls().tick();CabWireIntegration.tick(desk);
            for(Direction face:faces)check(manager.getSignalAt(sink,face)==15,"Applied steady output "+face);
            press(desk,CabAction.MASTER);
            for(int tick=0;tick<200;tick++)desk.controls().tick();CabWireIntegration.tick(desk);
            for(int i=0;i<1;i++)check(manager.getSignalAt(sink,faces.get(i))==15,"Key-off parking remains "+channels.get(i));
            check(manager.getSignalAt(sink,Direction.WEST)==0,"Key-off direction clears");
            var saved=desk.getUpdateTag(level.registryAccess());
            CabWireIntegration.unload(desk);
            check(manager.getSignalAt(sink,Direction.UP)==0,"Unload clears source");
            desk.controls().parkingBrake=false;
            desk.loadWithComponents(saved,level.registryAccess());CabWireIntegration.tick(desk);
            check(desk.controls().parkingBrake&&!desk.controls().on(CabAction.MASTER),"Saved parking and removed key restored");
            for(int i=0;i<1;i++)check(manager.getSignalAt(sink,faces.get(i))==15,"Reload restores parking");
            press(desk,CabAction.PARKING_RELEASE);
            for(Direction face:faces)check(manager.getSignalAt(sink,face)==0,"Release while key off clears "+face);
            for(int i=0;i<channels.size();i++)manager.removeConnectionInternal(level,center,sink,faces.get(i),channels.get(i));
            for(int i=-1;i<=1;i++)level.removeBlock(center.relative(facing.getClockWise(),i),false);
        }
        System.out.println("PARKING_WIRE_CHECK: PASS four facings, 27 channels, held output, key-off isolation, NBT reload,  key-off release");
    }
}
