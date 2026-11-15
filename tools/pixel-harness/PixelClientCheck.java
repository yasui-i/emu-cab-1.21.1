package emucabpixeltest;

import com.yasui_i.emucab.block.CabDeskBlock;
import com.yasui_i.emucab.block.CabDeskPart;
import com.yasui_i.emucab.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.nio.file.Files;
import java.util.*;

@EventBusSubscriber(modid="emucabpixeltest",value=Dist.CLIENT)
public final class PixelClientCheck {
    private static int ticks,phase;private static String capture;private static boolean done;
    private static final String[] CAMERAS={
        ".5 85.45 -3.6 0 12", "13.6 85.45 .5 90 12", ".5 85.45 13.6 180 12", "5.4 85.45 9.5 -90 12",
        "-.5 85.55 -1.3 0 31", "11.35 85.4 .5 90 22"};
    private static final String[] NAMES={"01-north-N.png","02-east-P7.png","03-south-B7.png","04-west-EB.png","05-button-closeup.png","06-handle-closeup.png"};
    private static final StringBuilder report=new StringBuilder();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(done||mc.player==null||mc.level==null)return;
        if(++ticks<40)return;ticks=0;
        try{
            mc.options.hideGui=true;
            if(phase==0){
                for(var facing:PixelHarness.FACINGS)for(var part:CabDeskPart.values()){
                    var state=ModBlocks.CAB_DESK.get().defaultBlockState().setValue(CabDeskBlock.FACING,facing).setValue(CabDeskBlock.PART,part);
                    var model=mc.getBlockRenderer().getBlockModel(state);var quads=model.getQuads(state,null,RandomSource.create(1));
                    if(quads.size()<100)throw new AssertionError("Missing baked cab "+facing+"/"+part+": "+quads.size());
                    Set<String> sprites=new TreeSet<>();for(var quad:quads){var name=quad.getSprite().contents().name().toString();if(name.contains("missingno"))throw new AssertionError("Missing cab sprite");sprites.add(name);}
                    if(sprites.stream().noneMatch(s->s.contains("gray_concrete")))throw new AssertionError("Concrete material absent "+facing+"/"+part);
                    report.append(facing).append('/').append(part).append(": ").append(quads.size()).append(" quads; ").append(sprites).append('\n');
                }
            }
            if(phase<12){int view=phase/2;if(phase%2==0)mc.getConnection().sendCommand("tp @s "+CAMERAS[view]);else capture=NAMES[view];phase++;}
            else{Files.writeString(mc.gameDirectory.toPath().resolve("pixel-client-check.txt"),"PASS: baked model and texture checks, twelve facing/part combinations, screenshots captured for N/P7/B7/EB and button/handle details\n"+report);System.out.println("EMU_PIXEL_CLIENT: PASS");done=true;mc.stop();}
        }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("pixel-client-check.txt"),"FAIL phase "+phase+": "+t+"\n"+report);}catch(Exception ignored){}done=true;}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event){if(capture==null)return;var name=capture;capture=null;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});}
}
