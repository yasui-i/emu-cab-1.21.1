import com.yasui_i.emucab.control.CabDisplayFormat;

public class CabDisplayFormatCheck {
    public static void main(String[] args){
        assert CabDisplayFormat.gameTime(0).equals("06:00:00");
        assert CabDisplayFormat.gameTime(6000).equals("12:00:00");
        assert CabDisplayFormat.gameTime(18000).equals("00:00:00");
        assert CabDisplayFormat.gameTime(24000).equals("06:00:00");
        assert CabDisplayFormat.gameTime(-6000).equals("00:00:00");
        assert CabDisplayFormat.validDate("2024-02-29");
        assert !CabDisplayFormat.validDate("2025-02-29");
        assert !CabDisplayFormat.validDate("2026-13-01");
        assert !CabDisplayFormat.validDate("2026-11-31");
        assert !CabDisplayFormat.validDate("2026-1-01");
        assert !CabDisplayFormat.validDate(null);
        assert CabDisplayFormat.locomotive("  CR400-1234  ").equals("CR400-1234");
        assert CabDisplayFormat.locomotive("abc\n\r\t§def").equals("abcdef");
        assert CabDisplayFormat.locomotive("a".repeat(50)).length()==24;
        assert CabDisplayFormat.locomotive(null).isEmpty();
        assert CabDisplayFormat.DEFAULT_FORCE_SCALE_KN==5;
        assert CabDisplayFormat.DEFAULT_BRAKE_SCALE_KN==2;
        assert CabDisplayFormat.forceFraction(1000,CabDisplayFormat.DEFAULT_BRAKE_SCALE_KN)==.5;
        assert CabDisplayFormat.forceFraction(2000,CabDisplayFormat.DEFAULT_BRAKE_SCALE_KN)==1;
        assert Math.abs(CabDisplayFormat.forceFraction(3400,5)-.68)<1E-9;
        assert CabDisplayFormat.forceFraction(15000,5)==1;
        assert CabDisplayFormat.forceFraction(3400,10)==.34;
        assert CabDisplayFormat.forceFraction(-100,5)==0;
        assert CabDisplayFormat.forceFraction(Double.NaN,5)==0;
        assert CabDisplayFormat.forceFraction(1000,0)==0;
        assert !CabDisplayFormat.validForceScale(Double.NaN)&&!CabDisplayFormat.validForceScale(Double.POSITIVE_INFINITY);
        assert !CabDisplayFormat.validForceScale(.09)&&!CabDisplayFormat.validForceScale(1001);
        assert CabDisplayFormat.validForceScale(.1)&&CabDisplayFormat.validForceScale(1000);
        assert CabDisplayFormat.scaleLabel(5).equals("5")&&CabDisplayFormat.scaleLabel(2.5).equals("2.5");
        System.out.println("Cab display formatting: Minecraft 06:00 offset, day wrap, valid leap dates, impossible date rejection and bounded/sanitized locomotive number passed");
    }
}
