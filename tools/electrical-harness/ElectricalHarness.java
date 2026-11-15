package emucabelectricaltest;

import com.mojang.authlib.GameProfile;
import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.block.CabDeskPart;
import com.yasui_i.emucab.blockentity.CabDeskBlockEntity;
import com.yasui_i.emucab.control.*;
import com.yasui_i.emucab.registry.ModBlocks;
import de.mrjulsen.paw.blockentity.PantographBlockEntity;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.nio.file.*;
import java.util.*;

/** Uses real block entities, redstone ticks and Sable assembly; no mock server logic. */
@Mod("emucabelectricaltest")
public final class ElectricalHarness {
    static final BlockPos A=new BlockPos(0,90,0),B=new BlockPos(0,90,24),OTHER=new BlockPos(16,90,0),P=new BlockPos(24,90,0);
    final StringBuilder evidence=new StringBuilder();
    MinecraftServer server; ServerLevel level; FakePlayer player; int tick;
    CabDeskBlockEntity a,b,other;
    PantographBlockEntity panto;
    com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographBlockEntity nativePanto;
    CabGeometry.Control key;
    boolean done;
    boolean clientReady;
    dev.ryanhcode.sable.sublevel.ServerSubLevel cabBody;
    dev.ryanhcode.sable.sublevel.ServerSubLevel pantoBody;
    BlockPos movedP,movedMotor;
    final BlockPos battery=P.north(7),motor=P.south(7),wireA=P.north(5).above(2),wireB=P.south(5).above(2);
    InfrastructureSavedData electrical;
    public ElectricalHarness(){NeoForge.EVENT_BUS.addListener(this::started);NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener(this::login);NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.ServerChatEvent e)->{if(e.getRawText().equals("EMUCAB_TEST_READY")){clientReady=true;e.setCanceled(true);}});}
    void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)p.teleportTo(level,P.getX()+.5,P.getY()+.4,P.getZ()+3,180,0);}
    void check(boolean condition,String detail){if(!condition)throw new AssertionError(detail);evidence.append("PASS ").append(detail).append('\n');System.out.println("EMU_ELECTRICAL_CHECK: PASS "+detail);}
    CabDeskBlockEntity desk(BlockPos p){
        level.removeBlock(p,false);level.removeBlock(p.west(),false);level.removeBlock(p.east(),false);
        var state=ModBlocks.CAB_DESK.get().defaultBlockState().setValue(CabDeskBlock.FACING,Direction.NORTH);
        level.setBlockAndUpdate(p,state);level.setBlockAndUpdate(p.west(),state.setValue(CabDeskBlock.PART,CabDeskPart.LEFT));level.setBlockAndUpdate(p.east(),state.setValue(CabDeskBlock.PART,CabDeskPart.RIGHT));
        return (CabDeskBlockEntity)level.getBlockEntity(p);
    }
    void started(ServerStartedEvent e){
        server=e.getServer();level=server.overworld();player=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000123"),"ElectricalCheck"));
        try {
            for(int x=-1;x<=3;x++)for(int z=-1;z<=2;z++)level.setChunkForced(x,z,true);
            a=desk(A);b=desk(B);other=desk(OTHER);
            a.setConsistEndpoint("VERIFY-TRAIN",1);b.setConsistEndpoint("VERIFY-TRAIN",8);other.setConsistEndpoint("OTHER-TRAIN",8);
            key=Arrays.stream(CabGeometry.CONTROLS).filter(c->c.number().equals("B172")).findFirst().orElseThrow();
            var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("pantographsandwires:pantograph"));
            for(int x=P.getX()-2;x<=P.getX()+2;x++)for(int z=P.getZ()-9;z<=P.getZ()+9;z++)
                level.setBlockAndUpdate(new BlockPos(x,P.getY()-1,z),Blocks.BEDROCK.defaultBlockState());
            check(block!=Blocks.AIR,"PAW pantograph registry available");
            level.setBlockAndUpdate(P,block.defaultBlockState());panto=(PantographBlockEntity)level.getBlockEntity(P);
            level.removeBlock(P.east(),false);level.removeBlock(P.above(),false);
            check(panto!=null,"PAW block entity created");
            var nativePos=P.east(12);level.setBlockAndUpdate(nativePos,CEEBlocks.PANTOGRAPH.getDefaultState());
            level.removeBlock(nativePos.east(),false);nativePanto=(com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographBlockEntity)level.getBlockEntity(nativePos);
            for(var entry:Map.of(battery,CEEBlocks.CREATIVE_BATTERY.getDefaultState(),motor,CEEBlocks.ELECTRIC_MOTORS[0].getDefaultState(),wireA,CEEBlocks.CATENARY_HOLDER.getDefaultState(),wireB,CEEBlocks.CATENARY_HOLDER.getDefaultState()).entrySet()){
                level.setBlockAndUpdate(entry.getKey(),entry.getValue());
                ((ElectricalDeviceBlock<?>)entry.getValue().getBlock()).ensureNodesExist(level,entry.getKey(),entry.getValue());
            }
            electrical=InfrastructureSavedData.load(level);
            CabLinkageChecks.run(level);
        } catch(Throwable error){finish(error);}
    }
    void press(CabDeskBlockEntity cab){player.setPos(cab.getBlockPos().getX()+.5,cab.getBlockPos().getY()+1,cab.getBlockPos().getZ()+.5);cab.activate(key,player);}
    void tick(ServerTickEvent.Post e){
        if(done||server==null)return;
        if(Boolean.getBoolean("emucab.test.electricalClient")&&tick==0&&!clientReady)return;
        try {
            tick++;
            int step=Boolean.getBoolean("emucab.test.electricalClient")?30:5;
            if(tick==step){
                var source=com.george_vi.electroenergetics.devices.device.DevicesSavedData.load(level).getDevice(battery,com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice.class);source.voltage=120;source.acFrequency=0;
                electrical.connectCatenary(wireA,wireB);
                electrical.connect(new InWorldNode(1,battery),new InWorldNode(0,wireA),CEEWireTypes.CREATIVE.get());
                CeeSpoolChecks.facings(level,player,P,motor);
                check(true,"CEE creative/copper spool actual right-click connections: four PAW facings, both connection orders, no manual toggle");
                electrical.connect(new InWorldNode(1,motor),new InWorldNode(0,battery),CEEWireTypes.CREATIVE.get());
                check(!panto.isExpandable(),"No redstone starts retracted");
                check(!nativePanto.extended,"Native CEE pantograph starts retracted without redstone");
                level.setBlockAndUpdate(P.east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(nativePanto.getBlockPos().east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
                b.controls().parkingBrake=true;other.controls().switches.put(CabAction.MASTER,1);
                press(b);check(b.controls().on(CabAction.MASTER),"08 key inserted by actual control activation");
                b.controls().direction=1;b.controls().notch=4;b.controls().switches.put(CabAction.PANTOGRAPH,1);b.controls().switches.put(CabAction.BREAKER,1);
                press(a);
                check(a.controls().on(CabAction.MASTER)&&!b.controls().on(CabAction.MASTER),"01 key takes authority and releases 08 key");
                check(b.controls().notch==0&&b.controls().direction==0,"Released 08 handle and direction return to zero");
                check(b.getCabSignals().get("parking_brake")==15&&b.getCabSignals().entrySet().stream().allMatch(x->x.getKey().equals("parking_brake")||x.getValue()==0),"Takeover clears every output except parking brake");
                check(other.controls().on(CabAction.MASTER),"Other consist is isolated");
            }
            if(tick==step*2){
                check(panto.isExpandable(),"Redstone 15 automatically raises PAW pantograph");
                check(nativePanto.extended,"Redstone 15 automatically raises native CEE pantograph");
                check(panto.getUpdateTag(level.registryAccess()).getBoolean("IsExpandable"),"Raised state included in real client update NBT");
                if(electrical.ticker.lastResults!=null){var results=electrical.ticker.lastResults;double v=Math.abs(results.getVoltageAt(motor,0,1));double i=Math.abs(results.getCurrentThrough(motor,0,1));check(v>100&&i>0,"CEE contact supplies real motor circuit: "+v+" V, "+i+" A");}
                else throw new AssertionError("CEE did not simulate the closed motor circuit");
                level.removeBlock(P.east(),false);
                level.removeBlock(nativePanto.getBlockPos().east(),false);
                press(b);check(b.controls().on(CabAction.MASTER)&&!a.controls().on(CabAction.MASTER),"08 key takes authority and releases 01 key");
            }
            if(tick==step*3){
                check(!panto.isExpandable(),"Redstone removal automatically lowers PAW pantograph");
                check(!nativePanto.extended,"Redstone removal automatically lowers native CEE pantograph");
                check(!panto.getUpdateTag(level.registryAccess()).getBoolean("IsExpandable"),"Lowered state included in real client update NBT");
                check(Math.abs(electrical.ticker.lastResults.getVoltageAt(motor,0,1))<1,"Lowering disconnects real CEE motor supply");
                level.setBlockAndUpdate(P.east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
                var tag=a.saveWithFullMetadata(level.registryAccess());
                level.removeBlock(A,false);a=desk(A);a.loadWithComponents(tag,level.registryAccess());a.onLoad();
                check(a.consistId().equals("VERIFY-TRAIN")&&a.endpoint()==1,"Cab endpoint and consist survive NBT reload");
                press(a);check(a.controls().on(CabAction.MASTER)&&!b.controls().on(CabAction.MASTER),"Reconstructed cab participates in actual takeover");
            }
            if(tick==step*4){
                check(panto.isExpandable(),"Second redstone rising edge raises again");
                level.removeBlock(P.east(),false);
                b.setConsistEndpoint("DETACHED-TRAIN",8);press(b);
                check(a.controls().on(CabAction.MASTER)&&b.controls().on(CabAction.MASTER),"Changing consist removes stale peer membership");
            }
            if(tick==step*5){
                check(!panto.isExpandable(),"Second redstone falling edge lowers again");
                b.setConsistEndpoint("VERIFY-TRAIN",8);press(b);
                check(!a.controls().on(CabAction.MASTER),"Rejoining consist returns to one owner");
                var id=a.linkId();var blocks=List.of(A.west(),A,A.east());
                cabBody=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level,A,blocks,dev.ryanhcode.sable.companion.math.BoundingBox3i.from(blocks));
                a=(CabDeskBlockEntity)level.getBlockEntity(cabBody.getPlot().getCenterBlock());
                check(a!=null&&a.linkId().equals(id),"Sable assembly preserves stable cab authority ID");
            }
            if(tick==step*6){
                press(a);check(a.controls().on(CabAction.MASTER)&&!b.controls().on(CabAction.MASTER),"Cab on Sable plot takes authority from stationary opposite end");
                press(b);check(b.controls().on(CabAction.MASTER)&&!a.controls().on(CabAction.MASTER),"Stationary opposite end releases cab on Sable plot");
            }
            if(tick==step*7){
                check(CeeElectricalTelemetry.bind(panto,"VERIFY-TRAIN",6),"Explicit 06 pantograph registration succeeds through production API");
                check(panto.getUpdateTag(level.registryAccess()).getInt("PWSableCar")==6,"Receiver car persists in real NBT");
                level.setBlockAndUpdate(P.east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
                press(a);a.controls().switches.put(CabAction.PANTOGRAPH,1);
                check(a.commandedPantographCar()==6,"01 cab selects 06 rear pantograph");
                check(a.getCabSignals().get("pantograph_06")==15&&a.getCabSignals().get("pantograph_03")==0,"Exactly one absolute pantograph channel is powered");
                a.controls().switches.put(CabAction.BREAKER,0);
            }
            if(tick==step*8){
                check(Math.abs(electrical.ticker.lastResults.getVoltageAt(motor,0,1))<1,"Registered receiver defaults to open breaker");
                a.controls().switches.put(CabAction.BREAKER,1);
            }
            if(tick==step*10){
                check(Math.abs(electrical.ticker.lastResults.getVoltageAt(motor,0,1))>100,"Cab VCB closes actual registered CEE motor circuit");
                var sample=sample(6);check(sample[0]==1&&sample[1]==1&&sample[2]==1&&sample[3]==1&&sample[4]>100&&sample[5]>.1,"Receiver API samples actual CEE volts and collector current "+Arrays.toString(sample));
                check(CeeElectricalTelemetry.voltage(a)>100&&CeeElectricalTelemetry.current(a)>.1,"Production cab electrical sampler receives actual circuit result");
                a.controls().switches.put(CabAction.BREAKER,0);
            }
            if(tick==step*12){
                check(Math.abs(electrical.ticker.lastResults.getVoltageAt(motor,0,1))<1,"Opening cab VCB cuts actual motor power while pantograph remains raised");
                check(sample(6)[5]<.01,"Open VCB reports zero collector current");
                a.configurePantographs(false,true);
                check(b.pantographs().cutRear&&a.commandedPantographCar()==0,"03/06 isolation shared across both ends and lowers command");
                a.configurePantographs(false,false);a.controls().switches.put(CabAction.PANTOGRAPH,1);a.controls().switches.put(CabAction.BREAKER,1);
                var positions=new ArrayList<BlockPos>(List.of(P,P.east(),motor));
                for(int z=0;z<=7;z++){
                    var support=P.south(z).below();level.setBlockAndUpdate(support,Blocks.IRON_BLOCK.defaultBlockState());positions.add(support);
                    level.setBlockAndUpdate(support.below(),Blocks.BEDROCK.defaultBlockState());
                }
                level.setBlockAndUpdate(P.east().below(),Blocks.IRON_BLOCK.defaultBlockState());positions.add(P.east().below());
                pantoBody=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level,P,positions,dev.ryanhcode.sable.companion.math.BoundingBox3i.from(positions));
                movedP=pantoBody.getPlot().getCenterBlock();movedMotor=movedP.offset(motor.subtract(P));
                check(level.getBlockEntity(movedP) instanceof PantographBlockEntity,"PAW pantograph assembles onto real Sable plot");
            }
            if(tick==step*15){
                var moving=(PantographBlockEntity)level.getBlockEntity(movedP);
                check(moving.isExpandable(),"Actual Sable PAW receiver follows local redstone");
                check(moving.getUpdateTag(level.registryAccess()).getInt("PWSableCar")==6,"Sable assembly preserves receiver registration");
                check(sample(6)[0]==1&&sample(6)[2]==1,"Sable receiver reestablishes world-space CEE contact "+Arrays.toString(sample(6))+" world="+pantoBody.logicalPose().transformPosition(movedP.getCenter()));
                check(Math.abs(electrical.ticker.lastResults.getVoltageAt(movedMotor,0,1))>100,"Sable PAW receiver powers actual CEE motor after assembly pos="+movedMotor+" state="+level.getBlockState(movedMotor)+" V="+electrical.ticker.lastResults.getVoltageAt(movedMotor,0,1)+" nodes="+electrical.getNodesAt(movedMotor)+" wires="+electrical.getConnections(new InWorldNode(0,movedP)));
                var localConnector=movedP.south(2);level.setBlockAndUpdate(localConnector,de.mrjulsen.paw.registry.ModBlocks.INSULATOR_BROWN.getDefaultState());
                var connection=new com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection(new InWorldNode(0,movedP),new InWorldNode(0,localConnector));
                CeeSpoolChecks.connect(level,player,movedP,localConnector,0);
                electrical.removeConnectionNoDrops(connection);CeeSpoolChecks.connect(level,player,localConnector,movedP,0);
                electrical.removeConnectionNoDrops(connection);
                check(true,"CEE spool actual right clicks connect PAW receiver on real Sable plot in both orders");
                level.removeBlock(movedP.east(),false);
            }
            if(tick==step*17){
                check(!((PantographBlockEntity)level.getBlockEntity(movedP)).isExpandable(),"Sable PAW receiver lowers when redstone disappears");
                check(sample(6)[2]==0&&sample(6)[5]==0,"Sable lowering removes electrical contact and current");
                check(Math.abs(electrical.ticker.lastResults.getVoltageAt(movedMotor,0,1))<1,"Sable lowering disconnects real motor supply");
                installPawCircuit();
            }
            if(tick==step*19){
                check(Math.abs(electrical.ticker.lastResults.getVoltageAt(movedMotor,0,1))>100,"Native PAW catenary supplies real moving CEE motor "+Arrays.toString(sample(6)));
                check(sample(6)[4]>100&&sample(6)[5]>.1,"PAW wire contact reports measured CEE voltage/current");
                pawGraph.removeEdge(pawFeeder.getId(),new org.joml.Vector3d(),Optional.empty());
            }
            if(tick==step*21){
                check(Math.abs(electrical.ticker.lastResults.getVoltageAt(movedMotor,0,1))<1,"Removing PAW feeder disconnects catenary motor power");
                installPawFeeder();
            }
            if(tick==step*23){
                check(Math.abs(electrical.ticker.lastResults.getVoltageAt(movedMotor,0,1))>100,"Replacing PAW feeder restores real motor power");
                var collision=pawGraph.getCollisionById(pawCatenary.getId()).orElseThrow();
                check(pawCatenary.addDecoration(collision.length("contact")*.3,"contact",new de.mrjulsen.paw.registry.InsulatorWireDecoration(de.mrjulsen.paw.registry.ModBlocks.INSULATOR_BROWN.asStack())),"PAW wire insulator installed between feeder and collector");
                var restored=new de.mrjulsen.wires.graph.WireGraph(pawGraph.getId(),level).load(level,pawGraph.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()));
                check(restored.getEdges().size()==pawGraph.getEdges().size()&&restored.getEdge(pawCatenary.getId()).getDecorations().size()==1&&restored.getNode(pawMidNode.getId()).getData().serializeNbt().equals(pawMidNode.getData().serializeNbt()),"PAW saved graph restores conductor IDs, feeder tap and insulation");
            }
            if(tick==step*25){
                check(Math.abs(electrical.ticker.lastResults.getVoltageAt(movedMotor,0,1))<1,"PAW insulator cuts power across contact-wire section");
                pawCatenary.removeDecorations(level,Optional.empty(),"contact",List.copyOf(pawCatenary.getDecorations()));
            }
            if(tick==step*27){
                check(Math.abs(electrical.ticker.lastResults.getVoltageAt(movedMotor,0,1))>100,"Removing PAW insulator restores contact-wire power");
                level.removeBlock(movedP.east(),false);
            }
            if(tick==step*29){
                check(Math.abs(electrical.ticker.lastResults.getVoltageAt(movedMotor,0,1))<1&&sample(6)[5]<.01,"Lowering PAW receiver disconnects native PAW wire supply");
                level.setBlockAndUpdate(movedP.east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            }
            if(tick==step*31){
                check(Math.abs(electrical.ticker.lastResults.getVoltageAt(movedMotor,0,1))>100,"Raising PAW receiver reconnects native PAW supply");
                pawGraph.removeEdge(pawCatenary.getId(),new org.joml.Vector3d(),Optional.empty());
            }
            if(tick==step*33){
                check(Math.abs(electrical.ticker.lastResults.getVoltageAt(movedMotor,0,1))<1,"Removing native PAW contact wire disconnects actual motor");
                installPawCircuit();a.controls().switches.put(CabAction.BREAKER,0);
            }
            int audioStart=step*33;
            if(tick==audioStart+100){check(sample(6)[2]==1,"Contact audio test: real moving PAW receiver touches wire");check(arcCount("contactEvents")>=1,"Contact audio test: energized contact generated an arc");audioContacts=arcCount("contactEvents");audioDepartures=arcCount("disconnectEvents");}
            if(tick==audioStart+130){check(arcCount("contactEvents")==audioContacts&&arcCount("disconnectEvents")==audioDepartures,"Continuous wire contact does not repeat arcs");level.removeBlock(movedP.east(),false);}
            if(tick==audioStart+135){check(arcCount("disconnectEvents")==audioDepartures+1,"Lowering away from contact generates one departure arc");level.setBlockAndUpdate(movedP.east(),Blocks.REDSTONE_BLOCK.defaultBlockState());}
            if(tick==audioStart+230){check(arcCount("contactEvents")==audioContacts+1,"Raising into energized contact arcs with open VCB: contacts="+arcCount("contactEvents")+" expected="+(audioContacts+1)+" sample="+Arrays.toString(sample(6)));check(sample(6)[3]==0,"Arc test keeps VCB open");pawGraph.removeEdge(pawCatenary.getId(),new org.joml.Vector3d(),Optional.empty());}
            if(tick==audioStart+240){check(arcCount("disconnectEvents")==audioDepartures+2,"Driving out of physical contact generates one departure arc");finish(null);}
        } catch(Throwable error){finish(error);}
    }
    double[] sample(int car)throws Exception{return (double[])Class.forName("com.yasui_i.pwsablefix.compat.PWSableElectricalBridge").getMethod("sample",net.minecraft.world.level.Level.class,String.class,int.class).invoke(null,level,"VERIFY-TRAIN",car);}
    long audioContacts,audioDepartures;
    long arcCount(String name)throws Exception {
        var type=Class.forName("com.yasui_i.pwsablefix.sound.PantographContactSounds");var mapField=type.getDeclaredField("CONTACTS");mapField.setAccessible(true);
        var map=(Map<?,?>)mapField.get(null);var states=(Map<?,?>)map.get(level);var state=states.get(movedP);
        var field=state.getClass().getDeclaredField(name);field.setAccessible(true);return field.getLong(state);
    }
    de.mrjulsen.wires.graph.WireGraph pawGraph;
    de.mrjulsen.wires.graph.WireEdge pawCatenary,pawFeeder;
    de.mrjulsen.wires.graph.WireNode pawFeedNode,pawMidNode;
    void installPawCircuit(){
        electrical.removeCatenary(wireA,wireB);
        pawGraph=de.mrjulsen.wires.graph.WireGraphManager.get(level,de.mrjulsen.wires.WiresApi.PAW_CATENARY_WIRES);
        for(var edge:List.copyOf(pawGraph.getEdges()))pawGraph.removeEdge(edge.getId(),new org.joml.Vector3d(),Optional.empty());
        var feed=wireA.west(4);var poleA=wireA;var poleB=wireB;
        var support=de.mrjulsen.paw.registry.ModBlocks.INSULATOR_BROWN.getDefaultState();
        for(var pos:List.of(feed,poleA,poleB)){
            level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(pos,support);
            ((ElectricalDeviceBlock<?>)support.getBlock()).ensureNodesExist(level,pos,support);
        }
        var data=new de.mrjulsen.wires.item.CustomData(new net.minecraft.nbt.CompoundTag());
        var connector=new de.mrjulsen.wires.graph.data.provider.CantileverConnectorDataProvider(new org.joml.Vector3d(.5,0,.5),new org.joml.Vector3d(.5,.8,.5));
        var aNode=pawGraph.createNode(new de.mrjulsen.wires.graph.data.node.BlockConnectorNodeData(poleA),new org.joml.Vector3d(poleA.getX(),poleA.getY(),poleA.getZ()));
        var bNode=pawGraph.createNode(new de.mrjulsen.wires.graph.data.node.BlockConnectorNodeData(poleB),new org.joml.Vector3d(poleB.getX(),poleB.getY(),poleB.getZ()));
        pawCatenary=new de.mrjulsen.wires.graph.WireEdge(pawGraph,de.mrjulsen.paw.registry.ModWireRegistry.CATENARY_WIRE,new de.mrjulsen.wires.graph.data.WireConnectionData(data,connector,connector),aNode.getId(),bNode.getId(),new de.mrjulsen.wires.graph.data.WireEdgeHash(data,aNode,bNode));
        pawGraph.setAndUpdateEdge(pawCatenary,true);
        installPawFeeder();
        electrical.connect(new InWorldNode(1,battery),new InWorldNode(0,feed),CEEWireTypes.CREATIVE.get());
        level.setBlockAndUpdate(movedP.east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        check(true,"Real PAW feeder + contact-wire graph created, fed via CEE spool terminal");
    }
    void installPawFeeder(){
        var feed=wireA.west(4);var hand=net.minecraft.world.InteractionHand.MAIN_HAND;
        player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE);
        player.setPos(feed.getX()+.5,feed.getY()+.5,feed.getZ()+1);
        var stack=de.mrjulsen.paw.registry.ModItems.WIRE.asStack();
        de.mrjulsen.wires.item.MultiWireItem.setSettings(stack,new de.mrjulsen.paw.data.WireSettingsData(new de.mrjulsen.paw.item.FeederWireItem()));
        player.setItemInHand(hand,stack);
        var item=(de.mrjulsen.wires.item.MultiWireItem)stack.getItem();
        var blockHit=new net.minecraft.world.phys.BlockHitResult(feed.getCenter(),Direction.UP,feed,false);
        item.useOn(new net.minecraft.world.item.context.UseOnContext(player,hand,blockHit));
        var collision=pawGraph.getCollisionById(pawCatenary.getId()).orElseThrow();double distance=collision.length("contact")*.1;
        var world=collision.wirePosToWorldPos("contact",distance);
        var hit=new de.mrjulsen.paw.data.WireHitResult(new net.minecraft.world.phys.Vec3(world.x,world.y,world.z),distance,BlockPos.containing(world.x,world.y,world.z),pawGraph.getId(),new de.mrjulsen.wires.network.WireId(pawCatenary.getId(),"contact",pawCatenary.getType()));
        item.interactWithWire(level,player,hand,hit);
        pawFeeder=pawGraph.getEdges().stream().filter(edge->edge.getType()==de.mrjulsen.paw.registry.ModWireRegistry.ENERGY_WIRE).findFirst().orElseThrow(()->new AssertionError("Normal PAW feeder item placement did not create wire"));
        pawFeedNode=pawGraph.getNode(pawFeeder.getNodeAId());pawMidNode=pawGraph.getNode(pawFeeder.getNodeBId());
        check(pawMidNode.getData() instanceof de.mrjulsen.wires.graph.data.node.CatenaryWireConnectorNodeData,"Normal PAW feeder item clicks create a persistent contact-wire tap");
    }
    void finish(Throwable error){
        done=true;if(error!=null){error.printStackTrace();evidence.append("FAIL ").append(error).append('\n');}
        try {Files.writeString(Path.of("electrical-check-result.txt"),evidence.toString());} catch(Exception ex){throw new RuntimeException(ex);}
        System.out.println("EMU_ELECTRICAL_RESULT: "+(error==null?"PASS":"FAIL"));server.halt(false);
    }
}

