package com.yasui_i.emucab.control;

import java.util.Map;

/** Server-side 0..15 signals. Adapters must use channel names, never ordinal indices. */
public interface CabSignalSource {
    Map<String, Integer> getCabSignals();
}
