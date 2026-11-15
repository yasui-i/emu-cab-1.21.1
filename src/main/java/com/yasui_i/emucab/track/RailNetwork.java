package com.yasui_i.emucab.track;

import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Sampled world track geometry, independent of Create train entities and Tramways. */
public final class RailNetwork {
    public record Sample(Vec3 point,double distance) {}
    public record Projection(Edge edge,double distance,Vec3 point,Vec3 tangent,double error) {}
    public record Marker(String id,String edge,double position,Vec3 forward,int limit) {}
    public record Hit(Marker marker,double distance) {}
    public record Passage(double distance,List<Hit> hits) {}
    public static final class Edge {
        public final String id,start,end;
        public final List<Sample> samples;
        public final double length;
        public Edge(String id,String start,String end,List<Vec3> points) {
            if(points.size()<2)throw new IllegalArgumentException("Track needs two samples");
            this.id=id;this.start=start;this.end=end;
            var out=new ArrayList<Sample>();double distance=0;
            for(int i=0;i<points.size();i++) {
                if(i>0)distance+=points.get(i).distanceTo(points.get(i-1));
                out.add(new Sample(points.get(i),distance));
            }
            samples=List.copyOf(out);length=distance;
        }
        public Vec3 tangent(double position) {
            for(int i=1;i<samples.size();i++)if(position<=samples.get(i).distance() || i==samples.size()-1)
                return samples.get(i).point().subtract(samples.get(i-1).point()).normalize();
            return Vec3.ZERO;
        }
        public Vec3 point(double position) {
            for(int i=1;i<samples.size();i++)if(position<=samples.get(i).distance()||i==samples.size()-1){
                var a=samples.get(i-1);var b=samples.get(i);
                double span=b.distance()-a.distance();
                return a.point().lerp(b.point(),span<1E-8?0:Math.clamp((position-a.distance())/span,0,1));
            }
            return samples.getFirst().point();
        }
    }
    private record Segment(Edge edge,int index) {}
    private final Map<String,Edge> edges=new HashMap<>();
    private final Map<String,List<Edge>> nodes=new HashMap<>();
    private final Map<Long,List<Segment>> cells=new HashMap<>();
    public RailNetwork(Collection<Edge> source) {
        for(var edge:source) {
            if(edge.length<1E-6)continue;
            edges.put(edge.id,edge);
            nodes.computeIfAbsent(edge.start,k->new ArrayList<>()).add(edge);
            nodes.computeIfAbsent(edge.end,k->new ArrayList<>()).add(edge);
            for(int i=1;i<edge.samples.size();i++) {
                var a=edge.samples.get(i-1).point();var b=edge.samples.get(i).point();
                for(int x=cell(Math.min(a.x,b.x));x<=cell(Math.max(a.x,b.x));x++)
                    for(int z=cell(Math.min(a.z,b.z));z<=cell(Math.max(a.z,b.z));z++)
                        cells.computeIfAbsent(key(x,z),k->new ArrayList<>()).add(new Segment(edge,i));
            }
        }
    }
    private static int cell(double x) { return (int)Math.floor(x/16); }
    private static long key(int x,int z) { return (long)x<<32 ^ (z&0xffffffffL); }
    public Vec3 markerPoint(Marker marker){var edge=edges.get(marker.edge());return edge==null?null:edge.point(marker.position());}
    /** Reject parallel/stacked lines and headings inconsistent with the track. */
    public Projection project(Vec3 point,Vec3 heading,double horizontal,double below,double above) {
        Projection best=null;double second=Double.MAX_VALUE;
        var tested=new HashSet<Segment>();
        for(int x=cell(point.x-horizontal);x<=cell(point.x+horizontal);x++)
            for(int z=cell(point.z-horizontal);z<=cell(point.z+horizontal);z++)
                for(var segment:cells.getOrDefault(key(x,z),List.of())) {
                    if(!tested.add(segment))continue;
                    var edge=segment.edge();var a=edge.samples.get(segment.index()-1);var b=edge.samples.get(segment.index());
                    Vec3 delta=b.point().subtract(a.point());double square=delta.lengthSqr();if(square<1E-10)continue;
                    Vec3 tangent=delta.normalize();
                    if(heading.lengthSqr()>1E-8 && Math.abs(tangent.dot(heading.normalize()))<.65)continue;
                    double t=Math.clamp(point.subtract(a.point()).dot(delta)/square,0,1);
                    Vec3 projected=a.point().add(delta.scale(t)),offset=point.subtract(projected);
                    double side=offset.x*offset.x+offset.z*offset.z;
                    if(side>horizontal*horizontal || offset.y>below || offset.y< -above)continue;
                    double score=side+offset.y*offset.y*.15;
                    var candidate=new Projection(edge,a.distance()+t*(b.distance()-a.distance()),projected,tangent,score);
                    if(best==null || score<best.error()) {
                        if(best!=null && !best.edge().id.equals(edge.id))second=best.error();
                        best=candidate;
                    } else if(!best.edge().id.equals(edge.id))second=Math.min(second,score);
                }
        // Crossing/overlapping tracks can be geometrically indistinguishable.
        if(best!=null && second-best.error()<.025 && best.distance()>.5 && best.distance()<best.edge().length-.5)return null;
        return best;
    }
    /** Walk only unambiguous continuations. Physical route choice is observed after a fork. */
    public List<Hit> scan(Projection origin,Vec3 heading,List<Marker> markers,double range,boolean ahead) {
        if(origin==null || heading.lengthSqr()<1E-8)return List.of();
        Edge edge=origin.edge();boolean plus=origin.tangent().dot(heading)>=0;
        if(!ahead)plus=!plus;
        double position=origin.distance(),travelled=0;
        var hits=new ArrayList<Hit>();var visited=new HashSet<String>();
        while(edge!=null && travelled<=range && visited.size()<1024) {
            if(!visited.add(edge.id+":"+plus))break;
            for(var marker:markers)if(marker.edge().equals(edge.id)) {
                double distance=plus?marker.position()-position:position-marker.position();
                Vec3 travel=edge.tangent(marker.position()).scale(plus?1:-1);
                Vec3 actualTravel=ahead?travel:travel.scale(-1);
                if(distance>=-1E-6 && travelled+distance<=range && accepts(marker,actualTravel))
                    hits.add(new Hit(marker,Math.max(0,travelled+distance)));
            }
            travelled+=plus?edge.length-position:position;
            if(travelled>range)break;
            String at=plus?edge.end:edge.start;
            Vec3 incoming=edge.tangent(plus?edge.length:0).scale(plus?1:-1);
            Edge next=null;boolean nextPlus=false;int choices=0;
            for(var candidate:nodes.getOrDefault(at,List.of())) {
                if(candidate==edge)continue;
                boolean outward=candidate.start.equals(at);
                Vec3 direction=candidate.tangent(outward?0:candidate.length).scale(outward?1:-1);
                if(incoming.dot(direction)<.45)continue;
                choices++;next=candidate;nextPlus=outward;
            }
            if(choices!=1)break;
            edge=next;plus=nextPlus;position=plus?0:edge.length;
        }
        hits.sort(Comparator.comparingDouble(Hit::distance).thenComparingInt(h->h.marker().limit()));
        return hits;
    }
    private static boolean accepts(Marker marker,Vec3 travel) {
        return marker.forward().lengthSqr()<1E-8 || marker.forward().normalize().dot(travel)>.65;
    }
    /** Resolve the route actually traversed, using both endpoints to select a fork.
     * Ambiguous routes and discontinuous/teleported positions produce no passage.
     */
    public Passage passage(Projection from,Projection to,Vec3 heading,List<Marker> markers,double maxDistance) {
        if(from==null||to==null||heading.lengthSqr()<1E-8||maxDistance<=0)return null;
        var paths=new ArrayList<Passage>();
        walkPassage(from.edge(),from.distance(),from.tangent().dot(heading)>=0,to,markers,
                maxDistance,0,new ArrayList<>(),new HashSet<>(),paths,new int[]{0});
        return paths.size()==1?paths.getFirst():null;
    }
    private void walkPassage(Edge edge,double start,boolean plus,Projection to,List<Marker> markers,
                             double range,double travelled,List<Hit> earlier,Set<String> visited,
                             List<Passage> results,int[] budget) {
        if(++budget[0]>512||results.size()>1||visited.size()>64||travelled>range)return;
        String key=edge.id+":"+plus;if(!visited.add(key))return;
        double end=plus?edge.length:0;
        boolean reached=edge.id.equals(to.edge().id) && (plus?to.distance()>=start-1E-6:to.distance()<=start+1E-6);
        if(reached)end=to.distance();
        double span=Math.abs(end-start);
        if(travelled+span>range+1E-6){visited.remove(key);return;}
        var hits=new ArrayList<>(earlier);
        for(var marker:markers)if(marker.edge().equals(edge.id)) {
            double distance=plus?marker.position()-start:start-marker.position();
            // Exclude the previous endpoint: a train stopped on a balise must
            // not receive it again on the next sample.
            if(distance>=-1E-6 && travelled+distance>1E-6 && distance<=span+1E-6
                    && accepts(marker,edge.tangent(marker.position()).scale(plus?1:-1)))
                hits.add(new Hit(marker,travelled+distance));
        }
        if(reached) {
            hits.sort(Comparator.comparingDouble(Hit::distance));
            results.add(new Passage(travelled+span,List.copyOf(hits)));
        } else {
            String at=plus?edge.end:edge.start;
            Vec3 incoming=edge.tangent(end).scale(plus?1:-1);
            for(var next:nodes.getOrDefault(at,List.of())) {
                if(next==edge)continue;
                boolean outward=next.start.equals(at);
                if(incoming.dot(next.tangent(outward?0:next.length).scale(outward?1:-1))<.45)continue;
                walkPassage(next,outward?0:next.length,outward,to,markers,range,travelled+span,hits,visited,results,budget);
            }
        }
        visited.remove(key);
    }
}
