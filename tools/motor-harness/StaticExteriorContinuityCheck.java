package emucabmotortest;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import java.nio.file.Files;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Real cached exterior buffers cross their loop seam without a Java refill. */
final class StaticExteriorContinuityCheck {
    private static final Channel[] channels=new Channel[4];
    private static final int[] sources=new int[4],buffers=new int[4];
    private static final float[] offsets=new float[4];
    private static CompletableFuture<SoundBuffer> rolling,wind;
    private static CompletableFuture<Void> pending;
    private static Executor executor;
    private static long started,nextCheck,lastCheck,maxGap;
    private static int checks,wraps;
    private static boolean begun,initialized,finished,longGap;
    private static String report;

    static void begin()throws Exception {
        if(begun)return;
        begun=true;
        var engine=MotorClientCheck.field(Minecraft.getInstance().getSoundManager(),"soundEngine");
        var access=MotorClientCheck.field(engine,"channelAccess");
        executor=(Executor)MotorClientCheck.field(access,"executor");
        var library=(SoundBufferLibrary)MotorClientCheck.field(engine,"soundBuffers");
        var rollingId=ResourceLocation.parse("emucab:sounds/bogey_rolling_loop.ogg");
        var windId=ResourceLocation.parse("emucab:sounds/bogey_wind_loop.ogg");
        rolling=library.getCompleteBuffer(rollingId);wind=library.getCompleteBuffer(windId);
        check(rolling==library.getCompleteBuffer(rollingId)&&wind==library.getCompleteBuffer(windId),
                "Exterior sounds did not share the normal sound-buffer cache");
    }

    /** Runs across ordinary client ticks; no blocking wait or sleep. */
    static boolean tick()throws Exception {
        if(finished)return true;
        if(!begun)return false;
        if(pending!=null){
            if(!pending.isDone())return false;
            pending.join();pending=null;
            if(finished)return true;
        }
        if(!initialized){
            if(!rolling.isDone()||!wind.isDone())return false;
            var r=rolling.join();var w=wind.join();
            submit(()->{
                var create=Channel.class.getDeclaredMethod("create");create.setAccessible(true);
                var get=SoundBuffer.class.getDeclaredMethod("getAlBuffer");get.setAccessible(true);
                int rb=((OptionalInt)get.invoke(r)).orElseThrow();
                int wb=((OptionalInt)get.invoke(w)).orElseThrow();
                check(rb!=wb,"Wind and rolling unexpectedly share the same PCM");
                for(int i=0;i<channels.length;i++){
                    channels[i]=(Channel)create.invoke(null);
                    check(channels[i]!=null,"Could not allocate static continuity channel");
                    sources[i]=(int)MotorClientCheck.field(channels[i],"source");
                    channels[i].disableAttenuation();channels[i].setRelative(true);
                    channels[i].setVolume(.01f);channels[i].setPitch(1);
                    channels[i].setLooping(true);channels[i].attachStaticBuffer(i<2?r:w);
                    buffers[i]=AL10.alGetSourcei(sources[i],AL10.AL_BUFFER);
                    check(buffers[i]==(i<2?rb:wb),"Static source did not attach shared exterior buffer");
                    check(AL10.alGetBufferi(buffers[i],AL10.AL_SIZE)==48000*8*2,
                            "Exterior buffer is not the generated eight-second PCM loop");
                    channels[i].play();
                    offsets[i]=AL10.alGetSourcef(sources[i],AL11.AL_SEC_OFFSET);
                }
                started=lastCheck=System.nanoTime();nextCheck=started+100_000_000L;initialized=true;
            });
            return false;
        }
        long now=System.nanoTime();if(now<nextCheck)return false;
        submit(()->{
            long at=System.nanoTime();maxGap=Math.max(maxGap,at-lastCheck);lastCheck=at;
            for(int i=0;i<channels.length;i++){
                check((int)MotorClientCheck.field(channels[i],"source")==sources[i],"Static source was recreated");
                check(MotorClientCheck.field(channels[i],"stream")==null,"Exterior loop used a PCM refill stream");
                check(AL10.alGetSourcei(sources[i],AL10.AL_BUFFER)==buffers[i],"Static buffer was replaced");
                check(AL10.alGetSourcei(sources[i],AL10.AL_LOOPING)==AL10.AL_TRUE,"Static auto-loop was disabled");
                check(AL10.alGetSourcei(sources[i],AL10.AL_SOURCE_STATE)==AL10.AL_PLAYING,"Static exterior source stopped");
                float offset=AL10.alGetSourcef(sources[i],AL11.AL_SEC_OFFSET);
                if(offset+1<offsets[i])wraps++;
                offsets[i]=offset;
            }
            checks++;
            double elapsed=(at-started)/1e9;
            if(elapsed>=9.2){
                check(wraps>=4,"Not all four static sources crossed the eight-second loop seam");
                check(maxGap>=850_000_000L,"Static probe did not include its refill-free long gap");
                destroy();
                report="PASS four real OpenAL static sources, two shared generated eight-second buffers; "
                        +elapsed+"s elapsed, "+checks+" checks, "+wraps+" observed loop wraps; source/buffer identity retained, looping/playing throughout, no AudioStream; longest probe gap="+maxGap/1e6+"ms without refill";
                Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("static-exterior-continuity.txt"),report+"\n");
                finished=true;
            }else if(elapsed>=3&&!longGap){longGap=true;nextCheck=at+900_000_000L;}
            else nextCheck=at+100_000_000L;
        });
        return false;
    }

    static String evidence(){return report;}
    private interface Work{void run()throws Exception;}
    private static void submit(Work work){
        pending=new CompletableFuture<>();var result=pending;
        executor.execute(()->{try{work.run();result.complete(null);}catch(Throwable e){destroy();result.completeExceptionally(e);}});
    }
    private static void destroy(){for(int i=0;i<channels.length;i++)if(channels[i]!=null){try{channels[i].destroy();}catch(Throwable ignored){}channels[i]=null;}}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private StaticExteriorContinuityCheck(){}
}
