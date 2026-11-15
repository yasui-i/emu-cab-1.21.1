package emucabgaugetest;

import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.block.CabDeskPart;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.CabAction;
import com.yasui_i.emucab.registry.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod("emucabgaugetest")
public final class GaugeHarness {
    public static final BlockPos CENTER=new BlockPos(0,85,0);
    public GaugeHarness(){NeoForge.EVENT_BUS.addListener(this::started);NeoForge.EVENT_BUS.addListener(this::login);}
    void started(ServerStartedEvent e){
        var level=e.getServer().overworld();level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,e.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,e.getServer());
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)level.setBlockAndUpdate(new BlockPos(x,84,z),Blocks.SMOOTH_STONE.defaultBlockState());
        var state=ModBlocks.CAB_DESK.get().defaultBlockState().setValue(CabDeskBlock.FACING,Direction.NORTH);
        for(int i=-1;i<=1;i++)level.setBlockAndUpdate(CENTER.east(i),state.setValue(CabDeskBlock.PART,i<0?CabDeskPart.LEFT:i>0?CabDeskPart.RIGHT:CabDeskPart.CENTER));
        var desk=(CabDeskBlockEntity)level.getBlockEntity(CENTER);desk.controls().switches.put(CabAction.MASTER,1);desk.setChanged();
    }
    void login(PlayerEvent.PlayerLoggedInEvent e){
        if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p){
            p.setGameMode(GameType.SPECTATOR);p.teleportTo(p.serverLevel(),.1,85.45,-1.3,0,30);p.getServer().getPlayerList().op(p.getGameProfile());
        }
    }
}
