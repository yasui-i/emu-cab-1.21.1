package com.yasui_i.emucab.block;

import com.yasui_i.emucab.track.SpeedSignRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.block.Block;
import java.util.List;

public final class SpeedSignItem extends BlockItem {
    public SpeedSignItem(Block block,Properties properties) { super(block,properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        var player=context.getPlayer();
        if(player!=null && player.isShiftKeyDown()) {
            if(context.getLevel() instanceof ServerLevel level) {
                var network=SpeedSignRegistry.network(level);
                var look=player.getLookAngle();
                var heading=new net.minecraft.world.phys.Vec3(look.x,0,look.z);
                var projection=network==null?null:network.project(context.getClickLocation(),heading,1.6,2,2);
                if(projection==null)player.displayClientMessage(Component.translatable("message.emucab.sign_no_track"),true);
                else {
                    var tangent=projection.tangent();if(tangent.dot(player.getLookAngle())<0)tangent=tangent.scale(-1);
                    var tag=new CompoundTag();var p=projection.point();
                    tag.putString("Dimension",level.dimension().location().toString());
                    tag.putDouble("X",p.x);tag.putDouble("Y",p.y);tag.putDouble("Z",p.z);
                    tag.putDouble("DX",tangent.x);tag.putDouble("DY",tangent.y);tag.putDouble("DZ",tangent.z);
                    CustomData.update(DataComponents.CUSTOM_DATA,context.getItemInHand(),data->data.put("SpeedSignTrack",tag));
                    player.displayClientMessage(Component.translatable("message.emucab.sign_bound"),true);
                }
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        var data=context.getItemInHand().get(DataComponents.CUSTOM_DATA);
        if(data==null || !data.copyTag().getCompound("SpeedSignTrack").getString("Dimension").equals(context.getLevel().dimension().location().toString())) {
            if(player!=null && !context.getLevel().isClientSide)player.displayClientMessage(Component.translatable("message.emucab.sign_select_first"),true);
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        super.appendHoverText(stack,context,lines,flag);
        lines.add(Component.translatable("tooltip.emucab.speed_sign"));
    }
}
