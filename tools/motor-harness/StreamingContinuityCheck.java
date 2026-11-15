package emucabmotortest;

import com.mojang.blaze3d.audio.Channel;
import com.yasui_i.emucab.audio.VvvfSynthesizer;
import com.yasui_i.emucab.client.BogeyPcmStream;
import com.yasui_i.emucab.client.BogeyRecordedLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lwjgl.openal.AL10;

import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Wall-clock playback, using the real audio executor and OpenAL queue rather than fast PCM reads. */
final class StreamingContinuityCheck {
    private static Executor audioExecutor;
    private static CompletableFuture<Void> pending;
    private static Channel channel;
    private static BogeyPcmStream stream;
    private static VvvfSynthesizer synth;
    private static int source,checks,minAvailable=4,starvations;
    private static long started,lastPump,nextPump,maxGap;
    private static boolean begun,starving,recovered,stopChecked,finished;
    private static String report;

    static void begin(BlockEntity movingMotor)throws Exception {
        if(begun)return;
        if(dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(
                movingMotor.getLevel(),movingMotor.getBlockPos())==null)
            throw new AssertionError("Continuity check requires the moving-body client environment");
        begun=true;
        Object engine=MotorClientCheck.field(Minecraft.getInstance().getSoundManager(),"soundEngine");
        Object access=MotorClientCheck.field(engine,"channelAccess");
        audioExecutor=(Executor)MotorClientCheck.field(access,"executor");
        submit(()->{
            var create=Channel.class.getDeclaredMethod("create");create.setAccessible(true);
            channel=(Channel)create.invoke(null);
            check(channel!=null,"OpenAL failed to allocate the continuity probe");
            source=(int)MotorClientCheck.field(channel,"source");
            synth=new VvvfSynthesizer(15731);
            var layers=BogeyRecordedLayers.load().getNow(BogeyRecordedLayers.EMPTY);
            synth.setRecordedLayers(layers.mechanical(),layers.wind());
            synth.setTarget(600,104,1,0,2.5,2);
            stream=new BogeyPcmStream(synth);
            channel.disableAttenuation();channel.setRelative(true);channel.setVolume(.1f);
            channel.attachBufferStream(stream);channel.play();
            check(AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED)==4,"Initial PCM queue is not four buffers");
            started=lastPump=nextPump=System.nanoTime();
        });
    }

    /** Call from client ticks. No sleeps or waiting on the render thread. */
    static boolean tick()throws Exception {
        if(finished)return true;
        if(!begun)return false;
        if(pending!=null){
            if(!pending.isDone())return false;
            pending.join();pending=null;
            if(finished)return true;
        }
        long now=System.nanoTime();
        if(now<nextPump)return false;
        double elapsed=(now-started)/1e9;
        if(elapsed>=12.6&&recovered){
            submit(()->{
                channel.stop();
                check(!stream.canRecover(),"Explicit Channel.stop did not disable recovery");
                channel.updateStream();
                check(AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE)==AL10.AL_STOPPED,
                        "Explicitly stopped PCM was incorrectly revived");
                stopChecked=true;
                channel.destroy();channel=null;
                report="PASS real OpenAL continuous playback >=12.6s; 50/100/150ms refill schedules; "
                        +checks+" measured queues; min unprocessed buffers="+minAvailable
                        +"; max observed refill gap="+(maxGap/1e6)+"ms; injected600ms starvation="+starvations
                        +"; same PCM/synth resumed without recreation; explicit stop stayed stopped";
                Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("streaming-continuity.txt"),report+"\n");
                finished=true;
            });
            return false;
        }
        if(elapsed>=9&&!starving){
            starving=true;nextPump=now+600_000_000L;
            return false;
        }
        boolean recovery=starving&&!recovered;
        int interval=elapsed<3?50:elapsed<6?100:elapsed<9?150:50;
        nextPump=now+interval*1_000_000L;
        submit(()->{
            long at=System.nanoTime(),gap=at-lastPump;lastPump=at;maxGap=Math.max(maxGap,gap);
            int state=AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE);
            int queued=AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED);
            int processed=AL10.alGetSourcei(source,AL10.AL_BUFFERS_PROCESSED);
            if(recovery){
                check(state==AL10.AL_STOPPED&&processed==4,"600ms starvation did not exhaust the400ms queue");
                starvations++;
            }else{
                check(state==AL10.AL_PLAYING,"Unexpected OpenAL underrun during scheduled playback; gap="+gap/1e6+"ms");
                check(queued>processed,"No playable PCM left before normal refill");
                minAvailable=Math.min(minAvailable,queued-processed);
            }
            channel.updateStream();
            check(AL10.alGetSourcei(source,AL10.AL_SOURCE_STATE)==AL10.AL_PLAYING,"PCM did not resume after refill");
            check(AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED)==4,"PCM refill lost a queued buffer");
            check(MotorClientCheck.field(stream,"synth")==synth,"Synthesizer was replaced during recovery");
            check((double)MotorClientCheck.field(synth,"rpm")>590,"Recovery restarted RPM from zero");
            if(recovery)recovered=true;
            checks++;
        });
        return false;
    }

    static String evidence(){return report==null?"continuity check pending":report;}
    private interface Work {void run()throws Exception;}
    private static void submit(Work work){
        pending=new CompletableFuture<>();var result=pending;
        audioExecutor.execute(()->{
            try{work.run();result.complete(null);}
            catch(Throwable error){
                if(channel!=null){try{channel.destroy();}catch(Throwable ignored){}channel=null;}
                result.completeExceptionally(error);
            }
        });
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private StreamingContinuityCheck(){}
}
