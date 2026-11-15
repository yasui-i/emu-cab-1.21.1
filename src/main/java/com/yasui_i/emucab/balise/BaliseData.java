package com.yasui_i.emucab.balise;

import com.yasui_i.emucab.control.AtpAnnouncement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/** Immutable server-owned configuration for one rail-mounted device. */
public record BaliseData(UUID id,BaliseTarget target,Kind kind,int limit,String cue,boolean enabled) {
    public enum Kind {LIMIT,ANNOUNCEMENT}
    public Vec3 forward(){return target.forward();}
    public BaliseData configure(int limit,String cue,boolean enabled,boolean reverse){
        return new BaliseData(id,reverse&&kind==Kind.LIMIT?target.reversed():target,kind,Math.clamp(limit,1,1000),cue,enabled);
    }
    public static boolean validCue(String cue){
        for(var announcement:AtpAnnouncement.values())if(announcement.id().equals(cue))return true;
        return false;
    }
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putUUID("Id",id);tag.put("Target",target.save());tag.putString("Kind",kind.name());
        tag.putInt("Limit",limit);tag.putString("Cue",cue);tag.putBoolean("Enabled",enabled);return tag;
    }
    @Nullable public static BaliseData load(CompoundTag tag){
        if(!tag.hasUUID("Id"))return null;
        var target=BaliseTarget.load(tag.getCompound("Target"));if(target==null)return null;
        Kind kind;try{kind=Kind.valueOf(tag.getString("Kind"));}catch(IllegalArgumentException e){return null;}
        String cue=tag.getString("Cue");if(!validCue(cue))cue=AtpAnnouncement.NEUTRAL_SECTION_AHEAD.id();
        return new BaliseData(tag.getUUID("Id"),target,kind,Math.clamp(tag.getInt("Limit"),1,1000),cue,tag.getBoolean("Enabled"));
    }
}
