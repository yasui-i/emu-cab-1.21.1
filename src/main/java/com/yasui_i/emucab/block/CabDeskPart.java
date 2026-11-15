package com.yasui_i.emucab.block;

import net.minecraft.util.StringRepresentable;

/**
 * The three blocks the CR400 driver's desk is built from. Only the CENTER part carries the
 * block entity (controls + display); the two side parts are the desk extension.
 */
public enum CabDeskPart implements StringRepresentable {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    private final String name;

    CabDeskPart(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
