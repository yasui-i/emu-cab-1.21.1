package com.yasui_i.emucab.control;

/** Pure RPM mapping used by the Create-backed gradual throttle. */
public final class GradualThrottleMath {
    private GradualThrottleMath() {}
    public static int targetRpm(int redstoneStrength,int rpmPerLevel,int maximum){
        return (int)Math.min(Math.max(0,maximum),(long)Math.clamp(redstoneStrength,0,15)*Math.max(1,rpmPerLevel));
    }
    public static int advanceRpm(int current,int redstoneStrength,int rpmPerLevel,int maximum){
        int target=targetRpm(redstoneStrength,rpmPerLevel,maximum);
        if(target==0)return 0;
        current=Math.clamp(current,0,Math.max(0,maximum));
        return current<target?Math.min(target,current+1):current>target?Math.max(target,current-1):current;
    }
}
