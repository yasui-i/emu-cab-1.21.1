import com.yasui_i.emucab.control.GradualThrottleMath;

public class GradualThrottleCheck {
    public static void main(String[] args) {
        assert GradualThrottleMath.targetRpm(0,17,256)==0;
        assert GradualThrottleMath.targetRpm(1,17,256)==17;
        assert GradualThrottleMath.targetRpm(15,17,256)==255;
        assert GradualThrottleMath.targetRpm(99,17,256)==255;
        assert GradualThrottleMath.advanceRpm(0,15,17,256)==1;
        assert GradualThrottleMath.advanceRpm(254,15,17,256)==255;
        assert GradualThrottleMath.advanceRpm(255,0,17,256)==0;
        assert GradualThrottleMath.advanceRpm(255,-1,17,256)==0;
        assert GradualThrottleMath.advanceRpm(255,14,17,256)==254;
        assert GradualThrottleMath.advanceRpm(136,1,17,256)==135;
        assert GradualThrottleMath.advanceRpm(0,0,17,256)==0;
        assert GradualThrottleMath.targetRpm(15,20,512)==300;
        assert GradualThrottleMath.targetRpm(15,20,256)==256;
        assert GradualThrottleMath.targetRpm(15,40,512)==512;
        assert GradualThrottleMath.targetRpm(1,20,512)==20;
        assert GradualThrottleMath.targetRpm(0,20,512)==0;
        assert GradualThrottleMath.targetRpm(15,Integer.MAX_VALUE,512)==512;
        assert GradualThrottleMath.advanceRpm(300,0,20,256)==0;
        assert GradualThrottleMath.advanceRpm(300,15,20,256)==256;
        assert GradualThrottleMath.advanceRpm(299,15,20,512)==300;
        assert GradualThrottleMath.advanceRpm(300,14,20,512)==299;
        assert GradualThrottleMath.targetRpm(15,17,512)==255; // ceiling never rescales the signal
        System.out.println("Gradual throttle: custom RPM-per-level; 15x20=300 at cap512 / 256 at cap256; 15x40 capped512; overflow-safe; zero stops immediately, nonzero changes by 1 RPM per interval");
    }
}
