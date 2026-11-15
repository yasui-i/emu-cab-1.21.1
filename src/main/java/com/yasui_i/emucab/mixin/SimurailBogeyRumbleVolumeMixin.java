package com.yasui_i.emucab.mixin;

import com.yasui_i.emucab.client.BogeyMotorSounds;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** All registered bogeys use the new mix, even when another bogey owns a channel. */
@Pseudo
@Mixin(targets="com.crystaelix.simurail.content.bogey.PhysicsBogeySounds$PhysicsBogeySoundInstance",remap=false)
public abstract class SimurailBogeyRumbleVolumeMixin {
    @Shadow @Final com.crystaelix.simurail.content.bogey.PhysicsBogeySounds this$0;
    @ModifyVariable(method="setVolume",at=@At("HEAD"),argsOnly=true)
    private float emucab$mixGain(float volume) {
        var bogey=((SimurailBogeyRumbleMixin)(Object)this$0).emucab$bogey();
        return volume*BogeyMotorSounds.originalRumbleGain(bogey);
    }
}
