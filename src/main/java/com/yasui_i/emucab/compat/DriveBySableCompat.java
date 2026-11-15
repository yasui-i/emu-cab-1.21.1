package com.yasui_i.emucab.compat;

import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.CabWireChannels;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import java.util.*;

/** All positions stay in block-storage coordinates, as required by DriveBySable's remapper. */
public final class DriveBySableCompat {
    public static CabDeskBlock createBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
        return new SableWiredCabDeskBlock(properties);
    }
    public static void tick(CabDeskBlockEntity desk,Map<String,Integer> signals) {
        Level level=desk.getLevel();
        if(level==null || level.isClientSide || desk.isRemoved())return;
        var manager=CableNetworkManager.get(level);
        Direction along=desk.getBlockState().getValue(CabDeskBlock.FACING).getClockWise();
        for(int offset=-1;offset<=1;offset++) {
            BlockPos pos=desk.getBlockPos().relative(along,offset);
            if(!level.hasChunkAt(pos))continue;
            var state=level.getBlockState(pos);
            if(!desk.getBlockPos().equals(CabDeskBlock.findCenter(level,pos,state)))continue;
            var previous=manager.getSourceSignals(pos);
            // Compare with the network itself, not just the last button state. New wires,
            // graph rebuilds and reloads also need the current steady signal.
            var connectedChannels=new ArrayList<>(CabWireChannels.CHANNELS);
            connectedChannels.addAll(CabWireChannels.LEGACY_PARKING_CHANNELS);
            for(String channel:connectedChannels) {
                if(!manager.hasSinks(pos,channel))continue;
                int value=CabWireChannels.value(channel,signals);
                if(previous.getOrDefault(channel,0)!=value)manager.setSource(level,pos,channel,value);
            }
        }
    }

    public static void unload(CabDeskBlockEntity desk) {
        Level level=desk.getLevel();
        if(level==null || level.isClientSide)return;
        Direction along=desk.getBlockState().getValue(CabDeskBlock.FACING).getClockWise();
        for(int i=-1;i<=1;i++)CableNetworkManager.get(level).clearSourceSignals(level,desk.getBlockPos().relative(along,i));
    }

    public static void removed(Level level,BlockPos pos) {
        // Keep cable connections when the desk is broken; the source value is cleared
        // by the block entity unload path and restored if the desk is recovered.
    }

    private DriveBySableCompat() {}
}
