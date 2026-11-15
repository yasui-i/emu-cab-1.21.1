package emucabtelemetrytest;

import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import com.crystaelix.simurail.content.bogey.PhysicsBogeyControlMode;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.telemetry.*;
import com.yasui_i.emucab.network.BogeyMotorSoundPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Real optional BE, Sable plot, registry identity and packet checks, with known wheel/force inputs. */
final class MotorTelemetryChecks {
    static void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}
    static BogeyMotorSoundPayload packet(ServerLevel level,UUID id){
        return BogeyMotorTelemetry.snapshot(level).stream().filter(p->p.bogey().equals(id)).findFirst().orElseThrow();
    }
    static void run(ServerLevel level,CabDeskBlockEntity cab,PhysicsBogeyBlockEntity be)throws Exception {
        UUID id=((BogeyIdentity)be).emucab$identity();
        var above=be.getBlockPos().above();var below=be.getBlockPos().below();
        var oldAbove=level.getBlockState(above);var oldBelow=level.getBlockState(below);
        var oldMode=be.getOptions().controlMode;boolean oldEnabled=be.getOptions().enabled;
        double oldVisual=be.visualSpeed;float oldSpeed=be.getSpeed();
        var physics=com.crystaelix.simurail.config.SimurailConfig.server().physics;
        double stress=be.getOptions().getStress();
        var duplicatePos=new BlockPos(14,112,30);var oldDuplicate=level.getBlockState(duplicatePos);
        var containing=dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(level,be.getBlockPos());
        check(containing instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel,"Motor telemetry needs assembled real Sable body");
        var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)containing;
        var handle=dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body);
        var oldLinear=handle.getLinearVelocity(new org.joml.Vector3d());
        var oldAngular=handle.getAngularVelocity(new org.joml.Vector3d());
        try {
            be.getOptions().enabled=true;be.getOptions().controlMode=PhysicsBogeyControlMode.BRAKING;
            level.setBlockAndUpdate(above,Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(below,Blocks.AIR.defaultBlockState());
            be.setSpeed(256);
            be.visualSpeed=2*Math.PI*be.getOptions().type.wheelRadius()*10;
            double maximumForce=2*Math.abs(256*physics.axleTargetSpeedFactor.get())*Math.abs(stress)*physics.axleDriveForceFactor.get();
            BogeyForceSamples.record(id,true,maximumForce*.25,0,0);
            BogeyForceSamples.record(id,false,maximumForce*.25,0,0);
            setVelocity(handle,new org.joml.Vector3d(350/3.6,0,0),new org.joml.Vector3d());
            var first=packet(level,id);
            check(Math.abs(first.wheelRpm()-600)<.01,"Audio RPM used Create input instead of actual wheel radius/speed: "+first);
            check(Math.abs(first.bodySpeedKmh()-350)<.02,"Body speed did not read actual rigid-body velocity: "+first);
            check(first.motorPowered()&&Math.abs(first.traction01()-.5)<.01,"Measured two-axle force did not reach audio load: "+first);
            check(first.pos().equals(be.getBlockPos()),"Moving bogey audio did not use plot coordinates");
            be.setSpeed(128);check(Math.abs(packet(level,id).wheelRpm()-600)<.01,"Input shaft RPM changed wheel pitch");
            be.visualSpeed=-be.visualSpeed;check(Math.abs(packet(level,id).wheelRpm()-600)<.01,"Reverse wheel RPM pitch");
            setVelocity(handle,new org.joml.Vector3d(),new org.joml.Vector3d());
            check(packet(level,id).bodySpeedKmh()==0&&Math.abs(packet(level,id).wheelRpm()-600)<.01,"Wheel slip fabricated body speed/wind");
            setVelocity(handle,new org.joml.Vector3d(350/3.6,0,0),new org.joml.Vector3d());
            be.visualSpeed=0;
            check(packet(level,id).wheelRpm()==0&&Math.abs(packet(level,id).bodySpeedKmh()-350)<.02,"Locked wheels erased moving-body wind speed");
            be.visualSpeed=2*Math.PI*be.getOptions().type.wheelRadius()*10;
            setVelocity(handle,new org.joml.Vector3d(10,3,0),new org.joml.Vector3d(0,.3,0));
            var point=body.logicalPose().transformPosition(be.getBlockPos().getCenter());
            var offset=new org.joml.Vector3d(point.x,point.y,point.z).sub(body.logicalPose().position());
            double expected=new org.joml.Vector3d(0,.3,0).cross(offset).add(10,3,0).length()*3.6;
            check(Math.abs(packet(level,id).bodySpeedKmh()-expected)<.02,"Rotating body source point omitted angular/vertical velocity");
            level.setBlockAndUpdate(above,Blocks.REDSTONE_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(below,Blocks.REDSTONE_BLOCK.defaultBlockState());
            check(packet(level,id).brake01()>.99,"Actual redstone group brake did not reach audio");
            be.getOptions().enabled=false;
            var disabled=packet(level,id);check(!disabled.motorPowered()&&disabled.traction01()==0&&disabled.brake01()==0,"Disabled bogey fabricated electric load");
            be.getOptions().enabled=true;
            BogeyForceSamples.remove(id);check(packet(level,id).traction01()==0,"Missing physical measurement fabricated load");
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
            try {BogeyMotorSoundPayload.CODEC.encode(buffer,first);check(first.equals(BogeyMotorSoundPayload.CODEC.decode(buffer)),"Motor payload roundtrip");}
            finally {buffer.release();}
            check(new BogeyMotorSoundPayload(first.pos(),id,0,600,1,0,true).bodySpeedKmh()==0,"Legacy constructor fabricated unknown body speed");
            // Existing copied NBT must silence both ambiguous sources until explicitly repaired.
            level.setBlockAndUpdate(duplicatePos,be.getBlockState());
            var duplicate=(PhysicsBogeyBlockEntity)level.getBlockEntity(duplicatePos);
            duplicate.loadWithComponents(be.saveCustomAndMetadata(level.registryAccess()),level.registryAccess());
            check(BogeyMotorTelemetry.snapshot(level).stream().noneMatch(p->p.bogey().equals(id)),"Copied UUID emitted duplicate motors");
            level.removeBlock(duplicatePos,false);
            check(packet(level,id).bogey().equals(id),"Motor did not recover after duplicate removal");
            var bindings=List.copyOf(cab.telemetry().bindings());
            for(int car=1;car<=8;car++)cab.telemetry().clearCar(car);cab.telemetryChanged();
            check(BogeyMotorTelemetry.snapshot(level).stream().noneMatch(p->p.bogey().equals(id)),"Unregistered bogey still sends motor telemetry");
            for(var binding:bindings)cab.telemetry().bind(binding.id(),binding.car());cab.telemetryChanged();
        }finally {
            setVelocity(handle,oldLinear,oldAngular);
            be.getOptions().controlMode=oldMode;be.getOptions().enabled=oldEnabled;
            be.visualSpeed=oldVisual;be.setSpeed(oldSpeed);
            level.setBlockAndUpdate(above,oldAbove);level.setBlockAndUpdate(below,oldBelow);
            level.setBlockAndUpdate(duplicatePos,oldDuplicate);BogeyForceSamples.remove(id);
        }
    }
    static void setVelocity(dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle,
                            org.joml.Vector3dc linear,org.joml.Vector3dc angular) {
        var deltaLinear=new org.joml.Vector3d(linear).sub(handle.getLinearVelocity(new org.joml.Vector3d()));
        var deltaAngular=new org.joml.Vector3d(angular).sub(handle.getAngularVelocity(new org.joml.Vector3d()));
        handle.addLinearAndAngularVelocity(deltaLinear,deltaAngular);
    }
    private MotorTelemetryChecks(){}
}
