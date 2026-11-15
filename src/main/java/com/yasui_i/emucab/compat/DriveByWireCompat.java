package com.yasui_i.emucab.compat;

import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.CabWireChannels;
import edn.stratodonut.drivebywire.wire.WireNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

/** All positions stay in block-storage coordinates, as required by DBW's Sable remapper. */
public final class DriveByWireCompat {
    public static CabDeskBlock createBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
        return new WiredCabDeskBlock(properties);
    }
    private static final Map<Level,Set<BlockPos>> PENDING_REMOVALS=new WeakHashMap<>();
    public static void init() {
        NeoForge.EVENT_BUS.addListener(DriveByWireCompat::afterTick);
        NeoForge.EVENT_BUS.addListener(DriveByWireCompat::onUnload);
    }

    public static void tick(CabDeskBlockEntity desk,Map<String,Integer> signals) {
        Level level=desk.getLevel();
        if(level==null || level.isClientSide || desk.isRemoved())return;
        var manager=WireNetworkManager.get(level);
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
                if(!manager.hasSinksOnChannel(pos,channel))continue;
                int value=CabWireChannels.value(channel,signals);
                if(previous.getOrDefault(channel,0)!=value)manager.setSource(level,pos,channel,value);
            }
        }
    }

    public static void unload(CabDeskBlockEntity desk) {
        Level level=desk.getLevel();
        if(level==null || level.isClientSide)return;
        Direction along=desk.getBlockState().getValue(CabDeskBlock.FACING).getClockWise();
        for(int i=-1;i<=1;i++)WireNetworkManager.get(level).clearSourceSignals(level,desk.getBlockPos().relative(along,i));
    }

    public static void removed(Level level,BlockPos pos) {
        // Keep DBW connections when the desk is broken; the source value is cleared
        // by the block entity unload path and restored if the desk is recovered.
    }

    private static void afterTick(LevelTickEvent.Post event) {
        Level level=event.getLevel();
        Set<BlockPos> pending=PENDING_REMOVALS.remove(level);
        if(pending==null)return;
        var manager=WireNetworkManager.get(level);
        // Sable remaps assembly moves before the end of the tick. Delaying deletion
        // preserves those connections, while genuine removal also clears the graph.
        for(BlockPos pos:pending) {
            if(!level.hasChunkAt(pos) || level.getBlockState(pos).getBlock() instanceof CabDeskBlock)continue;
            var channels=manager.getNetwork().getOrDefault(pos.asLong(),Map.of());
            channels.forEach((channel,sinks)->sinks.forEach(sink->manager.removeConnectionInternal(level,pos,
                    BlockPos.of(sink.position()),Direction.from3DDataValue(sink.direction()),channel)));
        }
    }
    private static void onUnload(LevelEvent.Unload event) { PENDING_REMOVALS.remove(event.getLevel()); }
    private DriveByWireCompat() {}
}
