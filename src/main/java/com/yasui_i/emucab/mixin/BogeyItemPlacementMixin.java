package com.yasui_i.emucab.mixin;

import com.yasui_i.emucab.telemetry.BogeyIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Old picked items can still contain a copied identity. Placement starts a new identity. */
@Mixin(BlockItem.class)
public abstract class BogeyItemPlacementMixin {
    @Inject(method="updateCustomBlockEntityTag(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Z",at=@At("RETURN"))
    private static void emucab$placed(Level level,Player player,BlockPos pos,ItemStack stack,CallbackInfoReturnable<Boolean> cir){
        if(!level.isClientSide&&cir.getReturnValueZ()&&level.getBlockEntity(pos) instanceof BogeyIdentity identity){
            identity.emucab$renewIdentity();
            var be=level.getBlockEntity(pos);be.setChanged();
        }
    }
}
