import com.yasui_i.emucab.control.CabElectricalDisplay;

public final class CabElectricalDisplayCheck {
    static void close(double a,double b){assert Math.abs(a-b)<1E-8:a+" vs "+b;}
    public static void main(String[] args){
        close(CabElectricalDisplay.voltageFraction(17500),0);
        close(CabElectricalDisplay.voltageFraction(25000),.5);
        close(CabElectricalDisplay.voltageFraction(32500),1);
        close(CabElectricalDisplay.voltageFraction(21250),.25);
        close(CabElectricalDisplay.voltageFraction(0),0);
        close(CabElectricalDisplay.voltageFraction(40000),1);
        close(CabElectricalDisplay.voltageFraction(Double.NaN),0);
        close(CabElectricalDisplay.currentFraction(177.4),177.4/600);
        close(CabElectricalDisplay.currentFraction(300),.5);
        close(CabElectricalDisplay.currentFraction(600),1);
        close(CabElectricalDisplay.currentFraction(-2),0);
        close(CabElectricalDisplay.currentFraction(Double.POSITIVE_INFINITY),0);
        var display=new CabElectricalDisplay();close(display.voltage(1),0);
        display.tick(25000,300);close(display.voltage(.5F),25000);close(display.current(.5F),300);
        for(int i=0;i<100;i++){display.tick(25000,300);close(display.voltage(.5F),25000);}
        double previous=25000;
        for(int i=0;i<40;i++){
            display.tick(30000,600);
            close(display.voltage(0),previous);
            assert display.voltage(0)<=display.voltage(.5F)&&display.voltage(.5F)<=display.voltage(1)&&display.voltage(1)<=30000;
            previous=display.voltage(1);
        }
        display.tick(0,0);close(display.voltage(1),0);close(display.current(1),0);
        display.tick(Double.NaN,Double.POSITIVE_INFINITY);close(display.voltage(.5F),0);close(display.current(.5F),0);
        System.out.println("Electrical display: 17.5/21.25/25/32.5 kV align at 0/25/50/100%; current0/300/600A, clamping, invalid values, constant stability, monotonic tick/frame interpolation and disconnection zero passed");
    }
}
