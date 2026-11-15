package com.yasui_i.emucab.control;

/** Two physical pantographs relative to this cab; never commands a double raise. */
public final class CabPantographs {
    public boolean cutFront,cutRear;
    /** Physical side: +1 near/front of this cab, -1 far/rear, 0 no candidate. */
    public int selected(int direction){
        int rear=direction<0?1:-1;
        if(available(rear))return rear;
        return available(-rear)?-rear:0;
    }
    public boolean available(int physical){return physical>0?!cutFront:!cutRear;}
    public boolean up(int physical,int direction,boolean raise){return raise&&selected(direction)==physical;}
    /** Configured consists use absolute 03/06 isolation, independent of the occupied end. */
    public int selectedCar(int endpoint,int direction){
        int preferred=endpoint==8?3:6;
        if(direction<0)preferred=preferred==3?6:3;
        if(preferred==3?!cutFront:!cutRear)return preferred;
        int fallback=preferred==3?6:3;
        return (fallback==3?!cutFront:!cutRear)?fallback:0;
    }
}
