package com.yasui_i.emucab.track;

import com.simibubi.create.Create;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Optional Create geometry adapter; no Tramways code or train navigation is used. */
final class CreateRailGeometry {
    static RailNetwork build(ServerLevel level) {
        var edges=new ArrayList<RailNetwork.Edge>();
        for(var graph:Create.RAILWAYS.sided(level).trackNetworks.values()) {
            for(var location:graph.getNodes()) {
                if(!location.getDimension().equals(level.dimension()))continue;
                var node=graph.locateNode(location);
                for(var entry:graph.getConnectionsFrom(node).entrySet()) {
                    var other=entry.getKey();var edge=entry.getValue();
                    if(node.getNetId()>=other.getNetId() || edge.isInterDimensional())continue;
                    if(!other.getLocation().getDimension().equals(level.dimension()))continue;
                    double length=edge.getLength();if(!Double.isFinite(length) || length<=0 || length>100000)continue;
                    // Subdivide straight diagonals too, keeping the spatial index linear.
                    int steps=Math.min(8192,Math.max(edge.isTurn()?2:1,(int)Math.ceil(length/(edge.isTurn()?1:16))));
                    var points=new ArrayList<Vec3>();
                    for(int i=0;i<=steps;i++)points.add(edge.getPosition(graph,(double)i/steps));
                    String first=graph.id+":"+node.getNetId(),second=graph.id+":"+other.getNetId();
                    edges.add(new RailNetwork.Edge(first+"-"+second,first,second,points));
                }
            }
        }
        return new RailNetwork(edges);
    }
    private CreateRailGeometry() {}
}
