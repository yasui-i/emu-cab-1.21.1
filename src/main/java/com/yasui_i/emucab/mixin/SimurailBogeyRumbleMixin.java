package com.yasui_i.emucab.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Client-only optional access to the owner of Simurail's rolling recording. */
@Pseudo
@Mixin(targets="com.crystaelix.simurail.content.bogey.PhysicsBogeySounds",remap=false)
public interface SimurailBogeyRumbleMixin {
    @Accessor("bogey") com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity emucab$bogey();
}
