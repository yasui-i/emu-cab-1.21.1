package com.yasui_i.emucab.balise;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import java.util.List;

/** An attachment item: it never places or replaces a Minecraft block. */
public final class BaliseItem extends Item {
    private final BaliseData.Kind kind;
    public BaliseItem(Properties properties,BaliseData.Kind kind){super(properties);this.kind=kind;}
    public BaliseData.Kind kind(){return kind;}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();if(player==null)return InteractionResult.FAIL;
        var target=BaliseTarget.forLook(context.getLevel(),context.getClickedPos(),null,player.getLookAngle());
        if(target==null)return InteractionResult.PASS;
        if(player instanceof ServerPlayer server)BaliseService.install(server,target,context.getHand());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("tooltip.emucab.balise_install"));
        lines.add(Component.translatable(kind==BaliseData.Kind.LIMIT?"tooltip.emucab.balise_limit":"tooltip.emucab.balise_announcement"));
    }
}
