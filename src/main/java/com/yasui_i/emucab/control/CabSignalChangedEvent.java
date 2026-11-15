package com.yasui_i.emucab.control;

import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import net.neoforged.bus.api.Event;
import java.util.Map;

/** Posted on NeoForge.EVENT_BUS on the server for presses, releases and switch changes. */
public final class CabSignalChangedEvent extends Event {
    private final CabDeskBlockEntity desk;
    private final Map<String, Integer> signals;

    public CabSignalChangedEvent(CabDeskBlockEntity desk, Map<String, Integer> signals) {
        this.desk = desk;
        this.signals = Map.copyOf(signals);
    }

    public CabDeskBlockEntity desk() { return desk; }
    public Map<String, Integer> signals() { return signals; }
}
