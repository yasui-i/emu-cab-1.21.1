package emucabdisplaytest;

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
@Mod("emucabdisplaytest")
public final class DisplayHarness {
    public static final BlockPos CENTER=new BlockPos(0,85,0);
    private double speed=184;
    private dev.ryanhcode.sable.sublevel.ServerSubLevel audioBody;
    private BlockPos audioPos;

    private boolean savedSettings;private int mode;
    private long sequence=1_000_000_000L;
    public DisplayHarness(){NeoForge.EVENT_BUS.addListener(this::started);NeoForge.EVENT_BUS.addListener(this::login);NeoForge.EVENT_BUS.addListener(this::commands);NeoForge.EVENT_BUS.addListener(this::tick);}
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
        if(desk.clockMode()!=0||desk.dateMode()!=0)throw new AssertionError("New cab defaults incorrect");
        level.setBlockAndUpdate(CENTER.north(2).east(),net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("simurail:physics_bogey")).defaultBlockState());
        System.out.println("EMU_SPEED_SCENE: synthetic 200/350/0 samples; real payload; fixed body has zero pose velocity");
    }
    private void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player))return;
        player.setGameMode(GameType.CREATIVE);player.getInventory().clearContent();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(com.yasui_i.emucab.registry.ModItems.TELEMETRY_TOOL.get()));
        player.teleportTo(player.serverLevel(),.5,85.45,-1.3,0,30);
        player.getServer().getPlayerList().op(player.getGameProfile());
    }
    private void tick(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)||level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||level.getGameTime()%2!=0)return;
        if(!savedSettings && level.getBlockEntity(CENTER) instanceof CabDeskBlockEntity test && test.locomotiveNumber().equals("EMU-1234")){
            var tag=test.getUpdateTag(level.registryAccess());
            var loaded=new CabDeskBlockEntity(CENTER,test.getBlockState());loaded.loadWithComponents(tag,level.registryAccess());
            if(!loaded.locomotiveNumber().equals("EMU-1234")||loaded.dateMode()!=1||!loaded.customDate().equals("2024-02-29"))throw new AssertionError("NBT display settings lost");
            savedSettings=true;System.out.println("EMU_DISPLAY_SETTINGS_NBT: PASS");
        }
        var packet=new CabSpeedPayload(CENTER,sequence++,speed);
        if(mode>0){
            var cars=com.yasui_i.emucab.telemetry.CabTelemetry.empty();
            for(int i=0;i<8;i++)cars[i]=new com.yasui_i.emucab.telemetry.CabTelemetry.Car(2,2,2,mode==1?3400:0,mode==2?2000:0,mode==2?.7f:0,mode==2?.7f:0,false,false);
            for(var player:level.players())net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new com.yasui_i.emucab.network.CabTelemetryPayload(CENTER,sequence,cars));
        }
        for(var player:level.players())PacketDistributor.sendToPlayer(player,packet);
        if(level.getBlockEntity(CENTER) instanceof CabDeskBlockEntity desk && level.getGameTime()%5==0){
            try {
                var field=CabDeskBlockEntity.class.getDeclaredField("speedPlan");field.setAccessible(true);
                field.set(desk,new com.yasui_i.emucab.track.CabSpeedPlan(true,1024,.7,java.util.List.of(
                    new com.yasui_i.emucab.track.CabSpeedPlan.Target(650,80),
                    new com.yasui_i.emucab.track.CabSpeedPlan.Target(900,120))));
                level.sendBlockUpdated(CENTER,desk.getBlockState(),desk.getBlockState(),2|4);
            } catch(Exception e){throw new RuntimeException(e);}
        }
    }
    private void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("griptest").requires(s->s.hasPermission(4)).executes(c->{
            var player=c.getSource().getPlayerOrException();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
            var desk=(CabDeskBlockEntity)c.getSource().getLevel().getBlockEntity(audioPos);
            var control=java.util.Arrays.stream(com.yasui_i.emucab.control.CabGeometry.CONTROLS).filter(v->v.action()==CabAction.HANDLE).findFirst().orElseThrow();
            var eye=com.yasui_i.emucab.control.CabHitTesting.fromModel(audioPos,desk.getBlockState().getValue(CabDeskBlock.FACING),
                com.yasui_i.emucab.control.CabHitTesting.bounds(control,0).getCenter().add(0,8,-8));
            var world=audioBody.logicalPose().transformPosition(eye);
            player.teleportTo(player.serverLevel(),world.x,world.y-player.getEyeHeight(),world.z,180,0);
            return 1;
        }));
        event.getDispatcher().register(Commands.literal("rangewrench").requires(s->s.hasPermission(4)).executes(c->{
            c.getSource().getPlayerOrException().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(com.simibubi.create.AllItems.WRENCH.get()));return 1;
        }));
        event.getDispatcher().register(Commands.literal("audioassembly").requires(s->s.hasPermission(4)).executes(c->{
            var level=c.getSource().getLevel();var pos=CENTER.east(10);
            level.setBlockAndUpdate(pos,ModBlocks.CAB_DESK.get().defaultBlockState());
            audioBody=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level,pos,java.util.List.of(pos),dev.ryanhcode.sable.companion.math.BoundingBox3i.from(java.util.List.of(pos)));
            audioPos=audioBody.getPlot().getCenterBlock();
            return 1;
        }));
        event.getDispatcher().register(Commands.literal("audiocue").requires(s->s.hasPermission(4)).executes(c->{
            var desk=(CabDeskBlockEntity)c.getSource().getLevel().getBlockEntity(CENTER);
            return desk.requestAtpAnnouncement(com.yasui_i.emucab.control.AtpAnnouncement.NEUTRAL_SECTION_AHEAD)?1:0;
        }));
        event.getDispatcher().register(Commands.literal("vehicleui").requires(s->s.hasPermission(4)).then(Commands.argument("mode",IntegerArgumentType.integer(1,3)).executes(c->{
            mode=IntegerArgumentType.getInteger(c,"mode");var desk=(CabDeskBlockEntity)c.getSource().getLevel().getBlockEntity(CENTER);
            desk.controls().switches.put(CabAction.PANTOGRAPH,1);desk.controls().switches.put(CabAction.BREAKER,1);desk.controls().direction=1;
            desk.controls().notch=mode==1?-2:mode==2?-6:0;desk.controls().emergency=mode==3;desk.telemetryChanged();return 1;
        })));
        event.getDispatcher().register(Commands.literal("emucabdisplaymon").requires(s->s.hasPermission(4)).executes(context->{
            var player=context.getSource().getPlayerOrException();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
            var desk=(CabDeskBlockEntity)context.getSource().getLevel().getBlockEntity(CENTER);
            desk.controls().switches.put(CabAction.PANTOGRAPH,1);
            desk.activate(java.util.Arrays.stream(com.yasui_i.emucab.control.CabGeometry.CONTROLS).filter(c->c.number().equals("B140")).findFirst().orElseThrow(),player);return 1;
        }));
        event.getDispatcher().register(Commands.literal("emucabdisplayeb").requires(s->s.hasPermission(4)).executes(context->{
            var desk=(CabDeskBlockEntity)context.getSource().getLevel().getBlockEntity(CENTER);desk.controls().emergency=true;
            context.getSource().getLevel().sendBlockUpdated(CENTER,desk.getBlockState(),desk.getBlockState(),2|4);return 1;
        }));
        event.getDispatcher().register(Commands.literal("emucabspeed").requires(s->s.hasPermission(4))
                .then(Commands.argument("speed",IntegerArgumentType.integer(0,1000)).executes(context->{
                    speed=IntegerArgumentType.getInteger(context,"speed");return 1;
                })));
    }
}
