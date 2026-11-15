import com.yasui_i.emucab.track.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class BalisePassageCheck {
    static RailNetwork.Edge edge(String id,String a,String b,Vec3... points){return new RailNetwork.Edge(id,a,b,List.of(points));}
    static RailNetwork.Projection at(RailNetwork n,double x,double z,Vec3 heading){return n.project(new Vec3(x,2,z),heading,1.4,8,1);}
    public static void main(String[] args) {
        Vec3 east=new Vec3(1,0,0),west=east.scale(-1);
        var network=new RailNetwork(List.of(edge("a","0","1",new Vec3(0,0,0),new Vec3(100,0,0)),
                edge("b","1","2",new Vec3(100,0,0),new Vec3(200,0,0)),
                edge("parallel","p","q",new Vec3(0,0,4),new Vec3(200,0,4))));
        var markers=List.of(new RailNetwork.Marker("voice","a",50,Vec3.ZERO,0),
                new RailNetwork.Marker("limit","a",60,east,80),
                new RailNetwork.Marker("opposite","a",70,west,40),
                new RailNetwork.Marker("parallel","parallel",50,Vec3.ZERO,0),
                new RailNetwork.Marker("next","b",30,Vec3.ZERO,0));
        var t=new BalisePassageTracker();
        assert t.update("train",1,network,at(network,20,0,east),east,markers,64).isEmpty();
        var hits=t.update("train",6,network,at(network,80,0,east),east,markers,64);
        assert hits.size()==2 && hits.get(0).marker().id().equals("voice") && hits.get(1).marker().id().equals("limit");
        assert t.update("train",11,network,at(network,80,0,east),east,markers,64).isEmpty();
        // Reverse does not invent a crossing on the direction-change sample.
        assert t.update("train",16,network,at(network,80,0,west),west,markers,64).isEmpty();
        hits=t.update("train",21,network,at(network,20,0,west),west,markers,64);
        assert hits.size()==2 && hits.get(0).marker().id().equals("opposite") && hits.get(1).marker().id().equals("voice");
        assert t.update("another",26,network,at(network,80,0,east),east,markers,64).isEmpty();
        var crossing=network.passage(at(network,90,0,east),at(network,150,0,east),east,markers,64);
        assert crossing!=null && crossing.hits().size()==1 && crossing.hits().getFirst().marker().id().equals("next");
        assert network.passage(at(network,20,0,east),at(network,180,0,east),east,markers,64)==null;
        assert network.passage(at(network,20,0,east),at(network,80,4,east),east,markers,100)==null;
        // Reaching exactly the marker triggers once, moving off it does not.
        t.reset();t.update("train",1,network,at(network,40,0,east),east,markers,64);
        assert t.update("train",6,network,at(network,50,0,east),east,markers,64).size()==1;
        assert t.update("train",11,network,at(network,51,0,east),east,markers,64).isEmpty();
        // A parked body rocking across the point stays locked until it leaves.
        t.update("train",16,network,at(network,51,0,west),west,markers,64);
        assert t.update("train",21,network,at(network,49.5,0,west),west,markers,64).isEmpty();
        t.update("train",26,network,at(network,49.5,0,east),east,markers,64);
        assert t.update("train",31,network,at(network,50.5,0,east),east,markers,64).isEmpty();
        t.update("train",36,network,at(network,54,0,east),east,markers,64);
        t.update("train",41,network,at(network,54,0,west),west,markers,64);
        assert t.update("train",46,network,at(network,49,0,west),west,markers,64).size()==1;
        // A device at the beginning of a successor edge is still crossed.
        var nodeMarker=List.of(new RailNetwork.Marker("node","b",0,Vec3.ZERO,0));
        crossing=network.passage(at(network,90,0,east),at(network,110,0,east),east,nodeMarker,64);
        assert crossing!=null&&crossing.hits().size()==1;
        var fork=new RailNetwork(List.of(edge("approach","0","1",new Vec3(0,0,0),new Vec3(100,0,0)),
                edge("straight","1","2",new Vec3(100,0,0),new Vec3(160,0,0)),
                edge("diverge","1","3",new Vec3(100,0,0),new Vec3(160,0,30))));
        var branchMarkers=List.of(new RailNetwork.Marker("chosen","straight",10,Vec3.ZERO,0),
                new RailNetwork.Marker("wrong","diverge",10,Vec3.ZERO,0));
        crossing=fork.passage(at(fork,90,0,east),at(fork,130,0,east),east,branchMarkers,64);
        assert crossing!=null && crossing.hits().size()==1 && crossing.hits().getFirst().marker().id().equals("chosen");
        System.out.println("Balise reception: high-speed swept path, bidirectional voice, directional limit, no stopped repeats, reverse pass, parallel isolation, fork route and teleport rejection passed");
    }
}
