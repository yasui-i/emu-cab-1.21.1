package com.yasui_i.emucab.telemetry;

import java.util.UUID;

/** Persisted on the bogey itself, so assembly and plot relocation retain binding. */
public interface BogeyIdentity {
    UUID emucab$identity();
    UUID emucab$renewIdentity();
}
