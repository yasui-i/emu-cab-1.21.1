package com.yasui_i.emucab.motor;

/** Server-tick drive envelope, separate from CEE's electrical simulation. */
final class VvvfDriveCommand {
    static float next(float current, int redstone, double riseSeconds, double fallSeconds) {
        if (redstone <= 0) return 0;
        float target = Math.min(15, redstone) / 15f;
        float step = (float) (1 / (20 * Math.max(0.05, target > current ? riseSeconds : fallSeconds)));
        return target > current ? Math.min(target, current + step) : Math.max(target, current - step);
    }

    private VvvfDriveCommand() {}
}
