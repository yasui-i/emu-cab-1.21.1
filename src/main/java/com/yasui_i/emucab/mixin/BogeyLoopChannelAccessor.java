package com.yasui_i.emucab.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Channel.class)
public interface BogeyLoopChannelAccessor {
    @Accessor("source") int emucab$source();
}
