import com.yasui_i.emucab.track.*;
import com.yasui_i.emucab.control.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public class CabTrackLimitCheck {
    static RailNetwork.Edge edge(String id,String a,String b,Vec3... points) {
        return new RailNetwork.Edge(id,a,b,List.of(points));
    }
    public static void main(String[] args) {
        var east=new Vec3(1,0,0);
        var network=new RailNetwork(List.of(
                edge("main","a","b",new Vec3(0,0,0),new Vec3(100,0,0)),
                edge("next","b","c",new Vec3(100,0,0),new Vec3(200,0,0)),
                edge("parallel","d","e",new Vec3(0,0,4),new Vec3(200,0,4)),
                edge("overhead","f","g",new Vec3(0,8,0),new Vec3(200,8,0))));
        var markers=List.of(new RailNetwork.Marker("80","main",40,east,80),
                new RailNetwork.Marker("120","next",50,east,120),
                new RailNetwork.Marker("wrong","main",45,east.scale(-1),20),
                new RailNetwork.Marker("adjacent","parallel",40,east,10),
                new RailNetwork.Marker("high","overhead",40,east,30));
        var origin=network.project(new Vec3(20,3,0),east,1.4,8,1);
        assert origin!=null && origin.edge().id.equals("main");
        var hits=network.scan(origin,east,markers,160,true);
        assert hits.size()==2 && hits.get(0).marker().limit()==80 && hits.get(0).distance()==20;
        assert hits.get(1).distance()==130;
        assert network.scan(origin,east,markers,160,false).isEmpty();
        var passed=network.project(new Vec3(60,3,0),east,1.4,8,1);
        var behind=network.scan(passed,east,markers,160,false);
        assert behind.size()==1 && behind.getFirst().marker().limit()==80 && behind.getFirst().distance()==20;
        var backward=network.scan(passed,east.scale(-1),markers,160,true);
        assert backward.size()==1 && backward.getFirst().marker().limit()==20;
        var upper=network.project(new Vec3(20,10,0),east,1.4,8,1);
        assert upper!=null && upper.edge().id.equals("overhead");
        assert network.project(new Vec3(20,3,2),east,1.4,8,1)==null;
        var fork=new RailNetwork(List.of(
                edge("approach","a","b",new Vec3(0,0,0),new Vec3(100,0,0)),
                edge("straight","b","c",new Vec3(100,0,0),new Vec3(200,0,0)),
                edge("diverge","b","d",new Vec3(100,0,0),new Vec3(200,0,40))));
        var forkMarkers=List.of(new RailNetwork.Marker("fork1","straight",20,east,80),
                new RailNetwork.Marker("fork2","diverge",20,new Vec3(1,0,.4).normalize(),40));
        assert fork.scan(fork.project(new Vec3(80,2,0),east,1.4,8,1),east,forkMarkers,200,true).isEmpty();
        var curve=new RailNetwork(List.of(edge("curve","a","b",new Vec3(0,0,0),new Vec3(10,0,0),new Vec3(20,0,10))));
        var curveProjection=curve.project(new Vec3(15,2,5),new Vec3(1,0,1),1.4,8,1);
        assert curveProjection!=null && Math.abs(curveProjection.distance()-(10+Math.sqrt(50)))<1E-8;
        assert CabTrackLimits.warningDistance(300,80,.7)>4000;
        assert CabTrackLimits.warningDistance(70,80,.7)==0;
        var audio=new AtpAudioController();
        assert audio.tick(true,100,350,"80:80")==AtpAnnouncement.REDUCE_SPEED;
        for(int i=0;i<300;i++)assert audio.tick(true,100,350,"80:80")==null;
        assert audio.tick(true,100,350,"")==null;
        assert audio.tick(true,100,350,"40:40")==AtpAnnouncement.REDUCE_SPEED;
        audio.clear();audio.request(AtpAnnouncement.PLATFORM_LEFT);
        assert audio.tick(true,0,350)==AtpAnnouncement.PLATFORM_LEFT;
        assert audio.tick(true,100,350,"lower")==null;
        for(int i=2;i<=AtpAnnouncement.PLATFORM_LEFT.ticks;i++)assert audio.tick(true,0,350,"")==null;
        System.out.println("Track limits: parallel/stacked track isolation, sign direction, along-curve distances, passed limits, junction stop, preview range and advance voice deduplication passed");
    }
}
