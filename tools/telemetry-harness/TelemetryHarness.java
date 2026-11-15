package emucabtelemetrytest;

import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.registry.ModBlocks;
import com.yasui_i.emucab.telemetry.*;
import com.crystaelix.simurail.content.bogey.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

@Mod("emucabtelemetrytest")
public final class TelemetryHarness {
    static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
    static Field field(Class<?> c,String name)throws Exception{var f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    static Method injected(Class<?> c,String suffix)throws Exception{return Arrays.stream(c.getDeclaredMethods()).filter(m->m.getName().contains(suffix)).peek(m->m.setAccessible(true)).findFirst().orElseThrow();}
    public TelemetryHarness(){NeoForge.EVENT_BUS.addListener(this::started);}
    void started(ServerStartedEvent e){String result;
        try{
            var level=e.getServer().overworld();var pos=new BlockPos(0,100,0);
            level.setBlockAndUpdate(pos,ModBlocks.CAB_DESK.get().defaultBlockState());var cab=(CabDeskBlockEntity)level.getBlockEntity(pos);
            if(!net.neoforged.fml.ModList.get().isLoaded("simurail")){
                check(Arrays.stream(SimurailTelemetry.read(cab)).allMatch(c->c.bound()==0),"Absent optional mod");
                result="PASS: optional Simurail absent startup and adapter fallback";
            }else{
                var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("simurail:physics_bogey"));
                check(block!=Blocks.AIR,"Simurail bogey registered");
                var bp=pos.east(4);level.setBlockAndUpdate(bp,block.defaultBlockState());var be=(PhysicsBogeyBlockEntity)level.getBlockEntity(bp);
                check(be instanceof BogeyIdentity,"Identity mixin missing");var id=((BogeyIdentity)be).emucab$identity();
                var copied=be.saveCustomAndMetadata(level.registryAccess());
                var sanitized=copied.copy();be.removeComponentsFromTag(sanitized);
                check(!sanitized.hasUUID("EmuCabTelemetryId"),"Picked item retained identity");
                var bp2=bp.south(2);level.setBlockAndUpdate(bp2,block.defaultBlockState());
                var second=(PhysicsBogeyBlockEntity)level.getBlockEntity(bp2);
                var item=new net.minecraft.world.item.ItemStack(block);
                net.minecraft.world.item.BlockItem.setBlockEntityData(item,be.getType(),copied);
                check(net.minecraft.world.item.BlockItem.updateCustomBlockEntityTag(level,null,bp2,item),"Old copied item NBT not loaded");
                check(!((BogeyIdentity)second).emucab$identity().equals(id),"Old copied item retained identity on placement");
                check(SimurailTelemetry.register(cab,be,8).accepted()&&SimurailTelemetry.register(cab,second,8).count()==2,"Two copied bogeys not registered separately");
                check(SimurailTelemetry.read(cab)[7].loaded()==2,"Two bogeys not online");
                check(SimurailTelemetry.register(cab,second,8).count()==2,"Repeat registration counted twice");
                // Reproduce an already-placed legacy copy without going through item placement.
                second.loadWithComponents(copied,level.registryAccess());
                check(((BogeyIdentity)second).emucab$identity().equals(id),"NBT reload unexpectedly changed identity");
                var repaired=SimurailTelemetry.register(cab,second,8);
                check(repaired.accepted()&&repaired.repaired(),"Legacy duplicate was not repaired");
                // Old overwritten registrations are intentionally removed by the user before rebinding.
                cab.telemetry().clearCar(8);SimurailTelemetry.register(cab,be,8);SimurailTelemetry.register(cab,second,8);
                check(cab.telemetry().registeredCount(8)==2&&SimurailTelemetry.read(cab)[7].loaded()==2,"Legacy repair counts wrong");
                cab.telemetry().clearCar(8);
                check(cab.telemetry().bind(id,2),"Bind");cab.telemetryChanged();
                // Production mixin intercepts the same Sable call as the actual physics loop.
                var axle=be.getAxle(true);var method=injected(axle.getClass(),"emucab$observe");
                var blocks=List.of(pos,bp);var sub=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level,pos,blocks,dev.ryanhcode.sable.companion.math.BoundingBox3i.from(blocks));
                var newCab=sub.getPlot().getCenterBlock();cab=(CabDeskBlockEntity)level.getBlockEntity(newCab);be=(PhysicsBogeyBlockEntity)level.getBlockEntity(newCab.east(4));
                check(((BogeyIdentity)be).emucab$identity().equals(id),"Identity changed on assembly");
                check(cab.telemetry().bindings().getFirst().id().equals(id),"Cab binding lost assembly");
                for(boolean front:new boolean[]{true,false}){
                    axle=be.getAxle(front);
                    var begin=injected(axle.getClass(),"emucab$begin");
                    var end=injected(axle.getClass(),"emucab$end");
                    var ci=new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("test",false);begin.invoke(axle,sub,.02,ci);
                    var traction=(Vector3d)field(axle.getClass(),"queuedTractionForce").get(axle);var brake=(Vector3d)field(axle.getClass(),"queuedBrakeForce").get(axle);
                    traction.set(100,0,0);brake.set(-40,0,0);
                    var group=sub.getOrCreateQueuedForceGroup(com.crystaelix.simurail.content.SimurailForceGroups.TRACTION.get());
                    method.invoke(axle,group,new Vector3d(),traction);method.invoke(axle,group,new Vector3d(),brake);end.invoke(axle,sub,.02,ci);
                }
                var sample=BogeyForceSamples.read(id);check(sample!=null&&Math.abs(sample.traction()-10000)<.01&&Math.abs(sample.brake()-1000)<.01,"Impulse/dt or two-axle sum wrong: "+sample);
                check(Math.abs(SimurailBrakeBindings.multiplier(level,id)-.25)<1E-9,"Default registered brake scale not 25%");
                var store=SimurailBrakeBindings.get(level);
                var brakeSave=store.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
                var load=SimurailBrakeBindings.class.getDeclaredMethod("load",net.minecraft.nbt.CompoundTag.class,HolderLookup.Provider.class);load.setAccessible(true);
                var loadedStore=(SimurailBrakeBindings)load.invoke(null,brakeSave,level.registryAccess());
                check(((Set<?>)field(SimurailBrakeBindings.class,"registered").get(loadedStore)).contains(id),"Registered brake scope lost on restart");
                var tractionGroup=sub.getOrCreateQueuedForceGroup(com.crystaelix.simurail.content.SimurailForceGroups.TRACTION.get());
                var brakeGroup=sub.getOrCreateQueuedForceGroup(com.crystaelix.simurail.content.SimurailForceGroups.BRAKE.get());
                axle=be.getAxle(true);method=injected(axle.getClass(),"emucab$observe");
                var brakeImpulse=(Vector3d)field(axle.getClass(),"queuedBrakeForce").get(axle);
                var tractionImpulse=(Vector3d)field(axle.getClass(),"queuedTractionForce").get(axle);
                for(double factor:new double[]{.25,.5,1}){
                    com.yasui_i.emucab.config.CabConfig.SIMURAIL_BRAKE_MULTIPLIER.set(factor);
                    brakeGroup.reset();brakeImpulse.set(-40,0,0);method.invoke(axle,brakeGroup,new Vector3d(),brakeImpulse);
                    check(Math.abs(brakeImpulse.x+40*factor)<1E-8,"Registered brake impulse not scaled");
                    check(Math.abs(brakeGroup.getForceTotal().getLocalForce().length()-40*factor)<1E-8,"Actual Sable submitted brake total not scaled");
                    tractionGroup.reset();tractionImpulse.set(100,0,0);method.invoke(axle,tractionGroup,new Vector3d(),tractionImpulse);
                    check(tractionImpulse.x==100&&Math.abs(tractionGroup.getForceTotal().getLocalForce().length()-100)<1E-8,"Brake multiplier changed traction");
                }
                com.yasui_i.emucab.config.CabConfig.SIMURAIL_BRAKE_MULTIPLIER.set(.25);
                check(SimurailBrakeBindings.multiplier(level,((BogeyIdentity)second).emucab$identity())==1,"Unregistered bogey was affected");
                // A second cab must not apply the factor twice or lose membership when one clears.
                var pos2=pos.south(10);level.setBlockAndUpdate(pos2,ModBlocks.CAB_DESK.get().defaultBlockState());var cab2=(CabDeskBlockEntity)level.getBlockEntity(pos2);
                cab2.telemetry().bind(id,2);cab2.telemetryChanged();
                SimurailBrakeBindings.remove(cab2);check(SimurailBrakeBindings.multiplier(level,id)==.25,"Removing one of two cabs cleared other binding");
                cab2.setRemoved();check(SimurailBrakeBindings.multiplier(level,id)==.25,"Cab unload changed persisted brake policy");
                check(cab.forceScaleKn()==5,"Legacy cab force scale default");
                cab.setDisplaySettings(0,0,"","",2.5);
                var forceTag=cab.getUpdateTag(level.registryAccess());var forceClone=new CabDeskBlockEntity(newCab,cab.getBlockState());forceClone.loadWithComponents(forceTag,level.registryAccess());
                check(forceClone.forceScaleKn()==2.5,"Force range NBT roundtrip");
                forceTag.putDouble("displayForceScaleKn",Double.NaN);forceClone.loadWithComponents(forceTag,level.registryAccess());check(forceClone.forceScaleKn()==5,"Corrupt range did not default");
                cab.setDisplaySettings(0,0,"","",Double.NaN);check(cab.forceScaleKn()==2.5,"Invalid range accepted");
                var before=be.getOptions().write().copy();float speed=be.getSpeed();
                var cars=SimurailTelemetry.read(cab);check(cars[1].complete()&&cars[1].traction()==10000&&cars[1].brake()==1000,"Adapter aggregation "+cars[1]);
                check(before.equals(be.getOptions().write())&&speed==be.getSpeed(),"Observer changed controls");
                check(cars[0].bound()==0,"Unbound car fabricated");
                cab.telemetry().bind(id,7);check(cab.telemetry().bindings().size()==1&&cab.telemetry().bindings().getFirst().car()==7,"Duplicate/reassignment");
                var saved=cab.getUpdateTag(level.registryAccess());var clone=new CabDeskBlockEntity(newCab,cab.getBlockState());clone.loadWithComponents(saved,level.registryAccess());
                check(clone.telemetry().bindings().equals(cab.telemetry().bindings())&&clone.telemetryId().equals(cab.telemetryId()),"Cab NBT lost");
                sub.logicalPose().position().add(20,3,10);sub.logicalPose().orientation().rotateY(.8);
                check(SimurailTelemetry.read(cab)[6].traction()==10000,"Moving/rotated read changed");
                BogeyForceSamples.remove(id);check(!SimurailTelemetry.read(cab)[6].complete(),"Missing physics became zero feedback");
                cab.telemetry().clearCar(7);cab.telemetryChanged();check(cab.telemetry().bindings().isEmpty(),"Clear registration");
                check(SimurailBrakeBindings.multiplier(level,id)==1,"Cleared registration did not restore original braking");
                cab.telemetry().bind(id,7);cab.telemetryChanged();check(SimurailBrakeBindings.multiplier(level,id)==.25,"Restored cab membership missing");
                BrakeReleaseChecks.run(level,cab,be);
                MotorTelemetryChecks.run(level,cab,be);
                CoastingChecks.run(level);
                level.removeBlock(newCab,false);check(SimurailBrakeBindings.multiplier(level,id)==1,"Destroyed cab kept brake membership");
                level.setBlockAndUpdate(newCab,cab.getBlockState());cab=(CabDeskBlockEntity)level.getBlockEntity(newCab);

                var handleControl=Arrays.stream(com.yasui_i.emucab.control.CabGeometry.CONTROLS).filter(c->c.action()==com.yasui_i.emucab.control.CabAction.HANDLE).findFirst().orElseThrow();
                var modelTarget=com.yasui_i.emucab.control.CabHitTesting.bounds(handleControl,0).getCenter();
                var modelEye=modelTarget.add(0,12,-14);
                var facing=cab.getBlockState().getValue(com.yasui_i.emucab.block.CabDeskBlock.FACING);
                var localEye=com.yasui_i.emucab.control.CabHitTesting.fromModel(newCab,facing,modelEye);
                var localTarget=com.yasui_i.emucab.control.CabHitTesting.fromModel(newCab,facing,modelTarget);
                var worldEye=sub.logicalPose().transformPosition(localEye);var worldTarget=sub.logicalPose().transformPosition(localTarget);
                var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"cab-grip-test"));
                player.setPos(worldEye.x,worldEye.y-player.getEyeHeight(),worldEye.z);player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,worldTarget);
                ((dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension)player).sable$setPlotPosition(localEye.add(0,-player.getEyeHeight(),0));
                // High speed/network delay: the body moved 30 blocks while the raw
                // player world position still describes the previous pose.
                sub.logicalPose().position().add(30,0,0);
                check(com.yasui_i.emucab.control.CabHitTesting.eyeInDesk(player,newCab).distanceTo(localEye)<1E-6,"Rider eye depends on stale world position");
                var buttonPacket=new com.yasui_i.emucab.network.CabButtonPayload(newCab,"B035",modelEye,modelTarget);
                var use=buttonPacket.getClass().getDeclaredMethod("handle",net.minecraft.server.level.ServerPlayer.class);use.setAccessible(true);use.invoke(buttonPacket,player);
                var grips=field(com.yasui_i.emucab.network.CabHandlePayload.class,"GRIPS").get(null);
                check(((Map<?,?>)grips).containsKey(player),"Moving handle grip was not authorized");
                var drag=new com.yasui_i.emucab.network.CabHandlePayload(newCab,1,-3);
                var dragMethod=drag.getClass().getDeclaredMethod("handle",net.minecraft.server.level.ServerPlayer.class);dragMethod.setAccessible(true);dragMethod.invoke(drag,player);
                check(cab.leverPosition()==-3,"Authorized moving grip did not apply notch");
                // A correct initial local ray used to pass pick() and then fail
                // the redundant current-look check. Reproduce that exact branch.
                var delayed=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"delayed-cab-grip"));
                delayed.setPos(worldEye.x,worldEye.y-delayed.getEyeHeight(),worldEye.z);
                delayed.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,worldEye.subtract(worldTarget.subtract(worldEye)));
                ((dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension)delayed).sable$setPlotPosition(localEye.add(0,-delayed.getEyeHeight(),0));
                use.invoke(buttonPacket,delayed);check(((Map<?,?>)grips).containsKey(delayed),"Valid local handle ray rejected by stale server look");
                var release=new com.yasui_i.emucab.network.CabHandlePayload(newCab,2,-5);dragMethod.invoke(release,delayed);
                check(cab.leverPosition()==-5&&!((Map<?,?>)grips).containsKey(delayed),"Final release did not commit notch/clear grip");
                result="PASS: real Sable submitted brake total scales25/50/100%, traction unchanged, unregistered unchanged, two-cab membership nonmultiplicative, clear/destroy restores, policy persistence and force-range legacy/NBT/NaN; moving handle grip authorization and notch update with 30-block raw-player/body mismatch; valid initial ray with opposite stale server look and final release commit; copied-item sanitation and old-item placement identity reset; two car08 bogeys count2/online2; repeat bind dedup; legacy duplicate repair; optional mixins applied to real Simurail; observe actual Sable impulse submission, dt conversion and two-axle sum; per-car aggregation; no control/options changes; stable identity and cab NBT across Sable assembly/moving/rotation; reassign, clear and unavailable state; brake-release 5-second cooldown and real input edges; bogey motor actual wheel RPM, measured load, real brake input, codec, duplicate and registration retirement. Injected known impulses, not a driven rail test.";
            }
        }catch(Throwable t){t.printStackTrace();result="FAIL: "+t;}
        try{Files.writeString(Path.of("telemetry-check.txt"),result);}catch(Exception x){throw new RuntimeException(x);}
        System.out.println("EMU_TELEMETRY: "+result);e.getServer().halt(false);
    }
}
