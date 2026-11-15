package emucabelectricaltest;

import de.mrjulsen.paw.blockentity.PantographBlockEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Observes the actual multiplayer client block entity, including server update packets. */
@EventBusSubscriber(modid="emucabelectricaltest",value=Dist.CLIENT)
public final class ElectricalClientCheck {
    static final List<Boolean> edges=new ArrayList<>(),expandedEdges=new ArrayList<>();static int ticks,menuTicks,exitTicks;static boolean done,connected,ready,movingAudioChecked;static String lastScreen="";
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();
        if(done){if(mc.level==null||++exitTicks>1000)mc.stop();return;}
        if(mc.level==null||mc.player==null){
            String screen=mc.screen==null?"null":mc.screen.getClass().getSimpleName();
            if(!screen.equals(lastScreen)){lastScreen=screen;System.out.println("EMU_TEST_CLIENT_SCREEN: "+screen);}
            if(screen.equals("AccessibilityOnboardingScreen"))mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
            if(mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen&&!connected&&++menuTicks>20){
                connected=true;
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25581"),new net.minecraft.client.multiplayer.ServerData("Isolated electrical test","127.0.0.1:25581",net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);
            }
            return;
        }
        try {
            ticks++;
            if(ticks%40==0)System.out.println("EMU_CLIENT_DIAGNOSTIC pos="+mc.player.position()+" block="+mc.level.getBlockState(ElectricalHarness.P)+" be="+mc.level.getBlockEntity(ElectricalHarness.P));
            if(mc.level.getBlockEntity(ElectricalHarness.P) instanceof PantographBlockEntity p){
                if(!ready){
                    var pos=ElectricalHarness.P;var hit=new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),net.minecraft.core.Direction.UP,pos,false);
                    for(var hand:net.minecraft.world.InteractionHand.values())for(var stack:List.of(com.george_vi.electroenergetics.CEEItems.CREATIVE_WIRE_SPOOL.asStack(),com.george_vi.electroenergetics.CEEItems.COPPER_WIRE_SPOOL.asStack(),com.george_vi.electroenergetics.CEEItems.DUPLEX_WIRE_SPOOL.asStack())){
                        var result=mc.level.getBlockState(pos).useItemOn(stack,mc.level,mc.player,hand,hit);
                        if(result!=net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION)throw new AssertionError("Client swallowed CEE spool: "+stack+" "+hand+" "+result);
                    }
                    ready=true;mc.player.connection.sendChat("EMUCAB_TEST_READY");
                }
                boolean state=p.isExpandable();
                if(edges.isEmpty()||edges.getLast()!=state){edges.add(state);System.out.println("EMU_PANTO_CLIENT_EDGE: "+state);}
                if(expandedEdges.isEmpty()||expandedEdges.getLast()!=p.isExpanded())expandedEdges.add(p.isExpanded());
            }
            if(!movingAudioChecked)for(var body:dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(mc.level).getAllSubLevels()){
                var source=mc.level.getBlockEntity(body.getPlot().getCenterBlock());if(source instanceof PantographBlockEntity){MovingAudioChecks.run(source);movingAudioChecked=true;break;}
            }
            if(movingAudioChecked&&edges.size()>=5&&edges.subList(0,5).equals(List.of(false,true,false,true,false))&&expandedEdges.size()>=5&&expandedEdges.subList(0,5).equals(List.of(false,true,false,true,false))){
                Files.writeString(Path.of("electrical-client-check.txt"),"PASS client CEE creative/copper/duplex spool dispatch for both hands\nPASS real multiplayer PAW command states: "+edges+"\nPASS actual mechanical animation states: "+expandedEdges+"\nPASS both packet codec edges; CEE long arc real sound channels, contact7tick/departure24tick duration and final fade, moving350km/h pose gain and single Sable transform\nPASS cleaned brake-release recording channel and ten synthetic350km/h pose steps retain volume");
                done=true;
            }
            if(ticks>1400&&!done)throw new AssertionError("Expected redstone 0/15/0/15/0 client state, observed "+edges+" expanded="+expandedEdges+" movingAudio="+movingAudioChecked);
        } catch(Throwable error){
            error.printStackTrace();done=true;try{Files.writeString(Path.of("electrical-client-check.txt"),"FAIL "+error);}catch(Exception ex){throw new RuntimeException(ex);}
        }
    }
}
