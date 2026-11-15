package com.yasui_i.emucab.balise;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Per-dimension attachments. The rail block and its block entity stay untouched. */
public final class BaliseStore extends SavedData {
    private final Map<UUID,BaliseData> devices=new LinkedHashMap<>();
    private long revision;
    public static BaliseStore get(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(new Factory<>(BaliseStore::new,BaliseStore::load),"emucab_balises");
    }
    public Collection<BaliseData> all(){return List.copyOf(devices.values());}
    public List<BaliseData> loaded(ServerLevel level){return devices.values().stream().filter(d->level.hasChunkAt(net.minecraft.core.BlockPos.containing(d.target().anchor()))&&level.hasChunkAt(d.target().trackPos())).toList();}
    @Nullable public BaliseData get(UUID id){return devices.get(id);}
    @Nullable public BaliseData find(BaliseTarget target){return devices.values().stream().filter(d->d.target().sameMount(target)).findFirst().orElse(null);}
    public boolean add(BaliseData data){if(devices.size()>=100_000||find(data.target())!=null)return false;devices.put(data.id(),data);changed();return true;}
    public void put(BaliseData data){if(devices.containsKey(data.id())){devices.put(data.id(),data);changed();}}
    @Nullable public BaliseData remove(UUID id){var removed=devices.remove(id);if(removed!=null)changed();return removed;}
    public long revision(){return revision;}
    private void changed(){revision++;setDirty();}
    private static BaliseStore load(CompoundTag tag,HolderLookup.Provider registries){
        var result=new BaliseStore();var list=tag.getList("Devices",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(list.size(),100_000);i++){var device=BaliseData.load(list.getCompound(i));if(device!=null)result.devices.put(device.id(),device);}
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        var list=new ListTag();devices.values().forEach(d->list.add(d.save()));tag.put("Devices",list);return tag;
    }
}
