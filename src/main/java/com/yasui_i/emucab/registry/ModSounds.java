package com.yasui_i.emucab.registry;

import com.yasui_i.emucab.EMUCab;
import com.yasui_i.emucab.control.AtpAnnouncement;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.EnumMap;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,EMUCab.MOD_ID);
    public static final DeferredHolder<SoundEvent,SoundEvent> BOGEY_ROLLING=SOUNDS.register("bogey_rolling_loop",
            ()->SoundEvent.createVariableRangeEvent(EMUCab.id("bogey_rolling_loop")));
    public static final DeferredHolder<SoundEvent,SoundEvent> BOGEY_WIND=SOUNDS.register("bogey_wind_loop",
            ()->SoundEvent.createVariableRangeEvent(EMUCab.id("bogey_wind_loop")));
    public static final DeferredHolder<SoundEvent,SoundEvent> BOGEY_MOTOR=SOUNDS.register("bogey_motor",
            ()->SoundEvent.createVariableRangeEvent(EMUCab.id("bogey_motor")));
    public static final DeferredHolder<SoundEvent,SoundEvent> BOGEY_BRAKE_RELEASE=SOUNDS.register("bogey_brake_release",
            ()->SoundEvent.createVariableRangeEvent(EMUCab.id("bogey_brake_release")));
    private static final EnumMap<AtpAnnouncement,DeferredHolder<SoundEvent,SoundEvent>> ATP=new EnumMap<>(AtpAnnouncement.class);
    static {
        for(var cue:AtpAnnouncement.values()) {
            String id="atp."+cue.id();
            ATP.put(cue,SOUNDS.register(id,()->SoundEvent.createVariableRangeEvent(EMUCab.id(id))));
        }
    }
    public static SoundEvent atp(AtpAnnouncement cue) { return ATP.get(cue).get(); }
    private ModSounds() {}
}
