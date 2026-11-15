package emucabspeedtest;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.block.CabDeskPart;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.CabAction;
import com.yasui_i.emucab.network.CabSpeedPayload;
import com.yasui_i.emucab.registry.ModBlocks;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Deliberately synthetic speeds, transported through the production payload receiver. */
@Mod("emucabspeedtest")
public final class SpeedHarness {
    public static final BlockPos CENTER=new BlockPos(0,85,0);
    private double speed=200;
    private long sequence=1_000_000_000L;
    public SpeedHarness(){NeoForge.EVENT_BUS.addListener(this::started);NeoForge.EVENT_BUS.addListener(this::login);NeoForge.EVENT_BUS.addListener(this::commands);NeoForge.EVENT_BUS.addListener(this::tick);}
    private void started(ServerStartedEvent event){
        var level=event.getServer().overworld();level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,event.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,event.getServer());
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){
            level.setBlockAndUpdate(new BlockPos(x,84,z),Blocks.SMOOTH_STONE.defaultBlockState());
            for(int y=85;y<=89;y++)if(!level.isEmptyBlock(new BlockPos(x,y,z)))level.removeBlock(new BlockPos(x,y,z),false);
        }
        var state=ModBlocks.CAB_DESK.get().defaultBlockState().setValue(CabDeskBlock.FACING,Direction.NORTH);
        level.setBlockAndUpdate(CENTER,state);
        level.setBlockAndUpdate(CENTER.west(),state.setValue(CabDeskBlock.PART,CabDeskPart.LEFT));
        level.setBlockAndUpdate(CENTER.east(),state.setValue(CabDeskBlock.PART,CabDeskPart.RIGHT));
        var desk=(CabDeskBlockEntity)level.getBlockEntity(CENTER);
        desk.controls().switches.put(CabAction.MASTER,1);desk.setChanged();
        System.out.println("EMU_SPEED_SCENE: synthetic 200/350/0 samples; real payload; fixed body has zero pose velocity");
    }
    private void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player))return;
        player.setGameMode(GameType.SPECTATOR);player.getInventory().clearContent();
        player.teleportTo(player.serverLevel(),.5,85.45,-1.3,0,30);
        player.getServer().getPlayerList().op(player.getGameProfile());
    }
    private void tick(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)||level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||level.getGameTime()%2!=0)return;
        var packet=new CabSpeedPayload(CENTER,sequence++,speed);
        for(var player:level.players())PacketDistributor.sendToPlayer(player,packet);
    }
    private void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("emucabspeed").requires(s->s.hasPermission(4))
                .then(Commands.argument("speed",IntegerArgumentType.integer(0,1000)).executes(context->{
                    speed=IntegerArgumentType.getInteger(context,"speed");return 1;
                })));
    }
}
