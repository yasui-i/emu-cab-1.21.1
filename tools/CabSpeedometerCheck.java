import com.yasui_i.emucab.control.CabSpeedometer;

/** Authoritative samples remain stable at high speeds and ease between client ticks. */
public final class CabSpeedometerCheck {
    private static void close(double actual,double expected,double tolerance,String message) {
        assert Math.abs(actual-expected)<=tolerance:message+": "+actual+" vs "+expected;
    }
    public static void main(String[] args) {
        var gauge=new CabSpeedometer();
        close(gauge.value(0),0,0,"No sample");
        assert !gauge.accept(0,Double.NaN);
        assert !gauge.accept(0,Double.POSITIVE_INFINITY);
        assert !gauge.accept(0,-1);
        assert gauge.accept(1,200);
        close(gauge.value(.5F),200,0,"First sample immediately usable");
        for(int i=2;i<400;i++) {
            if(i%2==0)assert gauge.accept(i,200);
            gauge.tick();
            for(float partial:new float[]{0,.25F,.5F,.75F,1})close(gauge.value(partial),200,1e-8,"Constant speed drifts");
        }
        assert !gauge.accept(2,450):"Delayed sample accepted";
        assert !gauge.accept(gauge.latestSample(),0):"Duplicate sample accepted";
        close(gauge.target(),200,0,"Old packets changed target");
        assert gauge.accept(400,350);
        double previous=200;
        for(int i=0;i<50;i++) {
            gauge.tick();
            double start=gauge.value(0),mid=gauge.value(.5F),end=gauge.value(1);
            close(start,previous,1e-8,"Tick transition jumps");
            assert start<=mid && mid<=end && end<=350:"Acceleration overshoot";
            close(mid,(start+end)/2,1e-8,"Missing frame interpolation");
            close(gauge.value(-1),start,1e-8,"Negative partial not clamped");
            close(gauge.value(2),end,1e-8,"Large partial not clamped");
            previous=end;
        }
        close(gauge.latest(),350,.02,"Acceleration fails to settle");
        assert gauge.accept(401,0);
        for(int i=0;i<60;i++) {
            gauge.tick();double shown=gauge.value(1);
            assert shown>=0 && shown<=previous:"Deceleration overshoot";
            previous=shown;
        }
        close(gauge.latest(),0,.02,"Stop fails to settle");
        for(double speed:new double[]{20,100,200,350,450,1000}) {
            var steady=new CabSpeedometer();assert steady.accept(1,speed);
            for(int tick=2;tick<200;tick++) {
                if(tick%2==0)assert steady.accept(tick,speed);
                steady.tick();close(steady.value(.43F),speed,1e-8,"High-speed constant input jitters");
            }
        }
        System.out.println("Speedometer: stable 20-1000 km/h, stale/invalid sample rejection, acceleration/deceleration bounds, stop settling and frame interpolation passed");
    }
}
