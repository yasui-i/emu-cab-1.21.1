package com.yasui_i.emucab.control;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.IdentityHashMap;
import java.util.Map;

/** Treat a throttle ratio rebuild as one speed change, without transient stops. */
public final class ThrottleKineticUpdate {
    private static final ThreadLocal<Map<KineticBlockEntity,Float>> CHANGES=new ThreadLocal<>();
    private ThrottleKineticUpdate() {}

    public static boolean deferSpeedChange(KineticBlockEntity entity,float previousSpeed) {
        Map<KineticBlockEntity,Float> changes=CHANGES.get();
        if(changes==null)return false;
        changes.putIfAbsent(entity,previousSpeed);
        entity.setChanged();
        return true;
    }

    public static void rebuild(Runnable update) {
        if(CHANGES.get()!=null){update.run();return;}
        Map<KineticBlockEntity,Float> changes=new IdentityHashMap<>();
        CHANGES.set(changes);
        try {update.run();}
        finally {
            CHANGES.remove();
            // Create still checks incompatible sources, direction and maximum
            // speed while rebuilding. Count only the actual before/after change
            // for its flicker protection, not the temporary zero in between.
            changes.forEach((entity,previousSpeed)->{
                if(!entity.isRemoved())entity.onSpeedChanged(previousSpeed);
            });
        }
    }
}
