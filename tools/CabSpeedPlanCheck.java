import com.yasui_i.emucab.track.CabSpeedPlan;
import com.yasui_i.emucab.control.CabAtpAcoustics;
import java.util.List;

public class CabSpeedPlanCheck {
    public static void main(String[] args) {
        var plan=new CabSpeedPlan(true,1024,.7,List.of(new CabSpeedPlan.Target(650,80)));
        double expected=Math.sqrt(Math.pow(80/3.6,2)+2*.7*650)*3.6;
        assert Math.abs(plan.speedAt(350,0)-expected)<1E-8;
        double previous=350;
        for(int x=0;x<=1024;x++){
            double value=plan.speedAt(350,x);
            assert value<=previous && value>=80 && Double.isFinite(value);
            previous=value;
        }
        assert plan.speedAt(350,650)==80;
        var closer=new CabSpeedPlan(true,1024,.7,List.of(new CabSpeedPlan.Target(200,80)));
        assert closer.speedAt(350,0)<plan.speedAt(350,0);
        var multi=new CabSpeedPlan(true,2048,.7,List.of(
                new CabSpeedPlan.Target(1200,40),new CabSpeedPlan.Target(500,120)));
        assert Math.abs(multi.speedAt(350,500)-Math.sqrt(Math.pow(40/3.6,2)+2*.7*700)*3.6)<1E-8;
        assert multi.speedAt(350,1199)>40 && multi.speedAt(350,1199)<41;
        assert multi.speedAt(350,1200)==40;
        var increase=new CabSpeedPlan(true,1024,.7,List.of(new CabSpeedPlan.Target(500,200)));
        assert increase.speedAt(80,499)==80 && increase.speedAt(80,500)==200;
        assert CabSpeedPlan.load(plan.save()).equals(plan);
        var bad=new CabSpeedPlan(true,Double.NaN,Double.NaN,List.of(
                new CabSpeedPlan.Target(Double.NaN,80),new CabSpeedPlan.Target(50,Double.POSITIVE_INFINITY),
                new CabSpeedPlan.Target(-20,80),new CabSpeedPlan.Target(2000,80)));
        assert bad.targets().isEmpty() && Double.isFinite(bad.speedAt(350,Double.NaN));
        assert CabSpeedPlan.EMPTY.speedAt(350,0)==350 && !CabSpeedPlan.EMPTY.available();
        assert CabAtpAcoustics.gain(0)==1;
        assert Math.abs(CabAtpAcoustics.gain(16)-1)<1E-8;
        assert Math.abs(CabAtpAcoustics.gain(36)-.5)<1E-8;
        assert CabAtpAcoustics.gain(63.9)>0 && CabAtpAcoustics.gain(64)==0;
        assert CabAtpAcoustics.gain(64.01)==0 && CabAtpAcoustics.gain(1024)==0;
        assert CabAtpAcoustics.gain(Double.NaN)==0;
        System.out.println("Speed plan: multi-target braking envelope, approach, passage, no early speed increase, NBT round trip, missing/invalid input; ATP 8-block radius passed");
    }
}
