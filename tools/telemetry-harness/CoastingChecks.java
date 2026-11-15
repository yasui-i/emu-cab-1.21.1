package emucabtelemetrytest;

import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import com.crystaelix.simurail.content.bogey.PhysicsBogeyControlMode;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.config.CabConfig;
import com.yasui_i.emucab.registry.ModBlocks;
import com.yasui_i.emucab.telemetry.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3d;
import java.util.*;

/** Runs the real Rapier backend and production hook, not a mocked damping formula. */
final class CoastingChecks {
    static void check(boolean ok,String detail){if(!ok)throw new AssertionError(detail);}
    static PhysicsBogeyBlockEntity bogey(ServerLevel level,BlockPos pos){return (PhysicsBogeyBlockEntity)level.getBlockEntity(pos);}
    static UUID id(PhysicsBogeyBlockEntity be){return ((BogeyIdentity)be).emucab$identity();}
    static double speed(PhysicsPipeline pipeline,ServerSubLevel body){return pipeline.getLinearVelocity(body,new Vector3d()).x;}
    static void reset(PhysicsPipeline pipeline,ServerSubLevel body) {
        pipeline.teleport(body,new Vector3d(300,250,300),new org.joml.Quaterniond());
        MotorTelemetryChecks.setVelocity(RigidBodyHandle.of(body),new Vector3d(100,2,0),new Vector3d(0,.3,0));
    }
    static double coast(PhysicsPipeline pipeline,ServerSubLevel body,double multiplier,int steps,double dt) {
        CabConfig.SIMURAIL_COASTING_DRAG_MULTIPLIER.set(multiplier);reset(pipeline,body);
        for(int i=0;i<steps;i++)pipeline.physicsTick(dt);
        return speed(pipeline,body);
    }
    static void run(ServerLevel level)throws Exception {
        double saved=CabConfig.SIMURAIL_COASTING_DRAG_MULTIPLIER.get();
        check(saved==.25,"Coasting default not 25%");
        var origin=new BlockPos(200,240,200);
        var state=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("simurail:physics_bogey")).defaultBlockState();
        level.setBlockAndUpdate(origin,ModBlocks.CAB_DESK.get().defaultBlockState());
        level.setBlockAndUpdate(origin.east(4),state);level.setBlockAndUpdate(origin.east(6),state);
        var positions=List.of(origin,origin.east(4),origin.east(6));
        var body=SubLevelAssemblyHelper.assembleBlocks(level,origin,positions,BoundingBox3i.from(positions));
        var cabPos=body.getPlot().getCenterBlock();var cab=(CabDeskBlockEntity)level.getBlockEntity(cabPos);
        var first=bogey(level,cabPos.east(4));var second=bogey(level,cabPos.east(6));
        for(var be:List.of(first,second)) {be.getOptions().enabled=true;be.getOptions().controlMode=PhysicsBogeyControlMode.BRAKING;be.setSpeed(0);}
        cab.telemetry().bind(id(first),2);cab.telemetryChanged();
        var pipeline=SubLevelPhysicsSystem.require(level).getPipeline();
        double drag=dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getUniversalDrag(level);
        try {
            double original=coast(pipeline,body,1,20,.05);
            double expected=100/Math.pow(1+drag*.05,20);
            check(Math.abs(original-expected)<.003,"Actual native damping differs from expected law: "+original+" vs "+expected);
            var normalLinear=pipeline.getLinearVelocity(body,new Vector3d());
            var normalAngular=pipeline.getAngularVelocity(body,new Vector3d());
            double reduced=coast(pipeline,body,.25,20,.05);
            check(Math.abs(reduced-100/Math.pow(1+drag*.25*.05,20))<.003&&reduced>original+5,"25% drag did not extend coast: "+reduced);
            var reducedLinear=pipeline.getLinearVelocity(body,new Vector3d());
            var reducedAngular=pipeline.getAngularVelocity(body,new Vector3d());
            check(Math.abs(reducedLinear.y-normalLinear.y)<.003&&reducedAngular.distance(normalAngular)<.003,"Coasting changed gravity/vertical/angular damping");
            double substeps=coast(pipeline,body,.25,120,1.0/120);
            check(Math.abs(substeps-reduced)<.02,"Coasting depends materially on physics substeps");
            check(Math.abs(coast(pipeline,body,0,120,1.0/120)-100)<.003,"Zero universal drag created acceleration or retained damping");
            cab.telemetry().bind(id(second),2);cab.telemetryChanged();
            check(Math.abs(coast(pipeline,body,.25,20,.05)-reduced)<.003,"Two bogeys multiplied coast correction twice");
            var otherCabPos=new BlockPos(210,240,210);level.setBlockAndUpdate(otherCabPos,ModBlocks.CAB_DESK.get().defaultBlockState());
            var otherCab=(CabDeskBlockEntity)level.getBlockEntity(otherCabPos);otherCab.telemetry().bind(id(first),2);otherCab.telemetryChanged();
            check(Math.abs(coast(pipeline,body,.25,20,.05)-reduced)<.003,"Two cabs multiplied coast correction twice");
            cab.telemetry().clearCar(2);cab.telemetryChanged();
            check(Math.abs(coast(pipeline,body,.25,20,.05)-reduced)<.003,"Clearing one cab removed second cab policy");
            level.removeBlock(otherCabPos,false);
            check(Math.abs(coast(pipeline,body,.25,20,.05)-original)<.003,"Destroyed last cab kept coast override");
            cab.telemetry().bind(id(first),2);cab.telemetryChanged();
            // An unregistered bogey on the same body can still brake or drive it.
            level.setBlockAndUpdate(second.getBlockPos().above(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(second.getBlockPos().below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            check(second.getGroupBrakeStrength()>0,"Coasting test brake did not apply");
            check(Math.abs(coast(pipeline,body,.25,20,.05)-original)<.003,"Coasting adjustment remained active during actual brake input");
            level.removeBlock(second.getBlockPos().above(),false);level.removeBlock(second.getBlockPos().below(),false);
            check(second.getGroupBrakeStrength()==0,"Coasting test brake did not release");
            second.setSpeed(128);check(!second.isUnpowered(),"Coasting test drive bogey unpowered");
            check(Math.abs(coast(pipeline,body,.25,20,.05)-original)<.003,"Coasting adjustment active during traction request");
            second.setSpeed(0);
            check(Math.abs(coast(pipeline,body,.25,20,.05)-reduced)<.003,"Released controls did not resume coasting");
            // Duplicate identity disables ambiguous registration; unload/deletion is read fresh.
            var duplicatePos=new BlockPos(215,240,210);level.setBlockAndUpdate(duplicatePos,state);
            var duplicate=bogey(level,duplicatePos);duplicate.loadWithComponents(first.saveCustomAndMetadata(level.registryAccess()),level.registryAccess());
            check(Math.abs(coast(pipeline,body,.25,20,.05)-original)<.003,"Duplicate UUID received coast override");
            level.removeBlock(duplicatePos,false);
            check(Math.abs(coast(pipeline,body,.25,20,.05)-reduced)<.003,"Duplicate removal did not restore coast override");
            cab.setRemoved();
            check(Math.abs(coast(pipeline,body,.25,20,.05)-reduced)<.003,"Unloaded cab removed durable registration");
            cab.clearRemoved();
            level.removeBlock(first.getBlockPos(),false);
            check(Math.abs(coast(pipeline,body,.25,20,.05)-original)<.003,"Removed last registered bogey kept coast override");
            System.out.println("EMU_COASTING: PASS native Rapier 100 m/s after 1s: original="+original+", 25%="+reduced+", six substeps="+substeps+"; zero drag stable, vertical/angular unchanged, drive/brake veto, multi-bogey/cab dedup, clear/delete/unload and duplicate identity scope");
        }finally {
            CabConfig.SIMURAIL_COASTING_DRAG_MULTIPLIER.set(saved);
            level.removeBlock(cabPos,false);
        }
    }
    private CoastingChecks(){}
}
