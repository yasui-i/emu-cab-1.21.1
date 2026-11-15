package emucabtest;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.transmission.sequencer.Instruction;
import com.simibubi.create.content.kinetics.transmission.sequencer.InstructionSpeedModifiers;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlock;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.yasui_i.emucab.block.GradualThrottleBlock;
import com.yasui_i.emucab.blockentity.GradualThrottleBlockEntity;
import com.yasui_i.emucab.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Mod("emucabthrottletest")
public final class ThrottleHarness {
    public ThrottleHarness() { NeoForge.EVENT_BUS.addListener(this::started); }
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
    private void started(ServerStartedEvent event) {
        String result;
        try {
            var level=event.getServer().overworld();
            checkPlacement(level);
            for(Direction facing:Direction.values())for(boolean reversed:new boolean[]{false,true})
                checkTransmission(level,facing,reversed? -128:64,reversed);
            checkCustomStep(level);
            checkSequencedGearshift(level);
            if(net.neoforged.fml.ModList.get().isLoaded("drivebysable"))ThrottleWireCheck.run(level);
            result="PASS: custom step20/40 with Create cap256/512, live clamp, NBT; stone/lever/Create shaft placement on six faces; motor on either end in six facings; all four redstone sides; nonzero 1 RPM steps and 0/1/8/15 targets; zero immediately clears full RPM/partial interval; restart waits full interval; motor reconnection; real shaft chain; sequenced gearshift survives ramp and stops at zero"
                    +(net.neoforged.fml.ModList.get().isLoaded("drivebysable")?"; DriveBySable four-side inputs and moving/rotated Sable assembly":"; DriveBySable absent startup");
        } catch(Throwable t) { t.printStackTrace();result="FAIL: "+t; }
        System.out.println("EMU_THROTTLE_CHECK: "+result);
        try { Files.writeString(Path.of("throttle-check-result.txt"),result); }
        catch(Exception e) { throw new RuntimeException(e); }
        event.getServer().halt(false);
    }
    private static void checkPlacement(ServerLevel level) {
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"throttle-placement"));
        player.setGameMode(GameType.CREATIVE);
        player.setShiftKeyDown(false);
        BlockPos pos=new BlockPos(8,90,8);
        for(Direction face:Direction.values()) {
            level.setBlockAndUpdate(pos,ModBlocks.GRADUAL_THROTTLE.get().defaultBlockState().setValue(GradualThrottleBlock.FACING,face));
            var shaftBlock=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","shaft"));
            for(var item:new net.minecraft.world.item.Item[]{Items.STONE,Items.LEVER,shaftBlock.asItem()}) {
                BlockPos target=pos.relative(face);
                level.removeBlock(target,false);
                Vec3 eye=pos.getCenter().add(Vec3.atLowerCornerOf(face.getNormal()).scale(2));
                player.setPos(eye.x,eye.y-player.getEyeHeight(),eye.z);
                Vec3 look=pos.getCenter().subtract(player.getEyePosition());
                player.setYRot((float)Math.toDegrees(Math.atan2(-look.x,look.z)));
                player.setXRot((float)-Math.toDegrees(Math.atan2(look.y,Math.hypot(look.x,look.z))));
                var stack=new ItemStack(item);
                player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var hit=new BlockHitResult(pos.getCenter().add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,pos,false);
                var result=player.gameMode.useItemOn(player,level,stack,InteractionHand.MAIN_HAND,hit);
                var expected=net.minecraft.world.level.block.Block.byItem(item);
                check(level.getBlockState(target).is(expected),"Non-sneaking "+item+" placement on "+face+" returned "+result);
                if(item==Items.LEVER) {
                    player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                    var leverHit=new BlockHitResult(target.getCenter(),face,target,false);
                    player.gameMode.useItemOn(player,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,leverHit);
                    check(level.getBlockState(target).getValue(LeverBlock.POWERED),"Mounted lever does not toggle on "+face);
                }
                if(item==shaftBlock.asItem())check(level.getBlockState(target).getValue(BlockStateProperties.AXIS)==face.getAxis(),"Placed shaft axis mismatch "+face);
                level.removeBlock(target,false);
            }
        }
        level.removeBlock(pos,false);
        System.out.println("EMU_THROTTLE_PLACEMENT: stone/lever/shaft on six faces passed");
    }
    private static void checkTransmission(ServerLevel level,Direction facing,int inputSpeed,boolean reversed) {
        BlockPos pos=new BlockPos(8,90,8);
        for(BlockPos clear:BlockPos.betweenClosed(pos.offset(-3,-3,-3),pos.offset(3,3,3)))level.removeBlock(clear,false);
        Direction side=facing.getAxis()==Direction.Axis.Y?Direction.EAST:Direction.UP;
        Direction outputFace=reversed?facing.getOpposite():facing;
        BlockPos motorPos=pos.relative(outputFace.getOpposite());
        BlockPos shaftPos=pos.relative(outputFace);
        BlockPos shaftEndPos=pos.relative(outputFace,2);
        var motorBlock=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","creative_motor"));
        var shaftBlock=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","shaft"));
        var motorState=motorBlock.defaultBlockState().setValue(CreativeMotorBlock.FACING,outputFace);
        level.setBlockAndUpdate(motorPos,motorState);
        level.setBlockAndUpdate(pos,ModBlocks.GRADUAL_THROTTLE.get().defaultBlockState().setValue(GradualThrottleBlock.FACING,facing));
        level.setBlockAndUpdate(shaftPos,shaftBlock.defaultBlockState().setValue(BlockStateProperties.AXIS,facing.getAxis()));
        level.setBlockAndUpdate(shaftEndPos,shaftBlock.defaultBlockState().setValue(BlockStateProperties.AXIS,facing.getAxis()));
        var motor=(CreativeMotorBlockEntity)level.getBlockEntity(motorPos);
        var throttle=(GradualThrottleBlockEntity)level.getBlockEntity(pos);
        var shaft=(KineticBlockEntity)level.getBlockEntity(shaftPos);
        var shaftEnd=(KineticBlockEntity)level.getBlockEntity(shaftEndPos);
        motor.initialize();throttle.initialize();shaft.initialize();shaftEnd.initialize();
        motor.generatedSpeed.setValue(inputSpeed);
        motor.updateGeneratedRotation();
        throttle.configure(1);
        motor.tick();throttle.tick();shaft.tick();
        check(throttle.hasSource(),"No input source "+facing);
        check(Math.abs(Math.abs(throttle.getSpeed())-Math.abs(inputSpeed))<.001f,"Wrong input RPM "+facing+" "+throttle.getSpeed());
        float actualInput=throttle.getSpeed();
        for(int strength:new int[]{0,1,8,15,8,0}) {
            BlockPos signalPos=pos.relative(side);
            if(strength==0)level.removeBlock(signalPos,false);
            // A vanilla weighted plate is an exact analogue source. No entity
            // simulation is needed for this server-side kinetic integration.
            else level.setBlock(signalPos,Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState().setValue(BlockStateProperties.POWER,strength),2);
            int target=strength*17;
            for(int i=0;i<260;i++){
                int before=throttle.outputRpm();
                throttle.tick();shaft.tick();shaftEnd.tick();
                int expectedRpm=target==0?0:before+Integer.compare(target,before);
                check(throttle.outputRpm()==expectedRpm,"Incorrect ramp/zero command "+facing+" tick="+i);
                check(Math.abs(Math.abs(shaftEnd.getSpeed())-expectedRpm)<.01f,"Downstream missed RPM step "+facing+" target="+expectedRpm+" actual="+shaftEnd.getSpeed());
            }
            check(throttle.outputRpm()==target,"Target "+facing+" strength="+strength+" actual="+throttle.outputRpm());
            check(level.getBlockEntity(pos)==throttle,"Throttle destroyed while ramping "+facing+" strength="+strength);
            check(level.getBlockEntity(shaftPos)==shaft,"Shaft destroyed while ramping "+facing+" strength="+strength);
            check(level.getBlockEntity(shaftEndPos)==shaftEnd,"Downstream shaft destroyed while ramping "+facing+" strength="+strength);
            float expected=Math.copySign(target,actualInput);
            check(Math.abs(shaft.getSpeed()-expected)<.01f,"Output "+facing+" strength="+strength+" expected="+expected+" actual="+shaft.getSpeed());
            check(Math.abs(shaftEnd.getSpeed()-expected)<.01f,"Downstream shaft "+facing+" strength="+strength+" actual="+shaftEnd.getSpeed());
        }
        for(Direction casingSide:Direction.values()) {
            if(casingSide.getAxis()==facing.getAxis())continue;
            BlockPos signalPos=pos.relative(casingSide);
            level.setBlock(signalPos,Blocks.REDSTONE_BLOCK.defaultBlockState(),2);
            for(int i=0;i<260;i++){throttle.tick();shaft.tick();shaftEnd.tick();}
            check(throttle.outputRpm()==255,"Side redstone not read "+facing+" side="+casingSide);
            check(Math.abs(Math.abs(shaftEnd.getSpeed())-255)<.01f,"Side target not transmitted "+facing+" side="+casingSide);
            level.removeBlock(signalPos,false);
            throttle.tick();shaft.tick();shaftEnd.tick();
            check(throttle.outputRpm()==0,"Feedback after signal removal "+facing+" side="+casingSide);
            check(Math.abs(shaftEnd.getSpeed())<.01f,"Side zero command did not stop downstream in one tick "+facing+" side="+casingSide);
        }
        BlockPos signalPos=pos.relative(side);
        throttle.configure(10);
        for(int i=0;i<9;i++) {
            level.setBlock(signalPos,Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState().setValue(BlockStateProperties.POWER,(i&1)==0?1:15),2);
            throttle.tick();
            shaft.tick();shaftEnd.tick();
        }
        check(throttle.outputRpm()==0,"Changing signal bypasses 10 tick interval "+facing);
        check(Math.abs(shaftEnd.getSpeed())<.01f,"Downstream interval bypass "+facing);
        throttle.tick();shaft.tick();shaftEnd.tick();
        check(throttle.outputRpm()==1,"No 1 RPM step after 10 ticks "+facing);
        check(Math.abs(Math.abs(shaftEnd.getSpeed())-1)<.01f,"Downstream did not get 1 RPM after 10 ticks "+facing);
        throttle.configure(1);
        level.setBlock(signalPos,Blocks.REDSTONE_BLOCK.defaultBlockState(),2);
        for(int i=0;i<260;i++){throttle.tick();shaft.tick();shaftEnd.tick();}
        throttle.configure(200);
        level.removeBlock(signalPos,false);
        throttle.tick();shaft.tick();shaftEnd.tick();
        check(throttle.outputRpm()==0&&Math.abs(shaftEnd.getSpeed())<.01f,"Full 255 RPM zero command waited for long interval "+facing);
        throttle.configure(1);
        level.setBlock(signalPos,Blocks.REDSTONE_BLOCK.defaultBlockState(),2);
        for(int i=0;i<260;i++){throttle.tick();shaft.tick();shaftEnd.tick();}
        throttle.configure(200);
        level.setBlock(signalPos,Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState().setValue(BlockStateProperties.POWER,14),2);
        for(int i=0;i<199;i++){throttle.tick();shaft.tick();shaftEnd.tick();}
        check(throttle.outputRpm()==255,"Nonzero downshift ignored long interval "+facing);
        throttle.tick();shaft.tick();shaftEnd.tick();
        check(throttle.outputRpm()==254&&Math.abs(Math.abs(shaftEnd.getSpeed())-254)<.01f,"Nonzero downshift did not take one RPM step "+facing);
        for(int i=0;i<37;i++){throttle.tick();shaft.tick();shaftEnd.tick();}
        level.removeBlock(signalPos,false);
        throttle.tick();shaft.tick();shaftEnd.tick();
        check(throttle.outputRpm()==0&&Math.abs(shaftEnd.getSpeed())<.01f,"Zero waited for partial 200-tick interval "+facing);
        level.setBlock(signalPos,Blocks.REDSTONE_BLOCK.defaultBlockState(),2);
        for(int i=0;i<199;i++){throttle.tick();shaft.tick();shaftEnd.tick();}
        check(throttle.outputRpm()==0&&Math.abs(shaftEnd.getSpeed())<.01f,"Restart reused pre-zero interval "+facing);
        throttle.tick();shaft.tick();shaftEnd.tick();
        check(throttle.outputRpm()==1&&Math.abs(Math.abs(shaftEnd.getSpeed())-1)<.01f,"Restart did not ramp from zero "+facing);
        throttle.configure(1);
        level.setBlock(signalPos,Blocks.REDSTONE_BLOCK.defaultBlockState(),2);
        for(int i=0;i<260;i++){throttle.tick();shaft.tick();shaftEnd.tick();}
        level.removeBlock(motorPos,false);
        for(int i=0;i<8;i++){throttle.tick();shaft.tick();shaftEnd.tick();}
        check(Math.abs(shaftEnd.getSpeed())<.01f,"Output still powered after motor removed "+facing);
        level.setBlockAndUpdate(motorPos,motorState);
        motor=(CreativeMotorBlockEntity)level.getBlockEntity(motorPos);
        motor.initialize();motor.generatedSpeed.setValue(inputSpeed);motor.updateGeneratedRotation();
        for(int i=0;i<8;i++){motor.tick();throttle.tick();shaft.tick();shaftEnd.tick();}
        check(Math.abs(Math.abs(shaftEnd.getSpeed())-255)<.01f,"No output after motor reconnect "+facing+" rpm="+shaftEnd.getSpeed());
        System.out.println("EMU_THROTTLE_TRANSMISSION: "+facing+" motor="+outputFace.getOpposite()+" input="+inputSpeed+" passed");
    }
    private static void checkCustomStep(ServerLevel level) {
        var config=com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxRotationSpeed;
        int original=config.get();
        BlockPos pos=new BlockPos(30,100,30),motorPos=pos.west(),shaftPos=pos.east(),signalPos=pos.above();
        try {
            config.set(512);
            var motorBlock=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","creative_motor"));
            var shaftBlock=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","shaft"));
            level.setBlockAndUpdate(motorPos,motorBlock.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));
            level.setBlockAndUpdate(pos,ModBlocks.GRADUAL_THROTTLE.get().defaultBlockState().setValue(GradualThrottleBlock.FACING,Direction.EAST));
            level.setBlockAndUpdate(shaftPos,shaftBlock.defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
            var motor=(CreativeMotorBlockEntity)level.getBlockEntity(motorPos);
            var throttle=(GradualThrottleBlockEntity)level.getBlockEntity(pos);
            var shaft=(KineticBlockEntity)level.getBlockEntity(shaftPos);
            motor.initialize();throttle.initialize();shaft.initialize();motor.generatedSpeed.setValue(64);motor.updateGeneratedRotation();
            check(throttle.rpmPerLevel()==17,"Default step migration");
            throttle.configure(1,20);level.setBlock(signalPos,Blocks.REDSTONE_BLOCK.defaultBlockState(),2);
            for(int i=0;i<305;i++){motor.tick();throttle.tick();shaft.tick();}
            check(throttle.outputRpm()==300&&Math.abs(shaft.getSpeed()-300)<.01,"15x20 not 300 at Create cap512: "+shaft.getSpeed());
            check(throttle.maximumRpm()==512,"Did not read server Create cap");
            var tag=throttle.getUpdateTag(level.registryAccess());
            var loaded=new GradualThrottleBlockEntity(pos,throttle.getBlockState());loaded.loadWithComponents(tag,level.registryAccess());
            check(loaded.rpmPerLevel()==20&&loaded.intervalTicks()==1&&loaded.outputRpm()==300,"Custom step NBT lost");
            throttle.configure(1,40);
            for(int i=0;i<220;i++){throttle.tick();shaft.tick();}
            check(throttle.outputRpm()==512&&Math.abs(shaft.getSpeed()-512)<.01,"15x40 not capped512: "+shaft.getSpeed());
            throttle.configure(200,20);
            config.set(256);throttle.tick();shaft.tick();
            check(throttle.outputRpm()==256&&Math.abs(shaft.getSpeed()-256)<.01,"Live cap reduction not immediately clamped");
            level.removeBlock(signalPos,false);throttle.tick();shaft.tick();
            check(throttle.outputRpm()==0&&Math.abs(shaft.getSpeed())<.01,"Zero not immediate after custom cap");
            throttle.configure(1,20);level.setBlock(signalPos,Blocks.REDSTONE_BLOCK.defaultBlockState(),2);
            for(int i=0;i<260;i++){throttle.tick();shaft.tick();}
            check(throttle.outputRpm()==256&&Math.abs(shaft.getSpeed()-256)<.01,"15x20 not capped256");
            var old=throttle.getUpdateTag(level.registryAccess());old.remove("RpmPerLevel");old.putInt("OutputRpm",200);
            loaded.loadWithComponents(old,level.registryAccess());check(loaded.rpmPerLevel()==17&&loaded.outputRpm()==200,"Legacy migration broken");
            System.out.println("EMU_THROTTLE_CUSTOM: step20/cap512=300, step40/cap512=512, live cap256 immediate, step20/cap256=256, shaft propagation, zero stop and old/new NBT passed");
        } finally {
            for(BlockPos clear:BlockPos.betweenClosed(pos.offset(-2,-1,-1),pos.offset(2,1,1)))level.removeBlock(clear,false);
            config.set(original);
        }
    }
    private static void checkSequencedGearshift(ServerLevel level) {
        BlockPos pos=new BlockPos(8,100,8),motorPos=pos.west(),sequencePos=pos.east(),shaftPos=pos.east(2),signalPos=pos.above();
        for(BlockPos clear:BlockPos.betweenClosed(pos.offset(-3,-3,-3),pos.offset(3,3,3)))level.removeBlock(clear,false);
        var motorBlock=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","creative_motor"));
        var sequenceBlock=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","sequenced_gearshift"));
        var shaftBlock=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","shaft"));
        level.setBlockAndUpdate(motorPos,motorBlock.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));
        level.setBlockAndUpdate(pos,ModBlocks.GRADUAL_THROTTLE.get().defaultBlockState().setValue(GradualThrottleBlock.FACING,Direction.EAST));
        level.setBlockAndUpdate(sequencePos,sequenceBlock.defaultBlockState()
                .setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS,Direction.Axis.X)
                .setValue(SequencedGearshiftBlock.VERTICAL,false).setValue(SequencedGearshiftBlock.STATE,0));
        level.setBlockAndUpdate(shaftPos,shaftBlock.defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        var motor=(CreativeMotorBlockEntity)level.getBlockEntity(motorPos);
        var throttle=(GradualThrottleBlockEntity)level.getBlockEntity(pos);
        var sequence=(SequencedGearshiftBlockEntity)level.getBlockEntity(sequencePos);
        var shaft=(KineticBlockEntity)level.getBlockEntity(shaftPos);
        motor.initialize();throttle.initialize();sequence.initialize();shaft.initialize();
        motor.generatedSpeed.setValue(64);motor.updateGeneratedRotation();throttle.configure(1);
        level.setBlock(signalPos,Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState().setValue(BlockStateProperties.POWER,1),2);
        for(int i=0;i<25;i++){motor.tick();throttle.tick();sequence.tick();shaft.tick();}
        check(Math.abs(Math.abs(sequence.getSpeed())-17)<.01f,"Sequence setup has no input, speed="+sequence.getSpeed()+" throttle="+throttle.getSpeed()+"/"+throttle.outputRpm()+" state="+level.getBlockState(sequencePos));
        var saved=sequence.getUpdateTag(level.registryAccess());
        saved.put("Instructions",Instruction.serializeAll(java.util.List.of(
                new Instruction(SequencerInstructions.TURN_ANGLE,InstructionSpeedModifiers.FORWARD,1_000_000),
                new Instruction(SequencerInstructions.END))));
        saved.putInt("InstructionIndex",-1);saved.putInt("InstructionDuration",-1);
        saved.putFloat("InstructionProgress",0);saved.putInt("Timer",0);
        sequence.loadWithComponents(saved,level.registryAccess());
        sequence.run(0);sequence.attachKinetics();sequence.tick();shaft.tick();
        check(!sequence.isIdle(),"Sequence did not start");
        check(Math.abs(Math.abs(shaft.getSpeed())-17)<.01f,"Sequence initial output");
        level.setBlock(signalPos,Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState().setValue(BlockStateProperties.POWER,8),2);
        for(int i=0;i<125;i++) {
            throttle.tick();sequence.tick();shaft.tick();
            check(!sequence.isIdle(),"Throttle ramp aborted active sequence at RPM "+throttle.outputRpm());
            check(level.getBlockState(sequencePos).getValue(SequencedGearshiftBlock.STATE)==1,"Sequence state lost during ramp");
            check(Math.abs(Math.abs(shaft.getSpeed())-throttle.outputRpm())<.01f,"Sequence downstream RPM differs during ramp");
        }
        level.removeBlock(signalPos,false);
        throttle.tick();sequence.tick();shaft.tick();
        check(sequence.isIdle(),"A real stop must still terminate the sequence");
        check(Math.abs(shaft.getSpeed())<.01f,"Sequence output did not stop");
        System.out.println("EMU_THROTTLE_SEQUENCE: active turn survives 17 to 136 RPM ramp; true zero still stops it");
    }
}
