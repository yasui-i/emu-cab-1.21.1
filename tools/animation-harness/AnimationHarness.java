package emucabanimationtest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.block.CabDeskPart;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.CabAction;
import com.yasui_i.emucab.control.CabGeometry;
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

/** Only lives in an isolated build directory. Actions call the real server activate API. */
@Mod("emucabanimationtest")
public final class AnimationHarness {
    public static final BlockPos CENTER=new BlockPos(0,85,0);
    public AnimationHarness(){NeoForge.EVENT_BUS.addListener(this::started);NeoForge.EVENT_BUS.addListener(this::login);NeoForge.EVENT_BUS.addListener(this::commands);}
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
        System.out.println("EMU_ANIMATION_SCENE: new cab, key off, direction zero");
    }
    private void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player))return;
        player.setGameMode(Boolean.getBoolean("emucab.test.directionAxis")?GameType.SPECTATOR:GameType.CREATIVE);player.getInventory().clearContent();
        player.getAbilities().flying=true;player.getAbilities().mayfly=true;player.onUpdateAbilities();
        if(Boolean.getBoolean("emucab.test.directionAxis"))player.teleportTo(player.serverLevel(),-.390231,84.670083,.136081,35,15.664F);
        else player.teleportTo(player.serverLevel(),.5,85.45,-1.3,0,30);
        player.getServer().getPlayerList().op(player.getGameProfile());
    }
    private void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("emucabanimation").requires(s->s.hasPermission(4))
                .then(Commands.argument("number",StringArgumentType.word()).executes(context->{
                    var player=context.getSource().getPlayerOrException();var level=player.serverLevel();
                    var desk=(CabDeskBlockEntity)level.getBlockEntity(CENTER);
                    String number=StringArgumentType.getString(context,"number");
                    var control=java.util.Arrays.stream(CabGeometry.CONTROLS).filter(c->c.number().equals(number)).findFirst().orElseThrow();
                    desk.activate(control,player);return 1;
                })));
    }
}
