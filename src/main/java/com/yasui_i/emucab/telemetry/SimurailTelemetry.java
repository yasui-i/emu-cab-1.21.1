package com.yasui_i.emucab.telemetry;

import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import net.neoforged.fml.ModList;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** Optional registration/feedback adapter; never sets a redstone or group control override. */
public final class SimurailTelemetry {
    public static boolean isBogey(BlockEntity be){return ModList.get().isLoaded("simurail")&&Adapter.isBogey(be);}
    public static CabTelemetry.Car[] read(CabDeskBlockEntity desk){return !desk.telemetry().bindings().isEmpty()&&ModList.get().isLoaded("simurail")?Adapter.read(desk):CabTelemetry.empty();}
    public record Registration(boolean accepted,boolean repaired,int count){}
    public static Registration register(CabDeskBlockEntity desk,BlockEntity be,int car){
        return ModList.get().isLoaded("simurail")?Adapter.register(desk,be,car):new Registration(false,false,0);
    }
    private static final class Adapter {
        static boolean isBogey(BlockEntity be){return be instanceof PhysicsBogeyBlockEntity&&be instanceof BogeyIdentity;}
        static boolean current(PhysicsBogeyBlockEntity be,net.minecraft.world.level.Level level){
            return be.getLevel()==level&&!be.isRemoved()&&level.getBlockEntity(be.getBlockPos())==be;
        }
        static Registration register(CabDeskBlockEntity desk,BlockEntity entity,int car){
            if(!(entity instanceof PhysicsBogeyBlockEntity be)||!(be instanceof BogeyIdentity identity)||car<1||car>8||!current(be,desk.getLevel()))return new Registration(false,false,0);
            UUID id=identity.emucab$identity();boolean collision=false;
            for(var other:PhysicsBogeyBlockEntity.LOADED_BOGEYS)if(other!=be&&current(other,desk.getLevel())&&other instanceof BogeyIdentity otherId&&otherId.emucab$identity().equals(id)){collision=true;break;}
            // Repair only the explicitly selected instance. Never auto-register nearby copies.
            if(collision){
                if(desk.telemetry().bindings().size()>=32)return new Registration(false,false,desk.telemetry().registeredCount(car));
                id=identity.emucab$renewIdentity();
            }
            if(!desk.telemetry().bind(id,car))return new Registration(false,false,desk.telemetry().registeredCount(car));
            be.setChanged();be.sendData();SimurailBrakeBindings.update(desk);
            return new Registration(true,collision,desk.telemetry().registeredCount(car));
        }
        static CabTelemetry.Car[] read(CabDeskBlockEntity desk){
            var values=CabTelemetry.empty();Map<UUID,PhysicsBogeyBlockEntity> loaded=new HashMap<>();Set<UUID> duplicate=new HashSet<>();
            for(var be:PhysicsBogeyBlockEntity.LOADED_BOGEYS)if(current(be,desk.getLevel())&&be instanceof BogeyIdentity id){
                UUID key=id.emucab$identity();if(loaded.put(key,be)!=null)duplicate.add(key);
            }
            for(var b:desk.telemetry().bindings()){
                int i=b.car()-1;var old=values[i];var be=loaded.get(b.id());
                boolean found=be!=null&&!duplicate.contains(b.id());
                var sample=found?BogeyForceSamples.read(b.id()):null;
                values[i]=new CabTelemetry.Car(old.bound()+1,old.loaded()+(found?1:0),old.measured()+(sample!=null?1:0),
                    old.traction()+(sample==null?0:sample.traction()),old.brake()+(sample==null?0:sample.brake()),
                    Math.max(old.localCommand(),found?be.getBrakeStrength():0),Math.max(old.effectiveCommand(),found?be.getGroupBrakeStrength():0),
                    old.fault()||duplicate.contains(b.id())||found&&(!be.getOptions().enabled||be.isDerailed()),old.slip()||sample!=null&&sample.slip()>.5);
            }
            return values;
        }
    }
    private SimurailTelemetry(){}
}
