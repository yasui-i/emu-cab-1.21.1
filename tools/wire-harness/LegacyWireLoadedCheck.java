package emucabtest;

import com.yasui_i.emucab.block.*;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.compat.CabWireIntegration;
import com.yasui_i.emucab.control.*;
import com.yasui_i.emucab.registry.ModBlocks;
import edn.stratodonut.drivebywire.wire.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;

final class LegacyWireLoadedCheck {
    static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    static void start(CabControlState s) {
        for(var a:new CabAction[]{CabAction.MASTER,CabAction.PANTOGRAPH,CabAction.BREAKER})
            check(s.press(a.channel(),a,0,false,false)==null,"Start "+a);
        check(s.press("direction",CabAction.DIRECTION,0,false,false)==null,"Direction");
    }
    static void run(ServerLevel level) {
        check(ModBlocks.CAB_DESK.get() instanceof MultiChannelWireSource,"Multichannel source recognition");
        var source=(MultiChannelWireSource)ModBlocks.CAB_DESK.get();
        check(source.wire$getChannels().size()==27,"27 channels");
        var manager=WireNetworkManager.get(level);
        BlockPos sink=new BlockPos(16,80,16);
        level.setBlock(sink,Blocks.STONE.defaultBlockState(),3);
        for(Direction facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
            BlockPos center=new BlockPos(8,80,8);
            for(int offset=-1;offset<=1;offset++)level.setBlock(center.relative(facing.getClockWise(),offset),
                    ModBlocks.CAB_DESK.get().defaultBlockState().setValue(CabDeskBlock.FACING,facing)
                    .setValue(CabDeskBlock.PART,offset<0?CabDeskPart.LEFT:offset>0?CabDeskPart.RIGHT:CabDeskPart.CENTER),3);
            var desk=(CabDeskBlockEntity)level.getBlockEntity(center);
            check(!desk.controls().displaysActive() && !desk.controls().ready(),"New desk cold");
            check(desk.controls().direction==0 && !desk.controls().on(CabAction.PANTOGRAPH)
                    && !desk.controls().on(CabAction.BREAKER) && !desk.controls().on(CabAction.MASTER),"Driver switches default off");
            start(desk.controls());
            for(int offset=-1;offset<=1;offset++) {
                BlockPos pos=center.relative(facing.getClockWise(),offset);
                desk.controls().setHandle(4,false);
                check(manager.addConnection(level,pos,sink,Direction.UP,"emucab.wire.traction").isSuccess(),"Connect "+facing+offset);
                CabWireIntegration.tick(desk);
                check(manager.getSignalAt(sink,Direction.UP)==9,"New wire reads held P4");
                desk.controls().setHandle(-3,false);CabWireIntegration.tick(desk);
                check(manager.getSignalAt(sink,Direction.UP)==0,"Traction reset");
                manager.addConnection(level,pos,sink,Direction.DOWN,"emucab.wire.brake");CabWireIntegration.tick(desk);
                check(manager.getSignalAt(sink,Direction.DOWN)==6,"B3 analog");
                manager.addConnection(level,pos,sink,Direction.EAST,"emucab.wire.button.B034");
                var horn=Arrays.stream(CabGeometry.CONTROLS).filter(c->c.number().equals("B034")).findFirst().orElseThrow();
                desk.controls().press(horn.id(),horn.action(),0,false,false);CabWireIntegration.tick(desk);
                check(manager.getSignalAt(sink,Direction.EAST)==15,"Horn pulse");
                for(int t=0;t<8;t++)desk.controls().tick();CabWireIntegration.tick(desk);
                check(manager.getSignalAt(sink,Direction.EAST)==0,"Horn pulse expires");
                CabWireIntegration.unload(desk);
                check(manager.getSignalAt(sink,Direction.DOWN)==0,"Unload clears");
                CabWireIntegration.tick(desk);
                check(manager.getSignalAt(sink,Direction.DOWN)==6,"Reload restores");
                var saved=manager.save(new net.minecraft.nbt.CompoundTag());manager.load(saved);
                CabWireIntegration.tick(desk);
                check(manager.getSignalAt(sink,Direction.DOWN)==6,"Saved network restores current state");
                for(String channel:List.of("emucab.wire.traction","emucab.wire.brake","emucab.wire.button.B034")) {
                    Direction face=channel.endsWith("traction")?Direction.UP:channel.endsWith("brake")?Direction.DOWN:Direction.EAST;
                    manager.removeConnectionInternal(level,pos,sink,face,channel);
                }
            }
            manager.addConnection(level,center,sink,Direction.UP,"emucab.wire.ready");CabWireIntegration.tick(desk);
            check(manager.getSignalAt(sink,Direction.UP)==15,"Ready output");
            level.removeBlock(center,false);NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(()->true,level));
            check(manager.getSignalAt(sink,Direction.UP)==0,"Removal resets output");
            check(manager.getNetwork().containsKey(center.asLong()),"Removal preserves wires");
            manager.removeConnectionInternal(level,center,sink,Direction.UP,"emucab.wire.ready");
        }
        // Assemble a wired east-facing desk into an actual Sable sublevel.
        BlockPos oldCenter=new BlockPos(8,80,8),oldSink=oldCenter.east(3);
        var moved=List.of(oldCenter,oldCenter.north(),oldCenter.south(),oldSink);
        level.setBlock(oldCenter,ModBlocks.CAB_DESK.get().defaultBlockState().setValue(CabDeskBlock.FACING,Direction.EAST),3);
        level.setBlock(oldCenter.south(),ModBlocks.CAB_DESK.get().defaultBlockState().setValue(CabDeskBlock.FACING,Direction.EAST)
                .setValue(CabDeskBlock.PART,CabDeskPart.RIGHT),3);
        level.setBlock(oldCenter.north(),ModBlocks.CAB_DESK.get().defaultBlockState().setValue(CabDeskBlock.FACING,Direction.EAST)
                .setValue(CabDeskBlock.PART,CabDeskPart.LEFT),3);
        level.setBlock(oldSink,Blocks.STONE.defaultBlockState(),3);
        var oldDesk=(CabDeskBlockEntity)level.getBlockEntity(oldCenter);
        start(oldDesk.controls());
        oldDesk.controls().setHandle(5,false);
        manager.addConnection(level,oldCenter,oldSink,Direction.UP,"emucab.wire.traction");CabWireIntegration.tick(oldDesk);
        check(manager.getSignalAt(oldSink,Direction.UP)==11,"Before assembly");
        var sub=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level,oldCenter,moved,
                dev.ryanhcode.sable.companion.math.BoundingBox3i.from(moved));
        BlockPos newCenter=sub.getPlot().getCenterBlock();
        BlockPos newSink=newCenter.offset(oldSink.subtract(oldCenter));
        NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(()->true,level));
        check(manager.hasSinksOnChannel(newCenter,"emucab.wire.traction"),"Assembly preserves moved source");
        check(!manager.getNetwork().containsKey(oldCenter.asLong()),"Assembly removes old source");
        var newDesk=(CabDeskBlockEntity)level.getBlockEntity(newCenter);
        check(newDesk!=null,"Assembled desk exists");
        CabWireIntegration.tick(newDesk);
        check(manager.getSignalAt(newSink,Direction.UP)==11,"Assembled P5 output");
        var firstAudio=com.yasui_i.emucab.network.CabAtpSoundPayload.worldPosition(level,newCenter);
        sub.logicalPose().position().add(25,3,-14);
        sub.logicalPose().orientation().rotateYXZ(.7,.1,-.2);
        var movedAudio=com.yasui_i.emucab.network.CabAtpSoundPayload.worldPosition(level,newCenter);
        check(firstAudio.distanceTo(movedAudio)>10,"Audio follows physical source movement");
        check(movedAudio.distanceTo(sub.logicalPose().transformPosition(newCenter.getCenter()))<.00001,"Audio follows current body pose");
        checkButtons(level,newDesk,sub);
        newDesk.controls().setHandle(7,false);CabWireIntegration.tick(newDesk);
        check(manager.getSignalAt(newSink,Direction.UP)==15,"Moved/rotated Sable body P7 output");
        newDesk.controls().setHandle(-8,false);CabWireIntegration.tick(newDesk);
        check(manager.getSignalAt(newSink,Direction.UP)==0,"Sable EB clears traction");
        level.removeBlock(newCenter,false);NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(()->true,level));
        check(manager.hasSinksOnChannel(newCenter,"emucab.wire.traction"),"Sable desk removal preserves wires");
        System.out.println("EMU_WIRE_SABLE: assembly remapping, pose changes and removal passed");
        System.out.println("EMU_ATP_POSITION: current physical body pose updates sound source passed");
    }
    static void checkButtons(ServerLevel level,CabDeskBlockEntity desk,dev.ryanhcode.sable.sublevel.ServerSubLevel sub) {
        var center=desk.getBlockPos();var state=desk.controls();
        state.setHandle(0,false);
        // Real vanilla iron door as the DriveByWire receiver.
        BlockPos door=center.south(4);
        level.setBlock(door.below(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(door,Blocks.IRON_DOOR.defaultBlockState(),3);
        level.setBlock(door.above(),Blocks.IRON_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),3);
        var manager=WireNetworkManager.get(level);
        manager.addConnection(level,center,door,Direction.WEST,"emucab.wire.door_left");
        state.press("release",CabAction.DOOR_RELEASE_LEFT,0,false,false);
        state.press("open",CabAction.DOOR_OPEN_LEFT,0,false,false);CabWireIntegration.tick(desk);
        check(level.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),"Vanilla door opens");
        for(int i=0;i<100;i++)state.tick();CabWireIntegration.tick(desk);
        check(manager.getSignalAt(door,Direction.WEST)==15 && level.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),"Door held open after pulse duration");
        var saved=desk.getUpdateTag(level.registryAccess());
        desk.loadWithComponents(saved,level.registryAccess());CabWireIntegration.tick(desk);
        check(state.signals().get("door_left")==15,"Saved open command remains latched");
        state.press("close",CabAction.DOOR_CLOSE_LEFT,0,false,false);CabWireIntegration.tick(desk);
        check(manager.getSignalAt(door,Direction.WEST)==0 && !level.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),"Close button removes power and shuts vanilla door");
        // Validate actual server payload processing against the tilted/moved body.
        var button=Arrays.stream(CabGeometry.CONTROLS).filter(c->c.number().equals("B018")).findFirst().orElseThrow();
        var target=CabHitTesting.bounds(button,0).getCenter();
        var eye=target.add(0,2,-2);
        var facing=desk.getBlockState().getValue(CabDeskBlock.FACING);
        var worldEye=sub.logicalPose().transformPosition(CabHitTesting.fromModel(center,facing,eye));
        var worldTarget=sub.logicalPose().transformPosition(CabHitTesting.fromModel(center,facing,target));
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"cab-button-test"));
        player.setPos(worldEye.x,worldEye.y-player.getEyeHeight(),worldEye.z);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,worldTarget);
        var packet=new com.yasui_i.emucab.network.CabButtonPayload(center,button.number(),eye,target);
        try {
            var handle=packet.getClass().getDeclaredMethod("handle",net.minecraft.server.level.ServerPlayer.class);
            handle.setAccessible(true);handle.invoke(packet,player);
            check(state.on(CabAction.SUNSHADE),"Moving body button packet activates");
            handle.invoke(packet,player);
            check(state.on(CabAction.SUNSHADE),"Duplicate packet does not toggle twice");
        } catch(ReflectiveOperationException e) { throw new RuntimeException(e); }
        System.out.println("EMU_BUTTONS: moving-body server packet, duplicate rejection, cold placement, saved door signal and vanilla iron door passed");
    }
}
