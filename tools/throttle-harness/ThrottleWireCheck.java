package emucabtest;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.yasui_i.emucab.block.GradualThrottleBlock;
import com.yasui_i.emucab.blockentity.GradualThrottleBlockEntity;
import com.yasui_i.emucab.registry.ModBlocks;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.List;

/** Exercise DriveBySable's actual getSignal mixin and Sable's assembly remapping. */
final class ThrottleWireCheck {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void run(ServerLevel level) {
        BlockPos pos=new BlockPos(8,95,8),source=pos.south(2),motorPos=pos.west(),shaftPos=pos.east();
        for(BlockPos clear:BlockPos.betweenClosed(pos.offset(-3,-3,-3),pos.offset(3,3,3)))level.removeBlock(clear,false);
        var motorBlock=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","creative_motor"));
        var shaftBlock=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","shaft"));
        level.setBlockAndUpdate(motorPos,motorBlock.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));
        level.setBlockAndUpdate(pos,ModBlocks.GRADUAL_THROTTLE.get().defaultBlockState().setValue(GradualThrottleBlock.FACING,Direction.EAST));
        level.setBlockAndUpdate(shaftPos,shaftBlock.defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        level.setBlockAndUpdate(source,Blocks.STONE.defaultBlockState());
        var motor=(CreativeMotorBlockEntity)level.getBlockEntity(motorPos);
        var throttle=(GradualThrottleBlockEntity)level.getBlockEntity(pos);
        var shaft=(KineticBlockEntity)level.getBlockEntity(shaftPos);
        motor.initialize();throttle.initialize();shaft.initialize();
        motor.generatedSpeed.setValue(64);motor.updateGeneratedRotation();
        throttle.configure(1);motor.tick();throttle.tick();shaft.tick();
        var manager=CableNetworkManager.get(level);
        for(Direction face:Direction.values()) {
            if(face.getAxis()==Direction.Axis.X)continue;
            check(manager.addConnection(level,source,pos,face,"test.throttle").isSuccess(),"DriveBySable connect "+face);
            for(int value:new int[]{1,8,15,0}) {
                throttle.configure(value==0?200:1);
                manager.setSource(level,source,"test.throttle",value);
                for(int i=0;i<(value==0?1:260);i++){throttle.tick();shaft.tick();}
                check(throttle.outputRpm()==value*17,"DriveBySable RPM target "+face+" value="+value+" actual="+throttle.outputRpm());
                check(Math.abs(Math.abs(shaft.getSpeed())-value*17)<.01f,"DriveBySable shaft output "+face+" value="+value);
            }
            manager.removeConnectionInternal(level,source,pos,face,"test.throttle");
        }
        throttle.configure(1);
        manager.addConnection(level,source,pos,Direction.UP,"test.throttle");
        manager.setSource(level,source,"test.throttle",8);
        for(int i=0;i<140;i++){throttle.tick();shaft.tick();}
        var moved=List.of(pos,source,motorPos,shaftPos);
        var sub=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level,pos,moved,
                dev.ryanhcode.sable.companion.math.BoundingBox3i.from(moved));
        manager.flushPendingGraphRebuild(level);
        BlockPos newPos=sub.getPlot().getCenterBlock();
        BlockPos newMotorPos=newPos.offset(motorPos.subtract(pos));
        BlockPos newShaftPos=newPos.offset(shaftPos.subtract(pos));
        BlockPos newSource=newPos.offset(source.subtract(pos));
        throttle=(GradualThrottleBlockEntity)level.getBlockEntity(newPos);
        motor=(CreativeMotorBlockEntity)level.getBlockEntity(newMotorPos);
        shaft=(KineticBlockEntity)level.getBlockEntity(newShaftPos);
        check(throttle!=null&&motor!=null&&shaft!=null,"Assembled kinetic blocks exist");
        motor.initialize();throttle.initialize();shaft.initialize();
        motor.updateGeneratedRotation();
        for(int i=0;i<16;i++){motor.tick();throttle.tick();shaft.tick();}
        check(manager.getSignalAt(newPos,Direction.UP)==8,"DriveBySable assembly signal preserved");
        check(Math.abs(Math.abs(shaft.getSpeed())-136)<.01f,"Sable assembled output "+shaft.getSpeed());
        sub.logicalPose().position().add(25,3,-14);
        sub.logicalPose().orientation().rotateYXZ(.7,.1,-.2);
        manager.setSource(level,newSource,"test.throttle",15);
        for(int i=0;i<140;i++){motor.tick();throttle.tick();shaft.tick();}
        check(Math.abs(Math.abs(shaft.getSpeed())-255)<.01f,"Moving/rotated Sable body output "+shaft.getSpeed());
        throttle.configure(200);
        manager.setSource(level,newSource,"test.throttle",0);
        throttle.tick();shaft.tick();
        check(throttle.outputRpm()==0&&Math.abs(shaft.getSpeed())<.01f,"Moving Sable body zero output did not clear in one tick");
        manager.setSource(level,newSource,"test.throttle",15);
        for(int i=0;i<199;i++){throttle.tick();shaft.tick();}
        check(throttle.outputRpm()==0&&Math.abs(shaft.getSpeed())<.01f,"Moving Sable restart bypassed interval");
        throttle.tick();shaft.tick();
        check(throttle.outputRpm()==1&&Math.abs(Math.abs(shaft.getSpeed())-1)<.01f,"Moving Sable restart did not ramp from zero");
        manager.removeConnectionInternal(level,newSource,newPos,Direction.UP,"test.throttle");
        System.out.println("EMU_THROTTLE_WIRE_SABLE: four casing faces, 1/8/15/0 strength, assembly and changed body pose passed");
    }
}
