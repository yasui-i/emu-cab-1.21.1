import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.yasui_i.emucab.config.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforgespi.language.IModInfo;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.locks.ReentrantLock;

/** Exercises the real Loading hook and LoadedConfig save against temporary TOML files. */
public final class ExteriorAudioConfigMigrationCheck {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static final class Container extends ModContainer {
        Container(){super((IModInfo)Proxy.newProxyInstance(IModInfo.class.getClassLoader(),new Class<?>[]{IModInfo.class},
                (proxy,method,args)->method.getName().equals("getModId")?"emucab":null));}
        @Override public IEventBus getEventBus(){return null;}
    }
    static ModConfig load(boolean brake,Path path)throws Exception {
        return load(brake?CabConfig.SPEC:BogeyAudioConfig.SPEC,brake?ModConfig.Type.COMMON:ModConfig.Type.CLIENT,path);
    }
    static ModConfig load(ModConfigSpec spec,ModConfig.Type kind,Path path)throws Exception {
        var ctor=ModConfig.class.getDeclaredConstructor(ModConfig.Type.class,IConfigSpec.class,ModContainer.class,String.class,ReentrantLock.class);
        ctor.setAccessible(true);
        var config=ctor.newInstance(kind,spec,new Container(),path.getFileName().toString(),new ReentrantLock());
        var values=CommentedConfig.inMemory();
        try(var file=CommentedFileConfig.builder(path).sync().build()){file.load();values.putAll(file);}
        spec.correct(values);
        var type=Class.forName("net.neoforged.fml.config.LoadedConfig");
        var loadedCtor=type.getDeclaredConstructor(CommentedConfig.class,Path.class,ModConfig.class);loadedCtor.setAccessible(true);
        var loaded=(IConfigSpec.ILoadedConfig)loadedCtor.newInstance(values,path,config);
        var field=ModConfig.class.getDeclaredField("loadedConfig");field.setAccessible(true);field.set(config,loaded);
        spec.acceptConfig(loaded);
        // The normal loader persists corrected/clamped fields before Loading.
        spec.save();
        return config;
    }
    static double disk(Path path,String key){
        try(var config=CommentedFileConfig.builder(path).sync().build()){config.load();return ((Number)config.get(key)).doubleValue();}
    }
    static void scenario(Path dir,boolean brake,double radius,int version,double expected)throws Exception {
        String section=brake?"simurail":"bogey_motor",radiusKey=brake?"brake_release_radius":"radius",versionKey=brake?"brake_release_config_version":"config_version";
        var spec=brake?CabConfig.SPEC:BogeyAudioConfig.SPEC;
        Path path=dir.resolve(section+radius+"-"+version+".toml");
        Files.writeString(path,"["+section+"]\n"+radiusKey+"="+radius+"\n"+versionKey+"="+version+"\n");
        ExteriorAudioConfigMigration.onLoading(new ModConfigEvent.Loading(load(brake,path)));
        check((brake?CabConfig.BOGEY_RELEASE_RADIUS.get():BogeyAudioConfig.RADIUS.get())==expected,"Incorrect radius "+path);
        check(disk(path,section+"."+radiusKey)==expected,"Radius not saved");
        check(disk(path,section+"."+versionKey)==(brake?4:3),"Marker not saved");
        if(brake)CabConfig.BOGEY_RELEASE_RADIUS.set(64.0);else BogeyAudioConfig.RADIUS.set(64.0);
        spec.save();spec.acceptConfig(null);
        ExteriorAudioConfigMigration.onLoading(new ModConfigEvent.Loading(load(brake,path)));
        check((brake?CabConfig.BOGEY_RELEASE_RADIUS.get():BogeyAudioConfig.RADIUS.get())==64,"Later user edit overwritten");
        spec.acceptConfig(null);
    }
    static void standalone(Path dir,double radius,int version,double expected)throws Exception {
        var spec=com.yasui_i.emucab.motor.VvvfMotorConfig.SPEC;
        var range=com.yasui_i.emucab.motor.VvvfMotorConfig.SOUND_RANGE;
        Path path=dir.resolve("standalone"+radius+"-"+version+".toml");
        Files.writeString(path,"[motor]\nsound_range="+radius+"\nsound_config_version="+version+"\nsound_volume=0.8\n");
        ExteriorAudioConfigMigration.onLoading(new ModConfigEvent.Loading(load(spec,ModConfig.Type.COMMON,path)));
        check(range.get()==expected,"Standalone migration failed "+path);
        check(disk(path,"motor.sound_range")==expected&&disk(path,"motor.sound_config_version")==3,"Standalone migration not saved");
        check(com.yasui_i.emucab.motor.VvvfMotorConfig.SOUND_VOLUME.get()==(version<2?1:.8),"Standalone gain migration");
        range.set(64.0);spec.save();spec.acceptConfig(null);
        ExteriorAudioConfigMigration.onLoading(new ModConfigEvent.Loading(load(spec,ModConfig.Type.COMMON,path)));
        check(range.get()==64,"Standalone later edit overwritten");spec.acceptConfig(null);
    }
    static void distanceCurves() {
        for(double radius:new double[]{64,128,256,512})for(double fraction:new double[]{.65,.8,1}) {
            double previous=1;
            for(int i=0;i<=100;i++) {
                double d=radius*i/100,g=com.yasui_i.emucab.audio.ExteriorSoundRange.gain(d,radius,fraction);
                check(g>=0&&g<=previous+1e-12,"Non-monotonic range curve");previous=g;
                if(i<100)check(g>0,"Sound cut off before configured radius");
                check(g+1e-12>=Math.clamp((radius-d)/(radius*fraction),0,1),"New curve attenuates more strongly than old");
            }
            check(previous==0&&com.yasui_i.emucab.audio.ExteriorSoundRange.gain(radius+1,radius,fraction)==0,"Range boundary leaked");
        }
        check(com.yasui_i.emucab.audio.ExteriorSoundRange.DELIVERY>512,"Telemetry delivery does not cover512");
        check(com.yasui_i.emucab.audio.ExteriorSoundRange.gain(Double.NaN,256,.8)==0,"NaN gain");
        check(com.yasui_i.emucab.audio.ExteriorSoundRange.balance(1+.01+.01)>.98,"Distant sources lower nearby gain");
        check(Math.abs(com.yasui_i.emucab.audio.ExteriorSoundRange.balance(3)-1/Math.sqrt(3))<1e-12,"Close group normalization changed");
    }
    public static void main(String[] args)throws Exception {
        Path dir=Files.createTempDirectory("emucab-exterior-config-");FMLPaths.loadAbsolutePaths(dir);
        try {
            Path fresh=dir.resolve("fresh.toml");Files.writeString(fresh,"");
            ExteriorAudioConfigMigration.onLoading(new ModConfigEvent.Loading(load(false,fresh)));
            check(BogeyAudioConfig.RADIUS.get()==128,"Fresh bogey default is not128");BogeyAudioConfig.SPEC.acceptConfig(null);
            Files.writeString(fresh,"");
            ExteriorAudioConfigMigration.onLoading(new ModConfigEvent.Loading(load(true,fresh)));
            check(CabConfig.BOGEY_RELEASE_RADIUS.get()==64,"Fresh release default is not64");CabConfig.SPEC.acceptConfig(null);
            Files.writeString(fresh,"");
            var motorSpec=com.yasui_i.emucab.motor.VvvfMotorConfig.SPEC;
            ExteriorAudioConfigMigration.onLoading(new ModConfigEvent.Loading(load(motorSpec,ModConfig.Type.COMMON,fresh)));
            check(com.yasui_i.emucab.motor.VvvfMotorConfig.SOUND_RANGE.get()==128,"Fresh standalone default is not128");motorSpec.acceptConfig(null);
            check(com.yasui_i.emucab.audio.ExteriorSoundRange.DEFAULT==128,"Shared default differs");
            for(boolean brake:new boolean[]{false,true}) {
                scenario(dir,brake,brake?24:48,0,brake?64:128);
                scenario(dir,brake,88,0,88);scenario(dir,brake,128,1,brake?64:128);
                scenario(dir,brake,64,0,brake?64:128);scenario(dir,brake,64,1,brake?64:128);
                scenario(dir,brake,256,2,brake?64:128);scenario(dir,brake,256,3,brake?64:256);
                scenario(dir,brake,64,2,64);scenario(dir,brake,512,2,512);
                scenario(dir,brake,600,2,512);scenario(dir,brake,32,2,64);
            }
            scenario(dir,true,128,3,64);scenario(dir,true,128,4,128);
            scenario(dir,true,256,4,256);
            standalone(dir,48,0,128);standalone(dir,128,0,128);
            standalone(dir,256,2,128);standalone(dir,256,3,256);
            standalone(dir,512,0,512);standalone(dir,64,2,64);
            distanceCurves();
            System.out.println("PASS release default64, bogey/standalone default128: real Loading hooks + persisted TOML for three configs; release128/256 defaults migrate once to64; later custom128/256 retained; other custom ranges and gain migration preserved; out-of-range corrected; monotonic attenuation and hard boundaries; telemetry margin");
        } finally {
            BogeyAudioConfig.SPEC.acceptConfig(null);CabConfig.SPEC.acceptConfig(null);
            com.yasui_i.emucab.motor.VvvfMotorConfig.SPEC.acceptConfig(null);
            try(var paths=Files.walk(dir)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
        }
    }
}
