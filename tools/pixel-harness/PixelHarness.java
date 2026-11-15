package emucabpixeltest;

import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.block.CabDeskPart;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.CabAction;
import com.yasui_i.emucab.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod("emucabpixeltest")
public final class PixelHarness {
    public static final BlockPos[] CENTERS={new BlockPos(0,85,0),new BlockPos(9,85,0),new BlockPos(0,85,9),new BlockPos(9,85,9)};
    public static final Direction[] FACINGS={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    public PixelHarness(){NeoForge.EVENT_BUS.addListener(this::started);NeoForge.EVENT_BUS.addListener(this::login);}
    private void started(ServerStartedEvent event){
        var level=event.getServer().overworld();
        level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,event.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,event.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,event.getServer());
        for(int x=-8;x<18;x++)for(int z=-8;z<18;z++){
            level.setBlockAndUpdate(new BlockPos(x,84,z),Blocks.SMOOTH_STONE.defaultBlockState());
            for(int y=85;y<=89;y++)if(!level.isEmptyBlock(new BlockPos(x,y,z)))level.removeBlock(new BlockPos(x,y,z),false);
        }
        for(int i=0;i<4;i++){
            var facing=FACINGS[i];var center=CENTERS[i];var state=ModBlocks.CAB_DESK.get().defaultBlockState().setValue(CabDeskBlock.FACING,facing);
            level.setBlockAndUpdate(center,state);
            level.setBlockAndUpdate(center.relative(facing.getClockWise(),-1),state.setValue(CabDeskBlock.PART,CabDeskPart.LEFT));
            level.setBlockAndUpdate(center.relative(facing.getClockWise()),state.setValue(CabDeskBlock.PART,CabDeskPart.RIGHT));
            var desk=(CabDeskBlockEntity)level.getBlockEntity(center);
            desk.controls().switches.put(CabAction.MASTER,1);desk.controls().switches.put(CabAction.PANTOGRAPH,1);desk.controls().switches.put(CabAction.BREAKER,1);
            desk.controls().direction=1;desk.controls().notch=new int[]{0,7,-7,0}[i];desk.controls().emergency=i==3;
            desk.setChanged();level.sendBlockUpdated(center,state,state,2);
        }
        System.out.println("EMU_PIXEL_SCENE: four facings, powered LCDs, handle N/P7/B7/EB ready");
    }
    private void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player))return;
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(player.serverLevel(),.5,85.45,-3.6,0,12);
        player.getServer().getPlayerList().op(player.getGameProfile());
    }
}
