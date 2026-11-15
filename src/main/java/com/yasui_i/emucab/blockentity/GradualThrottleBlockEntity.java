package com.yasui_i.emucab.blockentity;

import com.yasui_i.emucab.block.GradualThrottleBlock;
import com.yasui_i.emucab.registry.ModBlockEntities;
import com.yasui_i.emucab.control.ThrottleKineticUpdate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;

/** Configurable slew limiter for vanilla redstone strength. */
public final class GradualThrottleBlockEntity extends SplitShaftBlockEntity {
    private int intervalTicks=20,rpmPerLevel=17,outputRpm,counter;
    private int syncedMaximum=256;
    public GradualThrottleBlockEntity(BlockPos pos,BlockState state) { super(ModBlockEntities.GRADUAL_THROTTLE.get(),pos,state); }
    public int outputRpm(){return outputRpm;} public int output(){return outputRpm;}
    public int intervalTicks(){return intervalTicks;}
    public int rpmPerLevel(){return rpmPerLevel;}
    public int maximumRpm(){return level!=null&&level.isClientSide?syncedMaximum:createMaximumRpm();}
    private static int createMaximumRpm(){return Math.max(1,com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxRotationSpeed.get());}
    public void configure(int newInterval){configure(newInterval,rpmPerLevel);}
    public void configure(int newInterval,int newStep){intervalTicks=Math.clamp(newInterval,1,200);rpmPerLevel=Math.max(1,newStep);counter=0;sync();}
    /** Scale only the output half; the input shaft remains the source speed.
     *
     * This is intentionally a SplitShaft rather than a generated kinetic source.
     * Create must take the incoming speed from the input half and apply this
     * ratio to the outgoing half.  Advertising outputRpm through
     * getGeneratedSpeed() makes Create treat the block as a second independent
     * source, which bypasses the redstone-controlled ratio in a live network.
     */
    @Override public float getRotationSpeedModifier(Direction face) {
        Direction.Axis axis=getBlockState().getValue(GradualThrottleBlock.FACING).getAxis();
        if(face.getAxis()!=axis)return 0;
        // Let Create discover either axial source. Once sourced, only the
        // opposite shaft half receives the redstone-controlled ratio.
        if(!hasSource())return 1;
        Direction inputFace=getSourceFacing();
        if(inputFace.getAxis()!=axis)return 0;
        if(face==inputFace)return 1;
        float input=Math.abs(getTheoreticalSpeed());
        return input<0.01f?0:Math.abs(outputRpm/input);
    }
    private void sync(){setChanged();if(level!=null)sendData();}
    @Override public void tick(){
        super.tick();
        if(level==null||level.isClientSide)return;
        int maximum=createMaximumRpm();
        if(syncedMaximum!=maximum){syncedMaximum=maximum;sendData();}
        Direction.Axis axis=getBlockState().getValue(GradualThrottleBlock.FACING).getAxis();
        int signal=0;
        // Shaft ends remain available for power. Any casing side accepts a
        // lever, analogue redstone, or DBW's normal Level#getSignal hook.
        for(Direction side:Direction.values()) {
            if(side.getAxis()==axis)continue;
            signal=Math.max(signal,level.getSignal(worldPosition.relative(side),side));
        }
        int target=com.yasui_i.emucab.control.GradualThrottleMath.targetRpm(signal,rpmPerLevel,maximum);
        if(outputRpm==target){counter=0;return;}
        // A zero command withdraws traction on this tick, regardless of the
        // ramp timer. Nonzero changes retain the configured slew rate.
        if(target!=0&&outputRpm<=maximum&&++counter<intervalTicks)return;
        counter=0;
        outputRpm=target==0?0:outputRpm>maximum?maximum:com.yasui_i.emucab.control.GradualThrottleMath.advanceRpm(outputRpm,signal,rpmPerLevel,maximum);
        setChanged();
        ThrottleKineticUpdate.rebuild(()->{
            RotationPropagator.handleRemoved(level,worldPosition,this);
            attachKinetics();
        });
        sendData();
    }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
    @Override protected void write(CompoundTag t,HolderLookup.Provider r,boolean clientPacket){super.write(t,r,clientPacket);t.putInt("IntervalTicks",intervalTicks);t.putInt("RpmPerLevel",rpmPerLevel);t.putInt("MaximumRpm",maximumRpm());t.putInt("OutputRpm",outputRpm);t.putInt("Counter",counter);}
    @Override protected void read(CompoundTag t,HolderLookup.Provider r,boolean clientPacket){
        intervalTicks=Math.clamp(t.contains("IntervalTicks")?t.getInt("IntervalTicks"):20,1,200);
        rpmPerLevel=Math.max(1,t.contains("RpmPerLevel")?t.getInt("RpmPerLevel"):17);
        syncedMaximum=Math.max(1,t.contains("MaximumRpm")?t.getInt("MaximumRpm"):256);
        outputRpm=Math.max(0,t.getInt("OutputRpm"));
        counter=Math.clamp(t.getInt("Counter"),0,intervalTicks-1);super.read(t,r,clientPacket);
    }
}
