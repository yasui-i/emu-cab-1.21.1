package com.yasui_i.emucab.blockentity;

import com.yasui_i.emucab.registry.ModBlockEntities;
import com.yasui_i.emucab.track.SpeedSignRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class SpeedSignBlockEntity extends BlockEntity {
    private int limit=80;
    private boolean bound;
    private Vec3 anchor=Vec3.ZERO,forward=new Vec3(0,0,1);
    public SpeedSignBlockEntity(BlockPos pos,BlockState state) { super(ModBlockEntities.SPEED_SIGN.get(),pos,state); }
    public int limit() { return limit; }
    public boolean bound() { return bound; }
    public Vec3 anchor() { return anchor; }
    public Vec3 forward() { return forward; }
    public void bindFromItem(ItemStack stack) {
        if(level==null || level.isClientSide)return;
        var data=stack.get(DataComponents.CUSTOM_DATA);
        if(data==null)return;
        var tag=data.copyTag().getCompound("SpeedSignTrack");
        if(!tag.getString("Dimension").equals(level.dimension().location().toString()))return;
        anchor=new Vec3(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));
        forward=new Vec3(tag.getDouble("DX"),tag.getDouble("DY"),tag.getDouble("DZ"));
        bound=valid(anchor) && valid(forward) && forward.lengthSqr()>.5;
        update();
    }
    private static boolean valid(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    public void configure(int kmh,boolean reverse) {
        if(kmh<1 || kmh>1000 || level==null || level.isClientSide)return;
        limit=kmh;if(reverse)forward=forward.scale(-1);update();
    }
    private void update() {
        setChanged();SpeedSignRegistry.register(this);
        com.yasui_i.emucab.balise.BaliseService.queueMigration(this);
        level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
    }
    @Override public void onLoad() { super.onLoad();SpeedSignRegistry.register(this);com.yasui_i.emucab.balise.BaliseService.queueMigration(this); }
    @Override public void setRemoved() { SpeedSignRegistry.remove(this);super.setRemoved(); }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        tag.putInt("Limit",limit);tag.putBoolean("Bound",bound);
        tag.putDouble("X",anchor.x);tag.putDouble("Y",anchor.y);tag.putDouble("Z",anchor.z);
        tag.putDouble("DX",forward.x);tag.putDouble("DY",forward.y);tag.putDouble("DZ",forward.z);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);limit=Math.clamp(tag.getInt("Limit"),1,1000);
        anchor=new Vec3(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));
        forward=new Vec3(tag.getDouble("DX"),tag.getDouble("DY"),tag.getDouble("DZ"));
        bound=tag.getBoolean("Bound") && valid(anchor) && valid(forward) && forward.lengthSqr()>.5;
        if(level!=null && !level.isClientSide)SpeedSignRegistry.register(this);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag=new CompoundTag();saveAdditional(tag,registries);return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
