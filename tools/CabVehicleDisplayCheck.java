import com.yasui_i.emucab.control.*;

public class CabVehicleDisplayCheck {
    public static void main(String[] args){
        var state=new CabControlState();
        for(int car=1;car<=8;car++)assert !CabVehicleDisplay.traction(state,car);
        state.notch=1;
        for(int car=1;car<=8;car++)assert CabVehicleDisplay.traction(state,car)==(car==2||car==4||car==5||car==7);
        state.notch=7;assert CabVehicleDisplay.traction(state,2);
        state.notch=-1;assert !CabVehicleDisplay.traction(state,2)&&CabVehicleDisplay.braking(state);
        state.notch=0;assert !CabVehicleDisplay.braking(state);
        state.emergency=true;assert CabVehicleDisplay.braking(state)&&!CabVehicleDisplay.traction(state,2);
        state.emergency=false;state.parkingBrake=true;assert CabVehicleDisplay.braking(state);
        state.parkingBrake=false;state.switches.put(CabAction.HOLDING_BRAKE,1);assert CabVehicleDisplay.braking(state);
        state.switches.put(CabAction.HOLDING_BRAKE,0);state.switches.put(CabAction.PANTOGRAPH,1);state.switches.put(CabAction.BREAKER,1);
        state.notch=-2;
        for(int car=1;car<=8;car++)assert CabVehicleDisplay.electricBrake(state,80,car)==CabVehicleDisplay.motorCar(car);
        assert !CabVehicleDisplay.airPrecontrol(state,80);
        state.notch=-4;assert CabVehicleDisplay.airPrecontrol(state,80)&&CabVehicleDisplay.electricBrake(state,80,2);
        state.notch=-1;assert CabVehicleDisplay.airPrecontrol(state,4)&&!CabVehicleDisplay.electricBrake(state,4,2);
        state.emergency=true;assert CabVehicleDisplay.airPrecontrol(state,80)&&!CabVehicleDisplay.electricBrake(state,80,2)&&CabVehicleDisplay.brakeDemand(state)==1;
        state.emergency=false;state.notch=0;assert !CabVehicleDisplay.airPrecontrol(state,80)&&CabVehicleDisplay.brakeDemand(state)==0;
        state.notch=-7;assert CabVehicleDisplay.brakeDemand(state)==1;
        assert CabVehicleDisplay.carCenter(2)==82 && CabVehicleDisplay.carCenter(4)==120
            &&CabVehicleDisplay.carCenter(5)==139&&CabVehicleDisplay.carCenter(7)==177;
        var pantos=new CabPantographs();
        assert pantos.selected(1)==-1&&pantos.selected(0)==-1&&pantos.selected(-1)==1;
        for(int dir=-1;dir<=1;dir++){
            for(int mask=0;mask<4;mask++){
                pantos.cutFront=(mask&1)!=0;pantos.cutRear=(mask&2)!=0;
                int count=(pantos.up(1,dir,true)?1:0)+(pantos.up(-1,dir,true)?1:0);
                assert count==(mask==3?0:1):"double raise or no fallback";
                assert !pantos.up(1,dir,false)&&!pantos.up(-1,dir,false);
                if(pantos.cutFront)assert !pantos.up(1,dir,true);
                if(pantos.cutRear)assert !pantos.up(-1,dir,true);
            }
        }
        pantos.cutFront=false;pantos.cutRear=true;assert pantos.selected(1)==1;
        for(int endpoint:new int[]{1,8})for(int direction=-1;direction<=1;direction++)for(int mask=0;mask<4;mask++){
            pantos.cutFront=(mask&1)!=0;pantos.cutRear=(mask&2)!=0;
            int selected=pantos.selectedCar(endpoint,direction);
            assert selected==0||selected==3||selected==6;
            assert (selected==0)==(mask==3);
            assert selected!=3||!pantos.cutFront;
            assert selected!=6||!pantos.cutRear;
            if(mask==0)assert selected==((endpoint==8)^(direction<0)?3:6);
        }
        System.out.println("Vehicle display: P1-P7 motor cars 2/4/5/7, no traction at N/brake/EB, service/emergency/parking/holding brake indication and car alignment passed");
    }
}
