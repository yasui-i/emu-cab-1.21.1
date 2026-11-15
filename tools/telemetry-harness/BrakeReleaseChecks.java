package emucabtelemetrytest;

import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import com.crystaelix.simurail.content.bogey.PhysicsBogeyControlMode;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.telemetry.*;
import com.yasui_i.emucab.network.BogeyBrakeSoundPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.lang.reflect.*;
import java.util.*;

final class BrakeReleaseChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Field field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static BrakeReleaseState observed(ServerLevel level,UUID id)throws Exception {
        var all=(Map<?,?>)field(BogeyBrakeSounds.class,"STATES").get(null);
        var observed=((Map<?,?>)all.get(level)).get(id);if(observed==null)return null;
        var edge=observed.getClass().getDeclaredMethod("edge");edge.setAccessible(true);return (BrakeReleaseState)edge.invoke(observed);
    }
    static void run(ServerLevel level,CabDeskBlockEntity cab,PhysicsBogeyBlockEntity bogey)throws Exception {
        var edge=new BrakeReleaseState();
        for(int i=0;i<30;i++)check(!edge.update(0),"Released startup manufactured a release");
        for(int i=0;i<2;i++)check(!edge.update(.5f),"Apply emitted release");
        for(int i=0;i<5;i++)check(!edge.update(0),"Two-tick brake pulse armed sound");
        for(int i=0;i<3;i++)check(!edge.update(1f/225),"Minimum redstone brake emitted early");
        check(!edge.update(0)&&!edge.update(0),"Release was not debounced");
        check(!edge.update(.1f),"Brake reapply emitted release");
        check(!edge.update(0)&&!edge.update(0)&&edge.update(0),"Stable release did not emit once");
        for(int i=0;i<8;i++)check(!edge.update(0),"Continuous release repeated sound");
        for(int i=0;i<3;i++)edge.update(.2f);
        for(int i=0;i<3;i++)check(!edge.update(0),"Cooldown did not suppress chatter");
        for(int i=0;i<110;i++)edge.update(0);
        for(int i=0;i<3;i++)edge.update(1);
        check(!edge.update(0)&&!edge.update(0)&&edge.update(0),"Second deliberate release failed");

        // Read real Simurail commands, including direct redstone, on an assembled Sable body.
        var id=((BogeyIdentity)bogey).emucab$identity();
        check(SimurailBrakeBindings.registered(level).contains(id),"Registered audio membership missing");
        var above=bogey.getBlockPos().above();var below=bogey.getBlockPos().below();
        var oldAbove=level.getBlockState(above);var oldBelow=level.getBlockState(below);
        var oldMode=bogey.getOptions().controlMode;
        try {
            bogey.getOptions().controlMode=PhysicsBogeyControlMode.BRAKING;
            level.setBlockAndUpdate(above,Blocks.REDSTONE_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(below,Blocks.REDSTONE_BLOCK.defaultBlockState());
            check(bogey.getGroupBrakeStrength()>.9f,"Real bogey redstone input did not apply brake");
            var event=new LevelTickEvent.Post(()->true,level);
            for(int i=0;i<3;i++)BogeyBrakeSounds.tick(event);
            var current=observed(level,id);check(current!=null&&field(BrakeReleaseState.class,"armed").getBoolean(current),"Service did not arm actual redstone brake");
            level.setBlockAndUpdate(above,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(below,Blocks.AIR.defaultBlockState());
            check(bogey.getGroupBrakeStrength()==0,"Real bogey redstone removal did not release brake");
            BogeyBrakeSounds.tick(event);BogeyBrakeSounds.tick(event);
            check(field(BrakeReleaseState.class,"cooldown").getInt(current)==0,"Service emitted before debounce");
            BogeyBrakeSounds.tick(event);
            int cooldownTicks=(int)Math.ceil(com.yasui_i.emucab.config.CabConfig.BOGEY_RELEASE_COOLDOWN.get()*20);
            check(!field(BrakeReleaseState.class,"armed").getBoolean(current)&&field(BrakeReleaseState.class,"cooldown").getInt(current)==cooldownTicks,"Service did not request release audio");
            BogeyBrakeSounds.tick(event);check(field(BrakeReleaseState.class,"cooldown").getInt(current)==cooldownTicks-1,"Repeated release restarted audio");
            var packet=new BogeyBrakeSoundPayload(bogey.getBlockPos(),id,.65f,24);
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
            try {BogeyBrakeSoundPayload.CODEC.encode(buffer,packet);check(packet.equals(BogeyBrakeSoundPayload.CODEC.decode(buffer)),"Brake audio packet lost position/UUID/gain");}
            finally {buffer.release();}
            // Removing this cab's binding retires any old sound-trigger state.
            var bindings=List.copyOf(cab.telemetry().bindings());cab.telemetry().clearCar(7);cab.telemetryChanged();
            BogeyBrakeSounds.tick(event);
            var all=(Map<?,?>)field(BogeyBrakeSounds.class,"STATES").get(null);var states=(Map<?,?>)all.get(level);
            check(states==null||!states.containsKey(id),"Unregistered bogey kept release state");
            for(var binding:bindings)cab.telemetry().bind(binding.id(),binding.car());cab.telemetryChanged();
        } finally {
            bogey.getOptions().controlMode=oldMode;
            level.setBlockAndUpdate(above,oldAbove);level.setBlockAndUpdate(below,oldBelow);
        }
    }
    private BrakeReleaseChecks(){}
}
