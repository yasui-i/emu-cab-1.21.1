import com.yasui_i.emucab.telemetry.BogeyMotorMath;

public final class BogeyMotorMathCheck {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        check(Math.abs(BogeyMotorMath.wheelRpm(Math.PI,.5)-60)<1E-5,"Wheel surface speed to RPM");
        check(Math.abs(BogeyMotorMath.wheelRpm(-Math.PI,.5)-60)<1E-5,"Reverse wheel pitch");
        check(BogeyMotorMath.wheelRpm(50,0)==0&&BogeyMotorMath.wheelRpm(Double.NaN,.5)==0,"Corrupt speed/radius");
        check(Math.abs(BogeyMotorMath.tractionLoad(2000,10,100,2)-.5)<1E-6,"Two axle load normalization");
        check(BogeyMotorMath.tractionLoad(20000,10,100,2)==1,"Clamped load");
        check(BogeyMotorMath.tractionLoad(2000,0,100,2)==0,"Zero command cannot fabricate traction");
        check(BogeyMotorMath.tractionLoad(Double.NaN,10,100,2)==0,"Invalid force");
        check(BogeyMotorMath.unit(Double.NaN)==0&&BogeyMotorMath.unit(-1)==0&&BogeyMotorMath.unit(2)==1,"Bounded control");
        System.out.println("PASS actual wheel-speed RPM conversion, reverse, corrupt input, measured two-axle force normalization and zero-command silence");
    }
}
