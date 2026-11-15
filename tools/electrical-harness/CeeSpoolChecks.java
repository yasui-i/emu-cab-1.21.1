package emucabelectricaltest;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Exercise Minecraft's block-first interaction, not just WireSpoolItem.useOn or connect(). */
final class CeeSpoolChecks {
    static void connect(ServerLevel level,FakePlayer player,BlockPos first,BlockPos second,int secondTerminal){
        connect(level,player,first,0,second,secondTerminal,CEEItems.CREATIVE_WIRE_SPOOL.asStack());
    }
    static void connect(ServerLevel level,FakePlayer player,BlockPos first,int firstTerminal,BlockPos second,int secondTerminal,ItemStack spool){
        player.setShiftKeyDown(false);
        player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND,spool);
        click(level,player,spool,first,firstTerminal);
        if(!new InWorldNode(firstTerminal,first).equals(spool.get(CEEDataComponents.SELECTED_NODE)))
            throw new AssertionError("CEE spool first click did not select terminal at "+first+" selected="+spool.get(CEEDataComponents.SELECTED_NODE));
        click(level,player,spool,second,secondTerminal);
        if(!InfrastructureSavedData.load(level).isConnected(new InWorldNode(firstTerminal,first),new InWorldNode(secondTerminal,second)))
            throw new AssertionError("CEE spool block interaction did not connect "+first+" -> "+second+" selected="+spool.get(CEEDataComponents.SELECTED_NODE));
    }
    static void facings(ServerLevel level,FakePlayer player,BlockPos panto,BlockPos motor){
        var original=level.getBlockState(panto);var electrical=InfrastructureSavedData.load(level);
        var connector=panto.west(3);
        level.setBlockAndUpdate(connector.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(connector,de.mrjulsen.paw.registry.ModBlocks.INSULATOR_BROWN.getDefaultState());
        var connection=new com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection(new InWorldNode(0,panto),new InWorldNode(0,connector));
        for(var facing:Direction.Plane.HORIZONTAL){
            level.setBlockAndUpdate(panto,original.setValue(de.mrjulsen.paw.block.PantographBlock.FACING,facing));
            for(boolean reverse:new boolean[]{false,true}){
                for(boolean copper:new boolean[]{false,true}){
                    if(electrical.isConnected(new InWorldNode(0,panto),new InWorldNode(0,connector)))electrical.removeConnectionNoDrops(connection);
                    var spool=copper?CEEItems.COPPER_WIRE_SPOOL.asStack():CEEItems.CREATIVE_WIRE_SPOOL.asStack();
                    if(reverse)connect(level,player,connector,0,panto,0,spool);
                    else connect(level,player,panto,0,connector,0,spool);
                }
            }
        }
        level.setBlockAndUpdate(panto,original);
        electrical.removeConnectionNoDrops(connection);
        electrical.connect(new InWorldNode(0,panto),new InWorldNode(0,motor),com.george_vi.electroenergetics.CEEWireTypes.CREATIVE.get());
        var receiver=(de.mrjulsen.paw.blockentity.PantographBlockEntity)level.getBlockEntity(panto);
        if(receiver.isExpandable())throw new AssertionError("Spool clicks must not toggle PAW pantograph");
    }
    static void click(ServerLevel level,FakePlayer player,ItemStack spool,BlockPos pos,int terminal){
        player.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+1.5);
        var state=level.getBlockState(pos);
        var local=((com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock<?>)state.getBlock()).getNodePosition(level,pos,state,terminal);
        var hit=new BlockHitResult(local.add(pos.getX(),pos.getY(),pos.getZ()),Direction.UP,pos,false);
        player.gameMode.useItemOn(player,level,spool,InteractionHand.MAIN_HAND,hit);
    }
}
