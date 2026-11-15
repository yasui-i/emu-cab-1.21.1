package emucabtest;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.*;
import com.yasui_i.emucab.balise.*;
import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.blockentity.SpeedSignBlockEntity;
import com.yasui_i.emucab.control.*;
import com.yasui_i.emucab.registry.*;
import com.yasui_i.emucab.track.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.nio.file.*;
import java.util.*;

@Mod("emucabbalisetest")
public final class BaliseHarness {
    public BaliseHarness(){NeoForge.EVENT_BUS.addListener(this::started);NeoForge.EVENT_BUS.addListener(this::login);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void started(ServerStartedEvent event){
        if(Boolean.getBoolean("emucab.balise.visual")){visualScene(event.getServer().overworld());return;}
        String result;
        try{run(event.getServer().overworld());result="PASS: complete Create rail only; rendered curves rejected; wrench removal/rail preserved; survival install/remove; configuration permissions; NBT; slope/legacy curve compatibility; legacy sign migration; moving Sable directional limit and bidirectional voice";}
        catch(Throwable t){t.printStackTrace();result="FAIL: "+t;}
        System.out.println("EMU_BALISE_CHECK: "+result);
        try{Files.writeString(Path.of("balise-check-result.txt"),result);}catch(Exception e){throw new RuntimeException(e);}
        event.getServer().halt(false);
    }
    static void run(ServerLevel level)throws Exception{
        var store=BaliseStore.get(level);for(var d:store.all())store.remove(d.id());
        var track=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","track"));
        BlockPos pos=new BlockPos(8,85,8);
        level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        var trackState=track.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO);
        level.setBlockAndUpdate(pos,trackState);
        var target=BaliseTarget.resolve(level,pos,null,false);check(target!=null,"Actual straight track not resolved");
        check(target.anchor().distanceTo(new Vec3(8.5,85,8.5))<1,"Track anchor wrong "+target.anchor());
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"balise-test"));
        player.setGameMode(GameType.SURVIVAL);player.setPos(8.5,85,10.5);player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.SPEED_SIGN.get(),2));
        var hit=new BlockHitResult(target.anchor(),Direction.UP,pos,false);
        var result=player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
        check(result.consumesAction(),"Rail item install did not consume use");
        check(level.getBlockState(pos).equals(trackState),"Install replaced track");
        check(level.isEmptyBlock(pos.above()),"Install occupied block above track");
        check(player.getMainHandItem().getCount()==1,"Survival install consumption");
        var device=store.find(target);check(device!=null&&device.kind()==BaliseData.Kind.LIMIT,"Limit device missing");
        check(!BaliseService.install(player,target,InteractionHand.MAIN_HAND),"Duplicate install allowed");
        check(BaliseService.configure(player,new BalisePackets.Configure(device.id(),120,"neutral_section_ahead",true,true)),"Configure failed");
        check(store.get(device.id()).limit()==120&&store.get(device.id()).forward().dot(device.forward())<-.9,"Limit direction or value not saved");
        check(!BaliseService.configure(player,new BalisePackets.Configure(device.id(),0,"neutral_section_ahead",true,false)),"Invalid limit allowed");
        player.setPos(100,85,100);
        check(!BaliseService.configure(player,new BalisePackets.Configure(device.id(),10,"neutral_section_ahead",true,false)),"Remote configure allowed");
        player.setPos(8.5,85,10.5);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        check(!BaliseService.interact(player,target,true),"Empty-hand removal allowed");
        player.setShiftKeyDown(true);
        check(!BaliseService.interact(player,target,true)&&store.find(target)!=null,"Sneak empty-hand removal allowed");
        check(BaliseService.interact(player,target,false)&&store.find(target)!=null,"Sneak empty-hand settings failed");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
        check(!BaliseService.interact(player,target,true),"Ordinary item removal allowed");
        player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,AllItems.WRENCH.asStack());
        player.setPos(100,85,100);
        var denied=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit);
        BaliseService.clickRail(denied);
        check(denied.isCanceled()&&denied.getCancellationResult()==InteractionResult.FAIL&&store.find(target)!=null,
                "Rejected wrench interaction fell through to supporting rail");
        player.setPos(8.5,85,10.5);
        var removal=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit);
        NeoForge.EVENT_BUS.post(removal);
        check(removal.isCanceled()&&removal.getCancellationResult().consumesAction()&&store.find(target)==null,
                "Non-sneaking Create wrench removal failed");
        check(level.getBlockState(pos).equals(trackState),"Remove destroyed track");
        check(player.getInventory().contains(new ItemStack(ModItems.SPEED_SIGN.get())),"Removed item not returned");
        var plainRail=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit);
        BaliseService.clickRail(plainRail);
        check(!plainRail.isCanceled(),"Plain rail wrench use intercepted");
        player.setShiftKeyDown(false);
        // Disallow placing the device as a normal block.
        BlockPos stone=pos.south(3);level.setBlockAndUpdate(stone,Blocks.STONE.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.BALISE.get()));
        player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(stone.getCenter(),Direction.UP,stone,false)));
        check(store.all().isEmpty()&&level.isEmptyBlock(stone.above()),"Attachment placed as ordinary block");
        var voice=new BaliseData(UUID.randomUUID(),target,BaliseData.Kind.ANNOUNCEMENT,80,"neutral_section_ahead",true);
        store.add(voice);var nbt=store.save(new CompoundTag(),level.registryAccess());
        var load=BaliseStore.class.getDeclaredMethod("load",CompoundTag.class,HolderLookup.Provider.class);load.setAccessible(true);
        var reloaded=(BaliseStore)load.invoke(null,nbt,level.registryAccess());
        check(reloaded.get(voice.id()).equals(voice),"Balise NBT roundtrip changed data");
        store.remove(voice.id());
        BlockPos slopePos=pos.north(3);level.setBlockAndUpdate(slopePos,trackState.setValue(TrackBlock.SHAPE,TrackShape.AE));
        var slope=BaliseTarget.resolve(level,slopePos,null,false);
        check(slope!=null&&Math.abs(slope.tangent().y)>.1&&slope.normal().y>.1,"Slope placement basis wrong");
        check(Math.abs(slope.normal().dot(slope.tangent()))<.01,"Slope normal not perpendicular");
        player.setPos(slope.anchor().x,slope.anchor().y,slope.anchor().z+2);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.BALISE.get()));
        check(BaliseService.install(player,slope,InteractionHand.MAIN_HAND)&&store.find(slope)!=null,"Complete slope rail install rejected");
        player.setItemInHand(InteractionHand.MAIN_HAND,AllItems.WRENCH.asStack());
        check(BaliseService.interact(player,slope,true)&&store.find(slope)==null,"Slope wrench removal failed");
        checkCurve(level,trackState,pos.north(6),player);
        // Migrate an actual legacy block and keep its value/direction.
        BlockPos oldPos=pos.south(2);level.setBlockAndUpdate(oldPos,ModBlocks.SPEED_SIGN.get().defaultBlockState());
        var old=(SpeedSignBlockEntity)level.getBlockEntity(oldPos);var tag=old.getUpdateTag(level.registryAccess());
        tag.putBoolean("Bound",true);tag.putInt("Limit",90);
        tag.putDouble("X",target.anchor().x);tag.putDouble("Y",target.anchor().y);tag.putDouble("Z",target.anchor().z);
        tag.putDouble("DX",1);tag.putDouble("DY",0);tag.putDouble("DZ",0);
        old.loadWithComponents(tag,level.registryAccess());
        check(BaliseService.migrate(old),"Legacy migration failed");
        check(level.isEmptyBlock(oldPos),"Legacy block not removed");
        var migrated=store.find(target);check(migrated!=null&&migrated.limit()==90&&migrated.forward().x>.9,"Migration lost limit/direction");
        store.remove(migrated.id());
        checkMovingReception(level,trackState,pos);
        // Track destruction cleans attachment and leaves one dropped item.
        store.add(new BaliseData(UUID.randomUUID(),target,BaliseData.Kind.ANNOUNCEMENT,80,"neutral_section_ahead",true));
        level.removeBlock(pos,false);setTime(level,200);
        NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(()->true,level));
        check(store.find(target)==null,"Broken rail retained balise");
    }
    static void checkCurve(ServerLevel level,net.minecraft.world.level.block.state.BlockState state,BlockPos a,net.minecraft.server.level.ServerPlayer player){
        BlockPos b=a.offset(8,0,8);level.setBlockAndUpdate(a,state.setValue(TrackBlock.HAS_BE,true));level.setBlockAndUpdate(b,state.setValue(TrackBlock.HAS_BE,true));
        var connection=new BezierConnection(net.createmod.catnip.data.Couple.create(a,b),
                net.createmod.catnip.data.Couple.create(a.getCenter(),b.getCenter()),
                net.createmod.catnip.data.Couple.create(new Vec3(1,0,0),new Vec3(0,0,-1)),
                net.createmod.catnip.data.Couple.create(new Vec3(0,1,0),new Vec3(0,1,0)),true,false,TrackMaterial.ANDESITE);
        ((TrackBlockEntity)level.getBlockEntity(a)).addConnection(connection);
        ((TrackBlockEntity)level.getBlockEntity(b)).addConnection(connection.secondary());
        var target=BaliseTarget.resolve(level,a,new BezierTrackPointLocation(b,5),false);
        check(target!=null&&target.curve()!=null&&target.stillValid(level),"Curve attachment resolve failed");
        check(target.anchor().distanceTo(a.getCenter())>1,"Curve snapped to endpoint");
        var store=BaliseStore.get(level);
        player.setPos(target.anchor().x,target.anchor().y,target.anchor().z+2);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.BALISE.get(),2));
        check(!BaliseService.install(player,target,InteractionHand.MAIN_HAND)&&store.find(target)==null,
                "Rendered curve installation allowed");
        check(player.getMainHandItem().getCount()==2,"Rejected curve consumed item");
        var old=new BaliseData(UUID.randomUUID(),target,BaliseData.Kind.ANNOUNCEMENT,80,"neutral_section_ahead",true);
        check(store.add(old),"Legacy curve setup failed");
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        check(BaliseService.interact(player,target,false),"Legacy curve settings unavailable");
        player.setItemInHand(InteractionHand.MAIN_HAND,AllItems.WRENCH.asStack());
        check(BaliseService.interact(player,target,true)&&store.find(target)==null,"Legacy curve wrench removal failed");
        check(((TrackBlockEntity)level.getBlockEntity(a)).getConnections().containsKey(b),"Curve removal destroyed rail connection");
        var endpoint=BaliseTarget.resolve(level,a,null,false);
        player.setPos(endpoint.anchor().x,endpoint.anchor().y,endpoint.anchor().z+2);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.BALISE.get()));
        check(BaliseService.install(player,endpoint,InteractionHand.MAIN_HAND),"Complete curve endpoint rail rejected");
        player.setItemInHand(InteractionHand.MAIN_HAND,AllItems.WRENCH.asStack());
        check(BaliseService.interact(player,endpoint,true),"Complete curve endpoint removal failed");
    }
    static void checkMovingReception(ServerLevel level,net.minecraft.world.level.block.state.BlockState trackState,BlockPos origin)throws Exception{
        var store=BaliseStore.get(level);for(var d:store.all())store.remove(d.id());
        for(int x=0;x<=100;x++)level.setBlock(new BlockPos(x,85,8),trackState,2);
        var limitTarget=BaliseTarget.resolve(level,new BlockPos(40,85,8),null,false);
        if(limitTarget.forward().x<0)limitTarget=limitTarget.reversed();
        var voiceTarget=BaliseTarget.resolve(level,new BlockPos(30,85,8),null,false);
        store.add(new BaliseData(UUID.randomUUID(),limitTarget,BaliseData.Kind.LIMIT,80,"neutral_section_ahead",true));
        var voice=new BaliseData(UUID.randomUUID(),voiceTarget,BaliseData.Kind.ANNOUNCEMENT,80,"neutral_section_ahead",true);store.add(voice);
        // Deterministic real Create track graph, independent of delayed graph propagation.
        var graph=new TrackGraph();var left=new TrackNodeLocation(.5,85,8.5).in(level);var right=new TrackNodeLocation(100.5,85,8.5).in(level);
        graph.loadNode(left,TrackGraph.nextNodeId(),new Vec3(0,1,0));graph.loadNode(right,TrackGraph.nextNodeId(),new Vec3(0,1,0));
        var l=graph.locateNode(left);var r=graph.locateNode(right);
        graph.putConnection(l,r,new TrackEdge(l,r,null,TrackMaterial.ANDESITE));graph.putConnection(r,l,new TrackEdge(r,l,null,TrackMaterial.ANDESITE));
        Create.RAILWAYS.sided(level).trackNetworks.clear();Create.RAILWAYS.sided(level).trackNetworks.put(graph.id,graph);
        BlockPos deskPos=new BlockPos(5,88,8);level.setBlockAndUpdate(deskPos,ModBlocks.CAB_DESK.get().defaultBlockState().setValue(CabDeskBlock.FACING,Direction.WEST));
        var moved=List.of(deskPos);var sub=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level,deskPos,moved,dev.ryanhcode.sable.companion.math.BoundingBox3i.from(moved));
        BlockPos center=sub.getPlot().getCenterBlock();var desk=(CabDeskBlockEntity)level.getBlockEntity(center);
        var physics=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).physicsSystem().getPhysicsHandle(sub);
        desk.controls().switches.put(CabAction.MASTER,1);var limits=new CabTrackLimits();
        setTime(level,1000);SpeedSignRegistry.invalidate(level);
        var speedField=CabDeskBlockEntity.class.getDeclaredField("speedKmh");speedField.setAccessible(true);speedField.setDouble(desk,120);
        var reading=limits.update(desk);check(reading.next()==80,"Advance lower-limit preview missing "+reading);
        sub.logicalPose().position().add(40,0,0);setTime(level,1005);
        // A physical speed bound accompanies the synthetic swept movement.
        physics.addLinearAndAngularVelocity(new org.joml.Vector3d(160,0,0),new org.joml.Vector3d());
        reading=limits.update(desk);check(reading.current()==80,"Passed directional limit missing "+reading);
        var audioField=CabDeskBlockEntity.class.getDeclaredField("atpAudio");audioField.setAccessible(true);var audio=(AtpAudioController)audioField.get(desk);
        check(audio.tick(true,0,350)==AtpAnnouncement.NEUTRAL_SECTION_AHEAD,"Moving cab did not receive voice");
        for(int i=0;i<200;i++)audio.tick(true,0,350);
        physics.addLinearAndAngularVelocity(new org.joml.Vector3d(-160,0,0),new org.joml.Vector3d());
        setTime(level,1010);limits.update(desk);check(audio.tick(true,0,350)==null,"Stopped cab repeated voice");
        desk.controls().direction=-1;setTime(level,1015);limits.update(desk);
        sub.logicalPose().position().add(-40,0,0);physics.addLinearAndAngularVelocity(new org.joml.Vector3d(-160,0,0),new org.joml.Vector3d());setTime(level,1020);
        reading=limits.update(desk);
        check(audio.tick(true,0,350)==AtpAnnouncement.NEUTRAL_SECTION_AHEAD,"Reverse cab did not receive bidirectional voice");
        check(reading.current()!=80,"Reverse travel received wrong-direction limit");
        System.out.println("EMU_BALISE_SABLE: 40-block swept passage, next/current directional limit, both-way voice, stopped dedupe passed");
    }
    static void setTime(ServerLevel level,long time){((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(time);}
    static void visualScene(ServerLevel level){
        var track=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create","track")).defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO);
        for(int x=6;x<21;x++){
            for(int z=7;z<=12;z++)level.setBlockAndUpdate(new BlockPos(x,84,z),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(x,85,8),track);
        }
        var store=BaliseStore.get(level);for(var d:store.all())store.remove(d.id());
        store.add(new BaliseData(UUID.randomUUID(),BaliseTarget.resolve(level,new BlockPos(14,85,8),null,false),BaliseData.Kind.LIMIT,80,"neutral_section_ahead",true));
        level.setDayTime(6000);System.out.println("EMU_BALISE_VISUAL: scene ready");
    }
    private void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event){
        if(!Boolean.getBoolean("emucab.balise.visual")||!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player))return;
        player.setGameMode(GameType.CREATIVE);player.getInventory().setItem(0,new ItemStack(ModItems.BALISE.get()));
        player.getInventory().setItem(1,ItemStack.EMPTY);player.getInventory().setItem(2,new ItemStack(ModItems.SPEED_SIGN.get()));
        player.getInventory().setItem(3,AllItems.WRENCH.asStack());
        player.getInventory().selected=0;
        player.inventoryMenu.broadcastChanges();player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
        player.teleportTo(player.serverLevel(),10.5,85,11.5,180,28);
        player.getServer().getPlayerList().op(player.getGameProfile());BaliseService.sendSnapshot(player);
    }
}
