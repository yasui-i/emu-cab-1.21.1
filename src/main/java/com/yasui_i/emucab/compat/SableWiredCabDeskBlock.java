package com.yasui_i.emucab.compat;

import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.control.CabWireChannels;
import edn.lakeopossmc.drivebysable.cable.MultiChannelCableSource;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import java.util.List;

final class SableWiredCabDeskBlock extends CabDeskBlock implements MultiChannelCableSource {
    SableWiredCabDeskBlock(Properties properties) { super(properties); }
    @Override public List<String> cable$getChannels(Level level,BlockPos pos) { return CabWireChannels.CHANNELS; }
    @Override public String cable$nextChannel(Level level,BlockPos pos,String current,boolean forward) { return CabWireChannels.next(current,forward); }
}
