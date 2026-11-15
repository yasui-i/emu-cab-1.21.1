package com.yasui_i.emucab.mixin;

import com.yasui_i.emucab.telemetry.BogeyIdentity;
import com.yasui_i.emucab.telemetry.BogeyForceSamples;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

@Pseudo
@Mixin(targets="com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity",remap=false)
public abstract class SimurailBogeyIdentityMixin implements BogeyIdentity {
    @Unique private volatile UUID emucab$id;
    @Override public synchronized UUID emucab$identity(){
        if(emucab$id==null)emucab$id=UUID.randomUUID();return emucab$id;
    }
    @Override public synchronized UUID emucab$renewIdentity(){
        // Copied block/structure NBT can contain another live bogey's UUID.
        // Its force samples belong to that other bogey and must not be removed.
        emucab$id=UUID.randomUUID();return emucab$id;
    }
    @Inject(method="write",at=@At("TAIL"))
    private void emucab$write(CompoundTag tag,HolderLookup.Provider registries,boolean clientPacket,CallbackInfo ci){tag.putUUID("EmuCabTelemetryId",emucab$identity());}
    @Inject(method="read",at=@At("TAIL"))
    private void emucab$read(CompoundTag tag,HolderLookup.Provider registries,boolean clientPacket,CallbackInfo ci){
        if(tag.hasUUID("EmuCabTelemetryId"))emucab$id=tag.getUUID("EmuCabTelemetryId");
    }
    @Inject(method="removeComponentsFromTag",at=@At("TAIL"))
    private void emucab$itemCopy(CompoundTag tag,CallbackInfo ci){
        // This tag is going into a picked item, not a saved block or a Sable move.
        tag.remove("EmuCabTelemetryId");
    }
    @Inject(method="remove",at=@At("HEAD"))
    private void emucab$remove(CallbackInfo ci){if(emucab$id!=null)BogeyForceSamples.remove(emucab$id);}
}
