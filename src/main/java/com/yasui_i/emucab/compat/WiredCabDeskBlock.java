package com.yasui_i.emucab.compat;

import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.control.CabWireChannels;
import edn.stratodonut.drivebywire.wire.MultiChannelWireSource;
import java.util.List;

final class WiredCabDeskBlock extends CabDeskBlock implements MultiChannelWireSource {
    WiredCabDeskBlock(Properties properties) { super(properties); }
    @Override public List<String> wire$getChannels() { return CabWireChannels.CHANNELS; }
    @Override public String wire$nextChannel(String current,boolean forward) { return CabWireChannels.next(current,forward); }
}
